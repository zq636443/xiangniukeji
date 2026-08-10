package com.xniu.rental.settlement.service;

import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.ProfitRuleRequest;
import com.xniu.rental.settlement.dto.ProfitRuleResponse;
import com.xniu.rental.settlement.dto.SettlementPreviewRequest;
import com.xniu.rental.settlement.dto.SettlementSnapshotResponse;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.dto.StoreProfitRuleUpdateRequest;
import com.xniu.rental.settlement.model.RuleScope;
import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import com.xniu.rental.settlement.model.SettlementProfitRule;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SettlementRuleStatus;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private static final BigDecimal ONE = new BigDecimal("1.0000");
    private static final BigDecimal ORDER_FEE_SERVICE_RATE = new BigDecimal("0.03");

    private final SettlementRepository settlementRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;

    public SettlementService(
        SettlementRepository settlementRepository,
        ProductRepository productRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService
    ) {
        this.settlementRepository = settlementRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
    }

    public List<ProfitRuleResponse> listRules(String scope, String status) {
        authorizationService.requirePermission("settlement.read");
        return settlementRepository.listRules(parseScopeNullable(scope), parseStatusNullable(status)).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public List<ProfitRuleResponse> listStoreRules() {
        authorizationService.requirePermission("settlement.read");
        storeRepository.list(null, null).forEach(store -> initializeStoreProfitRuleInternal(store.id()));
        return settlementRepository.listRules(RuleScope.STORE, null).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProfitRuleResponse createRule(ProfitRuleRequest request) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("settlement.write");
        var normalized = normalizeRule(request, null);
        var rule = settlementRepository.createRule(
            nextCode("RULE"),
            normalized.ruleName(),
            normalized.scope(),
            normalized.sourceChannel(),
            normalized.priority(),
            normalized.skuId(),
            normalized.merchantId(),
            normalized.storeId(),
            normalized.storeSkuId(),
            normalized.channelFeeRate(),
            normalized.platformFeeRate(),
            normalized.storeOperationRate(),
            normalized.maintenanceFundRate(),
            normalized.channelReferralRate(),
            normalized.investorShareRate(),
            normalized.effectiveAt(),
            normalized.expiredAt()
        );
        return toResponse(rule);
    }

    @Transactional
    public ProfitRuleResponse updateRule(Long id, ProfitRuleRequest request) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("settlement.write");
        var existing = ensureRule(id);
        var normalized = normalizeRule(request, existing);
        ensureFallbackRemains(existing, normalized);
        return toResponse(settlementRepository.updateRule(
            id,
            normalized.ruleName(),
            normalized.scope(),
            normalized.sourceChannel(),
            normalized.priority(),
            normalized.skuId(),
            normalized.merchantId(),
            normalized.storeId(),
            normalized.storeSkuId(),
            normalized.channelFeeRate(),
            normalized.platformFeeRate(),
            normalized.storeOperationRate(),
            normalized.maintenanceFundRate(),
            normalized.channelReferralRate(),
            normalized.investorShareRate(),
            normalized.effectiveAt(),
            normalized.expiredAt()
        ));
    }

    @Transactional
    public ProfitRuleResponse updateRuleStatus(Long id, SettlementRuleStatus status) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("settlement.write");
        var rule = ensureRule(id);
        if (SettlementRuleStatus.DISABLED.equals(status)) {
            ensureCanDeactivate(rule);
        }
        return toResponse(settlementRepository.updateRuleStatus(id, status));
    }

    @Transactional
    public void deleteRule(Long id) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("settlement.write");
        var rule = ensureRule(id);
        ensureCanDeactivate(rule);
        if (settlementRepository.countSnapshotsByRuleId(id) > 0) {
            throw BusinessException.badRequest("该规则已生成分润快照，不能删除，可改为停用");
        }
        settlementRepository.deleteRule(id);
    }

    @Transactional
    public ProfitRuleResponse updateStoreRule(Long storeId, StoreProfitRuleUpdateRequest request) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("settlement.write");
        storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        validateRates(
            request.channelFeeRate(),
            request.platformFeeRate(),
            request.storeOperationRate(),
            request.maintenanceFundRate(),
            request.channelReferralRate(),
            request.investorShareRate()
        );
        initializeStoreProfitRuleInternal(storeId);
        var rule = settlementRepository.findDefaultStoreRule(storeId)
            .orElseThrow(() -> BusinessException.badRequest("门店分润规则初始化失败"));
        return toResponse(settlementRepository.updateStoreRule(
            rule.id(),
            rate(request.channelFeeRate()),
            rate(request.platformFeeRate()),
            rate(request.storeOperationRate()),
            rate(request.maintenanceFundRate()),
            rate(request.channelReferralRate()),
            rate(request.investorShareRate())
        ));
    }

    @Transactional
    public void initializeStoreProfitRule(Long storeId) {
        initializeStoreProfitRuleInternal(storeId);
    }

    @Transactional
    public void deleteStoreProfitRules(Long storeId) {
        settlementRepository.deleteRulesByStoreId(storeId);
    }

    private void initializeStoreProfitRuleInternal(Long storeId) {
        settlementRepository.createDefaultStoreRuleIfMissing(storeId);
        if (settlementRepository.findDefaultStoreRule(storeId).isEmpty()) {
            throw BusinessException.badRequest("平台默认分润规则不存在，无法初始化门店规则");
        }
    }

    @Transactional
    public SettlementSnapshotResponse preview(SettlementPreviewRequest request) {
        authorizationService.requirePermission("settlement.read");
        return toResponse(buildSnapshot(
            SnapshotSourceType.PREVIEW,
            null,
            request.storeSkuId(),
            request.frameAssetId(),
            request.batteryAssetId(),
            request.rentalAmount(),
            request.sourceChannel(),
            null,
            false
        ));
    }

    @Transactional
    public SettlementSnapshotResponse createSnapshot(SnapshotCreateRequest request) {
        authorizationService.requirePermission("settlement.write");
        return createSnapshotInternal(request);
    }

    @Transactional
    public SettlementSnapshotResponse createOrderSnapshot(SnapshotCreateRequest request) {
        return createSnapshotInternal(request);
    }

    private SettlementSnapshotResponse createSnapshotInternal(SnapshotCreateRequest request) {
        var snapshot = buildSnapshot(
            parseSourceType(request.sourceType()),
            request.sourceId(),
            request.storeSkuId(),
            request.frameAssetId(),
            request.batteryAssetId(),
            request.rentalAmount(),
            request.sourceChannel(),
            request.signFeeAmount(),
            true
        );
        return toResponse(snapshot);
    }

    public List<SettlementSnapshotResponse> listSnapshots(String sourceType, Long sourceId) {
        authorizationService.requirePermission("settlement.read");
        return settlementRepository.listSnapshots(sourceType, sourceId).stream().map(this::toResponse).toList();
    }

    public SettlementSnapshotResponse getMerchantOrderSnapshot(Long orderId, Long settlementSnapshotId, Long merchantId, Long storeId) {
        authorizationService.requireStoreAccess(merchantId, storeId);
        if (settlementSnapshotId == null) {
            throw BusinessException.badRequest("订单暂无分润快照");
        }
        var snapshot = settlementRepository.findSnapshot(settlementSnapshotId)
            .orElseThrow(() -> BusinessException.badRequest("分润快照不存在"));
        if (!SnapshotSourceType.ORDER.equals(snapshot.sourceType()) || !orderId.equals(snapshot.sourceId())) {
            throw BusinessException.forbidden("分润快照与订单不匹配");
        }
        if (!merchantId.equals(snapshot.merchantId()) || !storeId.equals(snapshot.storeId())) {
            throw BusinessException.forbidden("不能查看其他门店分润");
        }
        return toResponse(snapshot);
    }

    private SettlementRuleSnapshot buildSnapshot(
        SnapshotSourceType sourceType,
        Long sourceId,
        Long storeSkuId,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal rentalAmount,
        String sourceChannel,
        BigDecimal requestedSignFeeAmount,
        boolean persist
    ) {
        var storeSku = productRepository.findStoreSku(storeSkuId)
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        initializeStoreProfitRuleInternal(storeSku.storeId());
        var normalizedChannel = normalizeSnapshotChannel(sourceChannel);
        var matchedRule = settlementRepository.matchRule(
            storeSku.id(),
            storeSku.skuId(),
            storeSku.storeId(),
            normalizedChannel,
            LocalDateTime.now()
        ).orElseThrow(() -> BusinessException.badRequest("未找到可用分润规则"));
        var rental = money(rentalAmount);
        if (rental.signum() < 0) {
            throw BusinessException.badRequest("结算基数不能小于 0");
        }
        var signFeeAmount = money(requestedSignFeeAmount == null ? storeSku.signFeeAmount() : requestedSignFeeAmount);
        if (signFeeAmount.signum() < 0) {
            throw BusinessException.badRequest("办单费不能小于 0");
        }
        var allocation = ProfitSharingCalculator.calculate(
            rental,
            matchedRule.channelFeeRate(),
            matchedRule.platformFeeRate(),
            matchedRule.storeOperationRate(),
            matchedRule.maintenanceFundRate(),
            matchedRule.channelReferralRate(),
            matchedRule.investorShareRate()
        );
        var snapshot = new SettlementRuleSnapshot(
            null,
            nextCode("SNP"),
            sourceType,
            sourceId,
            SettlementCalculationVersion.PROFIT_V2,
            normalizedChannel,
            storeSku.id(),
            storeSku.skuId(),
            storeSku.merchantId(),
            storeSku.storeId(),
            frameAssetId,
            batteryAssetId,
            matchedRule.id(),
            matchedRule.ruleScope(),
            rental,
            allocation.settlementBaseAmount(),
            signFeeAmount,
            netOrderFee(signFeeAmount),
            allocation.storeOperationRate(),
            allocation.storeOperationAmount(),
            allocation.platformFeeRate(),
            allocation.platformFeeAmount(),
            allocation.investorShareRate(),
            allocation.investorShareAmount(),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            allocation.maintenanceFundAmount(),
            allocation.investorShareAmount(),
            allocation.channelFeeRate(),
            allocation.channelFeeAmount(),
            allocation.platformFeeRate(),
            allocation.platformFeeAmount(),
            allocation.distributableAmount(),
            allocation.storeOperationRate(),
            allocation.storeOperationAmount(),
            allocation.maintenanceFundRate(),
            allocation.maintenanceFundAmount(),
            allocation.channelReferralRate(),
            allocation.channelReferralAmount(),
            allocation.investorShareRate(),
            allocation.investorShareAmount(),
            summary(matchedRule, storeSku, normalizedChannel),
            null
        );
        return persist ? settlementRepository.createSnapshot(snapshot) : snapshot;
    }

    private NormalizedRule normalizeRule(ProfitRuleRequest request, SettlementProfitRule existing) {
        var scope = parseScope(request.ruleScope());
        Long skuId = null;
        Long merchantId = null;
        Long storeId = null;
        Long storeSkuId = null;
        switch (scope) {
            case STORE_SKU -> {
                if (request.storeSkuId() == null) {
                    throw BusinessException.badRequest("门店商品规则必须选择门店商品");
                }
                var storeSku = productRepository.findStoreSku(request.storeSkuId())
                    .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
                skuId = storeSku.skuId();
                merchantId = storeSku.merchantId();
                storeId = storeSku.storeId();
                storeSkuId = storeSku.id();
            }
            case STORE -> {
                if (request.storeId() == null) {
                    throw BusinessException.badRequest("门店规则必须选择门店");
                }
                var store = storeRepository.findById(request.storeId())
                    .orElseThrow(() -> BusinessException.badRequest("门店不存在"));
                merchantId = store.merchantId();
                storeId = store.id();
            }
            case SKU -> {
                if (request.skuId() == null) {
                    throw BusinessException.badRequest("链接规则必须选择商品链接");
                }
                var sku = productRepository.findSku(request.skuId())
                    .orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
                skuId = sku.id();
            }
            case PLATFORM -> {
            }
        }
        validateRates(
            request.channelFeeRate(),
            request.platformFeeRate(),
            request.storeOperationRate(),
            request.maintenanceFundRate(),
            request.channelReferralRate(),
            request.investorShareRate()
        );
        var effectiveAt = request.effectiveAt() == null
            ? existing == null ? LocalDateTime.now() : existing.effectiveAt()
            : request.effectiveAt();
        if (request.expiredAt() != null && !request.expiredAt().isAfter(effectiveAt)) {
            throw BusinessException.badRequest("失效时间必须晚于生效时间");
        }
        return new NormalizedRule(
            request.ruleName().trim(),
            scope,
            normalizeRuleChannel(request.sourceChannel()),
            normalizePriority(request.priority()),
            skuId,
            merchantId,
            storeId,
            storeSkuId,
            rate(request.channelFeeRate()),
            rate(request.platformFeeRate()),
            rate(request.storeOperationRate()),
            rate(request.maintenanceFundRate()),
            rate(request.channelReferralRate()),
            rate(request.investorShareRate()),
            effectiveAt,
            request.expiredAt()
        );
    }

    private void ensureFallbackRemains(SettlementProfitRule existing, NormalizedRule updated) {
        var now = LocalDateTime.now();
        if (!isCurrentFallback(existing, now)) {
            return;
        }
        var remainsCurrentFallback = existing.ruleScope().equals(updated.scope())
            && Objects.equals(existing.storeId(), updated.storeId())
            && updated.sourceChannel() == null
            && !updated.effectiveAt().isAfter(now)
            && (updated.expiredAt() == null || updated.expiredAt().isAfter(now));
        if (!remainsCurrentFallback) {
            ensureAlternativeFallback(existing, now);
        }
    }

    private void ensureCanDeactivate(SettlementProfitRule rule) {
        var now = LocalDateTime.now();
        if (isCurrentFallback(rule, now)) {
            ensureAlternativeFallback(rule, now);
        }
    }

    private void ensureAlternativeFallback(SettlementProfitRule rule, LocalDateTime now) {
        if (!settlementRepository.existsOtherActiveFallbackRule(rule.ruleScope(), rule.storeId(), rule.id(), now)) {
            var target = RuleScope.STORE.equals(rule.ruleScope()) ? "该门店" : "平台";
            throw BusinessException.badRequest(target + "必须保留一条当前生效的全部渠道默认规则");
        }
    }

    private boolean isCurrentFallback(SettlementProfitRule rule, LocalDateTime now) {
        return SettlementRuleStatus.ENABLED.equals(rule.status())
            && (RuleScope.STORE.equals(rule.ruleScope()) || RuleScope.PLATFORM.equals(rule.ruleScope()))
            && rule.sourceChannel() == null
            && !rule.effectiveAt().isAfter(now)
            && (rule.expiredAt() == null || rule.expiredAt().isAfter(now));
    }

    private void validateRates(
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        var deductionRate = rate(channelFeeRate).add(rate(platformFeeRate));
        if (deductionRate.compareTo(ONE) >= 0) {
            throw BusinessException.badRequest("渠道扣点与平台扣点之和必须小于 1");
        }
        var distributionRate = rate(storeOperationRate)
            .add(rate(maintenanceFundRate))
            .add(rate(channelReferralRate))
            .add(rate(investorShareRate));
        if (distributionRate.compareTo(ONE) != 0) {
            throw BusinessException.badRequest("门店运营、门店维修、渠道引流、出资方比例之和必须等于 1");
        }
    }

    private SettlementProfitRule ensureRule(Long id) {
        return settlementRepository.findRule(id).orElseThrow(() -> BusinessException.badRequest("分润规则不存在"));
    }

    private ProfitRuleResponse toResponse(SettlementProfitRule rule) {
        return new ProfitRuleResponse(
            rule.id(),
            rule.ruleCode(),
            rule.ruleName(),
            rule.ruleScope().name(),
            rule.sourceChannel(),
            rule.priority(),
            rule.skuId(),
            rule.merchantId(),
            rule.storeId(),
            rule.storeSkuId(),
            rule.channelFeeRate(),
            rule.platformFeeRate(),
            rule.storeOperationRate(),
            rule.maintenanceFundRate(),
            rule.channelReferralRate(),
            rule.investorShareRate(),
            rule.effectiveAt(),
            rule.expiredAt(),
            rule.status().name()
        );
    }

    private record NormalizedRule(
        String ruleName,
        RuleScope scope,
        String sourceChannel,
        Integer priority,
        Long skuId,
        Long merchantId,
        Long storeId,
        Long storeSkuId,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate,
        LocalDateTime effectiveAt,
        LocalDateTime expiredAt
    ) {
    }

    private SettlementSnapshotResponse toResponse(SettlementRuleSnapshot snapshot) {
        return new SettlementSnapshotResponse(
            snapshot.id(),
            snapshot.snapshotNo(),
            snapshot.sourceType().name(),
            snapshot.sourceId(),
            snapshot.calculationVersion().name(),
            snapshot.sourceChannel(),
            snapshot.storeSkuId(),
            snapshot.skuId(),
            snapshot.merchantId(),
            snapshot.storeId(),
            snapshot.frameAssetId(),
            snapshot.batteryAssetId(),
            snapshot.matchedRuleId(),
            snapshot.matchedRuleScope().name(),
            snapshot.rentalAmount(),
            snapshot.settlementBaseAmount(),
            snapshot.signFeeAmount(),
            snapshot.merchantOrderFeeAmount(),
            snapshot.merchantRentShareRate(),
            snapshot.merchantRentShareAmount(),
            snapshot.platformRentShareRate(),
            snapshot.platformRentShareAmount(),
            snapshot.investorRentShareRate(),
            snapshot.investorGrossShareAmount(),
            snapshot.investorOperationFeeAmount(),
            snapshot.maintenanceFeeAmount(),
            snapshot.investorNetShareAmount(),
            snapshot.channelFeeRate(),
            snapshot.channelFeeAmount(),
            snapshot.platformFeeRate(),
            snapshot.platformFeeAmount(),
            snapshot.distributableAmount(),
            snapshot.storeOperationRate(),
            snapshot.storeOperationAmount(),
            snapshot.maintenanceFundRate(),
            snapshot.maintenanceFundAmount(),
            snapshot.channelReferralRate(),
            snapshot.channelReferralAmount(),
            snapshot.investorShareRate(),
            snapshot.investorShareAmount(),
            snapshot.ruleSummary(),
            snapshot.createdAt()
        );
    }

    private String summary(SettlementProfitRule rule, StoreSku storeSku, String sourceChannel) {
        return "rule=" + rule.ruleCode()
            + ";scope=" + rule.ruleScope()
            + ";channel=" + sourceChannel
            + ";storeSku=" + storeSku.storeSkuCode()
            + ";channelFeeRate=" + rule.channelFeeRate()
            + ";platformFeeRate=" + rule.platformFeeRate()
            + ";storeOperationRate=" + rule.storeOperationRate()
            + ";maintenanceFundRate=" + rule.maintenanceFundRate()
            + ";channelReferralRate=" + rule.channelReferralRate()
            + ";investorShareRate=" + rule.investorShareRate();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal netOrderFee(BigDecimal amount) {
        return money(amount).multiply(BigDecimal.ONE.subtract(ORDER_FEE_SERVICE_RATE)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw BusinessException.badRequest("分成比例必须在 0 到 1 之间");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private int normalizePriority(Integer value) {
        var priority = value == null ? 0 : value;
        if (priority < -10000 || priority > 10000) {
            throw BusinessException.badRequest("规则优先级必须在 -10000 到 10000 之间");
        }
        return priority;
    }

    private String normalizeRuleChannel(String value) {
        return value == null || value.isBlank() ? null : normalizeChannel(value);
    }

    private String normalizeSnapshotChannel(String value) {
        return value == null || value.isBlank() ? "DIRECT" : normalizeChannel(value);
    }

    private String normalizeChannel(String value) {
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{2,32}")) {
            throw BusinessException.badRequest("渠道编码格式不正确");
        }
        return normalized;
    }

    private RuleScope parseScope(String value) {
        try {
            return RuleScope.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的规则范围");
        }
    }

    private RuleScope parseScopeNullable(String value) {
        return value == null || value.isBlank() ? null : parseScope(value);
    }

    private SettlementRuleStatus parseStatusNullable(String value) {
        try {
            return value == null || value.isBlank() ? null : SettlementRuleStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的规则状态");
        }
    }

    private SnapshotSourceType parseSourceType(String value) {
        try {
            return SnapshotSourceType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的快照来源");
        }
    }

    private String nextCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
