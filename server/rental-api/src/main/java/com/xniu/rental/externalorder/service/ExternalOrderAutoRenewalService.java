package com.xniu.rental.externalorder.service;

import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalSource;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderVerificationRevisionRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.ProfitSharingCalculator;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalOrderAutoRenewalService {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderAutoRenewalService.class);
    private static final int SCAN_LIMIT = 500;
    private static final int CATCH_UP_LIMIT = 120;
    private static final DateTimeFormatter STATEMENT_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExternalRentalOrderRepository orderRepository;
    private final ExternalOrderRenewalRepository renewalRepository;
    private final ExternalOrderVerificationRevisionRepository verificationRevisionRepository;
    private final ProductRepository productRepository;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final SettlementStatementService settlementStatementService;
    private final SettlementIncomeRepository settlementIncomeRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final TransactionTemplate transactionTemplate;

    public ExternalOrderAutoRenewalService(
        ExternalRentalOrderRepository orderRepository,
        ExternalOrderRenewalRepository renewalRepository,
        ExternalOrderVerificationRevisionRepository verificationRevisionRepository,
        ProductRepository productRepository,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        SettlementStatementService settlementStatementService,
        SettlementIncomeRepository settlementIncomeRepository,
        SettlementRepository settlementRepository,
        SettlementStatementRepository settlementStatementRepository,
        TransactionTemplate transactionTemplate
    ) {
        this.orderRepository = orderRepository;
        this.renewalRepository = renewalRepository;
        this.verificationRevisionRepository = verificationRevisionRepository;
        this.productRepository = productRepository;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.settlementStatementService = settlementStatementService;
        this.settlementIncomeRepository = settlementIncomeRepository;
        this.settlementRepository = settlementRepository;
        this.settlementStatementRepository = settlementStatementRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public int accrueDueOrders(LocalDateTime dueAt) {
        var accrued = 0;
        for (var orderId : renewalRepository.listDueOrderIds(dueAt, SCAN_LIMIT)) {
            try {
                accrued += transactionTemplate.execute(status -> accrueOrder(orderId, dueAt));
            } catch (RuntimeException exception) {
                log.error("补录订单 {} 自动续租记账失败，已跳过并保留原数据", orderId, exception);
            }
        }
        return accrued;
    }

    /**
     * Catch up one order synchronously before an operator completes it.  The
     * hourly scheduler is intentionally not the only accrual boundary: if an
     * order reaches its renewal time between scheduler runs, completing it
     * must not turn the elapsed period into an unrecorded free period.
     */
    @org.springframework.transaction.annotation.Transactional
    public int accrueDueOrder(Long externalOrderId, LocalDateTime dueAt) {
        if (externalOrderId == null) {
            return 0;
        }
        return accrueOrder(externalOrderId, dueAt == null ? LocalDateTime.now() : dueAt);
    }

    private int accrueOrder(Long orderId, LocalDateTime dueAt) {
        var accrued = 0;
        for (var iteration = 0; iteration < CATCH_UP_LIMIT; iteration += 1) {
            var order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (!isDue(order, dueAt)) {
                break;
            }
            var periodStartAt = order.expectedReturnAt();
            var periodEndAt = advance(periodStartAt, order.renewalUnit(), order.renewalValue());
            var revisions = verificationRevisionRepository.listByOrder(order.id());
            var renewalAmount = ExternalOrderRenewalAmountCalculator.calculate(
                order.renewalAmount(),
                periodStartAt,
                periodEndAt,
                revisions
            );
            var sku = productRepository.findSku(order.skuId()).orElseThrow();
            var batteryCost = BatteryCostCalculator.calculateExactPeriod(
                sku.batteryCostDailyAmount(),
                sku.batteryCostMonthlyAmount(),
                periodStartAt,
                periodEndAt
            );
            ensureFixedDeductionsCovered(order.settlementSnapshotId(), renewalAmount, batteryCost);
            var event = renewalRepository.create(
                order.id(),
                nextEventNo(),
                renewalRepository.nextPeriodNo(order.id()),
                periodStartAt,
                periodEndAt,
                money(renewalAmount),
                money(order.renewalAmount()),
                batteryCost
            );
            var snapshot = settlementService.createExternalRenewalSnapshot(
                event.id(),
                order.settlementSnapshotId(),
                event.renewalAmount(),
                event.batteryCostAmount()
            );
            renewalRepository.attachSnapshot(event.id(), snapshot.id());
            settlementIncomeService.createExternalRenewalEntries(
                event.id(),
                event.eventNo(),
                snapshot.id(),
                event.periodStartAt(),
                event.renewalAmount()
            );
            orderRepository.advanceExpectedReturnAt(order.id(), periodEndAt);
            orderRepository.addLog(
                order.id(),
                order.orderStatus(),
                order.orderStatus(),
                ExternalOrderOperationType.AUTO_RENEW,
                null,
                "系统自动续租至 " + periodEndAt
            );
            accrued += 1;
        }
        return accrued;
    }

    /**
     * Recalculate already-accrued renewal events after a verification edit,
     * but only while all related income/statement rows are still mutable.
     * Confirmed/paid/closed statements are intentionally left untouched.
     */
    @org.springframework.transaction.annotation.Transactional
    public int reconcilePendingEvents(Long externalOrderId) {
        var order = orderRepository.findByIdForUpdate(externalOrderId).orElse(null);
        if (order == null) {
            return 0;
        }
        /* A terminated order must never regain future income, even if a
         * legacy/partially-failed cleanup left an ACCRUED event behind.  The
         * normal terminate path reverses those events; this guard is a final
         * fail-closed boundary for historical dirty data and terminal edits. */
        if (order.orderStatus() == ExternalRentalOrderStatus.TERMINATED) {
            log.warn("已终止补录订单 {} 存在续租事件，跳过收益重算以避免复活终态收益", externalOrderId);
            return 0;
        }
        var revisions = verificationRevisionRepository.listByOrder(externalOrderId);
        var events = renewalRepository.listByExternalOrder(externalOrderId);
        /* Lock affected months before locking an individual event's rows.
         * Regeneration later takes the same month-wide lock; acquiring it
         * first avoids a cross-order cycle when two events share a month. */
        var affectedMonths = new LinkedHashSet<String>();
        for (var event : events) {
            if (!"ACCRUED".equals(event.eventStatus())) {
                continue;
            }
            affectedMonths.addAll(settlementStatementRepository.listDraftStatementMonthsBySource(
                SnapshotSourceType.EXTERNAL_RENEWAL.name(), event.id()));
            var eventMonth = event.periodStartAt().format(STATEMENT_MONTH_FORMAT);
            if (settlementStatementRepository.hasDraftStatements(eventMonth)) {
                affectedMonths.add(eventMonth);
            }
        }
        affectedMonths.stream()
            .sorted()
            .forEach(settlementStatementRepository::lockStatementsByMonthForUpdate);
        var changed = 0;
        var draftMonthsToRegenerate = new LinkedHashSet<String>();
        for (var event : events) {
            if (!"ACCRUED".equals(event.eventStatus())
                || event.renewalSource() != ExternalOrderRenewalSource.SYSTEM) {
                continue;
            }
            var systemAmount = event.systemRenewalAmount() == null
                || event.systemRenewalAmount().signum() <= 0
                ? order.renewalAmount()
                : event.systemRenewalAmount();
            var expectedAmount = ExternalOrderRenewalAmountCalculator.calculate(
                systemAmount,
                event.periodStartAt(),
                event.periodEndAt(),
                revisions
            );
            if (money(expectedAmount).compareTo(money(event.renewalAmount())) == 0) {
                continue;
            }
            /* Lock statement rows before income rows, matching the month-end
             * status transition order (statement -> income). This prevents a
             * concurrent PAID transition from racing the snapshot rebuild. */
            if (renewalRepository.hasLockedStatementLinesByEventForUpdate(event.id())
                || renewalRepository.hasNonPendingIncomeByEventForUpdate(event.id())) {
                log.warn(
                    "补录订单 {} 的续租事件 {} 已锁定，保留原金额 {}，人工修改 {} 仅用于后续未锁定期间",
                    externalOrderId,
                    event.eventNo(),
                    event.renewalAmount(),
                    expectedAmount
                );
                continue;
            }
            var previousSnapshotId = event.settlementSnapshotId();
            if (previousSnapshotId == null) {
                log.warn("补录订单 {} 的续租事件 {} 缺少分润快照，无法重算", externalOrderId, event.eventNo());
                continue;
            }
            var draftMonths = settlementStatementRepository.listDraftStatementMonthsBySource(
                SnapshotSourceType.EXTERNAL_RENEWAL.name(), event.id());
            var eventMonth = event.periodStartAt().format(STATEMENT_MONTH_FORMAT);
            if (settlementStatementRepository.hasDraftStatements(eventMonth)) {
                draftMonths = new java.util.ArrayList<>(draftMonths);
                if (!draftMonths.contains(eventMonth)) {
                    draftMonths.add(eventMonth);
                }
            }
            if (draftMonths.stream().anyMatch(settlementStatementRepository::hasLockedStatements)) {
                log.warn(
                    "补录订单 {} 的续租事件 {} 所在月份已有锁定月结，保留原金额 {}",
                    externalOrderId,
                    event.eventNo(),
                    event.renewalAmount()
                );
                continue;
            }
            // Build the replacement from the old event snapshot so rates and
            // asset attribution remain frozen at event creation time.
            var replacement = settlementService.rebuildExternalRenewalSnapshot(
                event.id(),
                previousSnapshotId,
                expectedAmount,
                event.batteryCostAmount()
            );
            settlementIncomeRepository.deleteBySource(IncomeSourceType.EXTERNAL_RENEWAL, event.id());
            // Keep the previous snapshot as an immutable audit record.  The
            // event now points to the replacement; no historical snapshot is
            // deleted during a repricing operation.
            renewalRepository.updateAmount(event.id(), expectedAmount);
            renewalRepository.attachSnapshot(event.id(), replacement.id());
            settlementIncomeService.createExternalRenewalEntries(
                event.id(),
                event.eventNo(),
                replacement.id(),
                event.periodStartAt(),
                expectedAmount
            );
            draftMonthsToRegenerate.addAll(draftMonths);
            changed++;
        }
        // Draft statements are derived data. Rebuild each affected month in
        // the same transaction so unrelated stores/adjustments are never left
        // deleted or stale after an event amount changes.
        for (var month : draftMonthsToRegenerate) {
            settlementStatementService.regenerateUnlockedMonthAlreadyLocked(month);
        }
        return changed;
    }

    /**
     * Repairs mutable historical renewal events after a pricing/verification
     * migration. Each order is isolated in its own transaction so one bad
     * legacy row cannot roll back repairs for every other store.
     */
    public int reconcileAllPendingEvents() {
        var changed = 0;
        for (var externalOrderId : renewalRepository.listExternalOrderIdsWithRenewals()) {
            try {
                var repaired = transactionTemplate.execute(status -> reconcilePendingEvents(externalOrderId));
                changed += repaired == null ? 0 : repaired;
            } catch (RuntimeException exception) {
                log.warn("补录订单 {} 历史续租金额重算失败，已保留原流水", externalOrderId, exception);
            }
        }
        return changed;
    }

    private boolean isDue(com.xniu.rental.externalorder.model.ExternalRentalOrder order, LocalDateTime dueAt) {
        return order != null
            && order.orderStatus() == ExternalRentalOrderStatus.ACTIVE
            && Boolean.TRUE.equals(order.autoRenewEnabled())
            && order.expectedReturnAt() != null
            && !order.expectedReturnAt().isAfter(dueAt)
            && order.renewalAmount() != null
            && order.renewalAmount().signum() > 0
            && order.renewalUnit() != null
            && order.renewalValue() != null
            && order.renewalValue() > 0;
    }

    private LocalDateTime advance(LocalDateTime base, String unit, int value) {
        return "MONTH".equals(unit) ? base.plusDays(30L * value) : base.plusDays(value);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureFixedDeductionsCovered(
        Long originalSnapshotId,
        BigDecimal renewalAmount,
        BigDecimal batteryCost
    ) {
        var original = settlementRepository.findSnapshot(originalSnapshotId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单原始分润快照不存在"));
        if (original.calculationVersion() != SettlementCalculationVersion.PROFIT_V2) {
            return;
        }
        var allocation = ProfitSharingCalculator.calculate(
            renewalAmount,
            original.channelFeeRate(),
            original.platformFeeRate(),
            batteryCost,
            original.storeOperationRate(),
            original.maintenanceFundRate(),
            original.channelReferralRate(),
            original.investorShareRate()
        );
        var remaining = allocation.settlementBaseAmount()
            .subtract(allocation.channelFeeAmount())
            .subtract(allocation.platformFeeAmount())
            .subtract(allocation.batteryCostAmount())
            .setScale(2, RoundingMode.HALF_UP);
        if (remaining.signum() < 0) {
            throw BusinessException.badRequest("系统续租金额不足以覆盖渠道费、平台费和全租期电池成本");
        }
    }

    private String nextEventNo() {
        return "ERN-" + UUID.randomUUID();
    }
}
