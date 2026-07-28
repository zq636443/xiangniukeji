package com.xniu.rental.settlement.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.settlement.dto.SettlementEntryGenerateResponse;
import com.xniu.rental.settlement.dto.SettlementIncomeEntryResponse;
import com.xniu.rental.settlement.model.IncomeBeneficiaryType;
import com.xniu.rental.settlement.model.IncomeEntryStatus;
import com.xniu.rental.settlement.model.IncomeLineType;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementIncomeService {

    private static final Long PLATFORM_BENEFICIARY_ID = 0L;

    private final SettlementIncomeRepository incomeRepository;
    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final AssetFulfillmentRepository assetFulfillmentRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;

    public SettlementIncomeService(
        SettlementIncomeRepository incomeRepository,
        SettlementRepository settlementRepository,
        OrderRepository orderRepository,
        AssetRepository assetRepository,
        AssetFulfillmentRepository assetFulfillmentRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService
    ) {
        this.incomeRepository = incomeRepository;
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
        this.assetFulfillmentRepository = assetFulfillmentRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
    }

    public List<SettlementIncomeEntryResponse> listAdmin(String beneficiaryType, Long beneficiaryId, String status, Long orderId, Long storeId) {
        authorizationService.requirePermission("settlement.read");
        return incomeRepository.list(parseBeneficiaryNullable(beneficiaryType), beneficiaryId, parseStatusNullable(status), orderId, storeId).stream().map(this::toResponse).toList();
    }

    public List<SettlementIncomeEntryResponse> listMerchant(Long storeId, String status) {
        authorizationService.requirePermission("settlement.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        var entries = incomeRepository.list(IncomeBeneficiaryType.MERCHANT, null, parseStatusNullable(status), null, storeId);
        return entries.stream().map(this::toResponse).toList();
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
        return incomeRepository.list(IncomeBeneficiaryType.INVESTOR, investorId, parseStatusNullable(status), null, null).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SettlementEntryGenerateResponse generateForOrder(Long orderId) {
        authorizationService.requirePermission("settlement.write");
        var order = orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        if (order.settlementSnapshotId() == null) {
            throw BusinessException.badRequest("订单暂无分润快照");
        }
        var snapshot = settlementRepository.findSnapshot(order.settlementSnapshotId()).orElseThrow(() -> BusinessException.badRequest("分润快照不存在"));
        var created = createEntries(snapshot, new IncomeSource(
            SnapshotSourceType.ORDER,
            order.id(),
            order.orderNo(),
            order.id(),
            order.orderedAt() == null ? order.createdAt() : order.orderedAt(),
            BigDecimal.ZERO
        ));
        var all = incomeRepository.list(null, null, null, orderId, null);
        return new SettlementEntryGenerateResponse(orderId, snapshot.id(), created.size(), all.stream().map(this::toResponse).toList());
    }

    @Transactional
    public int syncExternalOrder(ExternalRentalOrder order) {
        if (order.settlementSnapshotId() == null) {
            throw BusinessException.badRequest("补录订单暂无分润快照");
        }
        var snapshot = settlementRepository.findSnapshot(order.settlementSnapshotId())
            .orElseThrow(() -> BusinessException.badRequest("补录订单分润快照不存在"));
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER || !order.id().equals(snapshot.sourceId())) {
            throw BusinessException.badRequest("补录订单与分润快照不匹配");
        }
        incomeRepository.deleteBySource(SnapshotSourceType.EXTERNAL_ORDER, order.id());
        return createEntries(snapshot, new IncomeSource(
            SnapshotSourceType.EXTERNAL_ORDER,
            order.id(),
            order.recordNo(),
            null,
            order.createdAt() == null ? LocalDateTime.now() : order.createdAt(),
            order.signFeeAmount()
        )).size();
    }

    @Transactional
    public SettlementIncomeEntryResponse updateStatus(Long id, String status) {
        authorizationService.requirePermission("settlement.write");
        return toResponse(incomeRepository.updateStatus(id, parseStatus(status)));
    }

    private List<SettlementIncomeEntry> createEntries(SettlementRuleSnapshot snapshot, IncomeSource source) {
        if (snapshot.calculationVersion() == SettlementCalculationVersion.PROFIT_V2) {
            return createProfitV2Entries(snapshot, source);
        }
        return createLegacyEntries(snapshot, source);
    }

    private List<SettlementIncomeEntry> createProfitV2Entries(SettlementRuleSnapshot snapshot, IncomeSource source) {
        var created = new ArrayList<SettlementIncomeEntry>();
        add(created, snapshot, source, IncomeBeneficiaryType.CHANNEL, PLATFORM_BENEFICIARY_ID, IncomeLineType.CHANNEL_VERIFICATION_FEE, snapshot.channelFeeAmount(), snapshot.sourceChannel() + "渠道核销扣点");
        add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.PLATFORM_SERVICE_FEE, snapshot.platformFeeAmount(), "租赁平台扣点");
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.STORE_OPERATION_SHARE, snapshot.storeOperationAmount(), "门店运营分润");
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MAINTENANCE_FUND_SHARE, snapshot.maintenanceFundAmount(), "门店维修分润");
        add(created, snapshot, source, IncomeBeneficiaryType.CHANNEL, PLATFORM_BENEFICIARY_ID, IncomeLineType.CHANNEL_REFERRAL_SHARE, snapshot.channelReferralAmount(), snapshot.sourceChannel() + "渠道引流分润");
        addV2InvestorEntries(created, snapshot, source);
        if (source.sourceType() == SnapshotSourceType.EXTERNAL_ORDER) {
            add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MERCHANT_ORDER_FEE, source.signFeeAmount(), "补录订单签单费");
        }
        return created;
    }

    private List<SettlementIncomeEntry> createLegacyEntries(SettlementRuleSnapshot snapshot, IncomeSource source) {
        var created = new ArrayList<SettlementIncomeEntry>();
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MERCHANT_ORDER_FEE, source.sourceType() == SnapshotSourceType.EXTERNAL_ORDER ? source.signFeeAmount() : snapshot.merchantOrderFeeAmount(), "门店办单费");
        add(created, snapshot, source, IncomeBeneficiaryType.MERCHANT, snapshot.storeId(), IncomeLineType.MERCHANT_RENT_SHARE, snapshot.merchantRentShareAmount(), "门店租金分成");
        add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.PLATFORM_RENT_SHARE, snapshot.platformRentShareAmount(), "平台租金分成");
        var allocations = buildInvestorAllocations(snapshot);
        add(created, snapshot, source, IncomeBeneficiaryType.PLATFORM, PLATFORM_BENEFICIARY_ID, IncomeLineType.MAINTENANCE_FEE, totalMaintenanceFee(allocations, snapshot), "资产维保费用");
        addInvestorEntries(created, snapshot, source, allocations);
        return created;
    }

    private void addV2InvestorEntries(List<SettlementIncomeEntry> created, SettlementRuleSnapshot snapshot, IncomeSource source) {
        var allocations = buildV2InvestorAllocations(snapshot);
        if (allocations.isEmpty()) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, PLATFORM_BENEFICIARY_ID, IncomeLineType.INVESTOR_SHARE, snapshot.investorShareAmount(), "出资方分润（待绑定资产）");
            return;
        }
        for (var allocation : allocations) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(), IncomeLineType.INVESTOR_SHARE, allocation.amount(), "出资方分润");
        }
    }

    private List<V2InvestorAllocation> buildV2InvestorAllocations(SettlementRuleSnapshot snapshot) {
        var assets = actualUsageAssets(snapshot);
        if (assets.isEmpty()) {
            return List.of();
        }
        if (assets.stream().anyMatch(asset -> asset.investorId() == null)) {
            throw BusinessException.badRequest("订单资产未绑定出资方，不能生成分润");
        }
        var investorIds = assets.stream().map(AssetItem::investorId).distinct().toList();
        if (snapshot.sourceType() != SnapshotSourceType.EXTERNAL_ORDER && investorIds.size() > 1) {
            throw BusinessException.badRequest("订单绑定了不同出资方的资产，请拆分订单后再生成分润");
        }
        var amountByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var remainingAmount = money(snapshot.investorShareAmount());
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

    private void addInvestorEntries(List<SettlementIncomeEntry> created, SettlementRuleSnapshot snapshot, IncomeSource source, List<InvestorIncomeAllocation> allocations) {
        if (allocations.isEmpty()) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, PLATFORM_BENEFICIARY_ID, IncomeLineType.INVESTOR_NET_RENT, snapshot.investorNetShareAmount(), "出资方净收益（待绑定资产）");
            return;
        }
        for (var allocation : allocations) {
            add(created, snapshot, source, IncomeBeneficiaryType.INVESTOR, allocation.investorId(), IncomeLineType.INVESTOR_NET_RENT, allocation.netAmount(), "出资方净收益");
        }
    }

    private java.util.Optional<AssetItem> findAsset(Long id) {
        return id == null ? java.util.Optional.empty() : assetRepository.findById(id);
    }

    private List<InvestorIncomeAllocation> buildInvestorAllocations(SettlementRuleSnapshot snapshot) {
        var assets = actualUsageAssets(snapshot);
        if (assets.isEmpty()) {
            return List.of();
        }
        var grossByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var maintenanceByInvestor = new LinkedHashMap<Long, BigDecimal>();
        var totalWeight = assets.size();
        var remainingGross = snapshot.investorGrossShareAmount();
        for (var index = 0; index < assets.size(); index += 1) {
            var asset = assets.get(index);
            var gross = index == assets.size() - 1
                ? remainingGross
                : snapshot.investorGrossShareAmount().divide(new BigDecimal(totalWeight), 2, RoundingMode.HALF_UP);
            remainingGross = remainingGross.subtract(gross);
            grossByInvestor.merge(asset.investorId(), gross, BigDecimal::add);
            maintenanceByInvestor.merge(asset.investorId(), money(asset.maintenanceFeeAmount()), BigDecimal::add);
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

    private SettlementIncomeEntryResponse toResponse(SettlementIncomeEntry entry) {
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
            entry.entryStatus().name(),
            entry.remark(),
            entry.occurredAt(),
            entry.settledAt(),
            entry.createdAt()
        );
    }

    private record IncomeSource(
        SnapshotSourceType sourceType,
        Long sourceId,
        String sourceNo,
        Long orderId,
        LocalDateTime occurredAt,
        BigDecimal signFeeAmount
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
}
