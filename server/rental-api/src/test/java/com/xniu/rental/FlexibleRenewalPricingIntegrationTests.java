package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.bill.dto.BillGenerateRequest;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.contract.dto.ContractGenerateRequest;
import com.xniu.rental.contract.dto.PricingAmendmentGenerateRequest;
import com.xniu.rental.contract.service.ContractService;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.order.service.OrderRenewalService;
import com.xniu.rental.order.service.OrderService;
import com.xniu.rental.pricing.dto.RenewalPricingRuleRequest;
import com.xniu.rental.pricing.service.OrderRenewalPricingService;
import com.xniu.rental.product.dto.StoreSkuPackageRequest;
import com.xniu.rental.product.dto.StoreSkuRequest;
import com.xniu.rental.product.service.ProductService;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class FlexibleRenewalPricingIntegrationTests {

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BillService billService;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private OrderRenewalService orderRenewalService;

    @Autowired
    private OrderRenewalPricingService orderRenewalPricingService;

    @Autowired
    private ContractService contractService;

    @Autowired
    private SettlementIncomeService settlementIncomeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        AuthContext.set(adminAccount());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void skuValidationAndOrderCreationSnapshotFlexibleRenewalPrices() {
        var storeSku = productService.listStoreSkus(1L, null, null).stream()
            .filter(item -> item.id().equals(1L))
            .findFirst()
            .orElseThrow();
        var invalidPackages = storeSku.packages().stream().map(item -> new StoreSkuPackageRequest(
            item.packageId(),
            item.rentalAmount(),
            item.periodAmount(),
            item.depositAmount(),
            item.autoRenewEnabled(),
            item.renewalUnit(),
            item.renewalValue(),
            item.packageId().equals(2L) ? new BigDecimal("129.00") : item.renewalAmount(),
            item.packageId().equals(2L) ? "DAILY_CAPPED" : item.renewalBillingMode(),
            item.packageId().equals(2L) ? new BigDecimal("4.00") : item.renewalDailyAmount(),
            true,
            item.packageId().equals(2L) ? 12 : item.renewalGraceHours(),
            item.packageId().equals(2L) ? new BigDecimal("7.00") : item.overdueDailyAmount()
        )).toList();

        assertThatThrownBy(() -> productService.updateStoreSku(1L, storeSkuRequest(storeSku, invalidPackages)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("日租累计整期金额不能低于整期续租价");

        var validPackages = invalidPackages.stream().map(item -> item.packageId().equals(2L)
            ? new StoreSkuPackageRequest(
                item.packageId(), item.rentalAmount(), item.periodAmount(), item.depositAmount(), true,
                "MONTH", 1, new BigDecimal("129.00"), "DAILY_CAPPED", new BigDecimal("5.00"),
                true, 12, new BigDecimal("7.00")
            )
            : item).toList();
        productService.updateStoreSku(1L, storeSkuRequest(storeSku, validPackages));

        var order = createOrder();

        assertThat(order.renewalBillingMode()).isEqualTo("DAILY_CAPPED");
        assertThat(order.renewalAmount()).isEqualByComparingTo("129.00");
        assertThat(order.renewalDailyAmount()).isEqualByComparingTo("5.00");
        assertThat(order.overdueDailyAmount()).isEqualByComparingTo("7.00");
        assertThat(order.renewalGraceHours()).isEqualTo(12);
        assertThat(order.renewalDailyCapEnabled()).isTrue();
    }

    @Test
    void dailyBillKeepsItsSnapshotExtendsExactDaysAndEntersSettlement() {
        configureDailySku(true, "5.00", "7.00", 0);
        var order = activate(createOrder(), LocalDateTime.of(2026, 8, 1, 10, 0));

        var generated = billService.generate(new BillGenerateRequest(
            order.id(), "RENEWAL", null, null, "DAILY", 5, null, "五天续租"
        ));
        var bill = generated.bills().getFirst();
        assertThat(bill.payableAmount()).isEqualByComparingTo("25.00");
        assertThat(bill.renewalChargeMode()).isEqualTo("DAILY");
        assertThat(bill.renewalDays()).isEqualTo(5);
        assertThat(bill.renewalUnitPrice()).isEqualByComparingTo("5.00");

        jdbcTemplate.update("UPDATE rental_order SET renewal_daily_amount = 9.00, renewal_amount = 199.00 WHERE id = ?", order.id());
        var paid = billRepository.markPaid(bill.id(), bill.payableAmount());
        orderRenewalService.handlePaidBill(paid);
        var createdEntries = settlementIncomeService.syncPaidBill(paid);

        assertThat(expectedReturnAt(order.id())).isEqualTo(order.expectedReturnAt().plusDays(5));
        assertThat(billRepository.findBill(bill.id()).orElseThrow().payableAmount()).isEqualByComparingTo("25.00");
        assertThat(createdEntries).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM settlement_income_entry WHERE source_type = 'BILL' AND source_id = ?",
            BigDecimal.class,
            bill.id()
        )).isPositive();
    }

    @Test
    void returnAccrualAddsOnlyTheUnbilledRemainderAfterAnOpenCappedBill() {
        configureDailySku(true, "5.00", "5.00", 0);
        var dueAt = LocalDateTime.now().minusDays(35).plusMinutes(1).withNano(0);
        var order = activate(createOrder(), dueAt);

        var run = orderRenewalService.runDueRenewalsInternal(10, "整期封顶扫描");
        assertThat(run.generatedCount()).isEqualTo(1);
        var firstBill = renewalBills(order.id()).getFirst();
        assertThat(firstBill.renewalDays()).isEqualTo(30);
        assertThat(firstBill.payableAmount()).isEqualByComparingTo("129.00");

        jdbcTemplate.update("""
            UPDATE rental_order
            SET renewal_amount = 139.00, renewal_daily_amount = 6.00, overdue_daily_amount = 6.00
            WHERE id = ?
            """, order.id());

        var returnedAt = dueAt.plusDays(35);
        var remainderBill = billService.generateReturnDailyAccrual(
            orderRepository.findById(order.id()).orElseThrow(), returnedAt, "归还补收"
        );

        assertThat(remainderBill).isNotNull();
        assertThat(remainderBill.renewalChargeMode()).isEqualTo("RETURN_DAILY");
        assertThat(remainderBill.renewalDays()).isEqualTo(5);
        assertThat(remainderBill.payableAmount()).isEqualByComparingTo("30.00");
        assertThat(renewalBills(order.id())).hasSize(2);
        assertThat(renewalBills(order.id()).stream().map(item -> item.payableAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("159.00");
    }

    @Test
    void decreasesApplyImmediatelyWhileIncreasesWaitForSignedAmendment() {
        configureDailySku(true, "5.00", "7.00", 24);
        var order = activate(createOrder(), LocalDateTime.of(2026, 8, 1, 10, 0));
        var oldBill = billService.generate(new BillGenerateRequest(
            order.id(), "RENEWAL", null, null, "DAILY", 5, null, "调价前账单"
        )).bills().getFirst();

        var decrease = orderRenewalPricingService.createAdjustment(order.id(), rule(
            "119.00", "4.00", "6.00", true, 24, "合作优惠降价"
        ));
        assertThat(decrease.revisionStatus()).isEqualTo("APPLIED");
        assertThat(orderService.getOrder(order.id()).renewalAmount()).isEqualByComparingTo("119.00");
        assertThat(billRepository.findBill(oldBill.id()).orElseThrow().payableAmount()).isEqualByComparingTo("25.00");

        var increase = orderRenewalPricingService.createAdjustment(order.id(), rule(
            "139.00", "6.00", "8.00", true, 24, "市场价格调整"
        ));
        assertThat(increase.revisionStatus()).isEqualTo("PENDING_CUSTOMER_CONFIRMATION");
        assertThat(orderService.getOrder(order.id()).renewalAmount()).isEqualByComparingTo("119.00");

        insertVerifiedIdentity(order.id(), order.userAccountId());
        var amendment = contractService.generatePricingAmendment(new PricingAmendmentGenerateRequest(increase.id(), null));
        assertThat(amendment.contractKind()).isEqualTo("PRICE_AMENDMENT");
        assertThat(amendment.renderedContent()).contains("¥119.00", "¥139.00", "市场价格调整");

        AuthContext.set(userAccount(order.userAccountId()));
        var signed = contractService.userConfirmSigned(amendment.id());

        assertThat(signed.contractStatus()).isEqualTo("SIGNED");
        assertThat(orderRenewalPricingService.getRevision(increase.id()).revisionStatus().name()).isEqualTo("APPLIED");
        assertThat(orderService.getUserOrder(order.id()).renewalAmount()).isEqualByComparingTo("139.00");
        assertThat(orderService.getUserOrder(order.id()).renewalDailyAmount()).isEqualByComparingTo("6.00");
        assertThat(billRepository.findBill(oldBill.id()).orElseThrow().payableAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void removingTheCapOrShorteningGraceRequiresCustomerConfirmation() {
        configureDailySku(true, "5.00", "7.00", 24);
        var capOrder = activate(createOrder(), LocalDateTime.of(2026, 8, 1, 10, 0));
        var capRemoval = orderRenewalPricingService.createAdjustment(capOrder.id(), rule(
            "129.00", "5.00", "7.00", false, 24, "取消封顶"
        ));
        assertThat(capRemoval.revisionStatus()).isEqualTo("PENDING_CUSTOMER_CONFIRMATION");
        assertThat(orderService.getOrder(capOrder.id()).renewalDailyCapEnabled()).isTrue();

        var graceOrder = activate(createOrder(), LocalDateTime.of(2026, 8, 2, 10, 0));
        var graceReduction = orderRenewalPricingService.createAdjustment(graceOrder.id(), rule(
            "129.00", "5.00", "7.00", true, 0, "缩短宽限期"
        ));
        assertThat(graceReduction.revisionStatus()).isEqualTo("PENDING_CUSTOMER_CONFIRMATION");
        assertThat(orderService.getOrder(graceOrder.id()).renewalGraceHours()).isEqualTo(24);
    }

    @Test
    void mainContractGenerationRejectsRenewalPriceAmendmentTemplate() {
        var order = createOrder();
        insertVerifiedIdentity(order.id(), order.userAccountId());
        var amendmentTemplateId = jdbcTemplate.queryForObject(
            "SELECT id FROM contract_template WHERE contract_type = 'RENEWAL_PRICE_AMENDMENT' ORDER BY id DESC LIMIT 1",
            Long.class
        );

        assertThatThrownBy(() -> contractService.generateContract(
            new ContractGenerateRequest(order.id(), amendmentTemplateId)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只能用于生成补充协议");
    }

    @Test
    void existingPricingAmendmentStillRequiresStoreAccess() {
        configureDailySku(true, "5.00", "7.00", 24);
        var order = activate(createOrder(), LocalDateTime.of(2026, 8, 1, 10, 0));
        var increase = orderRenewalPricingService.createAdjustment(order.id(), rule(
            "139.00", "6.00", "8.00", true, 24, "跨门店权限测试"
        ));
        insertVerifiedIdentity(order.id(), order.userAccountId());
        contractService.generatePricingAmendment(new PricingAmendmentGenerateRequest(increase.id(), null));

        AuthContext.set(restrictedStoreAccount());

        assertThatThrownBy(() -> contractService.generatePricingAmendment(
            new PricingAmendmentGenerateRequest(increase.id(), null)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    private StoreSkuRequest storeSkuRequest(
        com.xniu.rental.product.dto.StoreSkuResponse storeSku,
        List<StoreSkuPackageRequest> packages
    ) {
        return new StoreSkuRequest(
            storeSku.merchantId(), storeSku.storeId(), storeSku.skuId(), storeSku.displayName(), storeSku.saleMode(),
            storeSku.signFeeAmount(), storeSku.signFeePayer(), packages
        );
    }

    private void configureDailySku(boolean capEnabled, String dailyAmount, String overdueDailyAmount, int graceHours) {
        jdbcTemplate.update("""
            UPDATE store_sku_package
            SET auto_renew_enabled = 1,
                renewal_unit = 'MONTH',
                renewal_value = 1,
                renewal_amount = 129.00,
                renewal_billing_mode = 'DAILY_CAPPED',
                renewal_daily_amount = ?,
                renewal_daily_cap_enabled = ?,
                renewal_grace_hours = ?,
                overdue_daily_amount = ?
            WHERE store_sku_id = 1 AND package_id = 2
            """, new BigDecimal(dailyAmount), capEnabled, graceHours,
            overdueDailyAmount == null ? null : new BigDecimal(overdueDailyAmount));
    }

    private com.xniu.rental.order.dto.OrderResponse createOrder() {
        return orderService.createOrder(new OrderCreateRequest(
            1004L, "灵活续租测试用户", "13800139004", 1L, 2L, null, null, null
        ));
    }

    private RentalOrder activate(com.xniu.rental.order.dto.OrderResponse order, LocalDateTime expectedReturnAt) {
        jdbcTemplate.update("""
            UPDATE rental_order
            SET order_status = 'RENTING', paid_amount = 99.00,
                lease_started_at = ?, expected_return_at = ?
            WHERE id = ?
            """, expectedReturnAt.minusDays(30), expectedReturnAt, order.id());
        return orderRepository.findById(order.id()).orElseThrow();
    }

    private RenewalPricingRuleRequest rule(
        String periodAmount,
        String dailyAmount,
        String overdueDailyAmount,
        boolean capEnabled,
        int graceHours,
        String reason
    ) {
        return new RenewalPricingRuleRequest(
            true, "MONTH", 1, new BigDecimal(periodAmount), "DAILY_CAPPED", new BigDecimal(dailyAmount),
            capEnabled, graceHours, new BigDecimal(overdueDailyAmount), reason
        );
    }

    private List<com.xniu.rental.bill.model.RentalBill> renewalBills(Long orderId) {
        return billRepository.listBills(null, orderId, null).stream()
            .filter(item -> item.billType() == BillType.RENEWAL)
            .sorted(java.util.Comparator.comparing(com.xniu.rental.bill.model.RentalBill::id))
            .toList();
    }

    private void insertVerifiedIdentity(Long orderId, Long userAccountId) {
        jdbcTemplate.update("""
            INSERT INTO user_identity_verification
            (user_account_id, order_id, ocr_status, real_name_status, real_name_masked, id_no_masked, verified_at)
            VALUES (?, ?, 'SUCCESS', 'VERIFIED', '续租用户', '310***********1234', CURRENT_TIMESTAMP)
            """, userAccountId, orderId);
    }

    private LocalDateTime expectedReturnAt(Long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT expected_return_at FROM rental_order WHERE id = ?",
            LocalDateTime.class,
            orderId
        );
    }

    private CurrentAccount adminAccount() {
        return new CurrentAccount(
            "flexible-renewal-admin-token",
            new CurrentAccountResponse(
                1L, "PLATFORM_ADMIN", "admin", "18800000001", null, "平台管理员",
                null, null, null, List.of("PLATFORM_ADMIN"), List.of("system.admin"), List.of()
            )
        );
    }

    private CurrentAccount userAccount(Long id) {
        return new CurrentAccount(
            "flexible-renewal-user-token",
            new CurrentAccountResponse(
                id, "USER", "renewal-user", "13800139004", "2088-renewal-user", "续租用户",
                null, null, null, List.of("USER"), List.of(), List.of()
            )
        );
    }

    private CurrentAccount restrictedStoreAccount() {
        return new CurrentAccount(
            "flexible-renewal-restricted-token",
            new CurrentAccountResponse(
                2001L, "STORE_STAFF", "restricted-store", "13800139999", null, "其他门店人员",
                1L, 99L, null, List.of("STORE_STAFF"), List.of("order.operate"),
                List.of(new StoreScopeResponse(1L, 99L, "SINGLE_STORE"))
            )
        );
    }
}
