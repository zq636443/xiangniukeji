package com.xniu.rental.asset.service;

import com.xniu.rental.asset.dto.AssetInvestorChangeRequest;
import com.xniu.rental.asset.dto.AssetLogResponse;
import com.xniu.rental.asset.dto.AssetRequest;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.dto.AssetStatusRequest;
import com.xniu.rental.asset.dto.AssetTransferRequest;
import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.repository.InvestorRepository;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final InvestorRepository investorRepository;
    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;

    public AssetService(
        AssetRepository assetRepository,
        InvestorRepository investorRepository,
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService
    ) {
        this.assetRepository = assetRepository;
        this.investorRepository = investorRepository;
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
    }

    public List<AssetResponse> listAssets(Long investorId, Long merchantId, Long storeId, String assetType, String status, String keyword) {
        authorizationService.requirePermission("asset.read");
        var current = AuthContext.get();
        if (current != null && current.account().investorId() != null) {
            investorId = current.account().investorId();
            merchantId = null;
            storeId = null;
        } else if (current != null && current.account().merchantId() != null && !current.hasPermission("system.admin")) {
            merchantId = current.account().merchantId();
            investorId = null;
            if (current.account().storeId() != null) {
                storeId = current.account().storeId();
            }
        }
        return assetRepository.list(
            investorId,
            merchantId,
            storeId,
            parseAssetType(assetType),
            parseAssetStatus(status),
            keyword
        ).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssetResponse createAsset(AssetRequest request) {
        authorizationService.requirePermission("asset.operate");
        ensureInvestorExists(request.investorId());
        if (request.currentStoreId() != null) {
            ensureStoreBelongsToMerchant(request.currentMerchantId(), request.currentStoreId());
        }
        var asset = assetRepository.create(
            nextCode(parseAssetType(request.assetType())),
            parseAssetType(request.assetType()),
            request.serialNo(),
            request.investorId(),
            request.currentMerchantId(),
            request.currentStoreId(),
            request.purchaseAmount(),
            request.maintenanceFeeAmount(),
            request.residualValue(),
            request.purchasedAt() == null ? LocalDate.now() : request.purchasedAt()
        );
        assetRepository.insertOwnership(asset.id(), asset.investorId(), "资产入库");
        assetRepository.insertStatusLog(asset.id(), null, asset.status(), currentAccountId(), "资产入库");
        assetRepository.insertLocationHistory(asset.id(), null, null, asset.currentMerchantId(), asset.currentStoreId(), "资产入库");
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse transferAsset(Long assetId, AssetTransferRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureAssetExists(assetId);
        ensureStoreBelongsToMerchant(request.merchantId(), request.storeId());
        if (asset.status() == AssetStatus.RENTING) {
            throw BusinessException.badRequest("租赁中的资产不能调拨门店");
        }
        var updated = assetRepository.transferStore(assetId, request.merchantId(), request.storeId());
        assetRepository.insertLocationHistory(
            assetId,
            asset.currentMerchantId(),
            asset.currentStoreId(),
            request.merchantId(),
            request.storeId(),
            request.remark() == null ? "门店调拨" : request.remark()
        );
        return toResponse(updated);
    }

    @Transactional
    public AssetResponse updateAssetStatus(Long assetId, AssetStatusRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureAssetExists(assetId);
        var nextStatus = parseAssetStatus(request.status());
        if (asset.status() == AssetStatus.SCRAPPED || asset.status() == AssetStatus.SOLD) {
            throw BusinessException.badRequest("已报废或已售出的资产不能继续变更状态");
        }
        var updated = assetRepository.updateStatus(assetId, nextStatus, LocalDateTime.now());
        assetRepository.insertStatusLog(
            assetId,
            asset.status(),
            nextStatus,
            currentAccountId(),
            request.remark() == null ? "状态变更" : request.remark()
        );
        return toResponse(updated);
    }

    @Transactional
    public AssetResponse changeInvestor(Long assetId, AssetInvestorChangeRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureAssetExists(assetId);
        ensureInvestorExists(request.investorId());
        if (asset.status() == AssetStatus.RENTING) {
            throw BusinessException.badRequest("租赁中的资产不能变更出资方");
        }
        assetRepository.closeActiveOwnership(assetId);
        var updated = assetRepository.changeInvestor(assetId, request.investorId());
        assetRepository.insertOwnership(assetId, request.investorId(), request.remark() == null ? "变更出资方" : request.remark());
        return toResponse(updated);
    }

    public List<AssetLogResponse> listAssetLogs(Long assetId) {
        authorizationService.requirePermission("asset.read");
        var asset = ensureAssetExists(assetId);
        var current = AuthContext.get();
        if (current != null && current.account().investorId() != null && !current.account().investorId().equals(asset.investorId())) {
            throw BusinessException.forbidden("没有该资产权限");
        }
        return assetRepository.listLogs(assetId).stream()
            .map(row -> new AssetLogResponse(row.id(), row.assetId(), row.logType(), row.fromValue(), row.toValue(), row.remark(), row.createdAt()))
            .toList();
    }

    public List<AssetResponse> listMerchantStoreAssets(Long storeId) {
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return assetRepository.list(null, store.merchantId(), store.id(), null, null, null).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<AssetResponse> listInvestorAssets() {
        var current = AuthContext.get();
        if (current == null || current.account().investorId() == null) {
            throw BusinessException.forbidden("当前账号不是出资方账号");
        }
        authorizationService.requirePermission("asset.read");
        return assetRepository.list(current.account().investorId(), null, null, null, null, null).stream()
            .map(this::toResponse)
            .toList();
    }

    private AssetItem ensureAssetExists(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private void ensureInvestorExists(Long investorId) {
        investorRepository.findById(investorId).orElseThrow(() -> BusinessException.badRequest("出资方不存在"));
    }

    private void ensureStoreBelongsToMerchant(Long merchantId, Long storeId) {
        if (merchantId == null || storeId == null) {
            throw BusinessException.badRequest("调拨到门店时必须选择商户和门店");
        }
        merchantRepository.findById(merchantId).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (!store.merchantId().equals(merchantId)) {
            throw BusinessException.badRequest("门店不属于所选商户");
        }
    }

    private AssetResponse toResponse(AssetItem asset) {
        var investorName = investorRepository.findById(asset.investorId()).map(investor -> investor.investorName()).orElse(null);
        var merchantName = asset.currentMerchantId() == null ? null : merchantRepository.findById(asset.currentMerchantId()).map(merchant -> merchant.merchantName()).orElse(null);
        var storeName = asset.currentStoreId() == null ? null : storeRepository.findById(asset.currentStoreId()).map(store -> store.storeName()).orElse(null);
        return new AssetResponse(
            asset.id(),
            asset.assetCode(),
            asset.assetType().name(),
            asset.serialNo(),
            asset.investorId(),
            investorName,
            asset.currentMerchantId(),
            merchantName,
            asset.currentStoreId(),
            storeName,
            asset.status().name(),
            asset.purchaseAmount(),
            asset.maintenanceFeeAmount(),
            asset.residualValue(),
            asset.purchasedAt(),
            asset.scrappedAt(),
            asset.soldAt()
        );
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private AssetType parseAssetType(String assetType) {
        try {
            return assetType == null || assetType.isBlank() ? null : AssetType.valueOf(assetType);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的资产类型");
        }
    }

    private AssetStatus parseAssetStatus(String status) {
        try {
            return status == null || status.isBlank() ? null : AssetStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的资产状态");
        }
    }

    private String nextCode(AssetType assetType) {
        var prefix = assetType == AssetType.BATTERY ? "BAT" : "FRM";
        return "A-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
