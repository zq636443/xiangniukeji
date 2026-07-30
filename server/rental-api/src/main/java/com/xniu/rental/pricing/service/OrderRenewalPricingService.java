package com.xniu.rental.pricing.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.pricing.dto.OrderPricingRevisionResponse;
import com.xniu.rental.pricing.dto.RenewalPricingRuleRequest;
import com.xniu.rental.pricing.dto.RenewalPricingRuleResponse;
import com.xniu.rental.pricing.model.OrderPricingRevision;
import com.xniu.rental.pricing.model.PricingRevisionStatus;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import com.xniu.rental.pricing.model.RenewalPricingRule;
import com.xniu.rental.pricing.repository.OrderPricingRevisionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderRenewalPricingService {

    private final OrderRepository orderRepository;
    private final OrderPricingRevisionRepository revisionRepository;
    private final AuthorizationService authorizationService;

    public OrderRenewalPricingService(
        OrderRepository orderRepository,
        OrderPricingRevisionRepository revisionRepository,
        AuthorizationService authorizationService
    ) {
        this.orderRepository = orderRepository;
        this.revisionRepository = revisionRepository;
        this.authorizationService = authorizationService;
    }

    public List<OrderPricingRevisionResponse> list(Long orderId) {
        authorizationService.requirePermission("order.read");
        var order = ensureOrder(orderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        return revisionRepository.listByOrder(orderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderPricingRevisionResponse createAdjustment(Long orderId, RenewalPricingRuleRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (isTerminal(order.orderStatus())) {
            throw BusinessException.badRequest("已结束订单不能调整续租价格");
        }
        if (revisionRepository.hasPending(orderId)) {
            throw BusinessException.badRequest("当前订单已有待用户确认的续租调价");
        }
        var previous = fromOrder(order);
        var next = normalize(request);
        var requiresConfirmation = requiresCustomerConfirmation(order, previous, next);
        var status = requiresConfirmation ? PricingRevisionStatus.PENDING_CUSTOMER_CONFIRMATION : PricingRevisionStatus.APPLIED;
        var created = revisionRepository.create(orderId, status, requiresConfirmation, previous, next, request.reason().trim(), currentAccountId());
        if (!requiresConfirmation) {
            applyRule(order, created, false);
        }
        return toResponse(revisionRepository.findById(created.id()).orElseThrow());
    }

    public OrderPricingRevision getRevision(Long id) {
        return revisionRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("续租调价记录不存在"));
    }

    @Transactional
    public OrderPricingRevisionResponse confirmAndApply(Long id) {
        var revision = getRevision(id);
        if (revision.revisionStatus() == PricingRevisionStatus.APPLIED) {
            return toResponse(revision);
        }
        if (revision.revisionStatus() != PricingRevisionStatus.PENDING_CUSTOMER_CONFIRMATION) {
            throw BusinessException.badRequest("当前续租调价记录不能确认生效");
        }
        var order = ensureOrder(revision.orderId());
        applyRule(order, revision, true);
        return toResponse(revisionRepository.findById(id).orElseThrow());
    }

    private void applyRule(RentalOrder order, OrderPricingRevision revision, boolean customerConfirmed) {
        var updated = orderRepository.updateRenewalPricing(order.id(), revision.newRule());
        revisionRepository.markApplied(revision.id(), customerConfirmed);
        orderRepository.addLog(
            order.id(), order.orderStatus(), updated.orderStatus(), OrderOperationType.RENEWAL_PRICING_ADJUSTMENT,
            currentAccountId(), "续租计费规则已调整，从下一笔未生成续租账单生效：" + revision.reason()
        );
    }

    private RenewalPricingRule normalize(RenewalPricingRuleRequest request) {
        var enabled = Boolean.TRUE.equals(request.autoRenewEnabled());
        var mode = parseMode(request.renewalBillingMode());
        if (!enabled) {
            return new RenewalPricingRule(false, null, null, null, RenewalBillingMode.PERIOD, null, true, 0, null);
        }
        var unit = request.renewalUnit();
        var value = request.renewalValue();
        var amount = money(request.renewalAmount());
        if (!("DAY".equals(unit) || "MONTH".equals(unit)) || value == null || value <= 0) {
            throw BusinessException.badRequest("续租周期必须大于 0");
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
        var periodDays = "MONTH".equals(unit) ? value * 30 : value;
        if (mode == RenewalBillingMode.DAILY_CAPPED && capEnabled
            && dailyAmount.multiply(BigDecimal.valueOf(periodDays)).compareTo(amount) < 0) {
            throw BusinessException.badRequest("启用整期封顶时，日租累计整期金额不能低于整期续租价");
        }
        return new RenewalPricingRule(enabled, unit, value, amount, mode, dailyAmount, capEnabled, graceHours, overdueDailyAmount);
    }

    private boolean requiresCustomerConfirmation(RentalOrder order, RenewalPricingRule previous, RenewalPricingRule next) {
        if (order.orderStatus() == OrderStatus.PENDING_PAYMENT && (order.paidAmount() == null || order.paidAmount().signum() == 0)) {
            return false;
        }
        return (!Boolean.TRUE.equals(previous.autoRenewEnabled()) && Boolean.TRUE.equals(next.autoRenewEnabled()))
            || previous.renewalBillingMode() != next.renewalBillingMode()
            || periodShortened(previous, next)
            || graceShortened(previous, next)
            || capRemoved(previous, next)
            || increased(previous.renewalAmount(), next.renewalAmount())
            || increased(previous.renewalDailyAmount(), next.renewalDailyAmount())
            || increased(previous.overdueDailyAmount(), next.overdueDailyAmount());
    }

    private boolean periodShortened(RenewalPricingRule previous, RenewalPricingRule next) {
        if (!Boolean.TRUE.equals(previous.autoRenewEnabled()) || !Boolean.TRUE.equals(next.autoRenewEnabled())) {
            return false;
        }
        return periodDays(next) < periodDays(previous);
    }

    private boolean graceShortened(RenewalPricingRule previous, RenewalPricingRule next) {
        if (previous.renewalBillingMode() != RenewalBillingMode.DAILY_CAPPED
            || next.renewalBillingMode() != RenewalBillingMode.DAILY_CAPPED) {
            return false;
        }
        return safeGraceHours(next) < safeGraceHours(previous);
    }

    private boolean capRemoved(RenewalPricingRule previous, RenewalPricingRule next) {
        return previous.renewalBillingMode() == RenewalBillingMode.DAILY_CAPPED
            && next.renewalBillingMode() == RenewalBillingMode.DAILY_CAPPED
            && Boolean.TRUE.equals(previous.renewalDailyCapEnabled())
            && !Boolean.TRUE.equals(next.renewalDailyCapEnabled());
    }

    private int periodDays(RenewalPricingRule rule) {
        if (rule.renewalValue() == null || rule.renewalValue() <= 0) {
            return Integer.MAX_VALUE;
        }
        return "MONTH".equals(rule.renewalUnit())
            ? Math.multiplyExact(rule.renewalValue(), 30)
            : rule.renewalValue();
    }

    private int safeGraceHours(RenewalPricingRule rule) {
        return rule.renewalGraceHours() == null ? 0 : rule.renewalGraceHours();
    }

    private boolean increased(BigDecimal previous, BigDecimal next) {
        if (next == null) return false;
        return previous == null || next.compareTo(previous) > 0;
    }

    private RenewalPricingRule fromOrder(RentalOrder order) {
        return new RenewalPricingRule(
            order.autoRenewEnabled(), order.renewalUnit(), order.renewalValue(), order.renewalAmount(),
            parseMode(order.renewalBillingMode()), order.renewalDailyAmount(), order.renewalDailyCapEnabled(),
            order.renewalGraceHours() == null ? 0 : order.renewalGraceHours(), order.overdueDailyAmount()
        );
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

    private RentalOrder ensureOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED || status == OrderStatus.EXCEPTION;
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private OrderPricingRevisionResponse toResponse(OrderPricingRevision revision) {
        return new OrderPricingRevisionResponse(
            revision.id(), revision.orderId(), revision.revisionStatus().name(), revision.requiresCustomerConfirmation(),
            revision.effectiveMode(), ruleResponse(revision.previousRule()), ruleResponse(revision.newRule()),
            revision.reason(), revision.operatorAccountId(), revision.customerConfirmedAt(), revision.appliedAt(), revision.createdAt()
        );
    }

    private RenewalPricingRuleResponse ruleResponse(RenewalPricingRule rule) {
        return new RenewalPricingRuleResponse(
            rule.autoRenewEnabled(), rule.renewalUnit(), rule.renewalValue(), rule.renewalAmount(),
            rule.renewalBillingMode().name(), rule.renewalDailyAmount(), rule.renewalDailyCapEnabled(),
            rule.renewalGraceHours(), rule.overdueDailyAmount()
        );
    }
}
