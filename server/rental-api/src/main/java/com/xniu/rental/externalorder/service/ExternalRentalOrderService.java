package com.xniu.rental.externalorder.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCompleteRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderCreateRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderBatchImportResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderImportRowRequest;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderImportRowResultResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderLogResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderResponse;
import com.xniu.rental.externalorder.dto.ExternalRentalOrderTerminateRequest;
import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalOrderSourcePlatform;
import com.xniu.rental.externalorder.model.ExternalRentalOrder;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.model.ProductPackage;
import com.xniu.rental.product.model.ProductSku;
import com.xniu.rental.product.model.ProductStatus;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.model.StoreSkuPackage;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalRentalOrderService {

    private final ExternalRentalOrderRepository externalRentalOrderRepository;
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final MerchantRepository merchantRepository;
    private final AuthorizationService authorizationService;
    private final TransactionTemplate transactionTemplate;

    public ExternalRentalOrderService(
        ExternalRentalOrderRepository externalRentalOrderRepository,
        ProductRepository productRepository,
        AssetRepository assetRepository,
        OrderRepository orderRepository,
        StoreRepository storeRepository,
        MerchantRepository merchantRepository,
        AuthorizationService authorizationService,
        TransactionTemplate transactionTemplate
    ) {
        this.externalRentalOrderRepository = externalRentalOrderRepository;
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.merchantRepository = merchantRepository;
        this.authorizationService = authorizationService;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ExternalRentalOrderResponse> listOrders(String status, Long storeId, String sourcePlatform, String keyword) {
        authorizationService.requirePermission("order.read");
        return externalRentalOrderRepository.list(
                parseStatusNullable(status),
                null,
                storeId,
                parseSourceNullable(sourcePlatform),
                keyword
            ).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<ExternalRentalOrderResponse> listMerchantOrders(Long storeId, String status, String sourcePlatform, String keyword) {
        authorizationService.requirePermission("order.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var store = ensureStore(storeId);
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return externalRentalOrderRepository.list(
                parseStatusNullable(status),
                store.merchantId(),
                storeId,
                parseSourceNullable(sourcePlatform),
                keyword
            ).stream()
            .map(this::toResponse)
            .toList();
    }

    public ExternalRentalOrderResponse getOrder(Long id) {
        authorizationService.requirePermission("order.read");
        var view = ensureView(id);
        authorizationService.requireStoreAccess(view.order().merchantId(), view.order().storeId());
        return toResponse(view);
    }

    @Transactional
    public ExternalRentalOrderResponse createOrder(ExternalRentalOrderCreateRequest request) {
        authorizationService.requirePermission("order.operate");
        return createOrderInternal(request);
    }

    public ExternalRentalOrderBatchImportResponse batchImport(ExternalRentalOrderBatchImportRequest request) {
        authorizationService.requirePermission("order.operate");
        var results = new ArrayList<ExternalRentalOrderImportRowResultResponse>();
        int successCount = 0;
        for (var row : request.rows()) {
            try {
                var created = transactionTemplate.execute(status -> createOrderInternal(toCreateRequest(row)));
                if (created == null) {
                    throw BusinessException.badRequest("导入失败");
                }
                results.add(new ExternalRentalOrderImportRowResultResponse(
                    row.lineNo(),
                    true,
                    created.id(),
                    created.recordNo(),
                    "导入成功"
                ));
                successCount++;
            } catch (Exception exception) {
                results.add(new ExternalRentalOrderImportRowResultResponse(
                    row.lineNo(),
                    false,
                    null,
                    null,
                    exception.getMessage()
                ));
            }
        }
        return new ExternalRentalOrderBatchImportResponse(
            request.rows().size(),
            successCount,
            request.rows().size() - successCount,
            results
        );
    }

    private ExternalRentalOrderResponse createOrderInternal(ExternalRentalOrderCreateRequest request) {
        var storeSku = ensureStoreSku(request.storeSkuId());
        authorizationService.requireStoreAccess(storeSku.merchantId(), storeSku.storeId());
        var sku = ensureSku(storeSku.skuId());
        var packageTemplate = ensureStoreSkuPackage(storeSku, request.packageId());
        var packagePricing = storeSkuPackageAmount(storeSku.id(), request.packageId());
        validateRequestAssets(request, sku);
        var expectedReturnAt = request.expectedReturnAt() == null ? calculateExpectedReturnAt(request.rentStartedAt(), packageTemplate) : request.expectedReturnAt();
        if (expectedReturnAt != null && expectedReturnAt.isBefore(request.rentStartedAt())) {
            throw BusinessException.badRequest("预计归还时间不能早于起租时间");
        }
        var frameAsset = request.frameAssetId() == null ? null : occupyAsset(request.frameAssetId(), AssetType.VEHICLE_FRAME, storeSku, "外部补录订单绑定主资产");
        var batteryAsset = request.batteryAssetId() == null ? null : occupyAsset(request.batteryAssetId(), AssetType.BATTERY, storeSku, "外部补录订单绑定电池");
        var externalRentalAmount = normalizeMoney(request.externalRentalAmount(), packagePricing.rentalAmount());
        var verificationAmount = normalizeVerificationAmount(request.verificationAmount(), externalRentalAmount);
        var order = externalRentalOrderRepository.create(new ExternalRentalOrderRepository.CreateRow(
            nextRecordNo(),
            parseSource(request.sourcePlatform()),
            blankToNull(request.externalOrderNo()),
            storeSku.merchantId(),
            storeSku.storeId(),
            storeSku.id(),
            storeSku.skuId(),
            packageTemplate.id(),
            request.customerName().trim(),
            request.customerPhone().trim(),
            frameAsset == null ? null : frameAsset.id(),
            batteryAsset == null ? null : batteryAsset.id(),
            ExternalRentalOrderStatus.ACTIVE,
            externalRentalAmount,
            verificationAmount,
            normalizeMoney(request.signFeeAmount(), storeSku.signFeeAmount()),
            normalizeMoney(request.depositAmount(), packagePricing.depositAmount()),
            packageTemplate.leaseUnit().name(),
            packageTemplate.leaseValue(),
            packageTemplate.totalPeriods(),
            request.rentStartedAt(),
            expectedReturnAt,
            blankToNull(request.remark()),
            currentAccountId(),
            currentAccountId()
        ));
        externalRentalOrderRepository.addLog(
            order.id(),
            null,
            ExternalRentalOrderStatus.ACTIVE,
            ExternalOrderOperationType.CREATE,
            currentAccountId(),
            defaultRemark(request.remark(), "创建外部补录订单")
        );
        return toResponse(ensureView(order.id()));
    }

    @Transactional
    public ExternalRentalOrderResponse complete(Long id, ExternalRentalOrderCompleteRequest request) {
        authorizationService.requirePermission("order.operate");
        request = request == null ? new ExternalRentalOrderCompleteRequest(null, null, null, null) : request;
        var order = ensureOrder(id);
        ensureActive(order);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        var returnStore = resolveReturnStore(order, request.returnStoreId());
        if (order.frameAssetId() != null) {
            returnAssetToStore(order.frameAssetId(), parseReturnStatus(request.frameResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单归还主资产"));
        }
        if (order.batteryAssetId() != null) {
            returnAssetToStore(order.batteryAssetId(), parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单归还电池"));
        }
        var finished = externalRentalOrderRepository.finish(
            id,
            ExternalRentalOrderStatus.COMPLETED,
            returnStore.id(),
            LocalDateTime.now(),
            null,
            blankToNull(request.remark()) == null ? order.remark() : request.remark().trim(),
            currentAccountId()
        );
        externalRentalOrderRepository.addLog(
            id,
            order.orderStatus(),
            ExternalRentalOrderStatus.COMPLETED,
            ExternalOrderOperationType.COMPLETE,
            currentAccountId(),
            defaultRemark(request.remark(), "外部补录订单正常完结")
        );
        return toResponse(ensureView(finished.id()));
    }

    @Transactional
    public ExternalRentalOrderResponse terminate(Long id, ExternalRentalOrderTerminateRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        ensureActive(order);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        var returnStore = resolveReturnStore(order, request.returnStoreId());
        if (order.frameAssetId() != null) {
            returnAssetToStore(order.frameAssetId(), parseReturnStatus(request.frameResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单提前终止归还主资产"));
        }
        if (order.batteryAssetId() != null) {
            returnAssetToStore(order.batteryAssetId(), parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单提前终止归还电池"));
        }
        var finished = externalRentalOrderRepository.finish(
            id,
            ExternalRentalOrderStatus.TERMINATED,
            returnStore.id(),
            LocalDateTime.now(),
            request.terminationReason().trim(),
            blankToNull(request.remark()) == null ? order.remark() : request.remark().trim(),
            currentAccountId()
        );
        externalRentalOrderRepository.addLog(
            id,
            order.orderStatus(),
            ExternalRentalOrderStatus.TERMINATED,
            ExternalOrderOperationType.TERMINATE,
            currentAccountId(),
            defaultRemark(request.remark(), "外部补录订单提前终止: " + request.terminationReason().trim())
        );
        return toResponse(ensureView(finished.id()));
    }

    private void validateRequestAssets(ExternalRentalOrderCreateRequest request, ProductSku sku) {
        var frameAsset = request.frameAssetId() == null ? null : ensureAsset(request.frameAssetId());
        if (frameAsset != null && !frameAsset.assetType().canBindAs(AssetType.VEHICLE_FRAME)) {
            throw BusinessException.badRequest("请选择主资产或自定义资产");
        }
        var integratedVehicle = frameAsset != null && frameAsset.assetType().isIntegratedVehicle();
        if (Boolean.TRUE.equals(sku.needFrameAsset()) && request.frameAssetId() == null) {
            throw BusinessException.badRequest("当前商品链接必须绑定主资产");
        }
        if (Boolean.TRUE.equals(sku.needBatteryAsset()) && request.batteryAssetId() == null && !integratedVehicle) {
            throw BusinessException.badRequest("当前商品链接必须绑定电池资产");
        }
        if (!Boolean.TRUE.equals(sku.needFrameAsset()) && request.frameAssetId() != null) {
            throw BusinessException.badRequest("当前商品链接不需要绑定主资产");
        }
        if (!Boolean.TRUE.equals(sku.needBatteryAsset()) && request.batteryAssetId() != null) {
            throw BusinessException.badRequest("当前商品链接不需要绑定电池资产");
        }
        if (integratedVehicle && request.batteryAssetId() != null) {
            throw BusinessException.badRequest("车电一体资产只需绑定车架号，无需再选择电池资产");
        }
        if (request.frameAssetId() != null && request.frameAssetId().equals(request.batteryAssetId())) {
            throw BusinessException.badRequest("车架和电池不能选择同一条资产");
        }
    }

    private AssetItem occupyAsset(Long assetId, AssetType expectedType, StoreSku storeSku, String remark) {
        var asset = ensureAsset(assetId);
        if (!asset.assetType().canBindAs(expectedType)) {
            throw BusinessException.badRequest(expectedType == AssetType.VEHICLE_FRAME ? "请选择主资产或自定义资产" : "请选择电池资产");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("所选资产不是空闲状态");
        }
        if (!storeSku.merchantId().equals(asset.currentMerchantId()) || !storeSku.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选资产不属于当前下单门店");
        }
        if (externalRentalOrderRepository.findActiveByAsset(assetId).isPresent()) {
            throw BusinessException.badRequest("所选资产已被其他补录订单占用");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("所选资产已被正式订单占用");
        }
        assetRepository.updateStatus(assetId, AssetStatus.RENTING, LocalDateTime.now());
        assetRepository.insertStatusLog(assetId, asset.status(), AssetStatus.RENTING, currentAccountId(), remark);
        return assetRepository.findById(assetId).orElseThrow();
    }

    private void returnAssetToStore(Long assetId, AssetStatus nextStatus, Long returnMerchantId, Long returnStoreId, String remark) {
        var asset = ensureAsset(assetId);
        if (asset.status() != nextStatus) {
            assetRepository.updateStatus(assetId, nextStatus, LocalDateTime.now());
            assetRepository.insertStatusLog(assetId, asset.status(), nextStatus, currentAccountId(), remark);
        }
        if (!returnMerchantId.equals(asset.currentMerchantId()) || !returnStoreId.equals(asset.currentStoreId())) {
            assetRepository.transferStore(assetId, returnMerchantId, returnStoreId);
            assetRepository.insertLocationHistory(
                assetId,
                asset.currentMerchantId(),
                asset.currentStoreId(),
                returnMerchantId,
                returnStoreId,
                "外部补录订单结束自动调拨"
            );
        }
    }

    private MerchantStore resolveReturnStore(ExternalRentalOrder order, Long returnStoreId) {
        var resolvedStoreId = returnStoreId == null ? order.storeId() : returnStoreId;
        var store = ensureStore(resolvedStoreId);
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("归还门店已停用");
        }
        if (!store.merchantId().equals(order.merchantId())) {
            throw BusinessException.badRequest("暂不支持跨商户归还");
        }
        if (!store.id().equals(order.storeId())) {
            var sku = productRepository.findSku(order.skuId()).orElseThrow(() -> BusinessException.badRequest("订单商品链接不存在"));
            if (!Boolean.TRUE.equals(sku.supportCrossStoreReturn())) {
                throw BusinessException.badRequest("当前商品链接不支持跨门店归还");
            }
        }
        return store;
    }

    private AssetStatus parseReturnStatus(String value, AssetStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var status = AssetStatus.valueOf(value);
            if (status != AssetStatus.IDLE && status != AssetStatus.PENDING_REPAIR && status != AssetStatus.EXCEPTION) {
                throw BusinessException.badRequest("归还资产状态只能为空闲、待检修或异常");
            }
            return status;
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的资产归还状态");
        }
    }

    private ExternalRentalOrderResponse toResponse(ExternalRentalOrderRepository.ExternalRentalOrderView view) {
        var order = view.order();
        return new ExternalRentalOrderResponse(
            order.id(),
            order.recordNo(),
            order.sourcePlatform().name(),
            order.externalOrderNo(),
            order.merchantId(),
            view.merchantName(),
            order.storeId(),
            view.storeName(),
            order.storeSkuId(),
            view.storeSkuDisplayName(),
            order.skuId(),
            view.skuName(),
            order.packageId(),
            view.packageName(),
            order.customerName(),
            order.customerPhone(),
            order.frameAssetId(),
            view.frameAssetSerialNo(),
            order.batteryAssetId(),
            view.batteryAssetSerialNo(),
            order.orderStatus().name(),
            order.externalRentalAmount(),
            order.verificationAmount(),
            order.signFeeAmount(),
            order.depositAmount(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.rentStartedAt(),
            order.expectedReturnAt(),
            order.finishedAt(),
            order.returnStoreId(),
            view.returnStoreName(),
            order.terminationReason(),
            order.remark(),
            order.createdByAccountId(),
            order.updatedByAccountId(),
            order.createdAt(),
            order.updatedAt(),
            externalRentalOrderRepository.listLogs(order.id()).stream().map(this::toLogResponse).toList()
        );
    }

    private ExternalRentalOrderResponse toResponse(ExternalRentalOrder order) {
        return toResponse(ensureView(order.id()));
    }

    public List<com.xniu.rental.asset.dto.AssetRentalRecordResponse> listAssetRentalRecords(Long assetId) {
        return externalRentalOrderRepository.listByAsset(assetId).stream()
            .map(this::toAssetRentalRecord)
            .toList();
    }

    private com.xniu.rental.asset.dto.AssetRentalRecordResponse toAssetRentalRecord(ExternalRentalOrderRepository.ExternalRentalOrderView view) {
        var order = view.order();
        return new com.xniu.rental.asset.dto.AssetRentalRecordResponse(
            "EXTERNAL",
            order.id(),
            order.recordNo(),
            order.sourcePlatform().name(),
            order.externalOrderNo(),
            null,
            order.storeId(),
            order.customerName(),
            order.customerPhone(),
            order.orderStatus().name(),
            order.frameAssetId(),
            order.batteryAssetId(),
            order.externalRentalAmount(),
            order.signFeeAmount(),
            BigDecimal.ZERO,
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.rentStartedAt(),
            order.expectedReturnAt(),
            order.finishedAt(),
            order.createdAt(),
            List.of()
        );
    }

    private ExternalRentalOrderLogResponse toLogResponse(com.xniu.rental.externalorder.model.ExternalRentalOrderLog log) {
        return new ExternalRentalOrderLogResponse(
            log.id(),
            log.externalOrderId(),
            log.fromStatus() == null ? null : log.fromStatus().name(),
            log.toStatus().name(),
            log.operationType().name(),
            log.operatorAccountId(),
            log.remark(),
            log.createdAt()
        );
    }

    private ExternalRentalOrder ensureOrder(Long id) {
        return externalRentalOrderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
    }

    private ExternalRentalOrderRepository.ExternalRentalOrderView ensureView(Long id) {
        return externalRentalOrderRepository.findViewById(id).orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
    }

    private void ensureActive(ExternalRentalOrder order) {
        if (order.orderStatus() != ExternalRentalOrderStatus.ACTIVE) {
            throw BusinessException.badRequest("只有进行中的补录订单才可以操作结束");
        }
    }

    private StoreSku ensureStoreSku(Long id) {
        var storeSku = productRepository.findStoreSku(id).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var merchant = merchantRepository.findById(storeSku.merchantId()).orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        if (merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        var store = storeRepository.findById(storeSku.storeId()).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        if (!store.merchantId().equals(storeSku.merchantId())) {
            throw BusinessException.badRequest("门店商品商户关系异常");
        }
        return storeSku;
    }

    private ProductSku ensureSku(Long id) {
        var sku = productRepository.findSku(id).orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        if (sku.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("商品链接已停用");
        }
        return sku;
    }

    private ProductPackage ensureStoreSkuPackage(StoreSku storeSku, Long packageId) {
        var configured = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(packageId))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("当前门店商品未配置该 SKU"));
        if (configured.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("门店 SKU 已停用");
        }
        var packageTemplate = productRepository.findPackage(configured.packageId()).orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        if (packageTemplate.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("SKU 已停用");
        }
        if (!packageTemplate.skuId().equals(storeSku.skuId())) {
            throw BusinessException.badRequest("SKU 与门店商品不匹配");
        }
        return packageTemplate;
    }

    private StoreSkuPackage storeSkuPackageAmount(Long storeSkuId, Long packageId) {
        return productRepository.listStoreSkuPackages(storeSkuId).stream()
            .filter(item -> item.packageId().equals(packageId) && item.status() == ProductStatus.ENABLED)
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("当前门店商品未配置该 SKU 价格"));
    }

    private ExternalRentalOrderCreateRequest toCreateRequest(ExternalRentalOrderImportRowRequest row) {
        return new ExternalRentalOrderCreateRequest(
            row.sourcePlatform(),
            row.externalOrderNo(),
            row.storeSkuId(),
            row.packageId(),
            row.customerName(),
            row.customerPhone(),
            row.rentStartedAt(),
            row.expectedReturnAt(),
            row.frameAssetId(),
            row.batteryAssetId(),
            row.externalRentalAmount(),
            row.verificationAmount(),
            row.signFeeAmount(),
            row.depositAmount(),
            row.remark()
        );
    }

    private MerchantStore ensureStore(Long id) {
        return storeRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
    }

    private AssetItem ensureAsset(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private ExternalRentalOrderStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ExternalRentalOrderStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的补录订单状态");
        }
    }

    private ExternalOrderSourcePlatform parseSourceNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseSource(value);
    }

    private ExternalOrderSourcePlatform parseSource(String value) {
        try {
            return ExternalOrderSourcePlatform.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的订单来源平台");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value, BigDecimal fallback) {
        var amount = value == null ? fallback : value;
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (amount.signum() < 0) {
            throw BusinessException.badRequest("金额不能小于 0");
        }
        return amount;
    }

    private BigDecimal normalizeVerificationAmount(BigDecimal value, BigDecimal fallback) {
        var amount = normalizeMoney(value, fallback);
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private LocalDateTime calculateExpectedReturnAt(LocalDateTime startedAt, ProductPackage productPackage) {
        if (startedAt == null) {
            return null;
        }
        if ("MONTH".equals(productPackage.leaseUnit().name())) {
            return startedAt.plusMonths(productPackage.leaseValue());
        }
        return startedAt.plusDays(productPackage.leaseValue());
    }

    private String defaultRemark(String remark, String fallback) {
        return remark == null || remark.isBlank() ? fallback : remark.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private String nextRecordNo() {
        return "EORD-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
