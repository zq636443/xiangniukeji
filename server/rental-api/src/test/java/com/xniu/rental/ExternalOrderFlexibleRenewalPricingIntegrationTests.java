package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xniu.rental.auth.dto.CurrentAccountResponse;
import com.xniu.rental.auth.dto.StoreScopeResponse;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.CurrentAccount;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingAdjustmentRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingBatchRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingConfirmRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingFilterRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCompleteRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderUpdateRequest;
import com.xniu.rental.externalorder.service.ExternalOrderRenewalPricingService;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
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
class ExternalOrderFlexibleRenewalPricingIntegrationTests {

    @Autowired
    private ExternalRentalOrderService externalOrderService;

    @Autowired
    private ExternalOrderRenewalPricingService pricingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE store_sku SET status = 'ON_SHELF' WHERE id = 1");
        jdbcTemplate.update("""
            UPDATE store_sku_package
            SET auto_renew_enabled = 1,
                renewal_unit = 'MONTH',
                renewal_value = 1,
                renewal_amount = 129.00,
                renewal_billing_mode = 'DAILY_CAPPED',
                renewal_daily_amount = 5.00,
                renewal_daily_cap_enabled = 1,
                renewal_grace_hours = 24,
                overdue_daily_amount = 7.00
            WHERE store_sku_id = 1 AND package_id = 2
            """);
        AuthContext.set(adminAccount());
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void newOrderSnapshotsSkuRuleAndSingleAdjustmentKeepsAuditAndConfirmationBoundary() {
        var order = createOrder("OFFLINE", "单笔调价客户", LocalDateTime.of(2026, 7, 1, 10, 0));

        assertThat(order.autoRenewEnabled()).isTrue();
        assertThat(order.renewalBillingMode()).isEqualTo("DAILY_CAPPED");
        assertThat(order.renewalAmount()).isEqualByComparingTo("129.00");
        assertThat(order.renewalDailyAmount()).isEqualByComparingTo("5.00");
        assertThat(order.overdueDailyAmount()).isEqualByComparingTo("7.00");
        assertThat(order.renewalGraceHours()).isEqualTo(24);

        var decrease = pricingService.adjust(order.id(), adjustment(
            "119.00", "4.00", "6.00", false, "合作优惠降价"
        ));
        assertThat(decrease.revisionStatus()).isEqualTo("APPLIED");
        assertThat(externalOrderService.getOrder(order.id()).renewalAmount()).isEqualByComparingTo("119.00");

        var edited = externalOrderService.updateOrder(order.id(), new ExternalRentalOrderUpdateRequest(
            order.sourcePlatform(),
            order.externalOrderNo(),
            order.storeSkuId(),
            1L,
            order.leaseMultiplier(),
            "更正后的客户姓名",
            order.customerPhone(),
            order.rentStartedAt(),
            order.expectedReturnAt(),
            order.frameAssetId(),
            order.batteryAssetId(),
            order.externalRentalAmount(),
            order.verificationAmount(),
            order.signFeeAmount(),
            order.depositAmount(),
            "只修改补录资料"
        ));
        assertThat(edited.customerName()).isEqualTo("更正后的客户姓名");
        assertThat(edited.packageId()).isEqualTo(1L);
        assertThat(edited.renewalAmount()).isEqualByComparingTo("119.00");

        var increase = pricingService.adjust(order.id(), adjustment(
            "139.00", "6.00", "8.00", false, "续租价格调整"
        ));
        assertThat(increase.revisionStatus()).isEqualTo("PENDING_CUSTOMER_CONFIRMATION");
        assertThat(externalOrderService.getOrder(order.id()).renewalAmount()).isEqualByComparingTo("119.00");
        assertThatThrownBy(() -> pricingService.adjust(order.id(), adjustment(
            "149.00", "7.00", "9.00", false, "重复调价"
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已有待客户确认");

        var confirmed = pricingService.confirm(increase.id(), new ExternalOrderPricingConfirmRequest(
            "WECHAT", "微信聊天记录 WX-20260701", LocalDateTime.of(2026, 7, 1, 15, 30)
        ));
        var appliedOrder = externalOrderService.getOrder(order.id());
        assertThat(confirmed.revisionStatus()).isEqualTo("APPLIED");
        assertThat(confirmed.confirmationMethod()).isEqualTo("WECHAT");
        assertThat(appliedOrder.renewalAmount()).isEqualByComparingTo("139.00");
        assertThat(appliedOrder.renewalDailyAmount()).isEqualByComparingTo("6.00");
        assertThat(appliedOrder.logs()).extracting("operationType")
            .contains("RENEWAL_PRICING_ADJUSTMENT");
        assertThat(pricingService.list(order.id())).hasSize(2);
    }

    @Test
    void batchPreviewSupportsCombinedConditionsAndRejectsChangedMatchedCount() {
        var targetAt = LocalDateTime.of(2026, 7, 10, 9, 0);
        var target = createOrder("OFFLINE", "筛选目标甲", targetAt);
        createOrder("OFFLINE", "筛选目标乙", targetAt.plusDays(3));
        createOrder("MEITUAN", "筛选目标甲", targetAt);

        var filter = new ExternalOrderPricingFilterRequest(
            List.of(),
            1L,
            "ACTIVE",
            "OFFLINE",
            1L,
            2L,
            targetAt.minusHours(1),
            targetAt.plusHours(1),
            targetAt.plusDays(29),
            targetAt.plusDays(31),
            target.frameAssetSerialNo()
        );
        var adjustment = adjustment("119.00", "4.00", "6.00", false, "条件批量优惠");
        var preview = pricingService.previewBatch(new ExternalOrderPricingBatchRequest(filter, adjustment, null));

        assertThat(preview.matchedCount()).isEqualTo(1);
        assertThat(preview.eligibleCount()).isEqualTo(1);
        assertThat(preview.immediateApplyCount()).isEqualTo(1);
        assertThatThrownBy(() -> pricingService.adjustBatch(new ExternalOrderPricingBatchRequest(filter, adjustment, 2)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("数量已变化");

        var result = pricingService.adjustBatch(new ExternalOrderPricingBatchRequest(filter, adjustment, 1));
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.results()).singleElement().satisfies(row -> {
            assertThat(row.externalOrderId()).isEqualTo(target.id());
            assertThat(row.revisionStatus()).isEqualTo("APPLIED");
        });
        assertThat(externalOrderService.getOrder(target.id()).renewalAmount()).isEqualByComparingTo("119.00");
    }

    @Test
    void batchIncreaseCreatesPendingWithoutConfirmationAndAppliesWithManualConfirmation() {
        var pendingOne = createOrder("OFFLINE", "待确认客户一", LocalDateTime.of(2026, 7, 11, 9, 0));
        var pendingTwo = createOrder("OFFLINE", "待确认客户二", LocalDateTime.of(2026, 7, 12, 9, 0));
        var pendingFilter = orderIdsFilter(pendingOne.id(), pendingTwo.id());
        var increase = adjustment("139.00", "6.00", "8.00", false, "批量续租涨价");

        var pendingPreview = pricingService.previewBatch(new ExternalOrderPricingBatchRequest(pendingFilter, increase, null));
        assertThat(pendingPreview.pendingConfirmationCount()).isEqualTo(2);
        var pendingResult = pricingService.adjustBatch(new ExternalOrderPricingBatchRequest(pendingFilter, increase, 2));
        assertThat(pendingResult.successCount()).isEqualTo(2);
        assertThat(pendingResult.pendingConfirmationCount()).isEqualTo(2);
        assertThat(externalOrderService.getOrder(pendingOne.id()).renewalAmount()).isEqualByComparingTo("129.00");
        assertThat(externalOrderService.getOrder(pendingTwo.id()).renewalAmount()).isEqualByComparingTo("129.00");
        externalOrderService.complete(pendingOne.id(), new ExternalRentalOrderCompleteRequest(null, null, null, "正常归还"));
        assertThat(pricingService.list(pendingOne.id()).getFirst().revisionStatus()).isEqualTo("CANCELLED");

        var confirmedOne = createOrder("OFFLINE", "已确认客户一", LocalDateTime.of(2026, 7, 13, 9, 0));
        var confirmedTwo = createOrder("OFFLINE", "已确认客户二", LocalDateTime.of(2026, 7, 14, 9, 0));
        var confirmedFilter = orderIdsFilter(confirmedOne.id(), confirmedTwo.id());
        var confirmedIncrease = confirmedAdjustment("139.00", "6.00", "8.00", "批量逐单确认涨价");

        var confirmedPreview = pricingService.previewBatch(new ExternalOrderPricingBatchRequest(confirmedFilter, confirmedIncrease, null));
        assertThat(confirmedPreview.confirmedApplyCount()).isEqualTo(2);
        var confirmedResult = pricingService.adjustBatch(new ExternalOrderPricingBatchRequest(confirmedFilter, confirmedIncrease, 2));
        assertThat(confirmedResult.pendingConfirmationCount()).isZero();
        assertThat(confirmedResult.results()).allSatisfy(row -> assertThat(row.revisionStatus()).isEqualTo("APPLIED"));
        assertThat(externalOrderService.getOrder(confirmedOne.id()).renewalAmount()).isEqualByComparingTo("139.00");
        assertThat(pricingService.list(confirmedOne.id()).getFirst().confirmationReference())
            .isEqualTo("批次客户确认清单 BATCH-CONFIRM-001");
    }

    @Test
    void batchSkipsInactiveOrdersAndBlocksOrdersWithPendingAdjustment() {
        var pending = createOrder("OFFLINE", "已有待确认", LocalDateTime.of(2026, 7, 15, 9, 0));
        pricingService.adjust(pending.id(), adjustment("139.00", "6.00", "8.00", false, "先创建待确认"));
        var inactive = createOrder("OFFLINE", "已结束客户", LocalDateTime.of(2026, 7, 16, 9, 0));
        jdbcTemplate.update("UPDATE external_rental_order SET order_status = 'COMPLETED' WHERE id = ?", inactive.id());
        var eligible = createOrder("OFFLINE", "可处理客户", LocalDateTime.of(2026, 7, 17, 9, 0));
        var filter = orderIdsFilter(pending.id(), inactive.id(), eligible.id());
        var decrease = adjustment("119.00", "4.00", "6.00", false, "批量优惠");

        var preview = pricingService.previewBatch(new ExternalOrderPricingBatchRequest(filter, decrease, null));
        assertThat(preview.matchedCount()).isEqualTo(3);
        assertThat(preview.eligibleCount()).isEqualTo(1);
        assertThat(preview.blockedPendingCount()).isEqualTo(1);
        assertThat(preview.skippedInactiveCount()).isEqualTo(1);

        var result = pricingService.adjustBatch(new ExternalOrderPricingBatchRequest(filter, decrease, 3));
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.skippedInactiveCount()).isEqualTo(1);
        assertThat(externalOrderService.getOrder(eligible.id()).renewalAmount()).isEqualByComparingTo("119.00");
        assertThat(externalOrderService.getOrder(inactive.id()).renewalAmount()).isEqualByComparingTo("129.00");
    }

    @Test
    void storePermissionIsolationAppliesToSingleAndBatchPricing() {
        var order = createOrder("OFFLINE", "权限隔离客户", LocalDateTime.of(2026, 7, 18, 9, 0));
        AuthContext.set(restrictedStoreAccount());

        assertThatThrownBy(() -> pricingService.list(order.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
        assertThatThrownBy(() -> pricingService.previewBatch(new ExternalOrderPricingBatchRequest(
            orderIdsFilter(order.id()),
            adjustment("119.00", "4.00", "6.00", false, "越权批量调价"),
            null
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有该门店权限");
    }

    private ExternalRentalOrderResponse createOrder(String source, String customerName, LocalDateTime startedAt) {
        var suffix = Long.toUnsignedString(System.nanoTime());
        var assetId = createIntegratedAsset(suffix);
        return externalOrderService.createOrder(new ExternalRentalOrderCreateRequest(
            source,
            "EXT-PRICE-" + suffix,
            1L,
            2L,
            1,
            customerName,
            "138" + suffix.substring(Math.max(0, suffix.length() - 8)),
            startedAt,
            startedAt.plusDays(30),
            assetId,
            null,
            new BigDecimal("99.00"),
            new BigDecimal("99.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "外部补录续租调价测试"
        ));
    }

    private Long createIntegratedAsset(String suffix) {
        var assetCode = "A-external-price-" + suffix;
        jdbcTemplate.update("""
            INSERT INTO asset_item
            (asset_code, asset_type, asset_type_id, serial_no, investor_id, current_merchant_id, current_store_id, status,
             purchase_amount, maintenance_fee_amount, residual_value, purchased_at)
            VALUES (?, 'INTEGRATED_VEHICLE',
                    (SELECT id FROM asset_type_definition WHERE type_code = 'INTEGRATED_VEHICLE'),
                    ?, 1, 1, 1, 'IDLE', 4200.00, 0.00, NULL, CURRENT_DATE)
            """, assetCode, "EXTERNAL-PRICE-" + suffix);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM asset_item WHERE asset_code = ?",
            Long.class,
            assetCode
        );
    }

    private ExternalOrderPricingAdjustmentRequest adjustment(
        String periodAmount,
        String dailyAmount,
        String overdueDailyAmount,
        boolean customerConfirmed,
        String reason
    ) {
        return new ExternalOrderPricingAdjustmentRequest(
            true,
            "MONTH",
            1,
            new BigDecimal(periodAmount),
            "DAILY_CAPPED",
            new BigDecimal(dailyAmount),
            true,
            24,
            new BigDecimal(overdueDailyAmount),
            reason,
            customerConfirmed,
            customerConfirmed ? "WECHAT" : null,
            customerConfirmed ? "已取得客户确认" : null,
            customerConfirmed ? LocalDateTime.of(2026, 7, 20, 10, 0) : null
        );
    }

    private ExternalOrderPricingAdjustmentRequest confirmedAdjustment(
        String periodAmount,
        String dailyAmount,
        String overdueDailyAmount,
        String reason
    ) {
        return new ExternalOrderPricingAdjustmentRequest(
            true,
            "MONTH",
            1,
            new BigDecimal(periodAmount),
            "DAILY_CAPPED",
            new BigDecimal(dailyAmount),
            true,
            24,
            new BigDecimal(overdueDailyAmount),
            reason,
            true,
            "PHONE",
            "批次客户确认清单 BATCH-CONFIRM-001",
            LocalDateTime.of(2026, 7, 20, 10, 0)
        );
    }

    private ExternalOrderPricingFilterRequest orderIdsFilter(Long... orderIds) {
        return new ExternalOrderPricingFilterRequest(
            List.of(orderIds), 1L, null, null, null, null, null, null, null, null, null
        );
    }

    private CurrentAccount adminAccount() {
        return new CurrentAccount(
            "external-renewal-admin-token",
            new CurrentAccountResponse(
                1L, "PLATFORM_ADMIN", "admin", "18800000001", null, "平台管理员",
                null, null, null, List.of("PLATFORM_ADMIN"), List.of("system.admin"), List.of()
            )
        );
    }

    private CurrentAccount restrictedStoreAccount() {
        return new CurrentAccount(
            "external-renewal-restricted-token",
            new CurrentAccountResponse(
                2002L, "STORE_STAFF", "restricted-store", "13800139998", null, "其他门店人员",
                1L, 99L, null, List.of("STORE_STAFF"), List.of("order.read", "order.operate"),
                List.of(new StoreScopeResponse(1L, 99L, "SINGLE_STORE"))
            )
        );
    }
}
