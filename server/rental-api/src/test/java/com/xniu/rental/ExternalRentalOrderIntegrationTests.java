package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.asset.service.MaintenanceService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderImportRowRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderTerminateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderUpdateRequest;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import com.xniu.rental.merchant.dto.StoreRequest;
import com.xniu.rental.merchant.service.MerchantService;
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
class ExternalRentalOrderIntegrationTests {

    @Autowired
    private ExternalRentalOrderService externalRentalOrderService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MaintenanceService maintenanceService;

    @Autowired
    private SettlementStatementService settlementStatementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setCurrentAccount() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
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
                List.of("system.admin", "order.read", "order.operate", "store.write", "store.read"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void createAndTerminateExternalOrderShouldOccupyAndReturnAssets() {
        var returnStore = merchantService.createStore(new StoreRequest(
            1L,
            "补录测试归还门店",
            "深圳市南山区归还路 18 号",
            "09:00-22:00",
            null,
            null
        ));

        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, ?, (SELECT id FROM asset_type_definition WHERE type_code = ?), ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE)
            """,
            "A-frame-ext-test",
            "VEHICLE_FRAME",
            "VEHICLE_FRAME",
            "FRAME-EXT-TEST",
            1L,
            1L,
            1L,
            "IDLE",
            new BigDecimal("2600.00"),
            new BigDecimal("35.00"),
            new BigDecimal("300.00")
        );
        var frameAssetId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-frame-ext-test");

        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, ?, (SELECT id FROM asset_type_definition WHERE type_code = ?), ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE)
            """,
            "A-battery-ext-test",
            "BATTERY",
            "BATTERY",
            "BATTERY-EXT-TEST",
            1L,
            1L,
            1L,
            "IDLE",
            new BigDecimal("1800.00"),
            new BigDecimal("25.00"),
            new BigDecimal("200.00")
        );
        var batteryAssetId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-battery-ext-test");

