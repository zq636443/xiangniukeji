package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderLeaseBonusRequest;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.service.OrderRenewalService;
import com.xniu.rental.order.service.OrderService;
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
class OrderLeaseBonusIntegrationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRenewalService orderRenewalService;

    @Autowired
    private AssetFulfillmentService assetFulfillmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setAdminAccount() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        jdbcTemplate.update("""
            UPDATE asset_item
            SET status = 'IDLE', current_merchant_id = 1, current_store_id = 1
            WHERE id IN (1, 2)
            """);
        AuthContext.set(adminAccount());
    }

    @AfterEach
    void clearCurrentAccount() {
        AuthContext.clear();
    }

    @Test
    void bonusGrantedBeforePickupIsIncludedWhenRentalStarts() {
        var order = createOrder("起租前赠送客户");

        var gifted = orderService.grantLeaseBonus(
            order.id(),
            new OrderLeaseBonusRequest("REVIEW", 2, "客户完成平台好评")
        );

        assertThat(gifted.reviewBonusDays()).isEqualTo(2);
        assertThat(gifted.campaignBonusDays()).isZero();
        assertThat(gifted.totalBonusDays()).isEqualTo(2);
        assertThat(gifted.expectedReturnAt()).isNull();
        assertThat(gifted.leaseBonuses()).singleElement().satisfies(item -> {
            assertThat(item.bonusType()).isEqualTo("REVIEW");
            assertThat(item.bonusDays()).isEqualTo(2);
            assertThat(item.expectedReturnBefore()).isNull();
            assertThat(item.expectedReturnAfter()).isNull();
        });

        var renting = transitionToRenting(order.id());
        assertThat(renting.expectedReturnAt()).isEqualTo(baseExpectedReturnAt(renting).plusDays(2));
        assertThat(renting.payableAmount()).isEqualByComparingTo(order.payableAmount());
        assertThat(renting.renewalCount()).isZero();
    }

    @Test
    void campaignBonusDuringRentalExtendsDeadlineWithoutChangingAmounts() {
        var order = createOrder("活动赠送客户");
        var renting = transitionToRenting(order.id());
        var deadlineBefore = renting.expectedReturnAt();

        var gifted = orderService.grantLeaseBonus(
            order.id(),
            new OrderLeaseBonusRequest("CAMPAIGN", 15, "暑期活动")
        );

        assertThat(gifted.reviewBonusDays()).isZero();
        assertThat(gifted.campaignBonusDays()).isEqualTo(15);
        assertThat(gifted.totalBonusDays()).isEqualTo(15);
        assertThat(gifted.expectedReturnAt()).isEqualTo(deadlineBefore.plusDays(15));
        assertThat(gifted.rentalAmount()).isEqualByComparingTo(order.rentalAmount());
        assertThat(gifted.payableAmount()).isEqualByComparingTo(order.payableAmount());
        assertThat(gifted.renewalCount()).isZero();
        assertThat(gifted.leaseBonuses()).singleElement().satisfies(item -> {
            assertThat(item.bonusType()).isEqualTo("CAMPAIGN");
            assertThat(item.bonusDays()).isEqualTo(15);
            assertThat(item.expectedReturnBefore()).isEqualTo(deadlineBefore);
            assertThat(item.expectedReturnAfter()).isEqualTo(deadlineBefore.plusDays(15));
        });
        assertThat(gifted.logs()).anySatisfy(log -> {
            assertThat(log.operationType()).isEqualTo("LEASE_BONUS");
            assertThat(log.remark()).contains("活动赠送 15 天");
        });
    }

    @Test
    void bonusMovesDueOrderOutOfAutoRenewalScan() {
        var order = createOrder("续租保护客户");
        var dueAt = LocalDateTime.now().minusHours(1).withNano(0);
        jdbcTemplate.update("""
            UPDATE rental_order
            SET order_status = 'RENTING',
                lease_started_at = ?,
                expected_return_at = ?,
                auto_renew_enabled = 1,
                renewal_unit = 'MONTH',
                renewal_value = 1,
                renewal_amount = 399.00
            WHERE id = ?
            """, dueAt.minusMonths(1), dueAt, order.id());

        var gifted = orderService.grantLeaseBonus(
            order.id(),
            new OrderLeaseBonusRequest("REVIEW", 2, "到期前补录好评赠送")
        );
        var renewalRun = orderRenewalService.runDueRenewalsInternal(10, "赠送租期后扫描");

        assertThat(gifted.expectedReturnAt()).isEqualTo(dueAt.plusDays(2));
        assertThat(gifted.orderStatus()).isEqualTo("RENTING");
        assertThat(renewalRun.scannedCount()).isZero();
        assertThat(renewalRun.generatedCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rental_bill WHERE order_id = ? AND bill_type = 'RENEWAL'",
            Integer.class,
            order.id()
        )).isZero();
    }

    @Test
    void storeStaffNeedsOrderPermissionAndMatchingStoreScope() {
        var order = createOrder("门店权限客户");

        AuthContext.set(storeAccount(List.of("order.read"), List.of(new StoreScopeResponse(1L, 1L, "SINGLE_STORE"))));
        assertThatThrownBy(() -> orderService.grantLeaseBonus(
            order.id(),
            new OrderLeaseBonusRequest("REVIEW", 2, null)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有操作权限");

        AuthContext.set(storeAccount(List.of("order.read", "order.operate"), List.of(new StoreScopeResponse(1L, 99L, "SINGLE_STORE"))));
        assertThatThrownBy(() -> orderService.grantLeaseBonus(
            order.id(),
            new OrderLeaseBonusRequest("REVIEW", 2, null)
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");

        AuthContext.set(storeAccount(List.of("order.read", "order.operate"), List.of(new StoreScopeResponse(1L, 1L, "SINGLE_STORE"))));
        var gifted = orderService.grantLeaseBonus(
            order.id(),
            new OrderLeaseBonusRequest("CAMPAIGN", 15, "门店周年活动")
        );

        assertThat(gifted.totalBonusDays()).isEqualTo(15);
        assertThat(gifted.leaseBonuses()).singleElement()
            .satisfies(item -> assertThat(item.operatorAccountId()).isEqualTo(3L));
    }

    @Test
    void assetFulfillmentStatesCannotBeBypassedByGenericTransition() {
        var order = createOrder("履约状态保护客户");
        transition(order.id(), "PENDING_REAL_NAME");
        transition(order.id(), "PENDING_AGREEMENT");
        transition(order.id(), "PENDING_DEPOSIT_AUTH");
        transition(order.id(), "PENDING_VERIFY");
        transition(order.id(), "PENDING_PICKUP");

        assertThatThrownBy(() -> transition(order.id(), "RENTING"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不允许");

        assetFulfillmentService.pickup(order.id(), new AssetPickupRequest(1L, 2L, "专用取车流程"));
        transition(order.id(), "PENDING_RETURN");

        assertThatThrownBy(() -> transition(order.id(), "COMPLETED"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不允许");

        assetFulfillmentService.returnAssets(order.id(), new AssetReturnRequest(null, "IDLE", "IDLE", "专用归还流程"));
        assertThat(orderService.getOrder(order.id()).orderStatus()).isEqualTo("COMPLETED");
    }

    private OrderResponse createOrder(String customerName) {
        AuthContext.set(adminAccount());
        return orderService.createOrder(new OrderCreateRequest(
            null,
            customerName,
            "13800009999",
            1L,
            2L,
            null,
            null,
            null
        ));
    }

    private OrderResponse transitionToRenting(Long orderId) {
        transition(orderId, "PENDING_REAL_NAME");
        transition(orderId, "PENDING_AGREEMENT");
        transition(orderId, "PENDING_DEPOSIT_AUTH");
        transition(orderId, "PENDING_VERIFY");
        transition(orderId, "PENDING_PICKUP");
        assetFulfillmentService.pickup(orderId, new AssetPickupRequest(1L, 2L, "赠送租期测试取车"));
        return orderService.getOrder(orderId);
    }

    private OrderResponse transition(Long orderId, String targetStatus) {
        return orderService.transition(orderId, new OrderTransitionRequest(targetStatus, "赠送租期测试"));
    }

    private LocalDateTime baseExpectedReturnAt(OrderResponse order) {
        if ("MONTH".equals(order.leaseUnit())) {
            return order.leaseStartedAt().plusMonths(order.leaseValue());
        }
        return order.leaseStartedAt().plusDays(order.leaseValue());
    }

    private CurrentAccount adminAccount() {
        return new CurrentAccount(
            "admin-test-token",
            new CurrentAccountResponse(
                1L,
                "PLATFORM_ADMIN",
                "admin",
                "18800000001",
                null,
                "平台管理员",
                null,
                null,
                null,
                List.of("PLATFORM_ADMIN"),
                List.of("system.admin"),
                List.of()
            )
        );
    }

    private CurrentAccount storeAccount(List<String> permissions, List<StoreScopeResponse> scopes) {
        return new CurrentAccount(
            "store-test-token",
            new CurrentAccountResponse(
                3L,
                "STORE_STAFF",
                "store_demo",
                "18800000003",
                null,
                "演示门店员工",
                1L,
                1L,
                null,
                List.of("STORE_STAFF"),
                permissions,
                scopes
            )
        );
    }
}
