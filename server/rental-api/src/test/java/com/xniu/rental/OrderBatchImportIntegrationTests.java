package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.dto.OrderBatchImportRequest;
import com.xniu.rental.order.dto.OrderBatchImportRowRequest;
import com.xniu.rental.order.service.OrderBatchImportService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
class OrderBatchImportIntegrationTests {

    @Autowired
    private OrderBatchImportService orderBatchImportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setAdminAccount() {
        AuthContext.set(new CurrentAccount(
            "admin-order-import-token",
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
                List.of("system.admin", "order.read", "order.operate"),
                List.of()
            )
        ));
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void historicalOrderImportShouldGenerateBillsFromBusinessOrderTimeAndKeepOtherRows() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var orderedAt = LocalDateTime.of(2026, 5, 10, 10, 30);
        var result = orderBatchImportService.batchImportAdmin(new OrderBatchImportRequest(List.of(
            row(2, "历史客户-" + suffix, "138" + suffix, "SSKU-demo-frame-battery", "PKG-3-month", "2026-05-10 10:30"),
            row(3, "错误客户-" + suffix, "139" + suffix, "SSKU-demo-frame-battery", "PKG-not-found", "2026-05-10 10:30")
        )));

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.results().get(0).orderNo()).startsWith("ORD-");
        assertThat(result.results().get(1).message()).contains("SKU 编码不存在");

        var orderId = result.results().get(0).orderId();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT ordered_at FROM rental_order WHERE id = ?",
            LocalDateTime.class,
            orderId
        )).isEqualTo(orderedAt);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT created_at FROM rental_order WHERE id = ?",
            LocalDateTime.class,
            orderId
        )).isAfter(orderedAt);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT settlement_snapshot_id FROM rental_order WHERE id = ?",
            Long.class,
            orderId
        )).isNotNull();

        var dueDates = jdbcTemplate.queryForList(
            "SELECT due_at FROM rental_bill WHERE order_id = ? ORDER BY period_no",
            LocalDateTime.class,
            orderId
        );
        assertThat(dueDates).containsExactly(
            orderedAt,
            orderedAt.plusMonths(1),
            orderedAt.plusMonths(2)
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE customer_name = ?",
            Integer.class,
            "错误客户-" + suffix
        )).isZero();
    }

    @Test
    void futureBusinessOrderTimeShouldFailWithoutCreatingOrder() {
        var result = orderBatchImportService.batchImportAdmin(new OrderBatchImportRequest(List.of(
            row(2, "未来客户", "13800009999", "SSKU-demo-frame-battery", "PKG-1-month", "2099-01-01 00:00")
        )));

        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.results().get(0).message()).contains("不能晚于当前时间");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE customer_phone = ?",
            Integer.class,
            "13800009999"
        )).isZero();
    }

    @Test
    void integratedVehicleFrameNumberShouldImportWithoutBatteryNumber() {
        var serialNo = "FRAME-INTEGRATED-IMPORT-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'INTEGRATED_VEHICLE', ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, "A-integrated-import-" + UUID.randomUUID().toString().substring(0, 8), serialNo);

        var result = orderBatchImportService.batchImportAdmin(new OrderBatchImportRequest(List.of(
            new OrderBatchImportRowRequest(
                2,
                "一体车导入客户",
                "13800136666",
                "",
                "SSKU-demo-frame-battery",
                "PKG-1-month",
                serialNo,
                "",
                "2026-07-01 09:00",
                ""
            )
        )));

        assertThat(result.successCount()).isEqualTo(1);
        var orderAssets = jdbcTemplate.queryForMap(
            "SELECT frame_asset_id, battery_asset_id FROM rental_order WHERE id = ?",
            result.results().getFirst().orderId()
        );
        assertThat(orderAssets.get("frame_asset_id")).isNotNull();
        assertThat(orderAssets.get("battery_asset_id")).isNull();
    }

    @Test
    void merchantImportShouldRequireCreatePermissionAndLockCurrentStore() {
        var request = new OrderBatchImportRequest(List.of(
            row(2, "门店批量客户", "13700007777", "SSKU-demo-frame-battery", "PKG-1-month", "2026-07-01 09:00")
        ));

        setMerchantAccount(List.of("order.read"), List.of(new StoreScopeResponse(1L, 1L, "STORE_ONLY")));
        assertThatThrownBy(() -> orderBatchImportService.batchImportMerchant(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有操作权限");

        setMerchantAccount(List.of("order.read", "order.create"), List.of(new StoreScopeResponse(1L, 1L, "STORE_ONLY")));
        var imported = orderBatchImportService.batchImportMerchant(1L, request);
        assertThat(imported.successCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT store_id FROM rental_order WHERE id = ?",
            Long.class,
            imported.results().get(0).orderId()
        )).isEqualTo(1L);

        var otherStoreSkuCode = "SSKU-other-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
            "INSERT INTO merchant_store (merchant_id, store_code, store_name, address, qr_content) VALUES (1, ?, '其他门店', '测试地址', ?)",
            "S-other-" + UUID.randomUUID().toString().substring(0, 8),
            "xniu://store/other"
        );
        var otherStoreId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
            "INSERT INTO store_sku (merchant_id, store_id, sku_id, store_sku_code, sale_mode, display_name, sign_fee_amount, sign_fee_payer, status) VALUES (1, ?, 1, ?, 'RENTAL', '其他门店商品', 0, 'USER', 'ON_SHELF')",
            otherStoreId,
            otherStoreSkuCode
        );

        var mismatched = orderBatchImportService.batchImportMerchant(1L, new OrderBatchImportRequest(List.of(
            row(3, "越权客户", "13600006666", otherStoreSkuCode, "PKG-1-month", "2026-07-01 09:00")
        )));
        assertThat(mismatched.failedCount()).isEqualTo(1);
        assertThat(mismatched.results().get(0).message()).contains("不属于当前门店");
    }

    private OrderBatchImportRowRequest row(
        int lineNo,
        String customerName,
        String customerPhone,
        String storeSkuCode,
        String packageCode,
        String orderedAt
    ) {
        return new OrderBatchImportRowRequest(
            lineNo,
            customerName,
            customerPhone,
            "",
            storeSkuCode,
            packageCode,
            "",
            "",
            orderedAt,
            ""
        );
    }

    private void setMerchantAccount(List<String> permissions, List<StoreScopeResponse> storeScopes) {
        AuthContext.set(new CurrentAccount(
            "merchant-order-import-token",
            new CurrentAccountResponse(
                2L,
                "MERCHANT_OWNER",
                "merchant_demo",
                "18800000002",
                null,
                "演示商户老板",
                1L,
                null,
                null,
                List.of("MERCHANT_OWNER"),
                permissions,
                storeScopes
            )
        ));
    }
}
