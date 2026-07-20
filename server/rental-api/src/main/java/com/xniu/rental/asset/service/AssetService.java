package com.xniu.rental.asset.service;

import com.xniu.rental.asset.dto.AssetBatchImportRequest;
import com.xniu.rental.asset.dto.AssetBatchImportResponse;
import com.xniu.rental.asset.dto.AssetBatchImportRowRequest;
import com.xniu.rental.asset.dto.AssetBatchImportRowResultResponse;
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
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AssetService {

    private static final Pattern IMPORT_DATE_PATTERN = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");

    private final AssetRepository assetRepository;
    private final InvestorRepository investorRepository;
    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;

    public AssetService(
        AssetRepository assetRepository,
        InvestorRepository investorRepository,
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService,
        TransactionTemplate transactionTemplate
    ) {
        this.assetRepository = assetRepository;
        this.investorRepository = investorRepository;
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
        this.transactionTemplate = transactionTemplate;
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
        return persistAsset(
            parseAssetType(request.assetType()),
            request.serialNo(),
            request.investorId(),
            request.currentMerchantId(),
            request.currentStoreId(),
            request.purchaseAmount(),
            request.residualValue(),
            request.purchasedAt() == null ? LocalDate.now() : request.purchasedAt()
        );
    }

    public AssetBatchImportResponse batchImportAssets(AssetBatchImportRequest request) {
        authorizationService.requirePermission("asset.import");
        authorizationService.requirePlatformAccount();
        return importAssets(request, null);
    }

    public AssetBatchImportResponse batchImportMerchantAssets(Long storeId, AssetBatchImportRequest request) {
        authorizationService.requirePermission("asset.import");
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return importAssets(request, store);
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

    private AssetBatchImportResponse importAssets(AssetBatchImportRequest request, MerchantStore lockedStore) {
        var results = new ArrayList<AssetBatchImportRowResultResponse>();
        int successCount = 0;
        for (int index = 0; index < request.rows().size(); index++) {
            var row = request.rows().get(index);
            var lineNo = row.lineNo() == null ? index + 2 : row.lineNo();
            try {
                var created = transactionTemplate.execute(status -> importAsset(row, lockedStore));
                if (created == null) {
                    throw BusinessException.badRequest("导入失败");
                }
                results.add(new AssetBatchImportRowResultResponse(
                    lineNo,
                    true,
                    created.id(),
                    created.assetCode(),
                    created.serialNo(),
                    "导入成功"
                ));
                successCount++;
            } catch (Exception exception) {
                results.add(new AssetBatchImportRowResultResponse(
                    lineNo,
                    false,
                    null,
                    null,
                    trimToNull(row.serialNo()),
                    importFailureMessage(exception)
                ));
            }
        }
        return new AssetBatchImportResponse(
            request.rows().size(),
            successCount,
            request.rows().size() - successCount,
            results
        );
    }

    private AssetResponse importAsset(AssetBatchImportRowRequest row, MerchantStore lockedStore) {
        var assetType = parseAssetType(requiredText(row.assetType(), "资产类型"));
        var serialNo = requiredText(row.serialNo(), assetType == AssetType.BATTERY ? "电池号" : "车架号");
        if (assetRepository.findBySerialNoAndType(serialNo, assetType).isPresent()) {
            throw BusinessException.badRequest(assetType == AssetType.BATTERY ? "该电池号已存在" : "该车架号已存在");
        }
        var investorCode = requiredText(row.investorCode(), "出资方编码");
        var investor = investorRepository.findByCode(investorCode)
            .orElseThrow(() -> BusinessException.badRequest("出资方编码不存在"));
        var store = resolveImportStore(row.storeCode(), lockedStore);
        return persistAsset(
            assetType,
            serialNo,
            investor.id(),
            store == null ? null : store.merchantId(),
            store == null ? null : store.id(),
            requiredAmount(row.purchaseAmount(), "采购金额"),
            optionalAmount(row.residualValue(), "报废残值"),
            importDate(row.purchasedAt())
        );
    }

    private MerchantStore resolveImportStore(String storeCode, MerchantStore lockedStore) {
        var normalizedStoreCode = trimToNull(storeCode);
        if (lockedStore != null) {
            if (normalizedStoreCode != null && !lockedStore.storeCode().equalsIgnoreCase(normalizedStoreCode)) {
                throw BusinessException.badRequest("模板门店与当前门店不一致");
            }
            return lockedStore;
        }
        if (normalizedStoreCode == null) {
            return null;
        }
        return storeRepository.findByCode(normalizedStoreCode)
            .orElseThrow(() -> BusinessException.badRequest("门店编码不存在"));
    }

    private AssetResponse persistAsset(
        AssetType assetType,
        String serialNo,
        Long investorId,
        Long merchantId,
        Long storeId,
        BigDecimal purchaseAmount,
        BigDecimal residualValue,
        LocalDate purchasedAt
    ) {
        var asset = assetRepository.create(
            nextCode(assetType),
            assetType,
            serialNo.trim(),
            investorId,
            merchantId,
            storeId,
            purchaseAmount,
            BigDecimal.ZERO,
            residualValue,
            purchasedAt
        );
        assetRepository.insertOwnership(asset.id(), asset.investorId(), "资产入库");
        assetRepository.insertStatusLog(asset.id(), null, asset.status(), currentAccountId(), "资产入库");
        assetRepository.insertLocationHistory(asset.id(), null, null, asset.currentMerchantId(), asset.currentStoreId(), "资产入库");
        return toResponse(asset);
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
        var rawValue = assetType == null ? null : assetType.trim();
        var normalized = rawValue == null ? null : rawValue.toUpperCase();
        if ("车架".equals(rawValue) || "FRAME".equals(normalized)) {
            return AssetType.VEHICLE_FRAME;
        }
        if ("电池".equals(rawValue)) {
            return AssetType.BATTERY;
        }
        if ("车电一体".equals(rawValue) || "一体车".equals(rawValue) || "INTEGRATED".equals(normalized)) {
            return AssetType.INTEGRATED_VEHICLE;
        }
        try {
            return normalized == null || normalized.isBlank() ? null : AssetType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("资产类型仅支持车架、电池或车电一体");
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
        var prefix = switch (assetType) {
            case VEHICLE_FRAME -> "FRM";
            case BATTERY -> "BAT";
            case INTEGRATED_VEHICLE -> "INT";
        };
        return "A-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String requiredText(String value, String fieldName) {
        var normalized = trimToNull(value);
        if (normalized == null) {
            throw BusinessException.badRequest("请填写" + fieldName);
        }
        return normalized;
    }

    private BigDecimal requiredAmount(String value, String fieldName) {
        var normalized = requiredText(value, fieldName).replace(",", "");
        return parseAmount(normalized, fieldName);
    }

    private BigDecimal optionalAmount(String value, String fieldName) {
        var normalized = trimToNull(value);
        return normalized == null ? null : parseAmount(normalized.replace(",", ""), fieldName);
    }

    private BigDecimal parseAmount(String normalized, String fieldName) {
        try {
            var amount = new BigDecimal(normalized);
            if (amount.signum() < 0) {
                throw BusinessException.badRequest(fieldName + "不能小于0");
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                throw BusinessException.badRequest(fieldName + "最多保留2位小数");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw BusinessException.badRequest(fieldName + "格式不正确");
        }
    }

    private LocalDate importDate(String value) {
        var normalized = trimToNull(value);
        if (normalized == null) {
            return LocalDate.now();
        }
        var matcher = IMPORT_DATE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            throw BusinessException.badRequest("采购日期格式应为YYYY-MM-DD");
        }
        try {
            return LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            );
        } catch (RuntimeException exception) {
            throw BusinessException.badRequest("采购日期格式应为YYYY-MM-DD");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String importFailureMessage(Exception exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank() ? "导入失败" : message;
    }
}
