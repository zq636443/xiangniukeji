package com.xniu.rental.settlement.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.model.Investor;
import com.xniu.rental.investor.repository.InvestorRepository;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.ProfitRuleRequest;
import com.xniu.rental.settlement.dto.ProfitRuleResponse;
import com.xniu.rental.settlement.dto.SettlementPreviewRequest;
import com.xniu.rental.settlement.dto.SettlementSnapshotResponse;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.model.RuleScope;
import com.xniu.rental.settlement.model.SettlementProfitRule;
import com.xniu.rental.settlement.model.SettlementRuleSnapshot;
import com.xniu.rental.settlement.model.SettlementRuleStatus;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private static final BigDecimal ONE = new BigDecimal("1.0000");

    private final SettlementRepository settlementRepository;
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final InvestorRepository investorRepository;
    private final AuthorizationService authorizationService;

    public SettlementService(
        SettlementRepository settlementRepository,
        ProductRepository productRepository,
        AssetRepository assetRepository,
        InvestorRepository investorRepository,
        AuthorizationService authorizationService
    ) {
        this.settlementRepository = settlementRepository;
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
        this.investorRepository = investorRepository;
        this.authorizationService = authorizationService;
    }

    public List<ProfitRuleResponse> listRules(String scope, String status) {
        authorizationService.requirePermission("settlement.read");
        return settlementRepository.listRules(parseScopeNullable(scope), parseStatusNullable(status)).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ProfitRuleResponse createRule(ProfitRuleRequest request) {
        authorizationService.requirePermission("settlement.write");
        validateRule(request);
        var rule = settlementRepository.createRule(
            nextCode("RULE"),
            request.ruleName(),
            parseScope(request.ruleScope()),
            request.skuId(),
            request.merchantId(),
            request.storeId(),
            request.storeSkuId(),
            money(request.merchantOrderFeeAmount()),
            rate(request.merchantRentShareRate()),
            rate(request.platformRentShareRate()),
            rate(request.investorRentShareRate()),
            request.effectiveAt() == null ? LocalDateTime.now() : request.effectiveAt(),
            request.expiredAt()
        );
        return toResponse(rule);
    }

    @Transactional
    public ProfitRuleResponse updateRuleStatus(Long id, SettlementRuleStatus status) {
        authorizationService.requirePermission("settlement.write");
        ensureRule(id);
        return toResponse(settlementRepository.updateRuleStatus(id, status));
    }

    public SettlementSnapshotResponse preview(SettlementPreviewRequest request) {
        authorizationService.requirePermission("settlement.read");
        return toResponse(buildSnapshot(
            SnapshotSourceType.PREVIEW,
            null,
            request.storeSkuId(),
            request.frameAssetId(),
            request.batteryAssetId(),
            request.rentalAmount(),
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
        boolean persist
    ) {
        var storeSku = productRepository.findStoreSku(storeSkuId)
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        var matchedRule = settlementRepository.matchRule(
            storeSku.id(),
            storeSku.skuId(),
            storeSku.merchantId(),
            storeSku.storeId(),
            LocalDateTime.now()
        ).orElseThrow(() -> BusinessException.badRequest("未找到可用分润规则"));
        var rental = money(rentalAmount);
        var merchantRentShare = multiply(rental, matchedRule.merchantRentShareRate());
        var platformRentShare = multiply(rental, matchedRule.platformRentShareRate());
        var investorGrossShare = multiply(rental, matchedRule.investorRentShareRate());
        var assets = Stream.of(findAsset(frameAssetId), findAsset(batteryAssetId))
            .filter(Objects::nonNull)
            .toList();
        var maintenanceFee = assets.stream()
            .map(AssetItem::maintenanceFeeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var investorOperationFee = assets.stream()
            .map(asset -> multiply(
                investorGrossShare,
                investorRepository.findById(asset.investorId()).map(Investor::operationFeeRate).orElse(BigDecimal.ZERO)
            ))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (assets.size() > 1) {
            investorOperationFee = investorOperationFee.divide(new BigDecimal(assets.size()), 2, RoundingMode.HALF_UP);
        }
        var investorNetShare = investorGrossShare.subtract(investorOperationFee).subtract(maintenanceFee);
        if (investorNetShare.signum() < 0) {
            investorNetShare = BigDecimal.ZERO;
        }
        var snapshot = new SettlementRuleSnapshot(
            null,
            nextCode("SNP"),
            sourceType,
            sourceId,
            storeSku.id(),
            storeSku.skuId(),
            storeSku.merchantId(),
            storeSku.storeId(),
            frameAssetId,
            batteryAssetId,
            matchedRule.id(),
            matchedRule.ruleScope(),
            rental,
            storeSku.signFeeAmount(),
            matchedRule.merchantOrderFeeAmount(),
            matchedRule.merchantRentShareRate(),
            merchantRentShare,
            matchedRule.platformRentShareRate(),
            platformRentShare,
            matchedRule.investorRentShareRate(),
            investorGrossShare,
            investorOperationFee,
            maintenanceFee,
            investorNetShare,
            summary(matchedRule, storeSku),
            null
        );
        return persist ? settlementRepository.createSnapshot(snapshot) : snapshot;
    }

    private AssetItem findAsset(Long assetId) {
        if (assetId == null) {
            return null;
        }
        return assetRepository.findById(assetId).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private void validateRule(ProfitRuleRequest request) {
        var scope = parseScope(request.ruleScope());
        switch (scope) {
            case STORE_SKU -> {
                if (request.storeSkuId() == null) {
                    throw BusinessException.badRequest("门店 SKU 规则必须选择门店商品");
                }
            }
            case STORE -> {
                if (request.storeId() == null) {
                    throw BusinessException.badRequest("门店规则必须选择门店");
                }
            }
            case SKU -> {
                if (request.skuId() == null) {
                    throw BusinessException.badRequest("SKU 规则必须选择 SKU");
                }
            }
            case PLATFORM -> {
            }
        }
        var totalRate = rate(request.merchantRentShareRate())
            .add(rate(request.platformRentShareRate()))
            .add(rate(request.investorRentShareRate()));
        if (totalRate.compareTo(ONE) != 0) {
            throw BusinessException.badRequest("门店、平台、出资方租金分成比例之和必须等于 1");
        }
        if (request.expiredAt() != null && request.effectiveAt() != null && !request.expiredAt().isAfter(request.effectiveAt())) {
            throw BusinessException.badRequest("失效时间必须晚于生效时间");
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
            rule.skuId(),
            rule.merchantId(),
            rule.storeId(),
            rule.storeSkuId(),
            rule.merchantOrderFeeAmount(),
            rule.merchantRentShareRate(),
            rule.platformRentShareRate(),
            rule.investorRentShareRate(),
            rule.effectiveAt(),
            rule.expiredAt(),
            rule.status().name()
        );
    }

    private SettlementSnapshotResponse toResponse(SettlementRuleSnapshot snapshot) {
        return new SettlementSnapshotResponse(
            snapshot.id(),
            snapshot.snapshotNo(),
            snapshot.sourceType().name(),
            snapshot.sourceId(),
            snapshot.storeSkuId(),
            snapshot.skuId(),
            snapshot.merchantId(),
            snapshot.storeId(),
            snapshot.frameAssetId(),
            snapshot.batteryAssetId(),
            snapshot.matchedRuleId(),
            snapshot.matchedRuleScope().name(),
            snapshot.rentalAmount(),
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
            snapshot.ruleSummary(),
            snapshot.createdAt()
        );
    }

    private String summary(SettlementProfitRule rule, StoreSku storeSku) {
        return "rule=" + rule.ruleCode()
            + ";scope=" + rule.ruleScope()
            + ";storeSku=" + storeSku.storeSkuCode()
            + ";merchantRate=" + rule.merchantRentShareRate()
            + ";platformRate=" + rule.platformRentShareRate()
            + ";investorRate=" + rule.investorRentShareRate();
    }

    private BigDecimal multiply(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw BusinessException.badRequest("分成比例必须在 0 到 1 之间");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
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
