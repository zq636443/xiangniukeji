package com.xniu.rental.externalorder.service;

import com.xniu.rental.asset.dto.AssetReplaceRequest;
import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalOrderAssetChangeResponse;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderAssetChangeRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.settlement.service.SettlementStatementService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalOrderAssetReplacementService {

    private static final DateTimeFormatter STATEMENT_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExternalRentalOrderRepository orderRepository;
    private final ExternalOrderRenewalRepository renewalRepository;
    private final ExternalOrderAssetChangeRepository changeRepository;
    private final ExternalOrderAutoRenewalService autoRenewalService;
    private final ExternalOrderRenewalAllocationService allocationService;
    private final AssetRepository assetRepository;
    private final OrderRepository formalOrderRepository;
    private final ProductRepository productRepository;
    private final AuthorizationService authorizationService;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final SettlementIncomeRepository settlementIncomeRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementStatementRepository statementRepository;
    private final SettlementStatementService statementService;

    public ExternalOrderAssetReplacementService(
        ExternalRentalOrderRepository orderRepository,
        ExternalOrderRenewalRepository renewalRepository,
        ExternalOrderAssetChangeRepository changeRepository,
        ExternalOrderAutoRenewalService autoRenewalService,
        ExternalOrderRenewalAllocationService allocationService,
        AssetRepository assetRepository,
        OrderRepository formalOrderRepository,
        ProductRepository productRepository,
        AuthorizationService authorizationService,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        SettlementIncomeRepository settlementIncomeRepository,
        SettlementRepository settlementRepository,
        SettlementStatementRepository statementRepository,
        SettlementStatementService statementService
    ) {
        this.orderRepository = orderRepository;
        this.renewalRepository = renewalRepository;
        this.changeRepository = changeRepository;
        this.autoRenewalService = autoRenewalService;
        this.allocationService = allocationService;
        this.assetRepository = assetRepository;
        this.formalOrderRepository = formalOrderRepository;
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.settlementIncomeRepository = settlementIncomeRepository;
        this.settlementRepository = settlementRepository;
        this.statementRepository = statementRepository;
        this.statementService = statementService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExternalOrderAssetChangeResponse replace(Long externalOrderId, AssetReplaceRequest request) {
        authorizationService.requirePermission("order.operate");
        if (request == null) {
            throw BusinessException.badRequest("请填写更换资产信息");
        }
        var order = orderRepository.findByIdForUpdate(externalOrderId)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            throw BusinessException.badRequest("只有进行中的补录订单可以更换资产");
        }

        var assetSlot = parseAssetSlot(request.assetType());
        var oldAssetId = assetSlot == AssetType.VEHICLE_FRAME
            ? order.frameAssetId()
            : order.batteryAssetId();
        if (oldAssetId == null) {
            throw BusinessException.badRequest(assetSlot == AssetType.VEHICLE_FRAME
                ? "订单当前未绑定主资产"
                : "订单当前未绑定第二资产");
        }
        if (request.expectedOldAssetId() == null) {
            throw BusinessException.badRequest("请刷新订单后重新选择要更换的当前资产");
        }
        if (!oldAssetId.equals(request.expectedOldAssetId())) {
            throw BusinessException.conflict("订单当前绑定资产已变更，请刷新后重试");
        }
        if (oldAssetId.equals(request.newAssetId())) {
            throw BusinessException.badRequest("新资产与当前资产相同，无需更换");
        }
        var otherAssetId = assetSlot == AssetType.VEHICLE_FRAME
            ? order.batteryAssetId()
            : order.frameAssetId();
        if (request.newAssetId().equals(otherAssetId)) {
            throw BusinessException.badRequest("主资产和第二资产不能绑定同一条资产");
        }

        /* A formal PENDING_PICKUP order already references an IDLE asset.
         * Pickup locks formal order -> asset, so replacement must pre-lock all
         * currently visible formal occupancies in the same order before it
         * touches asset_item. */
        rejectPrelockedFormalOccupancy(oldAssetId, request.newAssetId());
        var lockedAssets = lockAssets(oldAssetId, request.newAssetId());
        var oldAsset = findLockedAsset(lockedAssets, oldAssetId);
        var newAsset = findLockedAsset(lockedAssets, request.newAssetId());
        validateOldAsset(order, oldAsset);
        validateNewAsset(order, newAsset);
        validateResultingAssetCombination(order, assetSlot, newAsset);
        if (order.settlementSnapshotId() == null) {
            throw BusinessException.badRequest("补录订单缺少初始分润快照，不能更换资产");
        }
        var initialSnapshot = settlementRepository.findSnapshot(order.settlementSnapshotId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单初始分润快照不存在"));
        settlementIncomeService.ensureExternalOrderInitialInvestorAllocation(order, initialSnapshot);

        /* Catch up a due renewal with the old binding before choosing the
         * replacement boundary.  Both operations share this transaction, so
         * a later validation failure rolls the catch-up event back as well. */
        autoRenewalService.accrueDueOrder(order.id(), changeRepository.currentDatabaseTime());
        order = orderRepository.findByIdForUpdate(order.id()).orElseThrow();
        /* The first timestamp only bounds which events must be locked.  The
         * effective timestamp is deliberately read after every potentially
         * blocking renewal/statement/income lock: time spent waiting on a
         * concurrent month-end operation must still belong to the old asset. */
        var lockScanAt = changeRepository.currentDatabaseTime();
        var lockedEventStates = lockEventFinancialState(
            renewalRepository.listEffectiveAfterForUpdate(order.id(), lockScanAt)
        );
        var effectiveAt = changeRepository.currentDatabaseTime();
        var eventPlan = planMutableEvents(lockedEventStates, effectiveAt);

        var nextFrameAssetId = assetSlot == AssetType.VEHICLE_FRAME ? newAsset.id() : order.frameAssetId();
        var nextBatteryAssetId = assetSlot == AssetType.BATTERY ? newAsset.id() : order.batteryAssetId();
        rebuildFutureInvestorAttribution(
            eventPlan.mutableEvents(),
            assetSlot,
            oldAsset,
            newAsset,
            effectiveAt,
            nextFrameAssetId,
            nextBatteryAssetId
        );

        var oldStatus = parseOldAssetStatus(request.oldAssetResultStatus());
        var operatorAccountId = currentAccountId();
        var remark = normalizeRemark(request.remark());
        var auditRemark = replacementAuditRemark(remark, eventPlan.retainedLockedCount());
        updateAssetStatus(oldAsset, oldStatus, effectiveAt, operatorAccountId,
            auditRemark + "；补录订单更换后原资产回收");
        updateAssetStatus(newAsset, AssetStatus.RENTING, effectiveAt, operatorAccountId,
            auditRemark + "；补录订单更换后新资产租赁中");
        orderRepository.updateAssets(
            order.id(), nextFrameAssetId, nextBatteryAssetId, operatorAccountId
        );

        var change = changeRepository.create(new ExternalOrderAssetChangeRepository.CreateRow(
            "EAC-" + UUID.randomUUID(),
            order.id(),
            order.merchantId(),
            order.storeId(),
            assetSlot,
            oldAsset.id(),
            oldAsset.serialNo(),
            requireInvestor(oldAsset),
            newAsset.id(),
            newAsset.serialNo(),
            requireInvestor(newAsset),
            oldStatus,
            effectiveAt,
            operatorAccountId,
            auditRemark
        ));
        orderRepository.addLog(
            order.id(),
            order.orderStatus(),
            order.orderStatus(),
            ExternalOrderOperationType.REPLACE_ASSET,
            operatorAccountId,
            truncateLog("更换" + slotName(assetSlot) + "：" + oldAsset.serialNo() + " -> " + newAsset.serialNo()
                + "；生效时间 " + effectiveAt + "；" + auditRemark)
        );
        for (var month : eventPlan.draftMonths()) {
            statementService.regenerateUnlockedMonthAlreadyLocked(month);
        }
        return toResponse(change);
    }

    private void rebuildFutureInvestorAttribution(
        List<com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent> events,
        AssetType assetSlot,
        AssetItem oldAsset,
        AssetItem newAsset,
        LocalDateTime effectiveAt,
        Long frameAssetId,
        Long batteryAssetId
    ) {
        for (var event : events) {
            if (event.settlementSnapshotId() == null) {
                throw BusinessException.badRequest("补录续租事件缺少分润快照，不能安全更换资产");
            }
            var previousSnapshotId = event.settlementSnapshotId();
            var replacement = settlementService.cloneExternalRenewalAssetAttribution(
                event.id(), previousSnapshotId, frameAssetId, batteryAssetId
            );
            var allocations = allocationService.replaceAssetFrom(
                event,
                previousSnapshotId,
                replacement.id(),
                assetSlot,
                oldAsset,
                newAsset,
                effectiveAt
            );
            settlementIncomeRepository.deletePendingInvestorBySource(
                IncomeSourceType.EXTERNAL_RENEWAL, event.id()
            );
            settlementIncomeRepository.relinkPendingNonInvestorSnapshot(
                IncomeSourceType.EXTERNAL_RENEWAL, event.id(), replacement.id()
            );
            settlementIncomeService.createExternalRenewalInvestorEntries(
                event.id(), event.eventNo(), replacement.id(), event.periodStartAt(), allocations
            );
            renewalRepository.attachSnapshot(event.id(), replacement.id());
        }
    }

    /**
     * Financially locked renewal facts are historical and must remain exactly
     * as settled.  They cannot prevent the physical replacement itself: keep
     * their old snapshot/income attribution, while mutable periods are rebuilt
     * and all later renewals use the order's new current asset.
     */
    private List<EventLockState> lockEventFinancialState(
        List<com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent> events
    ) {
        var monthsToLock = new LinkedHashSet<String>();
        var draftMonthsByEvent = new java.util.LinkedHashMap<Long, List<String>>();
        for (var event : events) {
            monthsToLock.add(event.periodStartAt().format(STATEMENT_MONTH_FORMAT));
            var draftMonths = statementRepository.listDraftStatementMonthsBySource(
                SnapshotSourceType.EXTERNAL_RENEWAL.name(), event.id()
            );
            draftMonthsByEvent.put(event.id(), draftMonths);
            monthsToLock.addAll(draftMonths);
        }
        monthsToLock.stream().sorted().forEach(statementRepository::lockStatementsByMonthForUpdate);

        var result = new ArrayList<EventLockState>();
        for (var event : events) {
            var occurrenceMonth = event.periodStartAt().format(STATEMENT_MONTH_FORMAT);
            var draftMonths = new LinkedHashSet<>(draftMonthsByEvent.getOrDefault(event.id(), List.of()));
            /* A due event may have been created by accrueDueOrder immediately
             * before this scan, after an unrelated source already generated
             * the month's DRAFT statements.  It has no statement line yet, so
             * source-based discovery alone cannot find the month.  Re-check
             * the occurrence month only after its statement rows are locked. */
            if (statementRepository.hasDraftStatementsForUpdate(occurrenceMonth)) {
                draftMonths.add(occurrenceMonth);
            }
            var financiallyLocked = statementRepository.hasLockedStatementsForUpdate(occurrenceMonth)
                || renewalRepository.hasLockedStatementLinesByEventForUpdate(event.id())
                || renewalRepository.hasNonPendingIncomeByEventForUpdate(event.id());
            result.add(new EventLockState(
                event,
                List.copyOf(draftMonths),
                financiallyLocked
            ));
        }
        return List.copyOf(result);
    }

    private EventMutationPlan planMutableEvents(
        List<EventLockState> lockedStates,
        LocalDateTime effectiveAt
    ) {
        var mutable = new ArrayList<com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent>();
        var mutableDraftMonths = new LinkedHashSet<String>();
        var retainedLockedCount = 0;
        for (var state : lockedStates) {
            if (!state.event().periodEndAt().isAfter(effectiveAt)) {
                continue;
            }
            if (state.financiallyLocked()) {
                retainedLockedCount += 1;
                continue;
            }
            mutable.add(state.event());
            mutableDraftMonths.addAll(state.draftMonths());
        }
        return new EventMutationPlan(
            List.copyOf(mutable),
            mutableDraftMonths.stream().sorted().collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new)
            ),
            retainedLockedCount
        );
    }

    private List<AssetItem> lockAssets(Long firstId, Long secondId) {
        return java.util.stream.Stream.of(firstId, secondId)
            .distinct()
            .sorted()
            .map(id -> assetRepository.findByIdForUpdate(id)
                .orElseThrow(() -> BusinessException.badRequest("资产不存在")))
            .toList();
    }

    private void rejectPrelockedFormalOccupancy(Long oldAssetId, Long newAssetId) {
        var occupancies = formalOrderRepository.lockActiveByAssetIdsForUpdate(
            List.of(oldAssetId, newAssetId)
        );
        for (var occupancy : occupancies) {
            if (oldAssetId.equals(occupancy.frameAssetId())
                || oldAssetId.equals(occupancy.batteryAssetId())) {
                throw BusinessException.badRequest("订单原资产同时被正式订单占用，不能直接释放");
            }
            throw BusinessException.badRequest("所选新资产已被正式订单占用");
        }
    }

    private AssetItem findLockedAsset(List<AssetItem> assets, Long id) {
        return assets.stream().filter(asset -> asset.id().equals(id)).findFirst()
            .orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private void validateOldAsset(ExternalRentalOrder order, AssetItem asset) {
        if (!order.merchantId().equals(asset.currentMerchantId())
            || !order.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("订单原资产不在当前下单门店，请先核对资产位置");
        }
        if (orderRepository.existsOtherActiveByAssetForUpdate(asset.id(), order.id())) {
            throw BusinessException.badRequest("订单原资产同时被其他补录订单占用，不能直接释放");
        }
        if (formalOrderRepository.hasActiveByAsset(asset.id())) {
            throw BusinessException.badRequest("订单原资产同时被正式订单占用，不能直接释放");
        }
        requireInvestor(asset);
    }

    private void validateNewAsset(ExternalRentalOrder order, AssetItem asset) {
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("所选新资产不是空闲状态");
        }
        if (!order.merchantId().equals(asset.currentMerchantId())
            || !order.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选新资产不属于当前下单门店");
        }
        if (orderRepository.existsOtherActiveByAssetForUpdate(asset.id(), order.id())) {
            throw BusinessException.badRequest("所选新资产已被其他补录订单占用");
        }
        if (formalOrderRepository.hasActiveByAsset(asset.id())) {
            throw BusinessException.badRequest("所选新资产已被正式订单占用");
        }
        requireInvestor(asset);
    }

    /** Supplemental slots deliberately accept any physical asset type, but
     * the resulting pair must still satisfy the SKU's required slots. */
    private void validateResultingAssetCombination(
        ExternalRentalOrder order,
        AssetType assetSlot,
        AssetItem newAsset
    ) {
        var sku = productRepository.findSku(order.skuId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单 SKU 不存在"));
        var frameAssetId = assetSlot == AssetType.VEHICLE_FRAME ? newAsset.id() : order.frameAssetId();
        var batteryAssetId = assetSlot == AssetType.BATTERY ? newAsset.id() : order.batteryAssetId();
        var frameAsset = frameAssetId == null
            ? null
            : frameAssetId.equals(newAsset.id())
                ? newAsset
                : assetRepository.findById(frameAssetId)
                    .orElseThrow(() -> BusinessException.badRequest("订单当前主资产不存在"));
        var integratedFrame = frameAsset != null && frameAsset.assetType().isIntegratedVehicle();
        if (Boolean.TRUE.equals(sku.needFrameAsset()) && frameAssetId == null) {
            throw BusinessException.badRequest("更换后的资产组合缺少主资产");
        }
        if (Boolean.TRUE.equals(sku.needBatteryAsset()) && batteryAssetId == null && !integratedFrame) {
            throw BusinessException.badRequest("新主资产不是车电一体资产，更换后还需要绑定第二资产");
        }
        if (!Boolean.TRUE.equals(sku.needFrameAsset()) && frameAssetId != null) {
            throw BusinessException.badRequest("当前 SKU 不允许绑定主资产");
        }
        if (!Boolean.TRUE.equals(sku.needBatteryAsset()) && batteryAssetId != null) {
            throw BusinessException.badRequest("当前 SKU 不允许绑定第二资产");
        }
    }

    private void updateAssetStatus(
        AssetItem asset,
        AssetStatus target,
        LocalDateTime effectiveAt,
        Long operatorAccountId,
        String remark
    ) {
        if (asset.status() == target) {
            return;
        }
        assetRepository.updateStatus(asset.id(), target, effectiveAt);
        assetRepository.insertStatusLog(asset.id(), asset.status(), target, operatorAccountId, truncateLog(remark));
    }

    private AssetType parseAssetSlot(String value) {
        try {
            var type = AssetType.valueOf(value);
            if (type != AssetType.VEHICLE_FRAME && type != AssetType.BATTERY) {
                throw new IllegalArgumentException();
            }
            return type;
        } catch (Exception exception) {
            throw BusinessException.badRequest("资产类型只能是主资产或第二资产");
        }
    }

    private AssetStatus parseOldAssetStatus(String value) {
        if (value == null || value.isBlank()) {
            return AssetStatus.IDLE;
        }
        try {
            var status = AssetStatus.valueOf(value);
            if (status != AssetStatus.IDLE
                && status != AssetStatus.PENDING_REPAIR
                && status != AssetStatus.EXCEPTION) {
                throw new IllegalArgumentException();
            }
            return status;
        } catch (Exception exception) {
            throw BusinessException.badRequest("原资产状态只能为空闲、待检修或异常");
        }
    }

    private Long requireInvestor(AssetItem asset) {
        if (asset.investorId() == null || asset.investorId() <= 0) {
            throw BusinessException.badRequest("资产未绑定出资方，不能更换");
        }
        return asset.investorId();
    }

    private String normalizeRemark(String value) {
        var result = value == null ? null : value.trim();
        if (result == null || result.isEmpty()) {
            return "补录订单履约中更换资产";
        }
        if (result.length() > 500) {
            throw BusinessException.badRequest("更换备注不能超过 500 个字");
        }
        return result;
    }

    private String replacementAuditRemark(String remark, int retainedLockedCount) {
        if (retainedLockedCount <= 0) {
            return remark;
        }
        var suffix = "；已保留 " + retainedLockedCount + " 条锁定续租周期的原分润归属";
        var maxRemarkLength = Math.max(0, 500 - suffix.length());
        return (remark.length() <= maxRemarkLength ? remark : remark.substring(0, maxRemarkLength)) + suffix;
    }

    private String slotName(AssetType assetType) {
        return assetType == AssetType.VEHICLE_FRAME ? "主资产" : "第二资产";
    }

    private String truncateLog(String value) {
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current.account().id();
    }

    private ExternalOrderAssetChangeResponse toResponse(
        com.xniu.rental.externalorder.model.ExternalOrderAssetChange change
    ) {
        return new ExternalOrderAssetChangeResponse(
            change.id(),
            change.changeNo(),
            change.externalOrderId(),
            change.merchantId(),
            change.storeId(),
            change.assetType().name(),
            change.oldAssetId(),
            change.oldAssetSerialNo(),
            change.newAssetId(),
            change.newAssetSerialNo(),
            change.oldAssetResultStatus().name(),
            change.effectiveAt(),
            change.operatorAccountId(),
            change.remark(),
            change.createdAt()
        );
    }

    private record EventMutationPlan(
        List<com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent> mutableEvents,
        LinkedHashSet<String> draftMonths,
        int retainedLockedCount
    ) {
    }

    private record EventLockState(
        com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent event,
        List<String> draftMonths,
        boolean financiallyLocked
    ) {
    }
}
