package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.asset.dto.AssetMaintenancePartRequest;
import com.xniu.rental.asset.dto.AssetMaintenanceRequest;
import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.dto.SparePartStockAdjustRequest;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.service.OrderService;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementStatementService;
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
class RentalBusinessFlowIntegrationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BillService billService;

    @Autowired
    private AssetFulfillmentService assetFulfillmentService;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private SettlementIncomeService settlementIncomeService;

    @Autowired
    private SettlementStatementService settlementStatementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentAccount() {
        AuthContext.set(new CurrentAccount(
            "test-token",
            new CurrentAccountResponse(
                1L,
                "PLATFORM_ADMIN",
                "admin",
                "18800000001",
                null,
                "Platform Admin",
                null,
                null,
                null,
                List.of("PLATFORM_ADMIN"),
                List.of("system.admin"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void rentalOrderBillingAssetReturnAndIncomeLedgerStayConsistent() {
        var order = orderService.createOrder(new OrderCreateRequest(
            1001L,
            1L,
            3L,
            1L,
            2L,
            LocalDateTime.of(2026, 7, 1, 10, 0)
        ));

        assertThat(order.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.payableAmount()).isEqualByComparingTo(new BigDecimal("1029.00"));
        assertThat(order.settlementSnapshotId()).isNotNull();
        assertThat(order.items())
            .extracting("itemType")
            .containsExactlyInAnyOrder("SKU", "SIGN_FEE", "ASSET_FRAME", "ASSET_BATTERY");

        var billPlan = billService.generatePlan(order.id(), "integration plan");

        assertThat(billPlan.bills()).hasSize(3);
        assertThat(billPlan.bills())
            .extracting("periodNo")
            .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(sumBillPayable(order.id())).isEqualByComparingTo(order.payableAmount());

        var repeatedBillPlan = billService.generatePlan(order.id(), "repeat plan");
        assertThat(repeatedBillPlan.bills()).isEmpty();

        assertThatThrownBy(() -> assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "too early")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("待取车");

        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");

        var pickup = assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "pickup"));

        assertThat(pickup.frameAssetId()).isEqualTo(1L);
        assertThat(pickup.batteryAssetId()).isEqualTo(2L);
        assertThat(orderStatus(order.id())).isEqualTo("RENTING");
        assertThat(assetStatus(1L)).isEqualTo("RENTING");
        assertThat(assetStatus(2L)).isEqualTo("RENTING");

        var returned = assetFulfillmentService.returnAssets(order.id(), new AssetReturnRequest(null, "IDLE", "IDLE", "return"));

        assertThat(returned.handoverType()).isEqualTo("RETURN");
        assertThat(orderStatus(order.id())).isEqualTo("COMPLETED");
        assertThat(assetStatus(1L)).isEqualTo("IDLE");
        assertThat(assetStatus(2L)).isEqualTo("IDLE");

        var income = settlementIncomeService.generateForOrder(order.id());

        assertThat(income.createdCount()).isEqualTo(6);
        assertThat(income.entries())
            .extracting("lineType")
            .contains(
                "MERCHANT_ORDER_FEE",
                "MERCHANT_RENT_SHARE",
                "PLATFORM_RENT_SHARE",
                "PLATFORM_OPERATION_FEE",
                "MAINTENANCE_FEE",
                "INVESTOR_NET_RENT"
            );
        assertThat(income.entries())
            .filteredOn(entry -> "INVESTOR".equals(entry.beneficiaryType()))
            .singleElement()
            .satisfies(entry -> {
                assertThat(entry.beneficiaryId()).isEqualTo(1L);
                assertThat(entry.amount()).isPositive();
            });

        var repeatedIncome = settlementIncomeService.generateForOrder(order.id());
        assertThat(repeatedIncome.createdCount()).isZero();
        assertThat(repeatedIncome.entries()).hasSameSizeAs(income.entries());
    }

    @Test
    void monthStatementsGroupInvestorRentBeforeRounding() {
        jdbcTemplate.update(
            "UPDATE asset_item SET current_merchant_id = ?, current_store_id = ? WHERE id IN (?, ?)",
            1L,
            1L,
            1L,
            2L
        );

        var order = orderService.createOrder(new OrderCreateRequest(
            1002L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.of(2026, 9, 5, 9, 0)
        ));

        billService.generatePlan(order.id(), "statement rounding test");

        var paidAt = LocalDateTime.of(2026, 9, 6, 10, 0);
        var billId = jdbcTemplate.queryForObject(
            "SELECT id FROM rental_bill WHERE order_id = ? AND bill_type = 'INITIAL' AND period_no = 1",
            Long.class,
            order.id()
        );
        jdbcTemplate.update(
            """
            UPDATE rental_bill
            SET bill_status = 'PAID',
                paid_amount = ?,
                paid_at = ?,
                updated_at = ?
            WHERE id = ?
            """,
            new BigDecimal("429.00"),
            paidAt,
            paidAt,
            billId
        );

        jdbcTemplate.update(
            """
            INSERT INTO asset_maintenance_record
            (maintenance_no, asset_id, order_id, store_id, maintenance_type, maintenance_status,
             started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
             cost_bearer_type, cost_bearer_id, operator_account_id, remark)
            VALUES (?, ?, ?, ?, 'REPAIR', 'COMPLETED', ?, ?, ?, ?, ?, ?, 'INVESTOR', ?, ?, ?)
            """,
            "MT-TEST-INV-" + order.id(),
            1L,
            order.id(),
            1L,
            LocalDateTime.of(2026, 9, 12, 9, 0),
            LocalDateTime.of(2026, 9, 12, 11, 0),
            new BigDecimal("20.00"),
            BigDecimal.ZERO,
            new BigDecimal("30.00"),
            new BigDecimal("50.00"),
            1L,
            1L,
            "statement rounding investor maintenance"
        );

        jdbcTemplate.update(
            """
            INSERT INTO asset_maintenance_record
            (maintenance_no, asset_id, order_id, store_id, maintenance_type, maintenance_status,
             started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
             cost_bearer_type, cost_bearer_id, operator_account_id, remark)
            VALUES (?, ?, ?, ?, 'REPAIR', 'COMPLETED', ?, ?, ?, ?, ?, ?, 'MERCHANT', ?, ?, ?)
            """,
            "MT-TEST-MER-" + order.id(),
            2L,
            order.id(),
            1L,
            LocalDateTime.of(2026, 9, 18, 14, 0),
            LocalDateTime.of(2026, 9, 18, 15, 0),
            new BigDecimal("6.00"),
            BigDecimal.ZERO,
            new BigDecimal("6.00"),
            new BigDecimal("12.00"),
            1L,
            1L,
            "statement rounding merchant maintenance"
        );

        var generated = settlementStatementService.generateMonth("2026-09");

        assertThat(generated.merchantStatementCount()).isEqualTo(1);
        assertThat(generated.investorStatementCount()).isEqualTo(1);

        var investorStatement = jdbcTemplate.queryForMap(
            """
            SELECT rent_base_amount, rent_share_income_amount, operation_fee_amount,
                   maintenance_deduct_amount, payable_amount
            FROM settlement_statement
            WHERE statement_month = '2026-09'
              AND beneficiary_type = 'INVESTOR'
              AND beneficiary_id = 1
            """);
        assertThat(investorStatement.get("rent_base_amount")).isEqualTo(new BigDecimal("399.00"));
        assertThat(investorStatement.get("rent_share_income_amount")).isEqualTo(new BigDecimal("259.35"));
        assertThat(investorStatement.get("operation_fee_amount")).isEqualTo(new BigDecimal("20.75"));
        assertThat(investorStatement.get("maintenance_deduct_amount")).isEqualTo(new BigDecimal("50.00"));
        assertThat(investorStatement.get("payable_amount")).isEqualTo(new BigDecimal("188.60"));

        var merchantStatement = jdbcTemplate.queryForMap(
            """
            SELECT rent_base_amount, sign_fee_income_amount, rent_share_income_amount,
                   maintenance_deduct_amount, payable_amount
            FROM settlement_statement
            WHERE statement_month = '2026-09'
              AND beneficiary_type = 'MERCHANT'
              AND beneficiary_id = 1
              AND merchant_id = 1
              AND store_id = 1
            """);
        assertThat(merchantStatement.get("rent_base_amount")).isEqualTo(new BigDecimal("399.00"));
        assertThat(merchantStatement.get("sign_fee_income_amount")).isEqualTo(new BigDecimal("30.00"));
        assertThat(merchantStatement.get("rent_share_income_amount")).isEqualTo(new BigDecimal("99.75"));
        assertThat(merchantStatement.get("maintenance_deduct_amount")).isEqualTo(new BigDecimal("12.00"));
        assertThat(merchantStatement.get("payable_amount")).isEqualTo(new BigDecimal("117.75"));
    }

    @Test
    void sparePartPurchaseAndRoutineMaintenanceShouldTrackStoreStockAndSettlementAmounts() {
        jdbcTemplate.update(
            "UPDATE asset_item SET current_merchant_id = ?, current_store_id = ? WHERE id = ?",
            1L,
            1L,
            1L
        );

        var platformStockBefore = jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM spare_part_category WHERE id = ?",
            Integer.class,
            1L
        );
        var storeStockBefore = jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM store_spare_part_stock WHERE store_id = ? AND part_id = ?",
            Integer.class,
            1L,
            1L
        );

        maintenanceService.inbound(1L, new SparePartStockAdjustRequest(null, 3, new BigDecimal("18.00"), "integration platform inbound"));
        maintenanceService.purchase(1L, new SparePartStockAdjustRequest(1L, 2, new BigDecimal("28.00"), "integration store purchase"));

        var platformStockAfterPurchase = jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM spare_part_category WHERE id = ?",
            Integer.class,
            1L
        );
        var storeStockAfterPurchase = jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM store_spare_part_stock WHERE store_id = ? AND part_id = ?",
            Integer.class,
            1L,
            1L
        );

        assertThat(platformStockAfterPurchase).isEqualTo(platformStockBefore + 1);
        assertThat(storeStockAfterPurchase).isEqualTo(storeStockBefore + 2);

        var maintenance = maintenanceService.createMaintenance(new AssetMaintenanceRequest(
            1L,
            null,
            1L,
            "REPAIR",
            "COMPLETED",
            "ROUTINE_MAINTENANCE",
            LocalDateTime.of(2026, 10, 2, 10, 0),
            LocalDateTime.of(2026, 10, 2, 12, 0),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            "integration routine maintenance",
            List.of(new AssetMaintenancePartRequest(1L, 1, new BigDecimal("28.00"), "integration use"))
        ));

        assertThat(maintenance.responsibilityType()).isEqualTo("ROUTINE_MAINTENANCE");
        assertThat(maintenance.partsCost()).isEqualByComparingTo(new BigDecimal("28.00"));
        assertThat(maintenance.merchantReimbursementAmount()).isEqualByComparingTo(new BigDecimal("28.00"));
        assertThat(maintenance.investorDeductAmount()).isEqualByComparingTo(new BigDecimal("28.00"));
        assertThat(maintenance.customerChargeAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(maintenance.costBearerType()).isEqualTo("INVESTOR");

        var storeStockAfterConsume = jdbcTemplate.queryForObject(
            "SELECT stock_quantity FROM store_spare_part_stock WHERE store_id = ? AND part_id = ?",
            Integer.class,
            1L,
            1L
        );
        assertThat(storeStockAfterConsume).isEqualTo(storeStockAfterPurchase - 1);
    }

    private BigDecimal sumBillPayable(Long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(payable_amount), 0) FROM rental_bill WHERE order_id = ?",
            BigDecimal.class,
            orderId
        );
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT order_status FROM rental_order WHERE id = ?", String.class, orderId);
    }

    private String assetStatus(Long assetId) {
        return jdbcTemplate.queryForObject("SELECT status FROM asset_item WHERE id = ?", String.class, assetId);
    }

    private void transition(Long orderId, String targetStatus) {
        orderService.transition(orderId, new OrderTransitionRequest(targetStatus, "integration transition"));
    }
}
