package com.xniu.rental.externalorder.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderManualRenewalRequest;
import com.xniu.rental.externalorder.dto.ExternalOrderRenewalResponse;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalSource;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.model.IncomeEntryStatus;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.ProfitSharingCalculator;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalOrderManualRenewalService {

    private static final DateTimeFormatter STATEMENT_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExternalRentalOrderRepository orderRepository;
    private final ExternalOrderRenewalRepository renewalRepository;
    private final ProductRepository productRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final SettlementIncomeRepository settlementIncomeRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementStatementService settlementStatementService;
    private final AuthorizationService authorizationService;
    private final ExternalOrderRenewalAllocationService renewalAllocationService;

    public ExternalOrderManualRenewalService(
        ExternalRentalOrderRepository orderRepository,
        ExternalOrderRenewalRepository renewalRepository,
        ProductRepository productRepository,
        SettlementRepository settlementRepository,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        SettlementIncomeRepository settlementIncomeRepository,
        SettlementStatementRepository settlementStatementRepository,
        SettlementStatementService settlementStatementService,
        AuthorizationService authorizationService,
        ExternalOrderRenewalAllocationService renewalAllocationService
    ) {
        this.orderRepository = orderRepository;
        this.renewalRepository = renewalRepository;
        this.productRepository = productRepository;
        this.settlementRepository = settlementRepository;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.settlementIncomeRepository = settlementIncomeRepository;
        this.settlementStatementRepository = settlementStatementRepository;
        this.settlementStatementService = settlementStatementService;
        this.authorizationService = authorizationService;
        this.renewalAllocationService = renewalAllocationService;
    }

    @Transactional
    public ExternalOrderRenewalResponse create(Long externalOrderId, ExternalOrderManualRenewalRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = orderRepository.findByIdForUpdate(externalOrderId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            throw BusinessException.badRequest("只有进行中的补录订单才能人工续租");
        }
        if (order.expectedReturnAt() == null) {
            throw BusinessException.badRequest("补录订单缺少当前预计归还时间");
        }
        if (request == null || request.periodEndAt() == null) {
            throw BusinessException.badRequest("请输入本次续租结束时间");
        }
        if (request.expectedPeriodStartAt() == null) {
            throw BusinessException.badRequest("续租起点已失效，请刷新订单后重试");
        }
        var requestedPeriodStartAt = request.expectedPeriodStartAt().withNano(0);
        var periodEndAt = request.periodEndAt().withNano(0);
        if (!periodEndAt.isAfter(requestedPeriodStartAt)) {
            throw BusinessException.badRequest("本次续租结束时间必须晚于续租起点");
        }
        var renewalAmount = money(request.verificationAmount());
        if (renewalAmount == null || renewalAmount.signum() <= 0) {
            throw BusinessException.badRequest("本次续租毛额必须大于 0");
        }
        var remark = normalizeRemark(request.remark());

        var replacingSystemEvent = !sameSecond(order.expectedReturnAt(), requestedPeriodStartAt);
        ExternalOrderRenewalEvent systemEvent = null;
        var affectedStatementMonths = new LinkedHashSet<String>();
        affectedStatementMonths.add(requestedPeriodStartAt.format(STATEMENT_MONTH_FORMAT));
        if (replacingSystemEvent) {
            var effectiveEvents = renewalRepository.listByExternalOrder(order.id()).stream()
                .filter(event -> "ACCRUED".equals(event.eventStatus()))
                .filter(event -> event.periodEndAt().isAfter(requestedPeriodStartAt))
                .toList();
            if (effectiveEvents.size() != 1) {
                throw staleStartConflict();
            }
            systemEvent = effectiveEvents.getFirst();
            if (!isReplaceableSystemTail(order.expectedReturnAt(), requestedPeriodStartAt, systemEvent)) {
                throw staleStartConflict();
            }
            affectedStatementMonths.addAll(settlementStatementRepository.listDraftStatementMonthsBySource(
                SnapshotSourceType.EXTERNAL_RENEWAL.name(),
                systemEvent.id()
            ));
        }

        affectedStatementMonths.stream().sorted()
            .forEach(settlementStatementRepository::lockStatementsByMonthForUpdate);
        var draftStatementMonths = new LinkedHashSet<String>();
        for (var statementMonth : affectedStatementMonths.stream().sorted().toList()) {
            if (settlementStatementRepository.hasLockedStatementsForUpdate(statementMonth)) {
                if (replacingSystemEvent) {
                    throw BusinessException.conflict("系统自动续租已进入锁定月结，不能改为人工续租；请通过结算调整单处理");
                }
                throw BusinessException.badRequest("本次续租起点所在月份已锁定，不能直接补记；请通过结算调整单处理");
            }
            if (settlementStatementRepository.hasDraftStatementsForUpdate(statementMonth)) {
                draftStatementMonths.add(statementMonth);
            }
        }

        if (replacingSystemEvent) {
            var lockedEvents = renewalRepository.listEffectiveAfterForUpdate(order.id(), requestedPeriodStartAt);
            if (lockedEvents.size() != 1
                || !lockedEvents.getFirst().id().equals(systemEvent.id())
                || !isReplaceableSystemTail(order.expectedReturnAt(), requestedPeriodStartAt, lockedEvents.getFirst())) {
                throw staleStartConflict();
            }
            systemEvent = lockedEvents.getFirst();
            if (renewalRepository.hasLockedStatementLinesByEventForUpdate(systemEvent.id())) {
                throw BusinessException.conflict("系统自动续租已进入锁定月结，不能改为人工续租");
            }
            var incomeEntries = settlementIncomeRepository.listBySourceForUpdate(
                IncomeSourceType.EXTERNAL_RENEWAL,
                systemEvent.id()
            );
            var systemSnapshotId = systemEvent.settlementSnapshotId();
            if (incomeEntries.isEmpty()
                || incomeEntries.stream().anyMatch(entry -> entry.entryStatus() != IncomeEntryStatus.PENDING)
                || incomeEntries.stream().anyMatch(entry -> !systemSnapshotId.equals(entry.snapshotId()))) {
                throw BusinessException.conflict("系统自动续租收益已锁定或不完整，不能改为人工续租");
            }
        } else if (!renewalRepository.listEffectiveAfterForUpdate(order.id(), requestedPeriodStartAt).isEmpty()) {
            // The order row prevents the scheduler from adding a new period
            // after this check. Any existing event at/following the current
            // boundary is therefore conflicting legacy data, not a slot for
            // a second manual fact.
            throw BusinessException.conflict("该续租起点已存在续租记录，请刷新后核对");
        }

        var periodStartAt = replacingSystemEvent ? systemEvent.periodStartAt() : order.expectedReturnAt();
        var sourceSnapshotId = replacingSystemEvent ? systemEvent.settlementSnapshotId() : order.settlementSnapshotId();
        var sourceSnapshot = sourceSnapshotId == null
            ? null
            : settlementRepository.findSnapshot(sourceSnapshotId).orElse(null);
        var expectedSnapshotType = replacingSystemEvent
            ? SnapshotSourceType.EXTERNAL_RENEWAL
            : SnapshotSourceType.EXTERNAL_ORDER;
        var expectedSnapshotSourceId = replacingSystemEvent ? systemEvent.id() : order.id();
        if (sourceSnapshot == null
            || sourceSnapshot.sourceType() != expectedSnapshotType
            || !expectedSnapshotSourceId.equals(sourceSnapshot.sourceId())) {
            throw BusinessException.badRequest(replacingSystemEvent
                ? "系统自动续租分润快照不完整，不能改为人工续租"
                : "补录订单原始分润快照不存在");
        }
        if (!sourceSnapshot.calculationVersion().usesProfitSharing()) {
            throw BusinessException.badRequest("补录订单分润快照不是当前分润口径，请先修复快照后再人工续租");
        }
        var sku = productRepository.findSku(order.skuId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单 SKU 不存在"));
        var batteryCost = BatteryCostCalculator.calculateExactPeriod(
            sku.batteryCostDailyAmount(),
            sku.batteryCostMonthlyAmount(),
            periodStartAt,
            periodEndAt
        );

        // Validate the economic floor before creating any event or advancing
        // the paid-through boundary. ProfitSharingCalculator deliberately
        // clamps a negative distributable amount to zero, which is correct for
        // reporting but must not turn an underfunded manual renewal into a
        // silently accepted transaction.
        var previewVersion = sourceSnapshot.calculationVersion().usesGrossChannelReferral()
            ? sourceSnapshot.calculationVersion()
            : batteryCost.signum() > 0
                ? SettlementCalculationVersion.PROFIT_V3
                : sourceSnapshot.calculationVersion();
        var preview = ProfitSharingCalculator.calculate(
            previewVersion,
            renewalAmount,
            sourceSnapshot.channelFeeRate(),
            sourceSnapshot.platformFeeRate(),
            batteryCost,
            sourceSnapshot.storeOperationRate(),
            sourceSnapshot.maintenanceFundRate(),
            sourceSnapshot.channelReferralRate(),
            sourceSnapshot.investorShareRate()
        );
        var amountAfterFixedDeductions = preview.settlementBaseAmount()
            .subtract(preview.channelFeeAmount())
            .subtract(preview.platformFeeAmount())
            .subtract(previewVersion.usesGrossChannelReferral()
                ? preview.channelReferralAmount()
                : BigDecimal.ZERO)
            .subtract(preview.batteryCostAmount())
            .setScale(2, RoundingMode.HALF_UP);
        if (amountAfterFixedDeductions.signum() < 0) {
            throw BusinessException.badRequest("本次续租毛额不足以覆盖渠道费、平台费、渠道引流分润和全租期电池成本");
        }
        if (preview.investorShareAmount().signum() < 0) {
            throw BusinessException.badRequest("本次续租毛额不足以覆盖毛额级渠道引流分润、电池成本和其他分润");
        }

        var operatorAccountId = currentAccountId();
        ExternalOrderRenewalEvent event;
        com.xniu.rental.settlement.dto.SettlementSnapshotResponse snapshot;
        if (replacingSystemEvent) {
            snapshot = settlementService.rebuildExternalRenewalSnapshot(
                systemEvent.id(),
                systemEvent.settlementSnapshotId(),
                renewalAmount,
                batteryCost
            );
            var replaced = renewalRepository.replaceAccruedSystemWithManual(
                systemEvent.id(),
                order.id(),
                systemEvent.settlementSnapshotId(),
                systemEvent.periodStartAt(),
                systemEvent.periodEndAt(),
                periodEndAt,
                renewalAmount,
                batteryCost,
                snapshot.id(),
                operatorAccountId,
                remark
            );
            if (replaced != 1) {
                throw BusinessException.conflict("系统自动续租已变化，请刷新后重新提交人工续租");
            }
            event = renewalRepository.findById(systemEvent.id()).orElseThrow();
        } else {
            event = renewalRepository.create(
                order.id(),
                nextEventNo(),
                renewalRepository.nextPeriodNo(order.id()),
                periodStartAt,
                periodEndAt,
                renewalAmount,
                order.renewalAmount() == null ? renewalAmount : money(order.renewalAmount()),
                batteryCost,
                ExternalOrderRenewalSource.MANUAL,
                operatorAccountId,
                remark
            );
            snapshot = settlementService.createExternalRenewalSnapshot(
                event.id(),
                order.settlementSnapshotId(),
                event.renewalAmount(),
                event.batteryCostAmount(),
                order.frameAssetId(),
                order.batteryAssetId()
            );
            event = renewalRepository.attachSnapshot(event.id(), snapshot.id());
        }
        var investorAllocations = renewalAllocationService.freezeCurrentAssets(event, snapshot.id());
        if (replacingSystemEvent) {
            settlementIncomeRepository.deleteBySource(IncomeSourceType.EXTERNAL_RENEWAL, event.id());
        }
        settlementIncomeService.createExternalRenewalEntries(
            event.id(),
            event.eventNo(),
            snapshot.id(),
            event.periodStartAt(),
            event.renewalAmount(),
            investorAllocations
        );
        orderRepository.advanceExpectedReturnAt(order.id(), periodEndAt);
        orderRepository.addLog(
            order.id(),
            order.orderStatus(),
            order.orderStatus(),
            ExternalOrderOperationType.MANUAL_RENEW,
            operatorAccountId,
            replacingSystemEvent
                ? "人工续租已替换同起点系统自动续租；原结束时间 " + systemEvent.periodEndAt()
                    + "；原系统毛额 " + systemEvent.renewalAmount()
                    + "；人工续租至 " + periodEndAt + "；本次核销毛额 " + renewalAmount
                : "人工续租至 " + periodEndAt + "；本次核销毛额 " + renewalAmount
        );
        for (var statementMonth : draftStatementMonths) {
            settlementStatementService.regenerateUnlockedMonthAlreadyLocked(statementMonth);
        }
        return toResponse(order, event);
    }

    private boolean isReplaceableSystemTail(
        LocalDateTime currentExpectedReturnAt,
        LocalDateTime requestedPeriodStartAt,
        ExternalOrderRenewalEvent event
    ) {
        return event != null
            && "ACCRUED".equals(event.eventStatus())
            && event.renewalSource() == ExternalOrderRenewalSource.SYSTEM
            && event.settlementSnapshotId() != null
            && sameSecond(event.periodStartAt(), requestedPeriodStartAt)
            && sameSecond(event.periodEndAt(), currentExpectedReturnAt);
    }

    private boolean sameSecond(LocalDateTime left, LocalDateTime right) {
        return left != null && right != null && left.withNano(0).equals(right.withNano(0));
    }

    private BusinessException staleStartConflict() {
        return BusinessException.conflict("订单续租起点已变化，且不存在可安全替换的尾部系统续租；请刷新后核对");
    }

    private ExternalOrderRenewalResponse toResponse(
        com.xniu.rental.externalorder.model.ExternalRentalOrder order,
        com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent event
    ) {
        return new ExternalOrderRenewalResponse(
            event.id(),
            event.externalOrderId(),
            event.eventNo(),
            order.recordNo(),
            order.merchantId(),
            order.storeId(),
            event.periodNo(),
            event.periodStartAt(),
            event.periodEndAt(),
            event.renewalAmount(),
            event.batteryCostAmount(),
            event.eventStatus(),
            event.renewalSource().name(),
            event.operatorAccountId(),
            event.remark(),
            false,
            event.periodStartAt()
        );
    }

    private BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeRemark(String value) {
        var remark = value == null ? null : value.trim();
        if (remark == null || remark.isEmpty()) {
            throw BusinessException.badRequest("请填写人工续租备注");
        }
        if (remark.length() > 255) {
            throw BusinessException.badRequest("人工续租备注不能超过 255 个字");
        }
        return remark;
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current.account().id();
    }

    private String nextEventNo() {
        return "ERN-" + UUID.randomUUID();
    }
}