        var created = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "MEITUAN",
            "MT-OUT-001",
            1L,
            2L,
            "张三",
            "13800138000",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            frameAssetId,
            batteryAssetId,
            new BigDecimal("399.00"),
            new BigDecimal("368.50"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            "门店补录历史在租订单"
        ));

        assertThat(created.orderStatus()).isEqualTo("ACTIVE");
        assertThat(created.frameAssetId()).isEqualTo(frameAssetId);
        assertThat(created.batteryAssetId()).isEqualTo(batteryAssetId);
        assertThat(created.externalRentalAmount()).isEqualByComparingTo("399.00");
        assertThat(created.verificationAmount()).isEqualByComparingTo("368.50");
        assertThat(created.settlementSnapshotId()).isNotNull();
        assertThat(created.settlementBaseAmount()).isEqualByComparingTo("368.50");
        assertThat(created.investorShareAmount()).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, Integer.class, created.id())).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT amount
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND beneficiary_type = 'INVESTOR'
              AND beneficiary_id = 1
            """, BigDecimal.class, created.id())).isEqualByComparingTo(created.investorShareAmount());
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND beneficiary_type = 'MERCHANT'
            """, BigDecimal.class, created.id())).isEqualByComparingTo("114.75");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT beneficiary_id
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND line_type = 'MAINTENANCE_FUND_SHARE'
            """, Long.class, created.id())).isEqualTo(created.storeId());

        jdbcTemplate.update(
            "UPDATE external_rental_order SET created_at = '2099-01-15 10:00:00' WHERE id = ?",
            created.id()
        );
        var generated = settlementStatementService.generateMonth("2099-01");
        assertThat(generated.merchantStatementCount()).isEqualTo(1);
        assertThat(generated.investorStatementCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_statement_line l
            JOIN settlement_statement s ON s.id = l.statement_id
            WHERE l.source_type = 'EXTERNAL_ORDER'
              AND l.source_id = ?
              AND s.beneficiary_type = 'INVESTOR'
              AND s.beneficiary_id = 1
            """, Integer.class, created.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT amount
            FROM settlement_statement_line
            WHERE source_type = 'EXTERNAL_ORDER'
              AND source_id = ?
              AND line_type = 'MERCHANT_MAINTENANCE_SHARE'
            """, BigDecimal.class, created.id())).isEqualByComparingTo("33.90");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT payable_amount
            FROM settlement_statement
            WHERE statement_month = '2099-01'
              AND beneficiary_type = 'MERCHANT'
              AND store_id = ?
            """, BigDecimal.class, created.storeId())).isEqualByComparingTo("114.75");
        assertThat(assetStatus(frameAssetId)).isEqualTo("RENTING");
        assertThat(assetStatus(batteryAssetId)).isEqualTo("RENTING");

        var terminated = externalRentalOrderService.terminate(created.id(), new ExternalRentalOrderTerminateRequest(
            returnStore.id(),
            "IDLE",
            "PENDING_REPAIR",
            "客户提前退租",
            "已现场收回资产"
        ));

        assertThat(terminated.orderStatus()).isEqualTo("TERMINATED");
        assertThat(terminated.returnStoreId()).isEqualTo(returnStore.id());
        assertThat(terminated.terminationReason()).isEqualTo("客户提前退租");
        assertThat(terminated.logs())
            .extracting("operationType")
            .contains("CREATE", "TERMINATE");
        assertThat(assetStatus(frameAssetId)).isEqualTo("IDLE");
        assertThat(assetStatus(batteryAssetId)).isEqualTo("PENDING_REPAIR");
        assertThat(assetStore(frameAssetId)).isEqualTo(returnStore.id());
        assertThat(assetStore(batteryAssetId)).isEqualTo(returnStore.id());
    }

    @Test
    void leaseMultiplierShouldExpandExternalOrderAmountAndUseThirtyDayMonths() {
        var startedAt = LocalDateTime.of(2026, 7, 29, 10, 0);
        var created = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "LEASE-MULTIPLIER-001",
            1L,
            2L,
            2,
            "倍数补录客户",
            "13800136666",
            startedAt,
            null,
            1L,
            2L,
            null,
            null,
            null,
            null,
            "租期倍数测试"
        ));

        assertThat(created.leaseMultiplier()).isEqualTo(2);
        assertThat(created.leaseValue()).isEqualTo(2);
        assertThat(created.totalPeriods()).isEqualTo(2);
        assertThat(created.externalRentalAmount()).isEqualByComparingTo("798.00");
        assertThat(created.verificationAmount()).isEqualByComparingTo("798.00");
        assertThat(created.expectedReturnAt()).isEqualTo(startedAt.plusDays(60));
    }

    @Test
    void batchImportShouldAppearInAssetDetailRentalHistory() {
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, ?, (SELECT id FROM asset_type_definition WHERE type_code = ?), ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE)
            """,
            "A-frame-ext-batch",
            "VEHICLE_FRAME",
            "VEHICLE_FRAME",
            "FRAME-EXT-BATCH",
            1L,
            1L,
            1L,
            "IDLE",
            new BigDecimal("2600.00"),
            new BigDecimal("35.00"),
            new BigDecimal("300.00")
        );
        var frameAssetId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-frame-ext-batch");

        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, ?, (SELECT id FROM asset_type_definition WHERE type_code = ?), ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE)
            """,
            "A-battery-ext-batch",
            "BATTERY",
            "BATTERY",
            "BATTERY-EXT-BATCH",
            1L,
            1L,
            1L,
            "IDLE",
            new BigDecimal("1800.00"),
            new BigDecimal("25.00"),
            new BigDecimal("200.00")
        );
        var batteryAssetId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-battery-ext-batch");

        var result = externalRentalOrderService.batchImport(new ExternalRentalOrderBatchImportRequest(List.of(
            new ExternalRentalOrderImportRowRequest(
                1,
                "OFFLINE",
                "OFFLINE-BATCH-001",
                1L,
                2L,
                "王五",
                "13700137000",
                LocalDateTime.of(2026, 7, 10, 9, 0),
                LocalDateTime.of(2026, 8, 10, 9, 0),
                frameAssetId,
                batteryAssetId,
                new BigDecimal("399.00"),
                new BigDecimal("355.25"),
                new BigDecimal("30.00"),
                BigDecimal.ZERO,
                "批量导入测试"
            )
        )));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();

        var detail = maintenanceService.getAssetDetail(frameAssetId);

        assertThat(detail.rentals()).isNotEmpty();
        assertThat(detail.rentals().get(0).recordType()).isEqualTo("EXTERNAL");
        assertThat(detail.rentals().get(0).orderNo()).startsWith("EORD-");
        assertThat(detail.rentals().get(0).sourcePlatform()).isEqualTo("OFFLINE");
        assertThat(detail.rentals().get(0).externalOrderNo()).isEqualTo("OFFLINE-BATCH-001");
        assertThat(detail.rentals().get(0).customerName()).isEqualTo("王五");
    }

    @Test
    void integratedVehicleShouldSatisfyFrameAndBatteryRequirementsWithOneAsset() {
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES ('A-integrated-ext-test', 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    'FRAME-INTEGRATED-EXT', 1, 1, 1, 'IDLE',
                    4200.00, 0.00, NULL, CURRENT_DATE)
            """);
        var integratedAssetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = 'A-integrated-ext-test'",
            Long.class
        );

        assertThatThrownBy(() -> externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "INTEGRATED-WITH-BATTERY",
            1L,
            2L,
            "错误绑定客户",
            "13800135555",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            integratedAssetId,
            2L,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无需再选择电池资产");

        var created = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "INTEGRATED-ONLY-FRAME",
            1L,
            2L,
            "一体车补录客户",
            "13800134444",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            integratedAssetId,
            null,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        ));

        assertThat(created.frameAssetId()).isEqualTo(integratedAssetId);
        assertThat(created.batteryAssetId()).isNull();
        assertThat(assetStatus(integratedAssetId)).isEqualTo("RENTING");
    }

    @Test
    void customAssetTypeShouldBeSelectableAsExternalOrderPrimaryAsset() {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO asset_type_definition
            (type_code, type_name, asset_class, serial_label, system_defined, sort_order, status)
            VALUES (?, ?, 'GENERAL', '资产编号', 0, 90, 'ENABLED')
            """, "CUSTOM_EXTERNAL_" + suffix, "小黄鸭车电一体-" + suffix);
        var typeId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_type_definition WHERE type_code = ?",
            Long.class,
            "CUSTOM_EXTERNAL_" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'GENERAL', ?, ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, "A-custom-external-" + suffix, typeId, "CUSTOM-EXTERNAL-" + suffix);
        var customAssetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-custom-external-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'BATTERY',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'),
                    ?, 1, 1, 1, 'IDLE', 1800.00, 0.00, NULL, CURRENT_DATE)
            """, "A-custom-external-battery-" + suffix, "CUSTOM-EXTERNAL-BATTERY-" + suffix);
        var batteryAssetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-custom-external-battery-" + suffix
        );

        var created = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "CUSTOM-ASSET-" + suffix,
            1L,
            2L,
            "自定义补录客户",
            "13800131111",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            customAssetId,
            batteryAssetId,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        ));

        assertThat(created.frameAssetId()).isEqualTo(customAssetId);
        assertThat(assetStatus(customAssetId)).isEqualTo("RENTING");

        externalRentalOrderService.terminate(created.id(), new ExternalRentalOrderTerminateRequest(
            1L,
            "IDLE",
            "IDLE",
            "测试结束",
            "自定义资产归还"
        ));

        assertThat(assetStatus(customAssetId)).isEqualTo("IDLE");
        assertThat(assetStatus(batteryAssetId)).isEqualTo("IDLE");
    }

    @Test
    void editingActiveExternalOrderShouldReleaseOldAssetsAndOccupyNewAssets() {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES
            (?, 'VEHICLE_FRAME', (SELECT id FROM asset_type_definition WHERE type_code = 'VEHICLE_FRAME'), ?, 1, 1, 1, 'IDLE', 2600.00, 0.00, NULL, CURRENT_DATE),
            (?, 'BATTERY', (SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'), ?, 1, 1, 1, 'IDLE', 1800.00, 0.00, NULL, CURRENT_DATE),
            (?, 'VEHICLE_FRAME', (SELECT id FROM asset_type_definition WHERE type_code = 'VEHICLE_FRAME'), ?, 1, 1, 1, 'IDLE', 2600.00, 0.00, NULL, CURRENT_DATE),
            (?, 'BATTERY', (SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'), ?, 1, 1, 1, 'IDLE', 1800.00, 0.00, NULL, CURRENT_DATE)
            """,
            "A-edit-old-frame-" + suffix, "EDIT-OLD-FRAME-" + suffix,
            "A-edit-old-battery-" + suffix, "EDIT-OLD-BATTERY-" + suffix,
            "A-edit-new-frame-" + suffix, "EDIT-NEW-FRAME-" + suffix,
            "A-edit-new-battery-" + suffix, "EDIT-NEW-BATTERY-" + suffix
        );
        var oldFrameId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-edit-old-frame-" + suffix);
        var oldBatteryId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-edit-old-battery-" + suffix);
        var newFrameId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-edit-new-frame-" + suffix);
        var newBatteryId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-edit-new-battery-" + suffix);

        var created = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "EDIT-BEFORE-" + suffix,
            1L,
            2L,
            "补录错误客户",
            "13800132221",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            oldFrameId,
            oldBatteryId,
            new BigDecimal("399.00"),
            new BigDecimal("368.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            "修改前"
        ));

        var sameAssetsUpdated = externalRentalOrderService.updateOrder(created.id(), new ExternalRentalOrderUpdateRequest(
            "OFFLINE",
            "EDIT-TEXT-ONLY-" + suffix,
            1L,
            2L,
            "仅更正客户资料",
            "13800132220",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            oldFrameId,
            oldBatteryId,
            new BigDecimal("399.00"),
            new BigDecimal("378.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            "只改文字"
        ));

        assertThat(sameAssetsUpdated.customerName()).isEqualTo("仅更正客户资料");
        assertThat(assetStatus(oldFrameId)).isEqualTo("RENTING");
        assertThat(assetStatus(oldBatteryId)).isEqualTo("RENTING");

        var updated = externalRentalOrderService.updateOrder(sameAssetsUpdated.id(), new ExternalRentalOrderUpdateRequest(
            "MEITUAN",
            "EDIT-AFTER-" + suffix,
            1L,
            2L,
            "补录已更正客户",
            "13800132222",
            LocalDateTime.of(2026, 7, 18, 9, 0),
            LocalDateTime.of(2026, 8, 18, 9, 0),
            newFrameId,
            newBatteryId,
            new BigDecimal("420.00"),
            new BigDecimal("388.88"),
            new BigDecimal("35.00"),
            new BigDecimal("50.00"),
            "修改后"
        ));

        assertThat(updated.sourcePlatform()).isEqualTo("MEITUAN");
        assertThat(updated.externalOrderNo()).isEqualTo("EDIT-AFTER-" + suffix);
        assertThat(updated.customerName()).isEqualTo("补录已更正客户");
        assertThat(updated.verificationAmount()).isEqualByComparingTo("388.88");
        assertThat(updated.frameAssetId()).isEqualTo(newFrameId);
        assertThat(updated.batteryAssetId()).isEqualTo(newBatteryId);
        assertThat(updated.logs()).extracting("operationType").contains("CREATE", "EDIT");
        assertThat(assetStatus(oldFrameId)).isEqualTo("IDLE");
        assertThat(assetStatus(oldBatteryId)).isEqualTo("IDLE");
        assertThat(assetStatus(newFrameId)).isEqualTo("RENTING");
        assertThat(assetStatus(newBatteryId)).isEqualTo("RENTING");

        var terminated = externalRentalOrderService.terminate(updated.id(), new ExternalRentalOrderTerminateRequest(
            1L,
            "IDLE",
            "IDLE",
            "编辑测试结束",
            null
        ));

        var closedUpdated = externalRentalOrderService.updateOrder(updated.id(), new ExternalRentalOrderUpdateRequest(
            "OFFLINE",
            "EDIT-CLOSED-" + suffix,
            1L,
            2L,
            "已结束订单",
            "13800132223",
            LocalDateTime.of(2026, 7, 18, 9, 0),
            null,
            newFrameId,
            newBatteryId,
            new BigDecimal("420.00"),
            new BigDecimal("399.99"),
            new BigDecimal("35.00"),
            new BigDecimal("50.00"),
            "已结束后修正"
        ));

        assertThat(closedUpdated.orderStatus()).isEqualTo("TERMINATED");
        assertThat(closedUpdated.customerName()).isEqualTo("已结束订单");
        assertThat(closedUpdated.verificationAmount()).isEqualByComparingTo("399.99");
        assertThat(closedUpdated.settlementSnapshotId()).isNotEqualTo(terminated.settlementSnapshotId());
        assertThat(closedUpdated.settlementBaseAmount()).isEqualByComparingTo("399.99");
        assertThat(assetStatus(newFrameId)).isEqualTo("IDLE");
        assertThat(assetStatus(newBatteryId)).isEqualTo("IDLE");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(DISTINCT snapshot_id)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, Integer.class, updated.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT MAX(snapshot_id)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, Long.class, updated.id())).isEqualTo(closedUpdated.settlementSnapshotId());
    }

    @Test
    void createExternalOrderShouldRejectAssetOutsideSelectedStore() {
        var otherStore = merchantService.createStore(new StoreRequest(
            1L,
            "补录测试其他门店",
            "深圳市南山区其他路 28 号",
            "09:00-22:00",
            null,
            null
        ));
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES ('A-integrated-other-store', 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    'FRAME-OTHER-STORE', 1, 1, ?, 'IDLE',
                    4200.00, 0.00, NULL, CURRENT_DATE)
            """, otherStore.id());
        var assetId = jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = 'A-integrated-other-store'",
            Long.class
        );

        assertThatThrownBy(() -> externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "OTHER-STORE-ASSET",
            1L,
            2L,
            "跨门店错误客户",
            "13800136666",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            assetId,
            null,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不属于当前下单门店");

        assertThat(assetStatus(assetId)).isEqualTo("IDLE");
        assertThat(assetStore(assetId)).isEqualTo(otherStore.id());
    }

    @Test
    void createExternalOrderShouldRejectAssetsOwnedByDifferentInvestors() {
        var suffix = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
            INSERT INTO investor
            (investor_code, investor_name, contact_name, contact_phone, operation_fee_rate, status)
            VALUES (?, ?, '补录测试', '18800008888', 0.0000, 'ENABLED')
            """, "I-ext-mixed-" + suffix, "补录混合出资方-" + suffix);
        var otherInvestorId = jdbcTemplate.queryForObject(
            "SELECT id FROM investor WHERE investor_code = ?",
            Long.class,
            "I-ext-mixed-" + suffix
        );
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES
            (?, 'VEHICLE_FRAME', (SELECT id FROM asset_type_definition WHERE type_code = 'VEHICLE_FRAME'), ?, 1, 1, 1, 'IDLE', 2600.00, 0.00, NULL, CURRENT_DATE),
            (?, 'BATTERY', (SELECT id FROM asset_type_definition WHERE type_code = 'BATTERY'), ?, ?, 1, 1, 'IDLE', 1800.00, 0.00, NULL, CURRENT_DATE)
            """,
            "A-ext-mixed-frame-" + suffix, "EXT-MIXED-FRAME-" + suffix,
            "A-ext-mixed-battery-" + suffix, "EXT-MIXED-BATTERY-" + suffix, otherInvestorId
        );
        var frameAssetId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-ext-mixed-frame-" + suffix);
        var batteryAssetId = jdbcTemplate.queryForObject("SELECT id FROM asset_item WHERE asset_code = ?", Long.class, "A-ext-mixed-battery-" + suffix);

        assertThatThrownBy(() -> externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "EXT-MIXED-" + suffix,
            1L,
            2L,
            "混合出资客户",
            "13800138888",
            LocalDateTime.of(2026, 7, 19, 10, 0),
            null,
            frameAssetId,
            batteryAssetId,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不同出资方");

        assertThat(assetStatus(frameAssetId)).isEqualTo("IDLE");
        assertThat(assetStatus(batteryAssetId)).isEqualTo("IDLE");
    }

    @Test
    void deleteActiveExternalOrderShouldReleaseAssetAndRemoveSettlementData() {
        var suffix = String.valueOf(System.nanoTime());
        var assetId = createIntegratedAsset("delete-" + suffix);
        var created = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "DELETE-" + suffix,
            1L,
            2L,
            "删除补录客户",
            "13800139991",
            LocalDateTime.of(2026, 7, 20, 10, 0),
            null,
            assetId,
            null,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            "待删除补录订单"
        ));

        assertThat(assetStatus(assetId)).isEqualTo("RENTING");
        assertThat(created.settlementSnapshotId()).isNotNull();

        externalRentalOrderService.deleteOrder(created.id());

        assertThat(assetStatus(assetId)).isEqualTo("IDLE");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM external_rental_order WHERE id = ?",
            Integer.class,
            created.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM external_rental_order_log WHERE external_order_id = ?",
            Integer.class,
            created.id()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_income_entry
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, Integer.class, created.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(1)
            FROM settlement_rule_snapshot
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, Integer.class, created.id())).isZero();
        assertThatThrownBy(() -> externalRentalOrderService.getOrder(created.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("补录订单不存在");
    }

    @Test
    void deleteExternalOrderShouldRejectFinanciallyLockedRecords() {
        var suffix = String.valueOf(System.nanoTime());
        var settledAssetId = createIntegratedAsset("settled-" + suffix);
        var settledOrder = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "SETTLED-" + suffix,
            1L,
            2L,
            "已结算补录客户",
            "13800139992",
            LocalDateTime.of(2026, 7, 20, 10, 0),
            null,
            settledAssetId,
            null,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        ));
        jdbcTemplate.update("""
            UPDATE settlement_income_entry
            SET entry_status = 'SETTLED', settled_at = CURRENT_TIMESTAMP
            WHERE source_type = 'EXTERNAL_ORDER' AND source_id = ?
            """, settledOrder.id());

        assertThatThrownBy(() -> externalRentalOrderService.deleteOrder(settledOrder.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已结算或冻结");
        assertThat(assetStatus(settledAssetId)).isEqualTo("RENTING");

        var statementAssetId = createIntegratedAsset("statement-" + suffix);
        var statementOrder = externalRentalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            "OFFLINE",
            "STATEMENT-" + suffix,
            1L,
            2L,
            "月结补录客户",
            "13800139993",
            LocalDateTime.of(2099, 2, 10, 10, 0),
            null,
            statementAssetId,
            null,
            new BigDecimal("399.00"),
            new BigDecimal("388.00"),
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        ));
        jdbcTemplate.update(
            "UPDATE external_rental_order SET created_at = '2099-02-10 10:00:00' WHERE id = ?",
            statementOrder.id()
        );
        settlementStatementService.generateMonth("2099-02");

        assertThatThrownBy(() -> externalRentalOrderService.deleteOrder(statementOrder.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已进入月结单");
        assertThat(assetStatus(statementAssetId)).isEqualTo("RENTING");
    }

    private Long createIntegratedAsset(String suffix) {
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, "A-ext-" + suffix, "FRAME-EXT-" + suffix);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            "A-ext-" + suffix
        );
    }

    private String assetStatus(Long assetId) {
        return jdbcTemplate.queryForObject("SELECT status FROM asset_item WHERE id = ?", String.class, assetId);
    }

    private Long assetStore(Long assetId) {
        return jdbcTemplate.queryForObject("SELECT current_store_id FROM asset_item WHERE id = ?", Long.class, assetId);
    }
}
