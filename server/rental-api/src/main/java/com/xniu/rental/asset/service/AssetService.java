package com.xniu.rental.asset.service;

import com.xniu.rental.asset.dto.AssetBatchImportRequest;
import com.xniu.rental.asset.dto.AssetBatchImportResponse;
import com.xniu.rental.asset.dto.AssetBatchImportRowRequest;
import com.xniu.rental.asset.dto.AssetBatchImportRowResultResponse;
import com.xniu.rental.asset.dto.AssetInvestorChangeRequest;
import com.xniu.rental.asset.dto.AssetInvestorOptionResponse;
import com.xniu.rental.asset.dto.AssetLogResponse;
import com.xniu.rental.asset.dto.AssetMerchantOptionResponse;
import com.xniu.rental.asset.dto.AssetRequest;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.dto.AssetStatusRequest;
import com.xniu.rental.asset.dto.AssetStoreOptionResponse;
import com.xniu.rental.asset.dto.AssetTransferRequest;
import com.xniu.rental.asset.dto.AssetUpdateRequest;
import com.xniu.rental.asset.dto.InvestorAssetRequest;
import com.xniu.rental.asset.dto.InvestorAssetUpdateRequest;
import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.model.AssetTypeDefinition;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.investor.model.InvestorStatus;
import com.xniu.rental.investor.repository.InvestorRepository;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.StoreStatus;
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
    private static final Pattern AUTO_ARRIVAL_BATCH_PATTERN = Pattern.compile("^ARR-\\d{4}-\\d{2}-\\d{2}-I\\d+-B01$");

    private final AssetRepository assetRepository;
    private final AssetTypeService assetTypeService;
    private final InvestorRepository investorRepository;
    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;

    public AssetService(
        AssetRepository assetRepository,
        AssetTypeService assetTypeService,
        InvestorRepository investorRepository,
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService,
        TransactionTemplate transactionTemplate
    ) {
        this.assetRepository = assetRepository;
        this.assetTypeService = assetTypeService;
        this.investorRepository = investorRepository;
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
        this.transactionTemplate = transactionTemplate;
    }

    public List<AssetResponse> listAssets(Long investorId, Long merchantId, Long storeId, String assetType, String status, String keyword) {
        return listAssets(investorId, merchantId, storeId, null, assetType, status, keyword);
    }

    public List<AssetResponse> listAssets(
        Long investorId,
        Long merchantId,
        Long storeId,
        Long assetTypeId,
        String assetType,
        String status,
        String keyword
    ) {
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
            assetTypeId,
            parseAssetType(assetType),
            parseAssetStatus(status),
            keyword
        ).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssetResponse createAsset(AssetRequest request) {
        authorizationService.requirePermission("asset.manage");
        authorizationService.requirePlatformAccount();
        ensureInvestorExists(request.investorId());
        if ((request.currentMerchantId() == null) != (request.currentStoreId() == null)) {
            throw BusinessException.badRequest("资产分配到商户时，必须同时选择商户和门店");
        }
        if (request.currentMerchantId() != null) {
            ensureStoreBelongsToMerchant(request.currentMerchantId(), request.currentStoreId());
        }
        var typeDefinition = assetTypeService.resolveForEntry(request.assetTypeId(), request.assetType());
        ensureSerialAvailable(request.serialNo(), typeDefinition.assetClass(), null, typeDefinition.serialLabel());
        return persistAsset(
            typeDefinition,
            request.serialNo(),
            request.investorId(),
            request.currentMerchantId(),
            request.currentStoreId(),
            request.purchaseAmount(),
            request.residualValue(),
            request.purchasedAt() == null ? LocalDate.now() : request.purchasedAt(),
            request.arrivalBatchNo()
        );
    }

    @Transactional
    public AssetResponse createMerchantAsset(Long storeId, AssetRequest request) {
        authorizationService.requirePermission("asset.manage");
        var store = requireActiveAccessibleStore(storeId);
        ensureInvestorExists(request.investorId());
        var typeDefinition = assetTypeService.resolveForEntry(request.assetTypeId(), request.assetType());
        ensureSerialAvailable(request.serialNo(), typeDefinition.assetClass(), null, typeDefinition.serialLabel());
        return persistAsset(
            typeDefinition,
            request.serialNo(),
            request.investorId(),
            store.merchantId(),
            store.id(),
            request.purchaseAmount(),
            request.residualValue(),
            request.purchasedAt() == null ? LocalDate.now() : request.purchasedAt(),
            request.arrivalBatchNo()
        );
    }

    @Transactional
    public AssetResponse updateAsset(Long assetId, AssetUpdateRequest request) {
        authorizationService.requirePermission("asset.manage");
        authorizationService.requirePlatformAccount();
        return updateAssetInternal(ensureAssetExists(assetId), request);
    }

    @Transactional
    public AssetResponse updateMerchantAsset(Long storeId, Long assetId, AssetUpdateRequest request) {
        authorizationService.requirePermission("asset.manage");
        var store = requireActiveAccessibleStore(storeId);
        var asset = ensureAssetExists(assetId);
        if (!store.merchantId().equals(asset.currentMerchantId()) || !store.id().equals(asset.currentStoreId())) {
            throw BusinessException.forbidden("只能编辑当前门店的资产");
        }
        return updateAssetInternal(asset, request);
    }

    @Transactional
    public void deleteAsset(Long assetId) {
        authorizationService.requirePermission("asset.manage");
        authorizationService.requirePlatformAccount();
        deleteAssetInternal(ensureAssetExists(assetId));
    }

    @Transactional
    public void deleteMerchantAsset(Long storeId, Long assetId) {
        authorizationService.requirePermission("asset.manage");
        var store = requireActiveAccessibleStore(storeId);
        var asset = ensureAssetExists(assetId);
        if (!store.merchantId().equals(asset.currentMerchantId()) || !store.id().equals(asset.currentStoreId())) {
            throw BusinessException.forbidden("只能删除当前门店的资产");
        }
        deleteAssetInternal(asset);
    }

    public AssetBatchImportResponse batchImportAssets(AssetBatchImportRequest request) {
        authorizationService.requirePermission("asset.import");
        authorizationService.requirePlatformAccount();
        return importAssets(request, null);
    }

    public AssetBatchImportResponse batchImportMerchantAssets(Long storeId, AssetBatchImportRequest request) {
        authorizationService.requirePermission("asset.import");
        var store = requireActiveAccessibleStore(storeId);
        return importAssets(request, store);
    }

    @Transactional
    public AssetResponse transferAsset(Long assetId, AssetTransferRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureAssetExists(assetId);
        ensureAssetStoreAccess(asset);
        ensureStoreBelongsToMerchant(request.merchantId(), request.storeId());
        authorizationService.requireStoreAccess(request.merchantId(), request.storeId());
        return transferAssetInternal(asset, request.merchantId(), request.storeId(), request.remark());
    }

    @Transactional
    public AssetResponse transferMerchantAsset(Long sourceStoreId, Long assetId, AssetTransferRequest request) {
        authorizationService.requirePermission("asset.operate");
        var sourceStore = requireActiveAccessibleStore(sourceStoreId);
        var asset = ensureAssetExists(assetId);
        if (!sourceStore.merchantId().equals(asset.currentMerchantId()) || !sourceStore.id().equals(asset.currentStoreId())) {
            throw BusinessException.forbidden("只能调拨当前门店的资产");
        }
        if (!sourceStore.merchantId().equals(request.merchantId())) {
            throw BusinessException.badRequest("门店人员只能在同一商户下调拨资产");
        }
        ensureStoreBelongsToMerchant(sourceStore.merchantId(), request.storeId());
        return transferAssetInternal(asset, sourceStore.merchantId(), request.storeId(), request.remark());
    }

    @Transactional
    public AssetResponse updateAssetStatus(Long assetId, AssetStatusRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureAssetExists(assetId);
        ensureAssetStoreAccess(asset);
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
        ensureAssetStoreAccess(asset);
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
        if (current != null && current.account().investorId() == null && !current.hasPermission("system.admin")) {
            ensureAssetStoreAccess(asset);
        }
        return assetRepository.listLogs(assetId).stream()
            .map(row -> new AssetLogResponse(row.id(), row.assetId(), row.logType(), row.fromValue(), row.toValue(), row.remark(), row.createdAt()))
            .toList();
    }

    public List<AssetResponse> listMerchantStoreAssets(Long storeId) {
        var store = requireAccessibleStore(storeId);
        return assetRepository.list(null, store.merchantId(), store.id(), null, null, null).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<AssetInvestorOptionResponse> listMerchantInvestorOptions() {
        authorizationService.requirePermission("asset.manage");
        return investorRepository.list(null).stream()
            .filter(investor -> investor.status() == InvestorStatus.ENABLED)
            .map(investor -> new AssetInvestorOptionResponse(investor.id(), investor.investorCode(), investor.investorName()))
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

    @Transactional
    public AssetResponse createInvestorAsset(InvestorAssetRequest request) {
        authorizationService.requirePermission("asset.manage");
        var investorId = currentInvestorId();
        if ((request.currentMerchantId() == null) != (request.currentStoreId() == null)) {
            throw BusinessException.badRequest("资产关联商户时，必须同时选择商户和门店");
        }
        if (request.currentMerchantId() != null) {
            ensureStoreBelongsToMerchant(request.currentMerchantId(), request.currentStoreId());
        }
        var typeDefinition = assetTypeService.resolveForEntry(request.assetTypeId(), request.assetType());
        ensureSerialAvailable(request.serialNo(), typeDefinition.assetClass(), null, typeDefinition.serialLabel());
        return persistAsset(
            typeDefinition,
            request.serialNo(),
            investorId,
            request.currentMerchantId(),
            request.currentStoreId(),
            request.purchaseAmount(),
            request.residualValue(),
            request.purchasedAt() == null ? LocalDate.now() : request.purchasedAt(),
            request.arrivalBatchNo()
        );
    }

    @Transactional
    public AssetResponse updateInvestorAsset(Long assetId, InvestorAssetUpdateRequest request) {
        authorizationService.requirePermission("asset.manage");
        var investorId = currentInvestorId();
        var asset = ensureInvestorOwnedAsset(assetId, investorId);
        return updateAssetInternal(asset, new AssetUpdateRequest(
            request.assetTypeId(),
            request.serialNo(),
            investorId,
            request.purchaseAmount(),
            request.residualValue(),
            request.purchasedAt(),
            request.arrivalBatchNo()
        ));
    }

    @Transactional
    public AssetResponse transferInvestorAsset(Long assetId, AssetTransferRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureInvestorOwnedAsset(assetId, currentInvestorId());
        ensureStoreBelongsToMerchant(request.merchantId(), request.storeId());
        return transferAssetInternal(asset, request.merchantId(), request.storeId(), request.remark());
    }

    @Transactional
    public AssetResponse updateInvestorAssetStatus(Long assetId, AssetStatusRequest request) {
        authorizationService.requirePermission("asset.operate");
        var asset = ensureInvestorOwnedAsset(assetId, currentInvestorId());
        if (asset.status() == AssetStatus.RENTING) {
            throw BusinessException.badRequest("租赁中的资产不能手工变更状态");
        }
        if (asset.status() == AssetStatus.SCRAPPED || asset.status() == AssetStatus.SOLD) {
            throw BusinessException.badRequest("已报废或已售出的资产不能继续变更状态");
        }
        var nextStatus = parseAssetStatus(request.status());
        if (nextStatus == AssetStatus.RENTING) {
            throw BusinessException.badRequest("租赁中状态只能由订单履约自动更新");
        }
        var updated = assetRepository.updateStatus(asset.id(), nextStatus, LocalDateTime.now());
        assetRepository.insertStatusLog(
            asset.id(),
            asset.status(),
            nextStatus,
            currentAccountId(),
            request.remark() == null ? "出资方变更资产状态" : request.remark()
        );
        return toResponse(updated);
    }

    @Transactional
    public void deleteInvestorAsset(Long assetId) {
        authorizationService.requirePermission("asset.manage");
        deleteAssetInternal(ensureInvestorOwnedAsset(assetId, currentInvestorId()));
    }

    public List<AssetMerchantOptionResponse> listInvestorMerchantOptions() {
        authorizationService.requirePermission("asset.read");
        currentInvestorId();
        return merchantRepository.list(null).stream()
            .filter(merchant -> merchant.status() == MerchantStatus.ENABLED)
            .map(merchant -> new AssetMerchantOptionResponse(merchant.id(), merchant.merchantCode(), merchant.merchantName()))
            .toList();
    }

    public List<AssetStoreOptionResponse> listInvestorStoreOptions() {
        authorizationService.requirePermission("asset.read");
        currentInvestorId();
        return storeRepository.list(null, null).stream()
            .filter(store -> store.status() == StoreStatus.ENABLED)
            .map(store -> new AssetStoreOptionResponse(store.id(), store.merchantId(), store.storeCode(), store.storeName()))
            .toList();
    }

    private AssetItem ensureAssetExists(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private AssetItem ensureInvestorOwnedAsset(Long assetId, Long investorId) {
        var asset = ensureAssetExists(assetId);
        if (!investorId.equals(asset.investorId())) {
            throw BusinessException.forbidden("只能操作当前出资方名下的资产");
        }
        return asset;
    }

    private Long currentInvestorId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (current.account().investorId() == null) {
            throw BusinessException.forbidden("当前账号不是出资方账号");
        }
        return current.account().investorId();
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
        var typeDefinition = assetTypeService.resolveImportType(requiredText(row.assetType(), "资产类型"));
        if (!typeDefinition.enabled()) {
            throw BusinessException.badRequest("资产类型已停用");
        }
        var serialNo = requiredText(row.serialNo(), typeDefinition.serialLabel());
        ensureSerialAvailable(serialNo, typeDefinition.assetClass(), null, typeDefinition.serialLabel());
        var investorCode = requiredText(row.investorCode(), "出资方编码");
        var investor = investorRepository.findByCode(investorCode)
            .orElseThrow(() -> BusinessException.badRequest("出资方编码不存在"));
        var store = resolveImportStore(row.storeCode(), lockedStore);
        return persistAsset(
            typeDefinition,
            serialNo,
            investor.id(),
            store == null ? null : store.merchantId(),
            store == null ? null : store.id(),
            requiredAmount(row.purchaseAmount(), "采购金额"),
            optionalAmount(row.residualValue(), "报废残值"),
            importDate(row.purchasedAt()),
            row.arrivalBatchNo()
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
        var store = storeRepository.findByCode(normalizedStoreCode)
            .orElseThrow(() -> BusinessException.badRequest("门店编码不存在"));
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        return store;
    }

    private AssetResponse persistAsset(
        AssetTypeDefinition typeDefinition,
        String serialNo,
        Long investorId,
        Long merchantId,
        Long storeId,
        BigDecimal purchaseAmount,
        BigDecimal residualValue,
        LocalDate purchasedAt,
        String arrivalBatchNo
    ) {
        if (purchaseAmount == null || purchaseAmount.signum() < 0) {
            throw BusinessException.badRequest("采购金额不能小于0");
        }
        if (residualValue != null && residualValue.signum() < 0) {
            throw BusinessException.badRequest("报废残值不能小于0");
        }
        var asset = assetRepository.create(
            nextCode(typeDefinition.assetClass()),
            typeDefinition.assetClass(),
            typeDefinition.id(),
            serialNo.trim(),
            resolveArrivalBatchNo(arrivalBatchNo, investorId, purchasedAt),
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

    private AssetResponse updateAssetInternal(AssetItem asset, AssetUpdateRequest request) {
        if (asset.status() == AssetStatus.RENTING) {
            throw BusinessException.badRequest("租赁中的资产不能编辑基础资料");
        }
        ensureInvestorExists(request.investorId());
        var typeDefinition = assetTypeService.requireType(request.assetTypeId());
        if (typeDefinition.assetClass() != asset.assetType()) {
            throw BusinessException.badRequest("资产的业务归类不能修改，可选择同一归类下的其他类型");
        }
        if (!typeDefinition.enabled() && !typeDefinition.id().equals(asset.assetTypeId())) {
            throw BusinessException.badRequest("所选资产类型已停用");
        }
        var serialNo = requiredText(request.serialNo(), typeDefinition.serialLabel());
        ensureSerialAvailable(serialNo, typeDefinition.assetClass(), asset.id(), typeDefinition.serialLabel());
        if (request.purchaseAmount() == null || request.purchaseAmount().signum() < 0) {
            throw BusinessException.badRequest("采购金额不能小于0");
        }
        if (request.residualValue() != null && request.residualValue().signum() < 0) {
            throw BusinessException.badRequest("报废残值不能小于0");
        }
        var investorChanged = !request.investorId().equals(asset.investorId());
        if (investorChanged) {
            assetRepository.closeActiveOwnership(asset.id());
        }
        var purchasedAt = request.purchasedAt() == null ? asset.purchasedAt() : request.purchasedAt();
        var arrivalBatchNo = request.arrivalBatchNo() == null
            ? asset.arrivalBatchNo()
            : normalizeArrivalBatchNo(request.arrivalBatchNo());
        var purchasedAtChanged = request.purchasedAt() != null && !request.purchasedAt().equals(asset.purchasedAt());
        if (arrivalBatchNo == null || ((investorChanged || purchasedAtChanged) && isAutoArrivalBatchNo(arrivalBatchNo))) {
            var batchDate = purchasedAt == null ? asset.createdAt().toLocalDate() : purchasedAt;
            arrivalBatchNo = autoArrivalBatchNo(request.investorId(), batchDate);
        }
        var updated = assetRepository.updateDetails(
            asset.id(),
            typeDefinition.assetClass(),
            typeDefinition.id(),
            serialNo,
            arrivalBatchNo,
            request.investorId(),
            request.purchaseAmount(),
            request.residualValue(),
            purchasedAt
        );
        if (investorChanged) {
            assetRepository.insertOwnership(asset.id(), request.investorId(), "编辑资产资料变更出资方");
        }
        assetRepository.insertStatusLog(asset.id(), asset.status(), asset.status(), currentAccountId(), "编辑资产基础资料");
        return toResponse(updated);
    }

    private void deleteAssetInternal(AssetItem asset) {
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("只有空闲且尚未投入业务的资产可以删除");
        }
        if (assetRepository.countBusinessReferences(asset.id()) > 0) {
            throw BusinessException.badRequest("资产已有订单、履约、维修或结算记录，不能删除");
        }
        assetRepository.deleteAsset(asset.id());
    }

    private MerchantStore requireAccessibleStore(Long storeId) {
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return store;
    }

    private MerchantStore requireActiveAccessibleStore(Long storeId) {
        var store = requireAccessibleStore(storeId);
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        return store;
    }

    private void ensureAssetStoreAccess(AssetItem asset) {
        var current = AuthContext.get();
        if (current != null && current.hasPermission("system.admin")) {
            return;
        }
        if (asset.currentMerchantId() == null || asset.currentStoreId() == null) {
            throw BusinessException.forbidden("资产未分配门店，当前账号不能操作");
        }
        authorizationService.requireStoreAccess(asset.currentMerchantId(), asset.currentStoreId());
    }

    private AssetResponse transferAssetInternal(AssetItem asset, Long targetMerchantId, Long targetStoreId, String remark) {
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("只有空闲资产可以调拨门店");
        }
        if (targetMerchantId.equals(asset.currentMerchantId()) && targetStoreId.equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("资产已在目标门店，无需重复调拨");
        }
        if (assetRepository.transferStoreIfIdle(asset.id(), targetMerchantId, targetStoreId) != 1) {
            throw BusinessException.badRequest("只有空闲资产可以调拨门店");
        }
        var updated = assetRepository.findById(asset.id()).orElseThrow();
        assetRepository.insertLocationHistory(
            asset.id(),
            asset.currentMerchantId(),
            asset.currentStoreId(),
            targetMerchantId,
            targetStoreId,
            remark == null || remark.isBlank() ? "门店调拨" : remark.trim()
        );
        return toResponse(updated);
    }

    private void ensureSerialAvailable(String value, AssetType assetType, Long currentAssetId, String fieldLabel) {
        var serialNo = requiredText(value, fieldLabel);
        assetRepository.findBySerialNoAndType(serialNo, assetType).ifPresent(existing -> {
            if (currentAssetId == null || !currentAssetId.equals(existing.id())) {
                throw BusinessException.badRequest(fieldLabel + "已存在");
            }
        });
    }

    private void ensureInvestorExists(Long investorId) {
        var investor = investorRepository.findById(investorId).orElseThrow(() -> BusinessException.badRequest("出资方不存在"));
        if (investor.status() != InvestorStatus.ENABLED) {
            throw BusinessException.badRequest("出资方已停用");
        }
    }

    private void ensureStoreBelongsToMerchant(Long merchantId, Long storeId) {
        if (merchantId == null || storeId == null) {
            throw BusinessException.badRequest("调拨到门店时必须选择商户和门店");
        }
        var merchant = merchantRepository.findById(merchantId).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        if (merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (!store.merchantId().equals(merchantId)) {
            throw BusinessException.badRequest("门店不属于所选商户");
        }
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
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
            asset.assetTypeId(),
            asset.assetTypeCode(),
            asset.assetTypeName(),
            asset.serialLabel(),
            asset.serialNo(),
            asset.arrivalBatchNo(),
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

    private String normalizeArrivalBatchNo(String value) {
        var normalized = trimToNull(value);
        if (normalized != null && normalized.length() > 64) {
            throw BusinessException.badRequest("到车批次号不能超过64个字符");
        }
        return normalized;
    }

    private String resolveArrivalBatchNo(String value, Long investorId, LocalDate purchasedAt) {
        var normalized = normalizeArrivalBatchNo(value);
        return normalized == null ? autoArrivalBatchNo(investorId, purchasedAt) : normalized;
    }

    private String autoArrivalBatchNo(Long investorId, LocalDate purchasedAt) {
        var batchDate = purchasedAt == null ? LocalDate.now() : purchasedAt;
        return "ARR-" + batchDate + "-I" + investorId + "-B01";
    }

    private boolean isAutoArrivalBatchNo(String value) {
        return value != null && AUTO_ARRIVAL_BATCH_PATTERN.matcher(value).matches();
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
            throw BusinessException.badRequest("不支持的资产业务归类");
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
            case GENERAL -> "AST";
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
