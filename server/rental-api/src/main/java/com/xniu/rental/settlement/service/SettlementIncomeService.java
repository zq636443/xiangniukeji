package com.xniu.rental.settlement.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillItemType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalAllocationRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderAssetChangeRepository;
import com.xniu.rental.externalorder.repository.ExternalOrderInitialInvestorAllocationRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.settlement.dto.SettlementEntryGenerateResponse;
import com.xniu.rental.settlement.dto.SettlementIncomeEntryResponse;
import com.xniu.rental.settlement.model.IncomeBeneficiaryType;
import com.xniu.rental.settlement.model.IncomeEntryStatus;
import com.xniu.rental.settlement.model.IncomeLineType;
import com.xniu.rental.settlement.model.IncomeSourceType;
import com.xniu.rental.settlement.model.SettlementIncomeEntry;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementIncomeService {

    private static final Long PLATFORM_BENEFICIARY_ID = 0L;

    private final SettlementIncomeRepository incomeRepository;
    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final BillRepository billRepository;
    private final AssetRepository assetRepository;
    private final AssetFulfillmentRepository assetFulfillmentRepository;
    private final StoreRepository storeRepository;
    private final ExternalRentalOrderRepository externalRentalOrderRepository;
    private final ExternalOrderAssetChangeRepository externalOrderAssetChangeRepository;
    private final ExternalOrderInitialInvestorAllocationRepository externalOrderInitialInvestorAllocationRepository;
    private final AuthorizationService authorizationService;

    public SettlementIncomeService(
        SettlementIncomeRepository incomeRepository,
        SettlementRepository settlementRepository,
        OrderRepository orderRepository,
        BillRepository billRepository,
        AssetRepository assetRepository,
        AssetFulfillmentRepository assetFulfillmentRepository,
        StoreRepository storeRepository,
        ExternalRentalOrderRepository externalRentalOrderRepository,
        ExternalOrderAssetChangeRepository externalOrderAssetChangeRepository,
        ExternalOrderInitialInvestorAllocationRepository externalOrderInitialInvestorAllocationRepository,
        AuthorizationService authorizationService
    ) {
        this.incomeRepository = incomeRepository;
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.billRepository = billRepository;
        this.assetRepository = assetRepository;
        this.assetFulfillmentRepository = assetFulfillmentRepository;
        this.storeRepository = storeRepository;
        this.externalRentalOrderRepository = externalRentalOrderRepository;
        this.externalOrderAssetChangeRepository = externalOrderAssetChangeRepository;
        this.externalOrderInitialInvestorAllocationRepository = externalOrderInitialInvestorAllocationRepository;
        this.authorizationService = authorizationService;
    }

    public List<SettlementIncomeEntryResponse> listAdmin(String beneficiaryType, Long beneficiaryId, String status, Long orderId, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        return toResponses(incomeRepository.list(parseBeneficiaryNullable(beneficiaryType), beneficiaryId, parseStatusNullable(status), orderId, storeId));
    }

    public List<SettlementIncomeEntryResponse> listMerchant(Long storeId, String status) {
        authorizationService.requirePermission("settlement.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        var entries = incomeRepository.list(IncomeBeneficiaryType.MERCHANT, null, parseStatusNullable(status), null, storeId);
        return toResponses(entries);
    }

    public List<SettlementIncomeEntryResponse> listInvestor(String status) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        var investorId = current.account().investorId();
        if (investorId == null) {
            throw BusinessException.forbidden("当前账号未绑定出资方");
        }
        return toResponses(incomeRepository.list(IncomeBeneficiaryType.INVESTOR, investorId, parseStatusNullable(status), null, null));
    }

    @Transactional
    public SettlementEntryGenerateResponse generateForOrder(Long orderId) {
        authorizationService.requirePermission("settlement.write");
        var order = orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        if (order.settlementSnapshotId() == null) {
            throw BusinessException.badRequest("订单暂无分润快照");
        }
        var snapshot = settlementRepository.findSnapshot(order.settlementSnapshotId()).orElseThrow(() -> BusinessException.badRequest("分润快照不存在"));
        var createdCount = billRepository.listBills(BillStatus.PAID, orderId, null).stream()
            .mapToInt(this::syncPaidBill)
            .sum();
        var all = incomeRepository.list(null, null, null, orderId, null);
        return new SettlementEntryGenerateResponse(orderId, snapshot.id(), createdCount, toResponses(all));
    }

    @Transactional
    public int syncPaidBills(LocalDateTime startAt, LocalDateTime endAt) {
        return billRepository.listPaidBills(startAt, endAt).stream().mapToInt(this::syncPaidBill).sum();
    }

    @Transactional
    public int syncPaidBill(RentalBill bill) {
        if (bill == null || bill.billStatus() != BillStatus.PAID) {
            return 0;
        }
        var order = orderRepository.findById(bill.orderId()).orElseThrow(() -> BusinessException.badRequest("账单关联订单不存在"));
        if (order.settlementSnapshotId() == null) {
            throw BusinessException.badRequest("订单 " + order.orderNo() + " 缺少分润快照，不能确认实际收益");
        }
        var snapshot = settlementRepository.findSnapshot(order.settlementSnapshotId())
            .orElseThrow(() -> BusinessException.badRequest("订单 " + order.orderNo() + " 的分润快照不存在"));
        if (snapshot.sourceType() != SnapshotSourceType.ORDER || !order.id().equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("账单关联订单与分润快照不匹配");
        }
        var items = billRepository.listItems(bill.id());
        var rentAmount = items.stream()
            .filter(item -> item.itemType() == BillItemType.RENT || item.itemType() == BillItemType.RENEWAL_RENT)
            .map(item -> money(item.amount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var signFeeAmount = items.stream()
            .filter(item -> item.itemType() == BillItemType.SIGN_FEE)
            .map(item -> money(item.amount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return createEntries(snapshot, new IncomeSource(
            IncomeSourceType.BILL,
            bill.id(),
            bill.billNo(),
            order.id(),
            bill.paidAt() == null ? LocalDateTime.now() : bill.paidAt(),
            signFeeAmount,
            money(rentAmount)
        )).size();
    }

    @Transactional
    public int syncExternalOrder(ExternalRentalOrder order) {
        /* This synchronizer is normally called while the external-order
         * service already owns the order row lock.  Re-acquire it here as a
         * defensive boundary because the method is public and could also be
         * invoked by a repair/import path with a detached order object.  A
         * delete/terminate operation takes the same order lock before touching
         * income rows, so no stale entries can be recreated after it commits. */
        var lockedOrder = externalRentalOrderRepository.findByIdForUpdate(order.id())
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        /* Status transitions lock income rows independently of the order. Keep
         * the source rows locked for the whole delete/recreate operation so a
         * concurrent settlement cannot mark an entry settled between the
         * caller's mutability check and this replacement.  A synchronizer is
         * also a destructive replacement: once any source row is SETTLED or
         * FROZEN it must fail closed rather than delete the historical fact.
         * The FOR UPDATE query both checks the status and owns the rows until
         * this transaction commits, including the terminated-order cleanup
         * branch below.
         */
        var existingEntries = incomeRepository.listBySourceForUpdate(
            IncomeSourceType.EXTERNAL_ORDER, lockedOrder.id()
        );
        if (existingEntries.stream().anyMatch(entry -> entry.entryStatus() != IncomeEntryStatus.PENDING)) {
            throw BusinessException.badRequest("补录订单收益已结算或冻结，不能重建");
        }
        if (lockedOrder.orderStatus() == ExternalRentalOrderStatus.TERMINATED) {
            incomeRepository.deleteBySource(IncomeSourceType.EXTERNAL_ORDER, lockedOrder.id());
            return 0;
        }
        if (lockedOrder.settlementSnapshotId() == null) {
            throw BusinessException.badRequest("补录订单暂无分润快照");
        }
        var snapshot = settlementRepository.findSnapshot(lockedOrder.settlementSnapshotId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单分润快照不存在"));
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER || !lockedOrder.id().equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("补录订单与分润快照不匹配");
        }
        var frozenInvestorAllocations = preserveExternalOrderInvestorAllocations(lockedOrder, snapshot);
        incomeRepository.deleteBySource(IncomeSourceType.EXTERNAL_ORDER, lockedOrder.id());
        return createEntries(snapshot, new IncomeSource(
            IncomeSourceType.EXTERNAL_ORDER,
            lockedOrder.id(),
            lockedOrder.recordNo(),
            null,
            lockedOrder.createdAt() == null ? LocalDateTime.now() : lockedOrder.createdAt(),
            lockedOrder.signFeeAmount(),
            snapshot.settlementBaseAmount()
        ), frozenInvestorAllocations).size();
    }

    @Transactional
    public int createExternalRenewalEntries(
        Long eventId,
        String eventNo,
        Long snapshotId,
        LocalDateTime occurredAt,
        BigDecimal renewalAmount
    ) {
        return createExternalRenewalEntries(
            eventId, eventNo, snapshotId, occurredAt, renewalAmount, List.of()
        );
    }

    @Transactional
    public int createExternalRenewalEntries(
        Long eventId,
        String eventNo,
        Long snapshotId,
        LocalDateTime occurredAt,
        BigDecimal renewalAmount,
        List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> investorAllocations
    ) {
        var snapshot = settlementRepository.findSnapshot(snapshotId)
            .orElseThrow(() -> BusinessException.badRequest("补录续租分润快照不存在"));
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_RENEWAL || !eventId.equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("补录续租事件与分润快照不匹配");
        }
        return createEntries(snapshot, new IncomeSource(
            IncomeSourceType.EXTERNAL_RENEWAL,
            eventId,
            eventNo,
            null,
            occurredAt,
            BigDecimal.ZERO,
            renewalAmount
        ), investorAllocations).size();
    }

    @Transactional
    public int createExternalRenewalInvestorEntries(
        Long eventId,
        String eventNo,
        Long snapshotId,
        LocalDateTime occurredAt,
        List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> investorAllocations
    ) {
        var snapshot = settlementRepository.findSnapshot(snapshotId)
            .orElseThrow(() -> BusinessException.badRequest("补录续租分润快照不存在"));
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_RENEWAL || !eventId.equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("补录续租事件与分润快照不匹配");
        }
        var source = new IncomeSource(
            IncomeSourceType.EXTERNAL_RENEWAL,
            eventId,
            eventNo,
            null,
            occurredAt,
            BigDecimal.ZERO,
            snapshot.rentalAmount()
        );
        var lineType = snapshot.calculationVersion().usesProfitSharing()
            ? IncomeLineType.INVESTOR_SHARE
            : IncomeLineType.INVESTOR_NET_RENT;
        var created = new ArrayList<SettlementIncomeEntry>();
        for (var allocation : investorAllocations) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(),
                lineType, allocation.investorShareAmount(), "出资方分润（按资产有效时长）");
        }
        return created.size();
    }

    @Transactional
    public SettlementIncomeEntryResponse updateStatus(Long id, String status) {
        authorizationService.requirePermission("settlement.write");
        var current = incomeRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("收益流水不存在"));
        var target = parseStatus(status);
        /* A settled entry is a financial lock.  FROZEN entries may be
         * deliberately released by an administrator, but a settled amount
         * must never be rolled back to pending/frozen and then rewritten or
         * deleted through an order edit. */
        if (current.entryStatus() == IncomeEntryStatus.SETTLED
            && target != IncomeEntryStatus.SETTLED) {
            throw BusinessException.badRequest("已结算收益流水不可回退状态");
        }
        if (current.entryStatus() == target) {
            return toResponse(current);
        }
        return toResponse(incomeRepository.updateStatus(id, target));
    }

    private List<SettlementIncomeEntry> createEntries(SettlementRuleSnapshot snapshot, IncomeSource source) {
        return createEntries(snapshot, source, List.of());
    }

    private List<SettlementIncomeEntry> createEntries(
        SettlementRuleSnapshot snapshot,
        IncomeSource source,
        List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> investorAllocations
    ) {
        if (snapshot.calculationVersion().usesProfitSharing()) {
            return createProfitV2Entries(snapshot, source, investorAllocations);
        }
        return createLegacyEntries(snapshot, source, investorAllocations);
    }

    private List<SettlementIncomeEntry> createProfitV2Entries(
        SettlementRuleSnapshot snapshot,
        IncomeSource source,
        List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> investorAllocations
    ) {
        var created = new ArrayList<SettlementIncomeEntry>();
        var allocation = source.sourceType() == IncomeSourceType.BILL
            || source.sourceType() == IncomeSourceType.EXTERNAL_RENEWAL
            ? ProfitSharingCalculator.calculate(
                snapshot.calculationVersion(),
                source.rentAmount(),
                snapshot.channelFeeRate(),
                snapshot.platformFeeRate(),
                BatteryCostCalculator.prorate(snapshot.batteryCostAmount(), source.rentAmount(), snapshot.rentalAmount()),
                snapshot.storeOperationRate(),
                snapshot.maintenanceFundRate(),
                snapshot.channelReferralRate(),
                snapshot.investorShareRate()
            )
            : null;
        var channelFeeAmount = allocation == null ? snapshot.channelFeeAmount() : allocation.channelFeeAmount();
        var platformFeeAmount = allocation == null ? snapshot.platformFeeAmount() : allocation.platformFeeAmount();
        var storeOperationAmount = allocation == null ? snapshot.storeOperationAmount() : allocation.storeOperationAmount();
        var maintenanceFundAmount = allocation == null ? snapshot.maintenanceFundAmount() : allocation.maintenanceFundAmount();
        var channelReferralAmount = allocation == null ? snapshot.channelReferralAmount() : allocation.channelReferralAmount();
        var investorShareAmount = allocation == null ? snapshot.investorShareAmount() : allocation.investorShareAmount();
        add(created, snapshot, source, IncomeBeneficiaryType.CHANNEL, PLATFORM_BENEFICIARY_ID, IncomeLineType.CHANNEL_VERIFICATION_FEE, channelFeeAmount, snapshot.sourceChannel() + "渠道核销扣点");
        add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.PLATFORM_SERVICE_FEE, platformFeeAmount, "租赁平台扣点");
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.STORE_OPERATION_SHARE, storeOperationAmount, "门店运营分润");
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MAINTENANCE_FUND_SHARE, maintenanceFundAmount, "门店维修分润");
        add(created, snapshot, source, IncomeBeneficiaryType.CHANNEL, PLATFORM_BENEFICIARY_ID, IncomeLineType.CHANNEL_REFERRAL_SHARE, channelReferralAmount, snapshot.sourceChannel() + "渠道引流分润");
        addV2InvestorEntries(created, snapshot, source, investorShareAmount, investorAllocations);
        if (source.signFeeAmount().signum() > 0) {
            var remark = source.sourceType() == IncomeSourceType.EXTERNAL_ORDER ? "补录订单签单费" : "签单费实收";
            var orderFeeAllocation = ProfitSharingCalculator.calculateOrderFee(source.signFeeAmount());
            add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MERCHANT_ORDER_FEE, orderFeeAllocation.merchantNetAmount(), remark);
            if (orderFeeAllocation.serviceFeeAmount().signum() > 0) {
                add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.PLATFORM_ORDER_FEE_SERVICE_FEE, orderFeeAllocation.serviceFeeAmount(), remark + "手续费计入平台收益");
            }
        }
        return created;
    }

    private List<SettlementIncomeEntry> createLegacyEntries(
        SettlementRuleSnapshot snapshot,
        IncomeSource source,
        List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> investorAllocations
    ) {
        var created = new ArrayList<SettlementIncomeEntry>();
        var actualSource = source.sourceType() != IncomeSourceType.ORDER;
        var orderFeeAllocation = actualSource
            ? ProfitSharingCalculator.calculateOrderFee(source.signFeeAmount())
            : null;
        var signFeeAmount = actualSource ? orderFeeAllocation.merchantNetAmount() : snapshot.merchantOrderFeeAmount();
        var merchantShareAmount = actualSource
            ? money(source.rentAmount().multiply(snapshot.merchantRentShareRate()))
            : snapshot.merchantRentShareAmount();
        var platformShareAmount = actualSource
            ? money(source.rentAmount().multiply(snapshot.platformRentShareRate()))
            : snapshot.platformRentShareAmount();
        var investorGrossAmount = actualSource
            ? money(source.rentAmount().multiply(snapshot.investorRentShareRate()))
            : snapshot.investorGrossShareAmount();
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MERCHANT_ORDER_FEE, signFeeAmount, actualSource ? "签单费实收" : "门店办单费");
        if (actualSource && orderFeeAllocation.serviceFeeAmount().signum() > 0) {
            add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID,
                IncomeLineType.PLATFORM_ORDER_FEE_SERVICE_FEE, orderFeeAllocation.serviceFeeAmount(), "签单费手续费计入平台收益");
        }
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MERCHANT_RENT_SHARE, merchantShareAmount, "门店租金分成");
        add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.PLATFORM_RENT_SHARE, platformShareAmount, "平台租金分成");
        var allocations = buildInvestorAllocations(snapshot, investorGrossAmount, !actualSource);
        if (!actualSource) {
            add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.MAINTENANCE_FEE, totalMaintenanceFee(allocations, snapshot), "资产维保费用");
        }
        if ((source.sourceType() == IncomeSourceType.EXTERNAL_RENEWAL
            || source.sourceType() == IncomeSourceType.EXTERNAL_ORDER) && investorAllocations != null
            && !investorAllocations.isEmpty()) {
            for (var allocation : investorAllocations) {
                add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(),
                    IncomeLineType.INVESTOR_NET_RENT, allocation.investorShareAmount(), "出资方分润（按资产有效时长）");
            }
        } else {
            addInvestorEntries(created, snapshot, source, allocations);
        }
        return created;
    }

    private void addV2InvestorEntries(
        List<SettlementIncomeEntry> created,
        SettlementRuleSnapshot snapshot,
        IncomeSource source,
        BigDecimal investorShareAmount,
        List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal> investorAllocations
    ) {
        if ((source.sourceType() == IncomeSourceType.EXTERNAL_RENEWAL
            || source.sourceType() == IncomeSourceType.EXTERNAL_ORDER) && investorAllocations != null
            && !investorAllocations.isEmpty()) {
            for (var allocation : investorAllocations) {
                add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(),
                    IncomeLineType.INVESTOR_SHARE, allocation.investorShareAmount(), "出资方分润（按资产有效时长）");
            }
            return;
        }
        var allocations = buildV2InvestorAllocations(snapshot, investorShareAmount);
        if (allocations.isEmpty()) {
            if (source.sourceType() != IncomeSourceType.BILL) {
                add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, PLATFORM_BENEFICIARY_ID, IncomeLineType.INVESTOR_SHARE, investorShareAmount, "出资方分润（待绑定资产）");
            }
            return;
        }
        for (var allocation : allocations) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(), IncomeLineType.INVESTOR_SHARE, allocation.amount(), "出资方分润");
        }
    }

    private List<V2InvestorAllocation> buildV2InvestorAllocations(SettlementRuleSnapshot snapshot, BigDecimal investorShareAmount) {
        var assets = actualUsageAssets(snapshot);
        if (assets.isEmpty()) {
            return List.of();
        }
        if (assets.stream().anyMatch(asset -> asset.investorId() == null)) {
            throw BusinessException.badRequest("订单资产未绑定出资方，不能生成分润");
        }
        var investorIds = assets.stream().map(AssetItem::investorId).distinct().toList();
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER
            && snapshot.sourceType() != SnapshotSourceType.EXTERNAL_RENEWAL
            && investorIds.size() > 1) {
            throw BusinessException.badRequest("订单绑定了不同出资方的资产，请拆分订单后再生成分润");
        }
        var amountByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var remainingAmount = money(investorShareAmount);
        var averageAmount = remainingAmount.divide(BigDecimal.valueOf(assets.size()), 2, RoundingMode.HALF_UP);
        for (var index = 0; index < assets.size(); index += 1) {
            var asset = assets.get(index);
            var amount = index == assets.size() - 1 ? remainingAmount : averageAmount;
            remainingAmount = remainingAmount.subtract(amount);
            amountByInvestor.merge(asset.investorId(), money(amount), BigDecimal::add);
        }
        return amountByInvestor.entrySet().stream()
            .map(entry -> new V2InvestorAllocation(entry.getKey(), money(entry.getValue())))
            .toList();
    }

    public List<ExternalOrderInitialInvestorAllocationRepository.InitialAllocation>
        ensureExternalOrderInitialInvestorAllocation(
            ExternalRentalOrder order,
            SettlementRuleSnapshot snapshot
        ) {
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER
            || !order.id().equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("补录订单与初始分润快照不匹配");
        }
        var expectedSlots = java.util.stream.Stream.of(
                snapshot.frameAssetId() == null ? null
                    : new InitialAssetSlot(com.xniu.rental.asset.model.AssetType.VEHICLE_FRAME, snapshot.frameAssetId()),
                snapshot.batteryAssetId() == null ? null
                    : new InitialAssetSlot(com.xniu.rental.asset.model.AssetType.BATTERY, snapshot.batteryAssetId())
            )
            .filter(java.util.Objects::nonNull)
            .toList();
        var frozen = externalOrderInitialInvestorAllocationRepository.listByExternalOrder(order.id());
        if (frozen.isEmpty() && !expectedSlots.isEmpty()) {
            if (externalOrderAssetChangeRepository.existsByExternalOrder(order.id())) {
                throw BusinessException.badRequest("补录订单初始资产槽位冻结记录缺失，不能继续重建或更换资产");
            }
            var remainingWeight = BigDecimal.ONE.setScale(12, RoundingMode.HALF_UP);
            var averageWeight = BigDecimal.ONE.divide(
                BigDecimal.valueOf(expectedSlots.size()), 12, RoundingMode.DOWN
            );
            var lockedAssets = expectedSlots.stream()
                .map(InitialAssetSlot::assetId)
                .distinct()
                .sorted()
                .map(assetId -> assetRepository.findByIdForUpdate(assetId)
                    .orElseThrow(() -> BusinessException.badRequest("补录订单初始资产不存在")))
                .collect(java.util.stream.Collectors.toMap(AssetItem::id, asset -> asset));
            for (var index = 0; index < expectedSlots.size(); index += 1) {
                var slot = expectedSlots.get(index);
                var asset = lockedAssets.get(slot.assetId());
                if (asset.investorId() == null || asset.investorId() <= 0) {
                    throw BusinessException.badRequest("补录订单初始资产未绑定出资方");
                }
                var weight = index == expectedSlots.size() - 1 ? remainingWeight : averageWeight;
                remainingWeight = remainingWeight.subtract(weight);
                externalOrderInitialInvestorAllocationRepository.createIfAbsent(
                    new ExternalOrderInitialInvestorAllocationRepository.InitialAllocation(
                        order.id(), snapshot.id(), slot.assetType(), slot.assetId(), asset.investorId(), weight
                    )
                );
            }
            frozen = externalOrderInitialInvestorAllocationRepository.listByExternalOrder(order.id());
        }
        if (frozen.size() != expectedSlots.size()
            || frozen.stream().anyMatch(row -> row.investorId() == null || row.investorId() <= 0
                || row.allocationWeight() == null || row.allocationWeight().signum() <= 0)
            || frozen.stream().anyMatch(row -> expectedSlots.stream().noneMatch(slot ->
                slot.assetType() == row.assetType() && slot.assetId().equals(row.assetId())
            ))) {
            throw BusinessException.badRequest("补录订单初始资产槽位冻结记录不完整，请先人工核对");
        }
        var totalWeight = frozen.stream().map(
            ExternalOrderInitialInvestorAllocationRepository.InitialAllocation::allocationWeight
        ).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!frozen.isEmpty() && totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw BusinessException.badRequest("补录订单初始出资方权重不守恒，请先人工核对");
        }
        return List.copyOf(frozen);
    }

    private List<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal>
        preserveExternalOrderInvestorAllocations(
            ExternalRentalOrder order,
            SettlementRuleSnapshot snapshot
        ) {
        var frozenSlots = ensureExternalOrderInitialInvestorAllocation(order, snapshot);
        var nextTotal = money(snapshot.calculationVersion().usesProfitSharing()
            ? snapshot.investorShareAmount()
            : snapshot.settlementBaseAmount().multiply(snapshot.investorRentShareRate()));
        if (nextTotal.signum() <= 0) {
            return List.of();
        }
        if (frozenSlots.isEmpty()) {
            return List.of();
        }
        /* Preserve the historical frame -> battery tie order used by the old
         * initial-income writer.  Allocate cents per frozen slot first, then
         * aggregate by investor; grouping first would move a one-cent pool to
         * whichever investor happened to have the smaller database id. */
        var sortedSlots = frozenSlots.stream()
            .sorted(java.util.Comparator
                .comparingInt((ExternalOrderInitialInvestorAllocationRepository.InitialAllocation row) ->
                    row.assetType() == com.xniu.rental.asset.model.AssetType.VEHICLE_FRAME ? 0 : 1)
                .thenComparing(ExternalOrderInitialInvestorAllocationRepository.InitialAllocation::assetId))
            .toList();
        var slotAmounts = largestRemainderAmounts(
            nextTotal,
            sortedSlots.stream().map(
                ExternalOrderInitialInvestorAllocationRepository.InitialAllocation::allocationWeight
            ).toList()
        );
        var amountByInvestor = new LinkedHashMap<Long, BigDecimal>();
        for (var index = 0; index < sortedSlots.size(); index += 1) {
            amountByInvestor.merge(
                sortedSlots.get(index).investorId(), slotAmounts.get(index), BigDecimal::add
            );
        }
        var result = new ArrayList<ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal>();
        for (var item : amountByInvestor.entrySet()) {
            result.add(new ExternalOrderRenewalAllocationRepository.InvestorAllocationTotal(
                item.getKey(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), money(item.getValue())
            ));
        }
        return List.copyOf(result);
    }

    private List<BigDecimal> largestRemainderAmounts(
        BigDecimal total,
        List<BigDecimal> weights
    ) {
        var result = new ArrayList<BigDecimal>();
        var remainders = new ArrayList<IndexedRemainder>();
        var allocated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (var index = 0; index < weights.size(); index += 1) {
            var exact = total.multiply(weights.get(index));
            var floor = exact.setScale(2, RoundingMode.DOWN);
            result.add(floor);
            allocated = allocated.add(floor);
            remainders.add(new IndexedRemainder(index, exact.subtract(floor)));
        }
        var remainingCents = total.subtract(allocated).movePointRight(2).intValueExact();
        remainders.sort(java.util.Comparator.comparing(IndexedRemainder::remainder).reversed()
            .thenComparingInt(IndexedRemainder::index));
        for (var index = 0; index < remainingCents; index += 1) {
            var target = remainders.get(index % remainders.size()).index();
            result.set(target, result.get(target).add(new BigDecimal("0.01")));
        }
        var resultTotal = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (resultTotal.compareTo(total) != 0 || result.stream().anyMatch(amount -> amount.signum() < 0)) {
            throw BusinessException.badRequest("补录订单初始出资方分配不守恒，不能重建");
        }
        return List.copyOf(result);
    }

    private void addInvestorEntries(List<SettlementIncomeEntry> created, SettlementRuleSnapshot snapshot, IncomeSource source, List<InvestorIncomeAllocation> allocations) {
        if (allocations.isEmpty()) {
            if (source.sourceType() != IncomeSourceType.BILL) {
                add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, PLATFORM_BENEFICIARY_ID, IncomeLineType.INVESTOR_NET_RENT, snapshot.investorNetShareAmount(), "出资方净收益（待绑定资产）");
            }
            return;
        }
        for (var allocation : allocations) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(), IncomeLineType.INVESTOR_NET_RENT, allocation.netAmount(), "出资方净收益");
        }
    }

    private java.util.Optional<AssetItem> findAsset(Long id) {
        return id == null ? java.util.Optional.empty() : assetRepository.findById(id);
    }

    private List<InvestorIncomeAllocation> buildInvestorAllocations(
        SettlementRuleSnapshot snapshot,
        BigDecimal investorGrossAmount,
        boolean includeFixedMaintenanceFee
    ) {
        var assets = actualUsageAssets(snapshot);
        if (assets.isEmpty()) {
            return List.of();
        }
        var grossByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var maintenanceByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var totalWeight = assets.size();
        var remainingGross = money(investorGrossAmount);
        for (var index = 0; index < assets.size(); index += 1) {
            var asset = assets.get(index);
            var gross = index == assets.size() - 1
                ? remainingGross
                : money(investorGrossAmount).divide(new BigDecimal(totalWeight), 2, RoundingMode.HALF_UP);
            remainingGross = remainingGross.subtract(gross);
            grossByInvestor.merge(asset.investorId(), gross, BigDecimal::add);
            maintenanceByInvestor.merge(
                asset.investorId(),
                includeFixedMaintenanceFee ? money(asset.maintenanceFeeAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal::add
            );
        }
        return grossByInvestor.entrySet().stream().map(entry -> {
            var investorId = entry.getKey();
            var gross = money(entry.getValue());
            var maintenanceFee = money(maintenanceByInvestor.get(investorId));
            var net = gross.subtract(maintenanceFee).setScale(2, RoundingMode.HALF_UP);
            if (net.signum() < 0) {
                net = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return new InvestorIncomeAllocation(investorId, gross, maintenanceFee, net);
        }).toList();
    }

    private List<AssetItem> actualUsageAssets(SettlementRuleSnapshot snapshot) {
        if (snapshot.sourceType() == SnapshotSourceType.ORDER) {
            var usageAssets = assetFulfillmentRepository.listUsageByOrder(snapshot.sourceId()).stream()
                .map(usage -> assetRepository.findById(usage.assetId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
            if (!usageAssets.isEmpty()) {
                return usageAssets;
            }
        }
        return java.util.stream.Stream.of(findAsset(snapshot.frameAssetId()), findAsset(snapshot.batteryAssetId()))
            .flatMap(java.util.Optional::stream)
            .toList();
    }

    private BigDecimal totalMaintenanceFee(List<InvestorIncomeAllocation> allocations, SettlementRuleSnapshot snapshot) {
        if (allocations.isEmpty()) {
            return snapshot.maintenanceFeeAmount();
        }
        return allocations.stream().map(InvestorIncomeAllocation::maintenanceFeeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private void add(List<SettlementIncomeEntry> created, SettlementRuleSnapshot snapshot, IncomeSource source, IncomeBeneficiaryType beneficiaryType, Long beneficiaryId, IncomeLineType lineType, BigDecimal amount, String remark) {
        incomeRepository.create(new SettlementIncomeRepository.CreateRow(
            "INC-" + UUID.randomUUID().toString().substring(0, 8),
            source.sourceType(),
            source.sourceId(),
            source.sourceNo(),
            source.orderId(),
            snapshot.id(),
            snapshot.merchantId(),
            snapshot.storeId(),
            beneficiaryType,
            beneficiaryId,
            lineType,
            amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP),
            remark,
            source.occurredAt() == null ? LocalDateTime.now() : source.occurredAt()
        )).ifPresent(created::add);
    }

    private IncomeBeneficiaryType parseBeneficiaryNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return IncomeBeneficiaryType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的收益方类型");
        }
    }

    private IncomeEntryStatus parseStatusNullable(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    private IncomeEntryStatus parseStatus(String value) {
        try {
            return IncomeEntryStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的收益状态");
        }
    }

    private List<SettlementIncomeEntryResponse> toResponses(List<SettlementIncomeEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, SettlementRuleSnapshot> snapshots = settlementRepository.findSnapshotsByIds(
            entries.stream()
                .map(SettlementIncomeEntry::snapshotId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList()
        ).stream().collect(java.util.stream.Collectors.toMap(SettlementRuleSnapshot::id, snapshot -> snapshot));
        var externalOrderIds = entries.stream()
            .filter(entry -> entry.sourceType() == IncomeSourceType.EXTERNAL_ORDER
                && entry.lineType() == IncomeLineType.MERCHANT_ORDER_FEE)
            .map(SettlementIncomeEntry::sourceId)
            .distinct()
            .toList();
        var externalOrderFees = externalRentalOrderRepository.findSignFeeAmounts(externalOrderIds);
        return entries.stream().map(entry -> toResponse(
            entry,
            snapshots.get(entry.snapshotId()),
            entry.sourceType() == IncomeSourceType.EXTERNAL_ORDER
                ? externalOrderFees.get(entry.sourceId())
                : null
        )).toList();
    }

    private SettlementIncomeEntryResponse toResponse(SettlementIncomeEntry entry) {
        var snapshot = settlementRepository.findSnapshot(entry.snapshotId()).orElse(null);
        var orderFee = entry.sourceType() == IncomeSourceType.EXTERNAL_ORDER
            ? externalRentalOrderRepository.findSignFeeAmounts(List.of(entry.sourceId())).get(entry.sourceId())
            : null;
        return toResponse(entry, snapshot, orderFee);
    }

    private SettlementIncomeEntryResponse toResponse(
        SettlementIncomeEntry entry,
        SettlementRuleSnapshot snapshot,
        BigDecimal externalOrderFee
    ) {
        var storeRevenueAmount = entry.amount();
        /* Historical supplemental-order rows can contain the gross handling
         * fee (including the early PROFIT_V2 window where the snapshot fee was
         * left at zero). Keep the immutable ledger amount intact, but expose
         * the confirmed 97% net entitlement for dashboards/store views. New
         * net rows do not equal the snapshot gross, so they are not reduced
         * twice. */
        var grossFee = externalOrderFee != null ? money(externalOrderFee)
            : snapshot == null ? BigDecimal.ZERO : money(snapshot.signFeeAmount());
        if (entry.beneficiaryType() == IncomeBeneficiaryType.MERCHANT
            && entry.lineType() == IncomeLineType.MERCHANT_ORDER_FEE
            && entry.sourceType() != IncomeSourceType.ORDER
            && grossFee.signum() > 0
            && (money(entry.amount()).signum() == 0 || money(entry.amount()).compareTo(grossFee) == 0)) {
            // V2 was briefly deployed with a zero merchant fee row, and an
            // older importer could persist the gross fee.  Both fingerprints
            // are immutable historical data; expose the current merchant
            // entitlement only in the response projection.
            storeRevenueAmount = ProfitSharingCalculator.calculateOrderFee(grossFee).merchantNetAmount();
        }
        return new SettlementIncomeEntryResponse(
            entry.id(),
            entry.entryNo(),
            entry.sourceType().name(),
            entry.sourceId(),
            entry.sourceNo(),
            entry.orderId(),
            entry.snapshotId(),
            entry.merchantId(),
            entry.storeId(),
            entry.beneficiaryType().name(),
            entry.beneficiaryId(),
            entry.lineType().name(),
            entry.amount(),
            storeRevenueAmount,
            entry.entryStatus().name(),
            entry.remark(),
            entry.occurredAt(),
            entry.settledAt(),
            entry.createdAt()
        );
    }

    private record IncomeSource(
        IncomeSourceType sourceType,
        Long sourceId,
        String sourceNo,
        Long orderId,
        LocalDateTime occurredAt,
        BigDecimal signFeeAmount,
        BigDecimal rentAmount
    ) {
    }

    private record InvestorIncomeAllocation(
        Long investorId,
        BigDecimal grossAmount,
        BigDecimal maintenanceFeeAmount,
        BigDecimal netAmount
    ) {
    }

    private record V2InvestorAllocation(Long investorId, BigDecimal amount) {
    }

    private record InitialAssetSlot(com.xniu.rental.asset.model.AssetType assetType, Long assetId) {
    }

    private record IndexedRemainder(int index, BigDecimal remainder) {
    }
}
