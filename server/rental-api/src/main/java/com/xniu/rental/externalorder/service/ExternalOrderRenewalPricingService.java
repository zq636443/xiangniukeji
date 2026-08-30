package com.xniu.rental.externalorder.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingAdjustmentRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingBatchRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingBatchResultResponse;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingConfirmRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingFilterRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingPreviewResponse;
import com.xniu.rental.externalorder.dto.ExternalOrderPricingRevisionResponse;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderPricingRevision;
import com.xniu.rental.externalorder.model.ExternalOrderSourcePlatform;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderPricingRevisionRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.pricing.dto.RenewalPricingRuleResponse;
import com.xniu.rental.pricing.model.PricingRevisionStatus;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import com.xniu.rental.pricing.model.RenewalPricingRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalOrderRenewalPricingService {

    private static final Set<String> CONFIRMATION_METHODS = Set.of("WECHAT", "PHONE", "PAPER", "OTHER");

    private final ExternalRentalOrderRepository orderRepository;
    private final ExternalOrderPricingRevisionRepository revisionRepository;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;

    public ExternalOrderRenewalPricingService(
        ExternalRentalOrderRepository orderRepository,
        ExternalOrderPricingRevisionRepository revisionRepository,
        AuthorizationService authorizationService,
        TransactionTemplate transactionTemplate
    ) {
        this.orderRepository = orderRepository;
        this.revisionRepository = revisionRepository;
        this.authorizationService = authorizationService;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ExternalOrderPricingRevisionResponse> list(Long externalOrderId) {
        authorizationService.requirePermission("order.read");
        var order = ensureOrder(externalOrderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        return revisionRepository.listByOrder(externalOrderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExternalOrderPricingRevisionResponse adjust(Long externalOrderId, ExternalOrderPricingAdjustmentRequest request) {
        authorizationService.requirePermission("order.operate");
        var outcome = adjustInternal(externalOrderId, request, null);
        return toResponse(outcome.revision());
    }

    @Transactional
    public ExternalOrderPricingRevisionResponse confirm(Long revisionId, ExternalOrderPricingConfirmRequest request) {
        authorizationService.requirePermission("order.operate");
        /* Lock in the same order as complete/terminate (order -> pricing
         * revision).  Reading the revision once to discover its order and
         * then re-reading it under lock prevents a confirm/terminate pair
         * from deadlocking on opposite row-lock orders. */
        var revisionRef = revisionRepository.findById(revisionId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单续租调价记录不存在"));
        var order = orderRepository.findByIdForUpdate(revisionRef.externalOrderId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        var revision = revisionRepository.findByIdForUpdate(revisionId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单续租调价记录不存在"));
        if (!revision.externalOrderId().equals(order.id())) {
            throw BusinessException.badRequest("补录订单调价记录关联关系异常");
        }
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (revision.revisionStatus() == PricingRevisionStatus.APPLIED) {
            return toResponse(revision);
        }
        if (revision.revisionStatus() != PricingRevisionStatus.PENDING_CUSTOMER_CONFIRMATION) {
            throw BusinessException.badRequest("当前调价记录不能确认生效");
        }
        ensureActive(order);
        var method = normalizeConfirmationMethod(request.confirmationMethod());
        var reference = requireConfirmationReference(request.confirmationReference());
        var confirmedAt = request.customerConfirmedAt() == null ? LocalDateTime.now() : request.customerConfirmedAt();
        applyRule(order, revision.newRule(), revision.reason(), revision.batchNo());
        return toResponse(revisionRepository.confirmAndMarkApplied(revision.id(), method, reference, confirmedAt));
    }

    public ExternalOrderPricingPreviewResponse previewBatch(ExternalOrderPricingBatchRequest request) {
        authorizationService.requirePermission("order.operate");
        var filter = normalizeFilter(request.filter());
        var next = normalize(request.adjustment());
        validateConfirmationIfMarked(request.adjustment());
        var orders = orderRepository.listForPricing(filter);
        requireAccess(orders);
        var pendingOrderIds = revisionRepository.findPendingOrderIds(orders.stream().map(ExternalRentalOrder::id).toList());
        return classify(orders, pendingOrderIds, next, Boolean.TRUE.equals(request.adjustment().customerConfirmed()));
    }

    public ExternalOrderPricingBatchResultResponse adjustBatch(ExternalOrderPricingBatchRequest request) {
        authorizationService.requirePermission("order.operate");
        if (request.expectedMatchedCount() == null) {
            throw BusinessException.badRequest("请先预览批量调整范围");
        }
        var filter = normalizeFilter(request.filter());
        var next = normalize(request.adjustment());
        validateConfirmationIfMarked(request.adjustment());
        var orders = orderRepository.listForPricing(filter);
        requireAccess(orders);
        if (orders.size() != request.expectedMatchedCount()) {
            throw BusinessException.badRequest("命中订单数量已变化，请重新预览后再执行");
        }
        var pendingOrderIds = revisionRepository.findPendingOrderIds(orders.stream().map(ExternalRentalOrder::id).toList());
        var batchNo = "EPR-" + UUID.randomUUID().toString().substring(0, 8);
        var results = new ArrayList<ExternalOrderPricingBatchResultResponse.RowResult>();
        var successCount = 0;
        var pendingCount = 0;
        var unchangedCount = 0;
        var inactiveCount = 0;
        var failedCount = 0;

        for (var order : orders) {
            if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
                inactiveCount++;
                results.add(rowResult(order, true, null, "非进行中订单，已跳过"));
                continue;
            }
            if (sameRule(fromOrder(order), next)) {
                unchangedCount++;
                results.add(rowResult(order, true, null, "续租规则未变化"));
                continue;
            }
            if (pendingOrderIds.contains(order.id())) {
                failedCount++;
                results.add(rowResult(order, false, null, "已有待客户确认的续租调价"));
                continue;
            }
            try {
                var outcome = transactionTemplate.execute(status -> adjustInternal(order.id(), request.adjustment(), batchNo));
                if (outcome == null) {
                    throw BusinessException.badRequest("批量调价失败");
                }
                successCount++;
                if (outcome.revision().revisionStatus() == PricingRevisionStatus.PENDING_CUSTOMER_CONFIRMATION) {
                    pendingCount++;
                }
                results.add(rowResult(
                    order,
                    true,
                    outcome.revision().revisionStatus().name(),
                    outcome.revision().revisionStatus() == PricingRevisionStatus.APPLIED ? "调整已生效" : "已创建待人工确认记录"
                ));
            } catch (RuntimeException exception) {
                failedCount++;
                results.add(rowResult(order, false, null, exception.getMessage()));
            }
        }

        return new ExternalOrderPricingBatchResultResponse(
            batchNo,
            orders.size(),
            successCount,
            pendingCount,
            unchangedCount,
            inactiveCount,
            failedCount,
            results
        );
    }

    private AdjustmentOutcome adjustInternal(Long externalOrderId, ExternalOrderPricingAdjustmentRequest request, String batchNo) {
        var order = orderRepository.findByIdForUpdate(externalOrderId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        ensureActive(order);
        if (revisionRepository.hasPending(externalOrderId)) {
            throw BusinessException.badRequest("当前补录订单已有待客户确认的续租调价");
        }
        var previous = fromOrder(order);
        var next = normalize(request);
        if (sameRule(previous, next)) {
            throw BusinessException.badRequest("续租规则未发生变化");
        }
        var requiresConfirmation = requiresCustomerConfirmation(previous, next);
        var customerConfirmed = requiresConfirmation && Boolean.TRUE.equals(request.customerConfirmed());
        String confirmationMethod = null;
        String confirmationReference = null;
        LocalDateTime customerConfirmedAt = null;
        if (customerConfirmed) {
            confirmationMethod = normalizeConfirmationMethod(request.confirmationMethod());
            confirmationReference = requireConfirmationReference(request.confirmationReference());
            customerConfirmedAt = request.customerConfirmedAt() == null ? LocalDateTime.now() : request.customerConfirmedAt();
        }
        var status = requiresConfirmation && !customerConfirmed
            ? PricingRevisionStatus.PENDING_CUSTOMER_CONFIRMATION
            : PricingRevisionStatus.APPLIED;
        var revision = revisionRepository.create(
            externalOrderId,
            batchNo,
            status,
            requiresConfirmation,
            previous,
            next,
            request.reason().trim(),
            confirmationMethod,
            confirmationReference,
            currentAccountId(),
            customerConfirmedAt
        );
        if (status == PricingRevisionStatus.APPLIED) {
            applyRule(order, next, revision.reason(), batchNo);
        }
        return new AdjustmentOutcome(revision);
    }

    private void applyRule(ExternalRentalOrder order, RenewalPricingRule rule, String reason, String batchNo) {
        orderRepository.updateRenewalPricing(order.id(), rule, currentAccountId());
        orderRepository.addLog(
            order.id(),
            order.orderStatus(),
            order.orderStatus(),
            ExternalOrderOperationType.RENEWAL_PRICING_ADJUSTMENT,
            currentAccountId(),
            (batchNo == null ? "" : "批次 " + batchNo + "；") + "续租计费规则已调整：" + reason
        );
    }

    private ExternalOrderPricingPreviewResponse classify(
        List<ExternalRentalOrder> orders,
        Set<Long> pendingOrderIds,
        RenewalPricingRule next,
        boolean customerConfirmed
    ) {
        var eligible = 0;
        var unchanged = 0;
        var immediate = 0;
        var confirmed = 0;
        var pending = 0;
        var blocked = 0;
        var inactive = 0;
        for (var order : orders) {
            if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
                inactive++;
                continue;
            }
            var previous = fromOrder(order);
            if (sameRule(previous, next)) {
                unchanged++;
                continue;
            }
            if (pendingOrderIds.contains(order.id())) {
                blocked++;
                continue;
            }
            eligible++;
            if (!requiresCustomerConfirmation(previous, next)) {
                immediate++;
            } else if (customerConfirmed) {
                confirmed++;
            } else {
                pending++;
            }
        }
        return new ExternalOrderPricingPreviewResponse(
            orders.size(), eligible, unchanged, immediate, confirmed, pending, blocked, inactive
        );
    }

    private ExternalOrderPricingFilterRequest normalizeFilter(ExternalOrderPricingFilterRequest filter) {
        if (filter == null) {
            throw BusinessException.badRequest("缺少批量筛选条件");
        }
        if (filter.orderIds() != null && filter.orderIds().size() > 5000) {
            throw BusinessException.badRequest("单次批量调整不能超过 5000 笔指定订单");
        }
        var status = blankToNull(filter.status());
        if (status != null) {
            try {
                ExternalRentalOrderStatus.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("不支持的补录订单状态");
            }
        }
        var source = blankToNull(filter.sourcePlatform());
        if (source != null) {
            try {
                ExternalOrderSourcePlatform.valueOf(source);
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("不支持的订单来源平台");
            }
        }
        if (filter.rentStartedFrom() != null && filter.rentStartedTo() != null
            && filter.rentStartedFrom().isAfter(filter.rentStartedTo())) {
            throw BusinessException.badRequest("起租时间范围不正确");
        }
        if (filter.expectedReturnFrom() != null && filter.expectedReturnTo() != null
            && filter.expectedReturnFrom().isAfter(filter.expectedReturnTo())) {
            throw BusinessException.badRequest("预计归还时间范围不正确");
        }
        return new ExternalOrderPricingFilterRequest(
            filter.orderIds() == null ? List.of() : filter.orderIds().stream().filter(java.util.Objects::nonNull).distinct().toList(),
            filter.storeId(), status, source, filter.storeSkuId(), filter.packageId(), filter.rentStartedFrom(),
            filter.rentStartedTo(), filter.expectedReturnFrom(), filter.expectedReturnTo(), blankToNull(filter.keyword())
        );
    }

    private RenewalPricingRule normalize(ExternalOrderPricingAdjustmentRequest request) {
        var enabled = Boolean.TRUE.equals(request.autoRenewEnabled());
        var mode = parseMode(request.renewalBillingMode());
        if (!enabled) {
            return new RenewalPricingRule(false, null, null, null, RenewalBillingMode.PERIOD, null, true, 0, null);
        }
        var unit = request.renewalUnit();
        var value = request.renewalValue();
        var amount = money(request.renewalAmount());
        if (!("DAY".equals(unit) || "MONTH".equals(unit)) || value == null || value <= 0 || value > 3650) {
            throw BusinessException.badRequest("续租周期必须在 1 到 3650 之间");
        }
        if (amount == null || amount.signum() <= 0) {
            throw BusinessException.badRequest("整期续租金额必须大于 0");
        }
        var dailyAmount = mode == RenewalBillingMode.DAILY_CAPPED ? money(request.renewalDailyAmount()) : null;
        var overdueDailyAmount = mode == RenewalBillingMode.DAILY_CAPPED ? money(request.overdueDailyAmount()) : null;
        var capEnabled = request.renewalDailyCapEnabled() == null || request.renewalDailyCapEnabled();
        var graceHours = request.renewalGraceHours() == null ? 0 : request.renewalGraceHours();
        if (mode == RenewalBillingMode.DAILY_CAPPED && (dailyAmount == null || dailyAmount.signum() <= 0)) {
            throw BusinessException.badRequest("按日续租价格必须大于 0");
        }
        if (overdueDailyAmount != null && overdueDailyAmount.signum() <= 0) {
            throw BusinessException.badRequest("逾期日占用费必须大于 0");
        }
        if (graceHours < 0 || graceHours > 72) {
            throw BusinessException.badRequest("续租宽限时间必须在 0 到 72 小时之间");
        }
        var periodDays = "MONTH".equals(unit) ? Math.multiplyExact(value, 30) : value;
        if (mode == RenewalBillingMode.DAILY_CAPPED && capEnabled
            && dailyAmount.multiply(BigDecimal.valueOf(periodDays)).compareTo(amount) < 0) {
            throw BusinessException.badRequest("启用整期封顶时，日租累计整期金额不能低于整期续租价");
        }
        return new RenewalPricingRule(enabled, unit, value, amount, mode, dailyAmount, capEnabled, graceHours, overdueDailyAmount);
    }

    private boolean requiresCustomerConfirmation(RenewalPricingRule previous, RenewalPricingRule next) {
        if (Boolean.TRUE.equals(previous.autoRenewEnabled()) && !Boolean.TRUE.equals(next.autoRenewEnabled())) {
            return false;
        }
        var bothEnabled = Boolean.TRUE.equals(previous.autoRenewEnabled()) && Boolean.TRUE.equals(next.autoRenewEnabled());
        return (!Boolean.TRUE.equals(previous.autoRenewEnabled()) && Boolean.TRUE.equals(next.autoRenewEnabled()))
            || (bothEnabled && previous.renewalBillingMode() != next.renewalBillingMode())
            || (bothEnabled && periodDays(next) < periodDays(previous))
            || (bothEnabled
                && previous.renewalBillingMode() == RenewalBillingMode.DAILY_CAPPED
                && next.renewalBillingMode() == RenewalBillingMode.DAILY_CAPPED
                && safeGraceHours(next) < safeGraceHours(previous))
            || (previous.renewalBillingMode() == RenewalBillingMode.DAILY_CAPPED
                && next.renewalBillingMode() == RenewalBillingMode.DAILY_CAPPED
                && Boolean.TRUE.equals(previous.renewalDailyCapEnabled())
                && !Boolean.TRUE.equals(next.renewalDailyCapEnabled()))
            || increased(previous.renewalAmount(), next.renewalAmount())
            || increased(previous.renewalDailyAmount(), next.renewalDailyAmount())
            || increased(previous.overdueDailyAmount(), next.overdueDailyAmount());
    }

    private boolean sameRule(RenewalPricingRule left, RenewalPricingRule right) {
        return Boolean.TRUE.equals(left.autoRenewEnabled()) == Boolean.TRUE.equals(right.autoRenewEnabled())
            && java.util.Objects.equals(left.renewalUnit(), right.renewalUnit())
            && java.util.Objects.equals(left.renewalValue(), right.renewalValue())
            && sameMoney(left.renewalAmount(), right.renewalAmount())
            && left.renewalBillingMode() == right.renewalBillingMode()
            && sameMoney(left.renewalDailyAmount(), right.renewalDailyAmount())
            && Boolean.TRUE.equals(left.renewalDailyCapEnabled()) == Boolean.TRUE.equals(right.renewalDailyCapEnabled())
            && safeGraceHours(left) == safeGraceHours(right)
            && sameMoney(left.overdueDailyAmount(), right.overdueDailyAmount());
    }

    private RenewalPricingRule fromOrder(ExternalRentalOrder order) {
        return new RenewalPricingRule(
            order.autoRenewEnabled(), order.renewalUnit(), order.renewalValue(), order.renewalAmount(),
            parseMode(order.renewalBillingMode()), order.renewalDailyAmount(), order.renewalDailyCapEnabled(),
            order.renewalGraceHours() == null ? 0 : order.renewalGraceHours(), order.overdueDailyAmount()
        );
    }

    private int periodDays(RenewalPricingRule rule) {
        if (!Boolean.TRUE.equals(rule.autoRenewEnabled()) || rule.renewalValue() == null || rule.renewalValue() <= 0) {
            return Integer.MAX_VALUE;
        }
        return "MONTH".equals(rule.renewalUnit()) ? Math.multiplyExact(rule.renewalValue(), 30) : rule.renewalValue();
    }

    private int safeGraceHours(RenewalPricingRule rule) {
        return rule.renewalGraceHours() == null ? 0 : rule.renewalGraceHours();
    }

    private boolean increased(BigDecimal previous, BigDecimal next) {
        if (next == null) return false;
        return previous == null || next.compareTo(previous) > 0;
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }

    private void validateConfirmationIfMarked(ExternalOrderPricingAdjustmentRequest request) {
        if (!Boolean.TRUE.equals(request.customerConfirmed())) return;
        normalizeConfirmationMethod(request.confirmationMethod());
        requireConfirmationReference(request.confirmationReference());
    }

    private String normalizeConfirmationMethod(String value) {
        var method = blankToNull(value);
        if (method == null || !CONFIRMATION_METHODS.contains(method)) {
            throw BusinessException.badRequest("客户确认方式只能是微信、电话、纸质文件或其他");
        }
        return method;
    }

    private String requireConfirmationReference(String value) {
        var reference = blankToNull(value);
        if (reference == null) {
            throw BusinessException.badRequest("已人工确认的调价必须填写确认凭证或备注");
        }
        return reference;
    }

    private void requireAccess(List<ExternalRentalOrder> orders) {
        for (var order : orders) {
            authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        }
    }

    private ExternalOrderPricingBatchResultResponse.RowResult rowResult(
        ExternalRentalOrder order,
        boolean success,
        String revisionStatus,
        String message
    ) {
        return new ExternalOrderPricingBatchResultResponse.RowResult(
            order.id(), order.recordNo(), success, revisionStatus, message
        );
    }

    private ExternalRentalOrder ensureOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
    }

    private void ensureActive(ExternalRentalOrder order) {
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            throw BusinessException.badRequest("只有进行中的补录订单才能调整续租规则");
        }
    }

    private RenewalBillingMode parseMode(String value) {
        try {
            return value == null || value.isBlank() ? RenewalBillingMode.PERIOD : RenewalBillingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的续租计费模式");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private ExternalOrderPricingRevisionResponse toResponse(ExternalOrderPricingRevision revision) {
        return new ExternalOrderPricingRevisionResponse(
            revision.id(), revision.externalOrderId(), revision.batchNo(), revision.revisionStatus().name(),
            revision.requiresCustomerConfirmation(), ruleResponse(revision.previousRule()), ruleResponse(revision.newRule()),
            revision.reason(), revision.confirmationMethod(), revision.confirmationReference(), revision.operatorAccountId(),
            revision.customerConfirmedAt(), revision.appliedAt(), revision.createdAt()
        );
    }

    private RenewalPricingRuleResponse ruleResponse(RenewalPricingRule rule) {
        return new RenewalPricingRuleResponse(
            rule.autoRenewEnabled(), rule.renewalUnit(), rule.renewalValue(), rule.renewalAmount(),
            rule.renewalBillingMode().name(), rule.renewalDailyAmount(), rule.renewalDailyCapEnabled(),
            rule.renewalGraceHours(), rule.overdueDailyAmount()
        );
    }

    private record AdjustmentOutcome(ExternalOrderPricingRevision revision) {
    }
}
