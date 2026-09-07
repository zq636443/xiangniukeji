package com.xniu.rental.externalorder.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalEvent;
import com.xniu.rental.externalorder.model.ExternalOrderRenewalInvestorAllocation;
import com.xniu.rental.externalorder.repository.ExternalOrderAssetChangeRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalAllocationRepository;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.IncomeBeneficiaryType;
import com.xniu.rental.settlement.model.IncomeLineType;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Freezes the time-weighted investor attribution of a supplemental renewal.
 * The renewal event remains one billing period; only its investor pool is
 * divided by asset slot and exact usage time.
 */
@Service
public class ExternalOrderRenewalAllocationService {

    private static final int WEIGHT_SCALE = 12;
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);

    private final ExternalOrderRenewalAllocationRepository allocationRepository;
    private final ExternalOrderAssetChangeRepository changeRepository;
    private final SettlementRepository settlementRepository;
    private final AssetRepository assetRepository;
    private final SettlementIncomeRepository incomeRepository;

    public ExternalOrderRenewalAllocationService(
        ExternalOrderRenewalAllocationRepository allocationRepository,
        ExternalOrderAssetChangeRepository changeRepository,
        SettlementRepository settlementRepository,
        AssetRepository assetRepository,
        SettlementIncomeRepository incomeRepository
    ) {
        this.allocationRepository = allocationRepository;
        this.changeRepository = changeRepository;
        this.settlementRepository = settlementRepository;
        this.assetRepository = assetRepository;
        this.incomeRepository = incomeRepository;
    }

    public List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> freezeCurrentAssets(
        ExternalOrderRenewalEvent event,
        Long snapshotId
    ) {
        var snapshot = ensureRenewalSnapshot(event, snapshotId);
        var timeline = timelineFor(event, snapshot);
        persist(event, snapshot, timeline);
        return allocationRepository.listTotalsBySnapshot(snapshot.id());
    }

    /** Preserve the exact asset-time weights while a mutable event amount is repriced. */
    public List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> cloneForRepricedSnapshot(
        ExternalOrderRenewalEvent event,
        Long previousSnapshotId,
        Long replacementSnapshotId
    ) {
        var previous = ensureRenewalSnapshot(event, previousSnapshotId);
        var replacement = ensureRenewalSnapshot(event, replacementSnapshotId);
        var timeline = timelineFor(event, previous);
        persist(event, replacement, timeline);
        return allocationRepository.listTotalsBySnapshot(replacement.id());
    }

    /**
     * Apply an asset replacement to every still-future part of the event.  A
     * row crossing effectiveAt is split in the allocation ledger, not in the
     * renewal event itself.
     */
    public List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> replaceAssetFrom(
        ExternalOrderRenewalEvent event,
        Long previousSnapshotId,
        Long replacementSnapshotId,
        AssetType assetSlot,
        AssetItem oldAsset,
        AssetItem newAsset,
        LocalDateTime effectiveAt
    ) {
        var previous = ensureRenewalSnapshot(event, previousSnapshotId);
        var replacement = ensureRenewalSnapshot(event, replacementSnapshotId);
        var source = timelineFor(event, previous);
        var updated = new ArrayList<TimelineSegment>();
        var replaced = false;
        for (var segment : source) {
            if (segment.assetType() != assetSlot
                || !segment.assetId().equals(oldAsset.id())
                || !segment.endAt().isAfter(effectiveAt)) {
                updated.add(segment);
                continue;
            }
            replaced = true;
            if (segment.startAt().isBefore(effectiveAt)) {
                updated.add(new TimelineSegment(
                    segment.assetType(), segment.assetId(), segment.investorId(),
                    segment.startAt(), effectiveAt
                ));
            }
            var newStart = segment.startAt().isAfter(effectiveAt) ? segment.startAt() : effectiveAt;
            updated.add(new TimelineSegment(
                segment.assetType(), newAsset.id(), requireInvestor(newAsset),
                newStart, segment.endAt()
            ));
        }
        if (!replaced) {
            throw BusinessException.badRequest("续租周期中的原资产归属与订单不一致，请先人工核对后再更换");
        }
        persist(event, replacement, updated);
        return allocationRepository.listTotalsBySnapshot(replacement.id());
    }

    private List<TimelineSegment> timelineFor(
        ExternalOrderRenewalEvent event,
        SettlementRuleSnapshot snapshot
    ) {
        var frozen = allocationRepository.listBySnapshot(snapshot.id());
        if (frozen.isEmpty()) {
            return incomeBackedInitialTimeline(event, snapshot);
        }
        return frozen.stream().map(this::toSegment).toList();
    }

    /**
     * V67 intentionally did not rewrite historical renewal events.  When an
     * older event has no allocation rows, its existing income ledger is the
     * ownership anchor.  Reuse it when the slot mapping is unambiguous; if
     * the ledger and current asset metadata disagree in a multi-investor
     * order, fail closed instead of silently moving historical income.
     */
    private List<TimelineSegment> incomeBackedInitialTimeline(
        ExternalOrderRenewalEvent event,
        SettlementRuleSnapshot snapshot
    ) {
        var changes = java.util.stream.Stream.of(AssetType.VEHICLE_FRAME, AssetType.BATTERY)
            .flatMap(type -> changeRepository.listByOrderAndAssetType(event.externalOrderId(), type).stream())
            .toList();
        if (!changes.isEmpty()) {
            return initialTimeline(event, snapshot);
        }
        var expectedLine = snapshot.calculationVersion().usesProfitSharing()
            ? IncomeLineType.INVESTOR_SHARE
            : IncomeLineType.INVESTOR_NET_RENT;
        var incomeByInvestor = new java.util.LinkedHashMap<Long, BigDecimal>();
        incomeRepository.listBySource(IncomeSourceType.EXTERNAL_RENEWAL, event.id()).stream()
            .filter(entry -> snapshot.id().equals(entry.snapshotId()))
            .filter(entry -> entry.beneficiaryType() == IncomeBeneficiaryType.INVESTOR)
            .filter(entry -> entry.lineType() == expectedLine)
            .filter(entry -> entry.beneficiaryId() != null)
            .forEach(entry -> incomeByInvestor.merge(
                entry.beneficiaryId(), money(entry.amount()), BigDecimal::add
            ));
        incomeByInvestor.entrySet().removeIf(entry -> entry.getValue().signum() <= 0);
        if (incomeByInvestor.isEmpty()) {
            return initialTimeline(event, snapshot);
        }
        var slots = java.util.stream.Stream.of(
                snapshot.frameAssetId() == null ? null
                    : new AssetSlot(AssetType.VEHICLE_FRAME, snapshot.frameAssetId()),
                snapshot.batteryAssetId() == null ? null
                    : new AssetSlot(AssetType.BATTERY, snapshot.batteryAssetId())
            )
            .filter(java.util.Objects::nonNull)
            .toList();
        if (slots.isEmpty()) {
            throw BusinessException.badRequest("补录续租已有出资方收益，但快照缺少资产归属");
        }
        if (incomeByInvestor.size() == 1 && slots.size() == 1) {
            var frozenInvestorId = incomeByInvestor.keySet().iterator().next();
            return slots.stream().map(slot -> new TimelineSegment(
                slot.assetType(), slot.assetId(), frozenInvestorId,
                event.periodStartAt(), event.periodEndAt()
            )).toList();
        }
        var currentSegments = slots.stream().map(slot -> {
            var asset = ensureAsset(slot.assetId());
            return new TimelineSegment(
                slot.assetType(), slot.assetId(), requireInvestor(asset),
                event.periodStartAt(), event.periodEndAt()
            );
        }).toList();
        if (incomeByInvestor.size() == 1) {
            var frozenInvestorId = incomeByInvestor.keySet().iterator().next();
            if (currentSegments.stream().anyMatch(segment -> !frozenInvestorId.equals(segment.investorId()))) {
                throw BusinessException.badRequest("补录续租的多资产出资方历史不完整，不能自动重算");
            }
            return currentSegments;
        }
        if (!matchesFrozenIncome(snapshot, currentSegments, incomeByInvestor)) {
            throw BusinessException.badRequest("补录续租历史出资方与当前资产归属不一致，不能自动重算");
        }
        return currentSegments;
    }

    private boolean matchesFrozenIncome(
        SettlementRuleSnapshot snapshot,
        List<TimelineSegment> segments,
        java.util.Map<Long, BigDecimal> frozen
    ) {
        var investorTotal = money(snapshot.calculationVersion().usesProfitSharing()
            ? snapshot.investorShareAmount()
            : snapshot.investorGrossShareAmount());
        var expected = new java.util.LinkedHashMap<Long, BigDecimal>();
        var remaining = investorTotal;
        var average = investorTotal.divide(BigDecimal.valueOf(segments.size()), 2, RoundingMode.HALF_UP);
        for (var index = 0; index < segments.size(); index += 1) {
            var amount = index == segments.size() - 1 ? remaining : average;
            remaining = remaining.subtract(amount);
            expected.merge(segments.get(index).investorId(), money(amount), BigDecimal::add);
        }
        var normalizedFrozen = new java.util.LinkedHashMap<Long, BigDecimal>();
        frozen.forEach((key, value) -> normalizedFrozen.put(key, money(value)));
        return expected.equals(normalizedFrozen);
    }

    private List<TimelineSegment> initialTimeline(
        ExternalOrderRenewalEvent event,
        SettlementRuleSnapshot snapshot
    ) {
        var result = new ArrayList<TimelineSegment>();
        if (snapshot.frameAssetId() != null) {
            result.addAll(historicalTimeline(event, snapshot, AssetType.VEHICLE_FRAME, snapshot.frameAssetId()));
        }
        if (snapshot.batteryAssetId() != null) {
            result.addAll(historicalTimeline(event, snapshot, AssetType.BATTERY, snapshot.batteryAssetId()));
        }
        return result;
    }

    /**
     * A manual renewal may be recorded after an overdue asset was already
     * replaced. Rebuild that retroactive period from the effective-dated
     * replacement log instead of assigning its whole history to the asset
     * currently stored on the order/snapshot.
     */
    private List<TimelineSegment> historicalTimeline(
        ExternalOrderRenewalEvent event,
        SettlementRuleSnapshot snapshot,
        AssetType assetType,
        Long snapshotAssetId
    ) {
        var changes = changeRepository.listByOrderAndAssetType(event.externalOrderId(), assetType);
        if (changes.isEmpty()) {
            var asset = ensureAsset(snapshotAssetId);
            return List.of(new TimelineSegment(
                assetType, asset.id(), requireInvestor(asset), event.periodStartAt(), event.periodEndAt()
            ));
        }

        Long activeAssetId = null;
        Long activeInvestorId = null;
        for (var change : changes) {
            if (!change.effectiveAt().isAfter(event.periodStartAt())) {
                activeAssetId = change.newAssetId();
                activeInvestorId = change.newInvestorId();
            } else {
                activeAssetId = change.oldAssetId();
                activeInvestorId = change.oldInvestorId();
                break;
            }
        }
        if (activeAssetId == null) {
            var asset = ensureAsset(snapshotAssetId);
            activeAssetId = asset.id();
            activeInvestorId = requireInvestor(asset);
        }

        var result = new ArrayList<TimelineSegment>();
        var cursor = event.periodStartAt();
        for (var change : changes) {
            if (!change.effectiveAt().isAfter(event.periodStartAt())
                || !change.effectiveAt().isBefore(event.periodEndAt())) {
                continue;
            }
            if (!change.oldAssetId().equals(activeAssetId)
                || !change.oldInvestorId().equals(activeInvestorId)) {
                throw BusinessException.badRequest("补录订单资产更换历史不连续，不能生成续租分润");
            }
            result.add(new TimelineSegment(
                assetType, activeAssetId, activeInvestorId, cursor, change.effectiveAt()
            ));
            cursor = change.effectiveAt();
            activeAssetId = change.newAssetId();
            activeInvestorId = change.newInvestorId();
        }
        result.add(new TimelineSegment(
            assetType, activeAssetId, activeInvestorId, cursor, event.periodEndAt()
        ));
        return result;
    }

    private void persist(
        ExternalOrderRenewalEvent event,
        SettlementRuleSnapshot snapshot,
        List<TimelineSegment> sourceTimeline
    ) {
        var timeline = sourceTimeline.stream()
            .filter(segment -> segment.endAt().isAfter(segment.startAt()))
            .sorted(Comparator.comparingInt((TimelineSegment item) -> slotRank(item.assetType()))
                .thenComparing(TimelineSegment::startAt)
                .thenComparing(TimelineSegment::assetId))
            .toList();
        if (timeline.isEmpty()) {
            return;
        }
        validateCoverage(event, timeline);
        var slotCount = timeline.stream().map(TimelineSegment::assetType).distinct().count();
        var totalNanos = durationNanos(event.periodStartAt(), event.periodEndAt());
        var rentTotal = money(snapshot.settlementBaseAmount());
        /* Legacy external-renewal income and statements historically pay the
         * event's gross investor rent (despite the legacy income line name
         * INVESTOR_NET_RENT). Preserve that effective ledger behavior instead
         * of switching old events to the initial-order fixed-fee net field. */
        var investorTotal = money(snapshot.calculationVersion().usesProfitSharing()
            ? snapshot.investorShareAmount()
            : snapshot.investorGrossShareAmount());
        if (rentTotal.signum() < 0 || investorTotal.signum() < 0) {
            throw BusinessException.badRequest("续租出资方分配基数不能为负数");
        }
        var rawWeights = timeline.stream().map(segment ->
            durationNanos(segment.startAt(), segment.endAt())
                .divide(totalNanos, WEIGHT_SCALE + 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(slotCount), WEIGHT_SCALE + 8, RoundingMode.HALF_UP)
        ).toList();
        var rawWeightTotal = rawWeights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rawWeightTotal.signum() <= 0) {
            throw BusinessException.badRequest("续租出资方分配权重异常");
        }
        var normalizedWeights = rawWeights.stream()
            .map(weight -> weight.divide(rawWeightTotal, WEIGHT_SCALE + 8, RoundingMode.HALF_UP))
            .toList();
        var rentAmounts = allocateLargestRemainder(rentTotal, normalizedWeights);
        var shareAmounts = allocateLargestRemainder(investorTotal, normalizedWeights);
        var allocatedWeight = BigDecimal.ZERO.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
        var allocatedRent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        var allocatedShare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        var rows = new ArrayList<ExternalOrderRenewalAllocationRepository.CreateRow>();
        for (var index = 0; index < timeline.size(); index += 1) {
            var segment = timeline.get(index);
            var last = index == timeline.size() - 1;
            var weight = last
                ? ONE.subtract(allocatedWeight)
                : normalizedWeights.get(index).setScale(WEIGHT_SCALE, RoundingMode.DOWN);
            /* Money uses stable largest-remainder apportionment. This keeps
             * every row non-negative and distributes scarce cents to the
             * longest effective segments instead of dumping all rounding
             * residue into the globally last asset. Ties retain the already
             * stable assetType/start/assetId timeline order. */
            var rentAmount = rentAmounts.get(index);
            var shareAmount = shareAmounts.get(index);
            if (weight.signum() < 0 || rentAmount.signum() < 0 || shareAmount.signum() < 0) {
                throw BusinessException.badRequest("续租出资方分配不守恒，不能生成负数分配");
            }
            rows.add(new ExternalOrderRenewalAllocationRepository.CreateRow(
                event.id(), snapshot.id(), segment.assetType(), segment.assetId(), segment.investorId(),
                segment.startAt(), segment.endAt(), weight, money(rentAmount), money(shareAmount)
            ));
            allocatedWeight = allocatedWeight.add(weight);
            allocatedRent = allocatedRent.add(rentAmount);
            allocatedShare = allocatedShare.add(shareAmount);
        }
        if (allocatedWeight.compareTo(ONE) != 0
            || money(allocatedRent).compareTo(rentTotal) != 0
            || money(allocatedShare).compareTo(investorTotal) != 0) {
            throw BusinessException.badRequest("续租出资方分配不守恒，请先人工核对");
        }
        rows.forEach(allocationRepository::create);
    }

    private List<BigDecimal> allocateLargestRemainder(
        BigDecimal total,
        List<BigDecimal> normalizedWeights
    ) {
        var allocated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        var result = new ArrayList<BigDecimal>();
        var remainders = new ArrayList<SegmentRemainder>();
        for (var index = 0; index < normalizedWeights.size(); index += 1) {
            var exact = total.multiply(normalizedWeights.get(index));
            var floor = moneyDown(exact);
            result.add(floor);
            allocated = allocated.add(floor);
            remainders.add(new SegmentRemainder(index, exact.subtract(floor)));
        }
        var remainingCents = total.subtract(allocated).movePointRight(2).intValueExact();
        remainders.sort(Comparator.comparing(SegmentRemainder::remainder).reversed()
            .thenComparingInt(SegmentRemainder::index));
        for (var index = 0; index < remainingCents; index += 1) {
            var target = remainders.get(index % remainders.size()).index();
            result.set(target, result.get(target).add(new BigDecimal("0.01")));
        }
        var resultTotal = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (resultTotal.compareTo(total) != 0
            || result.stream().anyMatch(amount -> amount.signum() < 0)) {
            throw BusinessException.badRequest("续租出资方分配不守恒，请先人工核对");
        }
        return List.copyOf(result);
    }

    private void validateCoverage(ExternalOrderRenewalEvent event, List<TimelineSegment> timeline) {
        for (var assetType : timeline.stream().map(TimelineSegment::assetType).distinct().toList()) {
            var slotRows = timeline.stream().filter(row -> row.assetType() == assetType).toList();
            var cursor = event.periodStartAt();
            for (var row : slotRows) {
                if (!row.startAt().equals(cursor) || row.startAt().isBefore(event.periodStartAt())
                    || row.endAt().isAfter(event.periodEndAt())) {
                    throw BusinessException.badRequest("续租资产使用时间线存在重叠或断档，请先人工核对");
                }
                cursor = row.endAt();
            }
            if (!cursor.equals(event.periodEndAt())) {
                throw BusinessException.badRequest("续租资产使用时间线未覆盖完整租期，请先人工核对");
            }
        }
    }

    private SettlementRuleSnapshot ensureRenewalSnapshot(ExternalOrderRenewalEvent event, Long snapshotId) {
        var snapshot = settlementRepository.findSnapshot(snapshotId)
            .orElseThrow(() -> BusinessException.badRequest("补录续租分润快照不存在"));
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_RENEWAL
            || !event.id().equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("补录续租事件与分润快照不匹配");
        }
        return snapshot;
    }

    private AssetItem ensureAsset(Long assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> BusinessException.badRequest("续租分润资产不存在"));
    }

    private Long requireInvestor(AssetItem asset) {
        if (asset.investorId() == null || asset.investorId() <= 0) {
            throw BusinessException.badRequest("资产未绑定出资方，不能生成续租分润");
        }
        return asset.investorId();
    }

    private BigDecimal moneyDown(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.DOWN);
    }

    private int slotRank(AssetType assetType) {
        return assetType == AssetType.VEHICLE_FRAME ? 0 : 1;
    }

    private TimelineSegment toSegment(ExternalOrderRenewalInvestorAllocation row) {
        return new TimelineSegment(
            row.assetType(), row.assetId(), row.investorId(),
            row.effectiveStartAt(), row.effectiveEndAt()
        );
    }

    private BigDecimal durationNanos(LocalDateTime startAt, LocalDateTime endAt) {
        var duration = Duration.between(startAt, endAt);
        return BigDecimal.valueOf(duration.getSeconds())
            .multiply(BigDecimal.valueOf(1_000_000_000L))
            .add(BigDecimal.valueOf(duration.getNano()));
    }

    private BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private record TimelineSegment(
        AssetType assetType,
        Long assetId,
        Long investorId,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
    }

    private record AssetSlot(AssetType assetType, Long assetId) {
    }

    private record SegmentRemainder(int index, BigDecimal remainder) {
    }
}
