package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.controller.MerchantOrderController;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.dto.OrderUpdateRequest;
import com.xniu.rental.order.service.OrderCreationService;
import com.xniu.rental.order.service.OrderService;
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
class OrderEditingIntegrationTests {

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MerchantOrderController merchantOrderController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        setAdminAccount();
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void editingPendingOrderShouldSynchronizeItemsBillsAndSettlementSnapshot() {
        var created = createOrder("录入错误客户", "13800001001", new BigDecimal("321.45"));
        var oldSnapshotId = created.settlementSnapshotId();
        var correctedOrderedAt = LocalDateTime.of(2026, 7, 21, 9, 30);

        var updated = orderService.updateOrder(created.id(), new OrderUpdateRequest(
            null,
            "录入更正客户",
            "13800001002",
            1L,
            3L,
            null,
            null,
            correctedOrderedAt,
            new BigDecimal("288.88")
        ));

        assertThat(updated.customerName()).isEqualTo("录入更正客户");
        assertThat(updated.customerPhone()).isEqualTo("13800001002");
        assertThat(updated.rentalAmount()).isEqualByComparingTo("288.88");
        assertThat(updated.verificationAmount()).isEqualByComparingTo("288.88");
        assertThat(updated.payableAmount()).isEqualByComparingTo(
            updated.verificationAmount().add(updated.signFeeAmount()).add(updated.depositAmount())
        );
        assertThat(updated.orderedAt()).isEqualTo(correctedOrderedAt);
        assertThat(updated.packageId()).isEqualTo(3L);
        assertThat(updated.totalPeriods()).isEqualTo(3);
        assertThat(updated.settlementSnapshotId()).isNotEqualTo(oldSnapshotId);
        assertThat(updated.logs()).extracting("operationType").contains("EDIT");

        assertThat(jdbcTemplate.queryForObject("""
            SELECT total_amount
            FROM rental_order_item
            WHERE order_id = ? AND item_type = 'SKU'
            """, BigDecimal.class, updated.id())).isEqualByComparingTo("288.88");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT settlement_base_amount
            FROM settlement_rule_snapshot
            WHERE id = ?
            """, BigDecimal.class, updated.settlementSnapshotId())).isEqualByComparingTo("288.88");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT SUM(amount)
            FROM rental_bill_item bi
            JOIN rental_bill b ON b.id = bi.bill_id
            WHERE b.order_id = ? AND bi.item_type = 'RENT'
            """, BigDecimal.class, updated.id())).isEqualByComparingTo("288.88");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM rental_bill
            WHERE order_id = ?
            """, Integer.class, updated.id())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT due_at
            FROM rental_bill
            WHERE order_id = ? AND bill_type = 'INITIAL'
            """, LocalDateTime.class, updated.id())).isEqualTo(correctedOrderedAt);
    }

    @Test
    void editingPendingOrderShouldAllowChangingStoreProductAndSku() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 2");
        var created = createOrder("商品录错客户", "13800001501", new BigDecimal("399.00"));

        var updated = orderService.updateOrder(created.id(), new OrderUpdateRequest(
            null,
            "商品已更正客户",
            "13800001502",
            2L,
            4L,
            null,
            null,
            LocalDateTime.of(2026, 7, 21, 11, 0),
            new BigDecimal("188.00")
        ));

        assertThat(updated.storeSkuId()).isEqualTo(2L);
        assertThat(updated.skuId()).isEqualTo(2L);
        assertThat(updated.packageId()).isEqualTo(4L);
        assertThat(updated.signFeeAmount()).isEqualByComparingTo("20.00");
        assertThat(updated.payableAmount()).isEqualByComparingTo("208.00");
        assertThat(updated.totalPeriods()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM rental_bill
            WHERE order_id = ? AND merchant_id = ? AND store_id = ?
            """, Integer.class, updated.id(), updated.merchantId(), updated.storeId())).isEqualTo(1);
    }

    @Test
    void editingFormalOrderShouldStopAfterLifecycleOrPaymentStarts() {
        var created = createOrder("状态保护客户", "13800002001", new BigDecimal("199.00"));
        orderService.transition(created.id(), new OrderTransitionRequest("PENDING_REAL_NAME", "进入实名流程"));

        assertThatThrownBy(() -> orderService.updateOrder(created.id(), updateRequest("状态保护更正")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只有待支付订单可以编辑");
    }

    @Test
    void editingFormalOrderShouldStopAfterPaymentIsInitiated() {
        var created = createOrder("支付保护客户", "13800002501", new BigDecimal("199.00"));
        var billId = jdbcTemplate.queryForObject(
            "SELECT id FROM rental_bill WHERE order_id = ? AND bill_type = 'INITIAL'",
            Long.class,
            created.id()
        );
        jdbcTemplate.update("""
            INSERT INTO rental_payment_order
            (payment_no, bill_id, order_id, user_account_id, merchant_id, store_id,
             pay_channel, pay_status, pay_amount, paid_amount, subject)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "PAY-EDIT-BLOCK",
            billId,
            created.id(),
            null,
            created.merchantId(),
            created.storeId(),
            "ALIPAY_APP",
            "PENDING",
            new BigDecimal("199.00"),
            BigDecimal.ZERO,
            "订单编辑支付保护测试"
        );

        assertThatThrownBy(() -> orderService.updateOrder(created.id(), updateRequest("支付后更正")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已发起支付、代扣或核销");
    }

    @Test
    void merchantEditingShouldRequireOrderCreatePermissionAndStoreScope() {
        var created = createOrder("门店编辑客户", "13800003001", new BigDecimal("166.00"));

        setMerchantAccount(List.of("order.read"), allMerchantStores());
        assertThatThrownBy(() -> merchantOrderController.updateOrder(created.id(), updateRequest("无权限更正")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有操作权限");

        setMerchantAccount(List.of("order.read", "order.create"), List.of());
        assertThatThrownBy(() -> merchantOrderController.updateOrder(created.id(), updateRequest("越权更正")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");

        setMerchantAccount(List.of("order.read", "order.create"), allMerchantStores());
        var updated = merchantOrderController.updateOrder(created.id(), updateRequest("门店已更正")).data();

        assertThat(updated.customerName()).isEqualTo("门店已更正");
        assertThat(updated.verificationAmount()).isEqualByComparingTo("188.00");
    }

    private com.xniu.rental.order.dto.OrderResponse createOrder(String name, String phone, BigDecimal amount) {
        return orderCreationService.createAdminOrder(new OrderCreateRequest(
            null,
            name,
            phone,
            1L,
            1L,
            null,
            null,
            null,
            LocalDateTime.of(2026, 7, 20, 10, 0),
            amount
        ));
    }

    private OrderUpdateRequest updateRequest(String customerName) {
        return new OrderUpdateRequest(
            null,
            customerName,
            "13800003999",
            1L,
            1L,
            null,
            null,
            LocalDateTime.of(2026, 7, 21, 10, 0),
            new BigDecimal("188.00")
        );
    }

    private List<StoreScopeResponse> allMerchantStores() {
        return List.of(new StoreScopeResponse(1L, null, "ALL_MERCHANT_STORES"));
    }

    private void setAdminAccount() {
        AuthContext.set(new CurrentAccount(
            "admin-order-edit-token",
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

    private void setMerchantAccount(List<String> permissions, List<StoreScopeResponse> storeScopes) {
        AuthContext.set(new CurrentAccount(
            "merchant-order-edit-token",
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
