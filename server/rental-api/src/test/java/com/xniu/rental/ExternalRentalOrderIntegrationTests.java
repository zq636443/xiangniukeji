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
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import com.xniu.rental.merchant.dto.StoreRequest;
import com.xniu.rental.merchant.service.MerchantService;
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
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            "门店补录历史在租订单"
        ));

        assertThat(created.orderStatus()).isEqualTo("ACTIVE");
        assertThat(created.frameAssetId()).isEqualTo(frameAssetId);
        assertThat(created.batteryAssetId()).isEqualTo(batteryAssetId);
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
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            null
        ));

        assertThat(created.frameAssetId()).isEqualTo(integratedAssetId);
        assertThat(created.batteryAssetId()).isNull();
        assertThat(assetStatus(integratedAssetId)).isEqualTo("RENTING");
    }

    private String assetStatus(Long assetId) {
        return jdbcTemplate.queryForObject("SELECT status FROM asset_item WHERE id = ?", String.class, assetId);
    }

    private Long assetStore(Long assetId) {
        return jdbcTemplate.queryForObject("SELECT current_store_id FROM asset_item WHERE id = ?", Long.class, assetId);
    }
}
