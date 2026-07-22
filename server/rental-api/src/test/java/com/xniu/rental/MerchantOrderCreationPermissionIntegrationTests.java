package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.controller.MerchantOrderController;
import com.xniu.rental.order.dto.OrderCancelRequest;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class MerchantOrderCreationPermissionIntegrationTests {

    @Autowired
    private MerchantOrderController merchantOrderController;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void merchantOrderCreationRequiresDirectPermissionAndAuthorizedStore() {
        var request = orderRequest();

        setMerchantAccount(List.of("order.read"), allMerchantStores());
        assertThatThrownBy(() -> merchantOrderController.createOrder(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有操作权限");

        setMerchantAccount(List.of("order.read", "order.create"), List.of());
        assertThatThrownBy(() -> merchantOrderController.createOrder(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");

        setMerchantAccount(List.of("order.read", "order.create"), allMerchantStores());
        var created = merchantOrderController.createOrder(request).data();

        assertThat(created.merchantId()).isEqualTo(1L);
        assertThat(created.storeId()).isEqualTo(1L);
        assertThat(created.customerName()).isEqualTo("门店测试客户");
        assertThat(created.customerPhone()).isEqualTo("13800001111");
        assertThat(created.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rental_bill WHERE order_id = ?",
            Integer.class,
            created.id()
        )).isEqualTo(1);
    }

    @Test
    void merchantCannotBypassAdminOrConsumerOrderCreationEntrypoints() {
        setMerchantAccount(List.of("order.read", "order.operate", "order.create"), allMerchantStores());

        assertThatThrownBy(() -> orderService.createOrder(orderRequest()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不是平台账号");

        assertThatThrownBy(() -> orderService.createUserOrder(orderRequest()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不是消费者账号");
    }

    @Test
    void merchantCannotAttachAssetOutsideOrderStore() {
        jdbcTemplate.update("UPDATE asset_item SET current_store_id = NULL WHERE id = 1");
        setMerchantAccount(List.of("order.read", "order.create"), allMerchantStores());

        var request = new OrderCreateRequest(null, "门店测试客户", "13800001111", 1L, 1L, 1L, null, null);

        assertThatThrownBy(() -> merchantOrderController.createOrder(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("资产不在订单门店");
    }

    @Test
    void merchantOrderWithoutUserRequiresCustomerContact() {
        setMerchantAccount(List.of("order.read", "order.create"), allMerchantStores());

        assertThatThrownBy(() -> merchantOrderController.createOrder(
            new OrderCreateRequest(null, 1L, 1L, null, null, null)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("客户姓名");
    }

    @Test
    void disabledStoreCannotCreateNewOrderEvenIfProductWasPreviouslyOnShelf() {
        jdbcTemplate.update("UPDATE merchant_store SET status = 'DISABLED' WHERE id = 1");
        setMerchantAccount(List.of("order.read", "order.create"), allMerchantStores());

        assertThatThrownBy(() -> merchantOrderController.createOrder(orderRequest()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("门店已停用");
    }

    @Test
    void merchantOrderOperationsRequireMatchingStoreScope() {
        setAdminAccount();
        var order = orderService.createOrder(orderRequest());
        setMerchantAccount(
            List.of("order.read", "order.operate"),
            List.of(new StoreScopeResponse(1L, 99L, "SINGLE_STORE"))
        );

        assertThatThrownBy(() -> orderService.cancel(order.id(), new OrderCancelRequest("越权取消测试")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    private OrderCreateRequest orderRequest() {
        return new OrderCreateRequest(null, "门店测试客户", "13800001111", 1L, 1L, null, null, null);
    }

    private List<StoreScopeResponse> allMerchantStores() {
        return List.of(new StoreScopeResponse(1L, null, "ALL_MERCHANT_STORES"));
    }

    private void setMerchantAccount(List<String> permissions, List<StoreScopeResponse> storeScopes) {
        AuthContext.set(new CurrentAccount(
            "merchant-test-token",
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

    private void setAdminAccount() {
        AuthContext.set(new CurrentAccount(
            "admin-order-test-token",
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
}
