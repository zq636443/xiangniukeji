package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.xniu.rental.asset.dto.AssetMaintenancePartRequest;
import com.xniu.rental.asset.dto.AssetMaintenanceRequest;
import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReplaceRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.dto.SparePartStockAdjustRequest;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.service.OrderRenewalService;
import com.xniu.rental.order.service.OrderService;
import com.xniu.rental.pay.service.AlipayGatewayClient;
import com.xniu.rental.pay.service.AgreementDeductService;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import com.xniu.rental.settlement.dto.SettlementPreviewRequest;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.dto.StoreProfitRuleUpdateRequest;
import com.xniu.rental.voucher.dto.VoucherPrepareRequest;
import com.xniu.rental.voucher.dto.VoucherVerificationAmountRequest;
import com.xniu.rental.voucher.service.VoucherService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    private BillRepository billRepository;

    @Autowired
    private OrderRenewalService orderRenewalService;

    @Autowired
    private AgreementDeductService agreementDeductService;

    @MockBean
    private AlipayGatewayClient alipayGatewayClient;

    @Autowired
    private AssetFulfillmentService assetFulfillmentService;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private SettlementIncomeService settlementIncomeService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementStatementService settlementStatementService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentAccount() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        jdbcTemplate.update("""
            UPDATE asset_item
            SET status = 'IDLE', current_merchant_id = 1, current_store_id = 1
            WHERE id IN (1, 2)
            """);
        jdbcTemplate.update("UPDATE rental_order SET auto_renew_enabled = 0");
        jdbcTemplate.update("""
            UPDATE rental_bill
            SET bill_status = 'CANCELLED'
            WHERE bill_status IN ('PENDING_PAYMENT', 'FAILED')
              AND payable_amount > paid_amount
            """);
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
        jdbcTemplate.update(
            "UPDATE sys_account SET display_name = ?, phone = ? WHERE id = ?",
            "张三",
            "13800138000",
            1001L
        );
        var order = orderService.createOrder(new OrderCreateRequest(
            1001L,
            "张三",
            "13800138000",
            1L,
            3L,
            1L,
            2L,
            LocalDateTime.of(2026, 7, 1, 10, 0)
        ));

        assertThat(order.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.customerName()).isEqualTo("张三");
        assertThat(order.customerPhone()).isEqualTo("13800138000");
        assertThat(order.storeName()).isEqualTo("演示门店");
        assertThat(order.storeSkuName()).isNotBlank();
        assertThat(order.packageName()).isNotBlank();
        assertThat(order.frameAssetCode()).isEqualTo("A-frame-demo-001");
        assertThat(order.frameSerialNo()).isEqualTo("FRAME-DEMO-001");
        assertThat(order.batteryAssetCode()).isEqualTo("A-battery-demo-001");
        assertThat(order.batterySerialNo()).isEqualTo("BATTERY-DEMO-001");
        assertThat(order.payableAmount()).isEqualByComparingTo(new BigDecimal("1029.00"));
        assertThat(order.settlementSnapshotId()).isNotNull();
        var snapshot = jdbcTemplate.queryForMap(
            """
            SELECT calculation_version, source_channel, settlement_base_amount,
                   channel_fee_amount, platform_fee_amount, distributable_amount,
                   store_operation_amount, maintenance_fund_amount, channel_referral_amount,
                   investor_share_amount
            FROM settlement_rule_snapshot
            WHERE id = ?
            """,
            order.settlementSnapshotId()
        );
        assertThat(snapshot.get("calculation_version")).isEqualTo("PROFIT_V2");
        assertThat(snapshot.get("source_channel")).isEqualTo("DIRECT");
        assertThat(snapshot.get("settlement_base_amount")).isEqualTo(new BigDecimal("999.00"));
        assertThat(snapshot.get("channel_fee_amount")).isEqualTo(new BigDecimal("49.95"));
        assertThat(snapshot.get("platform_fee_amount")).isEqualTo(new BigDecimal("29.97"));
        assertThat(snapshot.get("distributable_amount")).isEqualTo(new BigDecimal("919.08"));
        assertThat(snapshot.get("store_operation_amount")).isEqualTo(new BigDecimal("137.86"));
        assertThat(snapshot.get("maintenance_fund_amount")).isEqualTo(new BigDecimal("91.91"));
        assertThat(snapshot.get("channel_referral_amount")).isEqualTo(new BigDecimal("183.82"));
        assertThat(snapshot.get("investor_share_amount")).isEqualTo(new BigDecimal("505.49"));
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

        var unpaidIncome = settlementIncomeService.generateForOrder(order.id());
        assertThat(unpaidIncome.createdCount()).isZero();
        billRepository.listBills(null, order.id(), null).forEach(bill -> billRepository.markPaid(bill.id(), bill.payableAmount()));

        var income = settlementIncomeService.generateForOrder(order.id());

        assertThat(income.createdCount()).isEqualTo(19);
        assertThat(income.entries())
            .extracting("lineType")
            .contains(
                "CHANNEL_VERIFICATION_FEE",
                "PLATFORM_SERVICE_FEE",
                "STORE_OPERATION_SHARE",
                "MAINTENANCE_FUND_SHARE",
                "CHANNEL_REFERRAL_SHARE",
                "INVESTOR_SHARE",
                "MERCHANT_ORDER_FEE"
            );
        var incomeAmounts = income.entries().stream().collect(java.util.stream.Collectors.toMap(
            entry -> entry.lineType(),
            entry -> entry.amount(),
            BigDecimal::add
        ));
        assertThat(incomeAmounts).containsAllEntriesOf(Map.of(
            "CHANNEL_VERIFICATION_FEE", new BigDecimal("49.95"),
            "PLATFORM_SERVICE_FEE", new BigDecimal("29.97"),
            "STORE_OPERATION_SHARE", new BigDecimal("137.85"),
            "MAINTENANCE_FUND_SHARE", new BigDecimal("91.92"),
            "CHANNEL_REFERRAL_SHARE", new BigDecimal("183.81"),
            "INVESTOR_SHARE", new BigDecimal("505.50"),
            "MERCHANT_ORDER_FEE", new BigDecimal("30.00")
        ));
        assertThat(incomeAmounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo(new BigDecimal("1029.00"));
        assertThat(income.entries())
            .filteredOn(entry -> "INVESTOR".equals(entry.beneficiaryType()))
            .allSatisfy(entry -> assertThat(entry.beneficiaryId()).isEqualTo(1L))
            .hasSize(3);
        assertThat(income.entries())
            .filteredOn(entry -> "MAINTENANCE_FUND_SHARE".equals(entry.lineType()))
            .allSatisfy(entry -> {
                assertThat(entry.beneficiaryType()).isEqualTo("MERCHANT");
                assertThat(entry.beneficiaryId()).isEqualTo(1L);
            })
            .hasSize(3);
        assertThat(income.entries().stream()
            .filter(entry -> "MAINTENANCE_FUND_SHARE".equals(entry.lineType()))
            .map(entry -> entry.amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("91.92");

        var repeatedIncome = settlementIncomeService.generateForOrder(order.id());
        assertThat(repeatedIncome.createdCount()).isZero();
        assertThat(repeatedIncome.entries()).hasSameSizeAs(income.entries());
    }

    @Test
    void merchantCanShipPendingPaymentOrderWithoutWaitingForPayment() {
        var order = orderService.createOrder(new OrderCreateRequest(
            1003L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.of(2026, 7, 2, 10, 0)
        ));

        assertThat(order.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.paidAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        var handover = assetFulfillmentService.shipWithoutPayment(
            order.id(),
            new AssetPickupRequest(null, null, "merchant ships before payment")
        );

        assertThat(handover.handoverType()).isEqualTo("PICKUP");
        assertThat(orderStatus(order.id())).isEqualTo("RENTING");
        assertThat(assetStatus(1L)).isEqualTo("RENTING");
        assertThat(assetStatus(2L)).isEqualTo("RENTING");
        assertThat(jdbcTemplate.queryForObject("SELECT paid_amount FROM rental_order WHERE id = ?", BigDecimal.class, order.id()))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void orderLifecycleShouldRejectAssetsOwnedByDifferentInvestors() {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO investor
            (investor_code, investor_name, contact_name, contact_phone, operation_fee_rate, status)
            VALUES (?, ?, '测试联系人', '18800009999', 0.0000, 'ENABLED')
            """, "I-mixed-" + suffix, "混合出资方-" + suffix);
        var otherInvestorId = jdbcTemplate.queryForObject(
            "SELECT id FROM investor WHERE investor_code = ?",
            Long.class,
            "I-mixed-" + suffix
        );
        jdbcTemplate.update("UPDATE asset_item SET investor_id = ? WHERE id = 2", otherInvestorId);

        assertThatThrownBy(() -> orderService.createOrder(new OrderCreateRequest(
            1002L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.of(2026, 7, 2, 10, 0)
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不同出资方");

        jdbcTemplate.update("UPDATE asset_item SET investor_id = 1 WHERE id = 2");
        var order = orderService.createOrder(new OrderCreateRequest(
            1002L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.of(2026, 7, 2, 10, 0)
        ));
        jdbcTemplate.update("UPDATE asset_item SET investor_id = ? WHERE id = 2", otherInvestorId);

        assertThatThrownBy(() -> assetFulfillmentService.shipWithoutPayment(
            order.id(),
            new AssetPickupRequest(null, null, "混合出资方发货")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不同出资方");
        assertThat(assetStatus(1L)).isEqualTo("IDLE");
        assertThat(assetStatus(2L)).isEqualTo("IDLE");

        jdbcTemplate.update("UPDATE asset_item SET investor_id = 1 WHERE id = 2");
        assetFulfillmentService.shipWithoutPayment(order.id(), new AssetPickupRequest(null, null, "同出资方发货"));
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'VEHICLE_FRAME',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'VEHICLE_FRAME'),
                    ?, ?, 1, 1, 'IDLE', 2600.00, 0.00, NULL, CURRENT_DATE)
            """, "A-mixed-replace-" + suffix, "FRAME-MIXED-REPLACE-" + suffix, otherInvestorId);
        var otherInvestorAssetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-mixed-replace-" + suffix
        );

        assertThatThrownBy(() -> assetFulfillmentService.replaceAsset(order.id(), new AssetReplaceRequest(
            "VEHICLE_FRAME",
            otherInvestorAssetId,
            "IDLE",
            "跨出资方换车"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("其他出资方");
        assertThat(assetStatus(otherInvestorAssetId)).isEqualTo("IDLE");
    }

    @Test
    void crossStoreReturnRequiresEnabledStoreAndProductPermission() {
        var returnStoreCode = "S-return-" + System.nanoTime();
        jdbcTemplate.update(
            "INSERT INTO merchant_store (merchant_id, store_code, store_name, address, qr_content) VALUES (1, ?, '跨店归还测试门店', '测试地址', ?)",
            returnStoreCode,
            "xniu://store/" + returnStoreCode
        );
        var returnStoreId = jdbcTemplate.queryForObject(
            "SELECT id FROM merchant_store WHERE store_code = ?",
            Long.class,
            returnStoreCode
        );
        var order = orderService.createOrder(new OrderCreateRequest(
            null,
            "跨店归还客户",
            "13800136666",
            1L,
            2L,
            1L,
            2L,
            null
        ));
        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");
        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "跨店归还测试取车"));

        jdbcTemplate.update("UPDATE product_sku SET support_cross_store_return = 0 WHERE id = ?", order.skuId());
        assertThatThrownBy(() -> assetFulfillmentService.returnAssets(
            order.id(),
            new AssetReturnRequest(returnStoreId, "IDLE", "IDLE", "未开放跨店归还")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持跨门店归还");

        jdbcTemplate.update("UPDATE product_sku SET support_cross_store_return = 1 WHERE id = ?", order.skuId());
        jdbcTemplate.update("UPDATE merchant_store SET status = 'DISABLED' WHERE id = ?", returnStoreId);
        assertThatThrownBy(() -> assetFulfillmentService.returnAssets(
            order.id(),
            new AssetReturnRequest(returnStoreId, "IDLE", "IDLE", "停用门店归还")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("归还门店已停用");

        jdbcTemplate.update("UPDATE merchant_store SET status = 'ENABLED' WHERE id = ?", returnStoreId);
        assetFulfillmentService.returnAssets(
            order.id(),
            new AssetReturnRequest(returnStoreId, "IDLE", "IDLE", "允许跨店归还")
        );

        assertThat(orderStatus(order.id())).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT current_store_id FROM asset_item WHERE id = 1", Long.class))
            .isEqualTo(returnStoreId);
        assertThat(jdbcTemplate.queryForObject("SELECT current_store_id FROM asset_item WHERE id = 2", Long.class))
            .isEqualTo(returnStoreId);
    }

    @Test
    void integratedVehicleOrderShouldOnlyBindFrameSlotThroughPickupAndReturn() {
        var integratedAssetId = insertIntegratedVehicle("A-integrated-direct", "FRAME-INTEGRATED-DIRECT");

        assertThatThrownBy(() -> orderService.createOrder(new OrderCreateRequest(
            null,
            "一体车客户",
            "13800138888",
            1L,
            2L,
            integratedAssetId,
            2L,
            null
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无需再选择电池资产");

        var order = orderService.createOrder(new OrderCreateRequest(
            null,
            "一体车客户",
            "13800138888",
            1L,
            2L,
            integratedAssetId,
            null,
            null
        ));

        assertThat(order.frameAssetId()).isEqualTo(integratedAssetId);
        assertThat(order.batteryAssetId()).isNull();
        assertThat(order.items()).extracting("itemType").contains("ASSET_FRAME").doesNotContain("ASSET_BATTERY");

        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");

        var pickup = assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "一体车取车"));

        assertThat(pickup.frameAssetId()).isEqualTo(integratedAssetId);
        assertThat(pickup.batteryAssetId()).isNull();
        assertThat(assetStatus(integratedAssetId)).isEqualTo("RENTING");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT asset_type FROM order_asset_usage WHERE order_id = ? AND usage_status = 'ACTIVE'",
            String.class,
            order.id()
        )).isEqualTo("INTEGRATED_VEHICLE");

        assetFulfillmentService.returnAssets(order.id(), new AssetReturnRequest(null, "IDLE", null, "一体车归还"));

        assertThat(assetStatus(integratedAssetId)).isEqualTo("IDLE");
        assertThat(orderStatus(order.id())).isEqualTo("COMPLETED");
    }

    @Test
    void customAssetTypeShouldWorkAsPrimaryOrderAssetThroughPickupAndReturn() {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO asset_type_definition
            (type_code, type_name, asset_class, serial_label, system_defined, sort_order, status)
            VALUES (?, ?, 'GENERAL', '资产编号', 0, 90, 'ENABLED')
            """, "CUSTOM_ORDER_" + suffix, "小豆芽车电一体-" + suffix);
        var typeId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_type_definition WHERE type_code = ?",
            Long.class,
            "CUSTOM_ORDER_" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'GENERAL', ?, ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, "A-custom-order-" + suffix, typeId, "CUSTOM-ORDER-" + suffix);
        var customAssetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-custom-order-" + suffix
        );

        var order = orderService.createOrder(new OrderCreateRequest(
            null,
            "自定义资产客户",
            "13800139999",
            1L,
            2L,
            customAssetId,
            null,
            null
        ));

        assertThat(order.frameAssetId()).isEqualTo(customAssetId);
        assertThat(order.items()).extracting("itemType").contains("ASSET_FRAME");
        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");

        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "自定义资产取车"));

        assertThat(assetStatus(customAssetId)).isEqualTo("RENTING");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT asset_type FROM order_asset_usage WHERE order_id = ? AND usage_status = 'ACTIVE'",
            String.class,
            order.id()
        )).isEqualTo("GENERAL");

        assetFulfillmentService.returnAssets(order.id(), new AssetReturnRequest(null, "IDLE", null, "自定义资产归还"));

        assertThat(assetStatus(customAssetId)).isEqualTo("IDLE");
        assertThat(orderStatus(order.id())).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_asset_usage WHERE order_id = ? AND usage_status = 'ACTIVE'",
            Integer.class,
            order.id()
        )).isZero();
    }

    @Test
    void replacingFrameWithIntegratedVehicleShouldReleaseIndependentBattery() {
        var order = orderService.createOrder(new OrderCreateRequest(
            null,
            "换一体车客户",
            "13800137777",
            1L,
            2L,
            1L,
            2L,
            null
        ));
        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");
        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "普通车取车"));

        var integratedAssetId = insertIntegratedVehicle("A-integrated-replace", "FRAME-INTEGRATED-REPLACE");
        assetFulfillmentService.replaceAsset(order.id(), new AssetReplaceRequest(
            "VEHICLE_FRAME",
            integratedAssetId,
            "IDLE",
            "换为车电一体"
        ));

        var boundAssets = jdbcTemplate.queryForMap(
            "SELECT frame_asset_id, battery_asset_id FROM rental_order WHERE id = ?",
            order.id()
        );
        assertThat(boundAssets.get("frame_asset_id")).isEqualTo(integratedAssetId);
        assertThat(boundAssets.get("battery_asset_id")).isNull();
        assertThat(assetStatus(1L)).isEqualTo("IDLE");
        assertThat(assetStatus(2L)).isEqualTo("IDLE");
        assertThat(assetStatus(integratedAssetId)).isEqualTo("RENTING");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM order_asset_usage WHERE order_id = ? AND usage_status = 'ACTIVE'",
            Integer.class,
            order.id()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT asset_type FROM order_asset_usage WHERE order_id = ? AND usage_status = 'ACTIVE'",
            String.class,
            order.id()
        )).isEqualTo("INTEGRATED_VEHICLE");
    }

    @Test
    void orderIndexSearchShouldMatchCustomerPhoneAssetStoreAndSku() {
        var order = orderService.createOrder(new OrderCreateRequest(
            null,
            "索引测试客户",
            "13800132222",
            1L,
            2L,
            1L,
            2L,
            null
        ));

        assertThat(orderService.listOrders(null, null, null, "索引测试客户"))
            .extracting("id")
            .contains(order.id());
        assertThat(orderService.listOrders(null, null, null, "13800132222"))
            .extracting("id")
            .contains(order.id());
        assertThat(orderService.listOrders(null, null, null, "FRAME-DEMO-001"))
            .extracting("id")
            .contains(order.id());
        assertThat(orderService.listOrders(null, 1L, null, "演示门店"))
            .extracting("id")
            .contains(order.id());
        assertThat(orderService.listOrders(null, null, null, "不存在的订单索引"))
            .isEmpty();

        setStoreAccount();
        assertThat(orderService.listMerchantOrders(1L, null, "13800132222"))
            .extracting("id")
            .containsExactly(order.id());
    }

    @Test
    void autoRenewalGeneratesSingleRenewalBillAndExtendsOrderAfterPayment() {
        var order = orderService.createOrder(new OrderCreateRequest(
            1004L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.of(2026, 7, 3, 10, 0)
        ));

        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");
        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "auto renewal pickup"));

        var dueAt = LocalDateTime.of(2026, 7, 15, 9, 0);
        jdbcTemplate.update("UPDATE rental_order SET expected_return_at = ? WHERE id = ?", dueAt, order.id());

        var generated = orderRenewalService.runDueRenewalsInternal(10, "integration auto renewal");

        assertThat(generated.scannedCount()).isEqualTo(1);
        assertThat(generated.generatedCount()).isEqualTo(1);
        assertThat(generated.batchId()).isNotNull();
        assertThat(orderStatus(order.id())).isEqualTo("OVERDUE");

        var renewalBills = billRepository.listBills(null, order.id(), null).stream()
            .filter(bill -> bill.billType() == BillType.RENEWAL)
            .toList();
        assertThat(renewalBills).hasSize(1);
        var renewalBill = renewalBills.get(0);
        assertThat(renewalBill.periodNo()).isEqualTo(2);
        assertThat(renewalBill.billStatus()).isEqualTo(BillStatus.PENDING_PAYMENT);
        assertThat(renewalBill.dueAt()).isEqualTo(dueAt);
        assertThat(renewalBill.payableAmount()).isEqualByComparingTo(new BigDecimal("399.00"));
        assertThat(billRepository.listItems(renewalBill.id()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.itemType().name()).isEqualTo("RENEWAL_RENT");
                assertThat(item.amount()).isEqualByComparingTo(new BigDecimal("399.00"));
            });

        var repeated = orderRenewalService.runDueRenewalsInternal(10, "repeat auto renewal scan");
        assertThat(repeated.scannedCount()).isEqualTo(1);
        assertThat(repeated.generatedCount()).isZero();

        var paidBill = billRepository.markPaid(renewalBill.id(), renewalBill.payableAmount());
        orderRenewalService.handlePaidBill(paidBill);

        assertThat(orderStatus(order.id())).isEqualTo("RENTING");
        assertThat(expectedReturnAt(order.id())).isEqualTo(dueAt.plusDays(30));
        assertThat(renewalCount(order.id())).isEqualTo(1);

        var statementMonth = paidBill.paidAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        settlementStatementService.generateMonth(statementMonth);
        assertThat(statementLineAmount(renewalBill.id(), "MERCHANT_RENT_SHARE"))
            .isEqualByComparingTo(new BigDecimal("55.06"));
        assertThat(statementLineAmount(renewalBill.id(), "MERCHANT_MAINTENANCE_SHARE"))
            .isEqualByComparingTo(new BigDecimal("36.71"));
        assertThat(statementLineAmount(renewalBill.id(), "INVESTOR_GROSS_RENT"))
            .isEqualByComparingTo(new BigDecimal("201.89"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM settlement_statement_line WHERE bill_id = ? AND line_type = 'INVESTOR_OPERATION_FEE'",
            Integer.class,
            renewalBill.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'BILL' AND source_id = ? AND entry_status = 'PENDING'
            """, Integer.class, renewalBill.id())).isGreaterThan(0);

        var merchantStatementId = jdbcTemplate.queryForObject("""
            SELECT DISTINCT s.id
            FROM settlement_statement s
            JOIN settlement_statement_line l ON l.statement_id = s.id
            WHERE l.bill_id = ? AND s.beneficiary_type = 'MERCHANT'
            """, Long.class, renewalBill.id());
        var investorStatementId = jdbcTemplate.queryForObject("""
            SELECT DISTINCT s.id
            FROM settlement_statement s
            JOIN settlement_statement_line l ON l.statement_id = s.id
            WHERE l.bill_id = ? AND s.beneficiary_type = 'INVESTOR'
            """, Long.class, renewalBill.id());
        settlementStatementService.updateStatus(merchantStatementId, "PAID");
        settlementStatementService.updateStatus(investorStatementId, "PAID");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'BILL'
              AND source_id = ?
              AND beneficiary_type IN ('MERCHANT', 'INVESTOR')
              AND entry_status = 'PENDING'
            """, Integer.class, renewalBill.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'BILL'
              AND source_id = ?
              AND beneficiary_type IN ('MERCHANT', 'INVESTOR')
              AND entry_status = 'SETTLED'
            """, Integer.class, renewalBill.id())).isGreaterThan(0);
    }

    @Test
    void automaticDeductBatchGeneratesRenewalAndCreatesOverdueCaseWithoutAgreement() {
        var order = orderService.createOrder(new OrderCreateRequest(
            1004L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.now().minusMonths(1)
        ));

        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");
        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "deduct renewal pickup"));
        jdbcTemplate.update(
            "UPDATE rental_order SET expected_return_at = ? WHERE id = ?",
            LocalDateTime.now().minusDays(1),
            order.id()
        );

        var batch = agreementDeductService.runDueDeductInternal(10, "integration renewal deduct");

        assertThat(batch.batchStatus()).isEqualTo("FINISHED");
        assertThat(batch.plannedCount()).isEqualTo(1);
        assertThat(batch.successCount()).isZero();
        assertThat(batch.failedCount()).isEqualTo(1);
        var renewalBills = billRepository.listBills(null, order.id(), null).stream()
            .filter(bill -> bill.billType() == BillType.RENEWAL)
            .toList();
        assertThat(renewalBills).hasSize(1);
        var renewalBill = renewalBills.getFirst();
        assertThat(renewalBill.billStatus()).isEqualTo(BillStatus.FAILED);
        assertThat(orderStatus(order.id())).isEqualTo("PENDING_SUPPLEMENT");
        var overdueCase = jdbcTemplate.queryForMap(
            "SELECT overdue_status, unpaid_amount, last_fail_reason FROM rental_overdue_case WHERE bill_id = ?",
            renewalBill.id()
        );
        assertThat(overdueCase.get("overdue_status")).isEqualTo("OPEN");
        assertThat(overdueCase.get("unpaid_amount")).isEqualTo(new BigDecimal("399.00"));
        assertThat(overdueCase.get("last_fail_reason")).asString().contains("未找到有效支付宝扣款协议");
    }

    @Test
    void automaticDeductWithSignedAgreementPaysRenewalAndExtendsOrder() {
        var order = orderService.createOrder(new OrderCreateRequest(
            1004L,
            1L,
            2L,
            1L,
            2L,
            LocalDateTime.now().minusMonths(1)
        ));

        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");
        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(null, null, "signed agreement pickup"));
        var dueAt = LocalDateTime.now().minusDays(1).withNano(0);
        jdbcTemplate.update("UPDATE rental_order SET expected_return_at = ? WHERE id = ?", dueAt, order.id());
        jdbcTemplate.update(
            """
            INSERT INTO rental_pay_agreement
            (agreement_no, external_agreement_no, user_account_id, alipay_user_id, order_id,
             merchant_id, store_id, agreement_type, agreement_status, personal_product_code,
             sign_scene, max_single_amount, sign_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'CYCLE_PAY', 'SIGNED', ?, ?, ?, ?)
            """,
            "AGR-TEST-" + order.id(),
            "EXT-TEST-" + order.id(),
            1004L,
            "2088-test-user",
            order.id(),
            1L,
            1L,
            "GENERAL_WITHHOLDING",
            "INDUSTRY|CARRENTAL",
            new BigDecimal("1000.00"),
            LocalDateTime.now().minusDays(2)
        );
        when(alipayGatewayClient.payWithAgreement(
            anyString(),
            any(BigDecimal.class),
            anyString(),
            anyString(),
            anyString()
        )).thenReturn(new AlipayGatewayClient.TradePayResult("ALI-TRADE-TEST", "399.00"));

        var batch = agreementDeductService.runDueDeductInternal(10, "integration signed renewal deduct");

        assertThat(batch.plannedCount()).isEqualTo(1);
        assertThat(batch.successCount()).isEqualTo(1);
        assertThat(batch.failedCount()).isZero();
        var renewalBill = billRepository.listBills(null, order.id(), null).stream()
            .filter(bill -> bill.billType() == BillType.RENEWAL)
            .findFirst()
            .orElseThrow();
        assertThat(renewalBill.billStatus()).isEqualTo(BillStatus.PAID);
        assertThat(orderStatus(order.id())).isEqualTo("RENTING");
        assertThat(expectedReturnAt(order.id())).isEqualTo(dueAt.plusDays(30));
        assertThat(renewalCount(order.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT paid_amount FROM rental_order WHERE id = ?",
            BigDecimal.class,
            order.id()
        )).isEqualByComparingTo(new BigDecimal("399.00"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT pay_status FROM rental_payment_order WHERE bill_id = ?",
            String.class,
            renewalBill.id()
        )).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT deduct_status FROM rental_deduct_record WHERE bill_id = ?",
            String.class,
            renewalBill.id()
        )).isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rental_overdue_case WHERE bill_id = ?",
            Integer.class,
            renewalBill.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'BILL' AND source_id = ?
            """, Integer.class, renewalBill.id())).isGreaterThan(0);
    }

    @Test
    void verificationAmountCanDifferFromSkuPriceAndBeFilledByStore() {
        setUserAccount(2001L);
        var prepared = voucherService.prepare(new VoucherPrepareRequest(
            "DOUYIN",
            "DY-TEST-" + System.nanoTime(),
            1L,
            2L,
            null
        ));

        assertThat(prepared.voucherAmount()).isEqualByComparingTo(new BigDecimal("399.00"));
        assertThat(prepared.verificationAmount()).isNull();

        setStoreAccount();
        var updated = voucherService.updateMerchantVerificationAmount(
            prepared.id(),
            new VoucherVerificationAmountRequest(new BigDecimal("321.45"))
        );

        assertThat(updated.verificationAmount()).isEqualByComparingTo(new BigDecimal("321.45"));

        setUserAccount(2001L);
        var verified = voucherService.verify(prepared.id());
        var order = orderService.getUserOrder(verified.orderId());

        assertThat(verified.voucherAmount()).isEqualByComparingTo(new BigDecimal("399.00"));
        assertThat(verified.verificationAmount()).isEqualByComparingTo(new BigDecimal("321.45"));
        assertThat(order.rentalAmount()).isEqualByComparingTo(new BigDecimal("321.45"));
        assertThat(order.items())
            .filteredOn(item -> "SKU".equals(item.itemType()))
            .singleElement()
            .satisfies(item -> assertThat(item.totalAmount()).isEqualByComparingTo(new BigDecimal("321.45")));

        var snapshot = jdbcTemplate.queryForMap(
            """
            SELECT source_channel, settlement_base_amount, channel_fee_amount, platform_fee_amount,
                   distributable_amount, store_operation_amount, maintenance_fund_amount,
                   channel_referral_amount, investor_share_amount
            FROM settlement_rule_snapshot
            WHERE id = ?
            """,
            order.settlementSnapshotId()
        );
        assertThat(snapshot.get("source_channel")).isEqualTo("DOUYIN");
        assertThat(snapshot.get("settlement_base_amount")).isEqualTo(new BigDecimal("321.45"));
        assertThat(snapshot.get("channel_fee_amount")).isEqualTo(new BigDecimal("16.07"));
        assertThat(snapshot.get("platform_fee_amount")).isEqualTo(new BigDecimal("9.64"));
        assertThat(snapshot.get("distributable_amount")).isEqualTo(new BigDecimal("295.74"));
        assertThat(snapshot.get("store_operation_amount")).isEqualTo(new BigDecimal("44.36"));
        assertThat(snapshot.get("maintenance_fund_amount")).isEqualTo(new BigDecimal("29.57"));
        assertThat(snapshot.get("channel_referral_amount")).isEqualTo(new BigDecimal("59.15"));
        assertThat(snapshot.get("investor_share_amount")).isEqualTo(new BigDecimal("162.66"));

        var signFeeBill = billRepository.findBill(verified.signFeeBillId()).orElseThrow();
        billRepository.markPaid(signFeeBill.id(), signFeeBill.payableAmount());
        var consumed = voucherService.consume(prepared.id());
        assertThat(consumed.verifyStatus()).isEqualTo("CONSUMED");
        var voucherRentBill = billRepository.listBills(BillStatus.PAID, order.id(), null).stream()
            .filter(bill -> bill.billType() == BillType.VOUCHER_RENT)
            .findFirst()
            .orElseThrow();
        assertThat(voucherRentBill.paidAmount()).isEqualByComparingTo("321.45");
        assertThat(billRepository.listItems(voucherRentBill.id()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.itemType().name()).isEqualTo("RENT");
                assertThat(item.amount()).isEqualByComparingTo("321.45");
            });
        assertThat(jdbcTemplate.queryForObject("""
            SELECT amount
            FROM settlement_income_entry
            WHERE source_type = 'BILL'
              AND source_id = ?
              AND line_type = 'STORE_OPERATION_SHARE'
            """, BigDecimal.class, voucherRentBill.id())).isEqualByComparingTo("44.36");
        voucherService.consume(prepared.id());
        assertThat(billRepository.listBills(null, order.id(), null).stream()
            .filter(bill -> bill.billType() == BillType.VOUCHER_RENT)
            .count()).isEqualTo(1);

        assertThatThrownBy(() -> voucherService.updateMineVerificationAmount(
            prepared.id(),
            new VoucherVerificationAmountRequest(new BigDecimal("300.00"))
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能再修改");

        setStoreAccount();
        assertThatThrownBy(() -> voucherService.updateMerchantVerificationAmount(
            prepared.id(),
            new VoucherVerificationAmountRequest(new BigDecimal("300.00"))
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能再修改");
    }

    @Test
    void verificationCannotCreateOrderUntilAmountIsFilled() {
        setUserAccount(2002L);
        var prepared = voucherService.prepare(new VoucherPrepareRequest(
            "MEITUAN",
            "MT-TEST-" + System.nanoTime(),
            1L,
            2L,
            null
        ));

        assertThatThrownBy(() -> voucherService.verify(prepared.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("核销金额");

        var updated = voucherService.updateMineVerificationAmount(
            prepared.id(),
            new VoucherVerificationAmountRequest(new BigDecimal("288.88"))
        );
        assertThat(updated.verificationAmount()).isEqualByComparingTo(new BigDecimal("288.88"));
        var verified = voucherService.verify(prepared.id());
        assertThat(orderService.getUserOrder(verified.orderId()).rentalAmount())
            .isEqualByComparingTo(new BigDecimal("288.88"));
    }

    @Test
    void verificationAmountRejectsNegativeValuesAtServiceBoundary() {
        setUserAccount(2003L);
        var prepared = voucherService.prepare(new VoucherPrepareRequest(
            "DOUYIN",
            "DY-NEGATIVE-" + System.nanoTime(),
            1L,
            2L,
            null
        ));

        assertThatThrownBy(() -> voucherService.updateMineVerificationAmount(
            prepared.id(),
            new VoucherVerificationAmountRequest(new BigDecimal("-0.01"))
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能小于 0");
    }

    @Test
    void monthStatementsIncludeStoreMaintenanceShareWithoutInvestorMaintenanceDeduction() {
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
             responsibility_type, started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
             cost_bearer_type, cost_bearer_id, operator_account_id, remark)
            VALUES (?, ?, ?, ?, 'REPAIR', 'COMPLETED', 'ROUTINE_MAINTENANCE', ?, ?, ?, ?, ?, ?, 'INVESTOR', ?, ?, ?)
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
             responsibility_type, started_at, completed_at, labor_cost, external_cost, parts_cost, total_cost,
             cost_bearer_type, cost_bearer_id, operator_account_id, remark)
            VALUES (?, ?, ?, ?, 'REPAIR', 'COMPLETED', 'MERCHANT_RESPONSIBILITY', ?, ?, ?, ?, ?, ?, 'MERCHANT', ?, ?, ?)
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
        assertThat(investorStatement.get("rent_share_income_amount")).isEqualTo(new BigDecimal("201.89"));
        assertThat(investorStatement.get("operation_fee_amount")).isEqualTo(new BigDecimal("0.00"));
        assertThat(investorStatement.get("maintenance_deduct_amount")).isEqualTo(new BigDecimal("0.00"));
        assertThat(investorStatement.get("payable_amount")).isEqualTo(new BigDecimal("201.89"));
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line
            WHERE line_type = 'INVESTOR_MAINTENANCE_DEDUCT'
              AND source_type = 'MAINTENANCE'
              AND source_id IN (
                SELECT id FROM asset_maintenance_record WHERE order_id = ?
              )
            """, Integer.class, order.id())).isZero();

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
        assertThat(merchantStatement.get("rent_share_income_amount")).isEqualTo(new BigDecimal("91.77"));
        assertThat(merchantStatement.get("maintenance_deduct_amount")).isEqualTo(new BigDecimal("12.00"));
        assertThat(merchantStatement.get("payable_amount")).isEqualTo(new BigDecimal("109.77"));

        assertThat(settlementStatementService.listStoreProfitOverview("2026-09", 1L, 1L))
            .singleElement()
            .satisfies(storeProfit -> {
                assertThat(storeProfit.statementMonth()).isEqualTo("2026-09");
                assertThat(storeProfit.merchantId()).isEqualTo(1L);
                assertThat(storeProfit.storeId()).isEqualTo(1L);
                assertThat(storeProfit.settlementBaseAmount()).isEqualByComparingTo("399.00");
                assertThat(storeProfit.signFeeAmount()).isEqualByComparingTo("30.00");
                assertThat(storeProfit.storeOperationAmount()).isEqualByComparingTo("55.06");
                assertThat(storeProfit.storeMaintenanceAmount()).isEqualByComparingTo("36.71");
                assertThat(storeProfit.maintenanceReimburseAmount()).isEqualByComparingTo("0.00");
                assertThat(storeProfit.maintenanceDeductAmount()).isEqualByComparingTo("12.00");
                assertThat(storeProfit.payableAmount()).isEqualByComparingTo("109.77");
                assertThat(storeProfit.orderCount()).isEqualTo(1);
                assertThat(storeProfit.billCount()).isEqualTo(1);
                assertThat(storeProfit.lineCount()).isEqualTo(4);
                assertThat(storeProfit.status()).isEqualTo("DRAFT");
            });
    }

    @Test
    void storeRuleAppliesAcrossChannelsAndFreezesHistoricalAmounts() {
        var rule = settlementService.updateStoreRule(1L, new StoreProfitRuleUpdateRequest(
            new BigDecimal("0.04"),
            new BigDecimal("0.02"),
            new BigDecimal("0.20"),
            new BigDecimal("0.10"),
            new BigDecimal("0.15"),
            new BigDecimal("0.55")
        ));

        var douyin = settlementService.preview(new SettlementPreviewRequest(
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "DOUYIN"
        ));
        var direct = settlementService.preview(new SettlementPreviewRequest(
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "DIRECT"
        ));

        assertThat(douyin.matchedRuleId()).isEqualTo(rule.id());
        assertThat(douyin.matchedRuleScope()).isEqualTo("STORE");
        assertThat(douyin.channelFeeAmount()).isEqualByComparingTo("40.00");
        assertThat(douyin.platformFeeAmount()).isEqualByComparingTo("20.00");
        assertThat(douyin.distributableAmount()).isEqualByComparingTo("940.00");
        assertThat(douyin.storeOperationAmount()).isEqualByComparingTo("188.00");
        assertThat(douyin.maintenanceFundAmount()).isEqualByComparingTo("94.00");
        assertThat(douyin.channelReferralAmount()).isEqualByComparingTo("141.00");
        assertThat(douyin.investorShareAmount()).isEqualByComparingTo("517.00");
        assertThat(direct.matchedRuleId()).isEqualTo(rule.id());
        assertThat(direct.channelFeeAmount()).isEqualByComparingTo("40.00");
        assertThat(direct.platformFeeAmount()).isEqualByComparingTo("20.00");

        var frozen = settlementService.createSnapshot(new SnapshotCreateRequest(
            "PREVIEW",
            null,
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "DOUYIN"
        ));
        settlementService.updateStoreRule(1L, new StoreProfitRuleUpdateRequest(
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        ));
        var current = settlementService.preview(new SettlementPreviewRequest(
            1L,
            null,
            null,
            new BigDecimal("1000.00"),
            "DOUYIN"
        ));
        assertThat(current.matchedRuleId()).isEqualTo(rule.id());
        assertThat(current.channelFeeAmount()).isEqualByComparingTo("50.00");
        assertThat(current.platformFeeAmount()).isEqualByComparingTo("30.00");

        var frozenAmounts = jdbcTemplate.queryForMap(
            """
            SELECT matched_rule_id, store_operation_amount, investor_share_amount
            FROM settlement_rule_snapshot
            WHERE id = ?
            """,
            frozen.id()
        );
        assertThat(frozenAmounts.get("matched_rule_id")).isEqualTo(rule.id());
        assertThat(frozenAmounts.get("store_operation_amount")).isEqualTo(new BigDecimal("188.00"));
        assertThat(frozenAmounts.get("investor_share_amount")).isEqualTo(new BigDecimal("517.00"));
    }

    @Test
    void merchantAccountCannotChangeStoreProfitRuleEvenWithSettlementPermission() {
        AuthContext.set(new CurrentAccount(
            "merchant-token",
            new CurrentAccountResponse(
                2L,
                "MERCHANT_OWNER",
                "merchant-owner",
                "18800000002",
                null,
                "Merchant Owner",
                1L,
                null,
                null,
                List.of("MERCHANT_OWNER"),
                List.of("settlement.write"),
                List.of()
            )
        ));

        assertThatThrownBy(() -> settlementService.updateStoreRule(1L, new StoreProfitRuleUpdateRequest(
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("平台账号");
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
        assertThat(maintenance.merchantReimbursementAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(maintenance.investorDeductAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(maintenance.customerChargeAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(maintenance.costBearerType()).isEqualTo("MERCHANT");

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

    private BigDecimal statementLineAmount(Long billId, String lineType) {
        return jdbcTemplate.queryForObject(
            "SELECT amount FROM settlement_statement_line WHERE bill_id = ? AND line_type = ?",
            BigDecimal.class,
            billId,
            lineType
        );
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT order_status FROM rental_order WHERE id = ?", String.class, orderId);
    }

    private LocalDateTime expectedReturnAt(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT expected_return_at FROM rental_order WHERE id = ?", LocalDateTime.class, orderId);
    }

    private Integer renewalCount(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT renewal_count FROM rental_order WHERE id = ?", Integer.class, orderId);
    }

    private String assetStatus(Long assetId) {
        return jdbcTemplate.queryForObject("SELECT status FROM asset_item WHERE id = ?", String.class, assetId);
    }

    private Long insertIntegratedVehicle(String assetCode, String serialNo) {
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, assetCode, serialNo);
        return jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, assetCode);
    }

    private void transition(Long orderId, String targetStatus) {
        orderService.transition(orderId, new OrderTransitionRequest(targetStatus, "integration transition"));
    }

    private void setUserAccount(Long accountId) {
        AuthContext.set(new CurrentAccount(
            "user-test-token",
            new CurrentAccountResponse(
                accountId,
                "USER",
                "user-test",
                null,
                "alipay-user-test",
                "User Test",
                null,
                null,
                null,
                List.of("USER"),
                List.of(),
                List.of()
            )
        ));
    }

    private void setStoreAccount() {
        AuthContext.set(new CurrentAccount(
            "store-test-token",
            new CurrentAccountResponse(
                2L,
                "MERCHANT",
                "store-test",
                "18800000002",
                null,
                "Store Test",
                1L,
                1L,
                null,
                List.of("STORE_MANAGER"),
                List.of("order.read", "order.operate", "asset.read", "asset.operate"),
                List.of(new StoreScopeResponse(1L, 1L, "STORE_ONLY"))
            )
        ));
    }
}
