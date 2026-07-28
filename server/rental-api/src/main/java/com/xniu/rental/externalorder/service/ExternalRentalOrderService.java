package com.xniu.rental.externalorder.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
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
import com.xniu.rental.externalorder.dto.ExternalRentalOrderUpdateRequest;
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
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.model.SnapshotSourceType;
import com.xniu.rental.settlement.repository.SettlementIncomeRepository;
import com.xniu.rental.settlement.repository.SettlementRepository;
import com.xniu.rental.settlement.repository.SettlementStatementRepository;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalRentalOrderService {

    private static final Logger log = LoggerFactory.getLogger(ExternalRentalOrderService.class);

    private final ExternalRentalOrderRepository externalRentalOrderRepository;
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final MerchantRepository merchantRepository;
    private final AuthorizationService authorizationService;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final SettlementIncomeRepository settlementIncomeRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementStatementRepository settlementStatementRepository;
    private final TransactionTemplate transactionTemplate;

    public ExternalRentalOrderService(
        ExternalRentalOrderRepository externalRentalOrderRepository,
        ProductRepository productRepository,
        AssetRepository assetRepository,
        OrderRepository orderRepository,
        StoreRepository storeRepository,
        MerchantRepository merchantRepository,
        AuthorizationService authorizationService,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        SettlementIncomeRepository settlementIncomeRepository,
        SettlementRepository settlementRepository,
        SettlementStatementRepository settlementStatementRepository,
        TransactionTemplate transactionTemplate
    ) {
        this.externalRentalOrderRepository = externalRentalOrderRepository;
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.merchantRepository = merchantRepository;
        this.authorizationService = authorizationService;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.settlementIncomeRepository = settlementIncomeRepository;
        this.settlementRepository = settlementRepository;
        this.settlementStatementRepository = settlementStatementRepository;
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

    @Transactional
    public ExternalRentalOrderResponse updateOrder(Long id, ExternalRentalOrderUpdateRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = externalRentalOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());

        var storeSku = ensureStoreSku(request.storeSkuId());
        authorizationService.requireStoreAccess(storeSku.merchantId(), storeSku.storeId());
        var sku = ensureSku(storeSku.skuId());
        var packageTemplate = ensureStoreSkuPackage(storeSku, request.packageId());
        var packagePricing = storeSkuPackageAmount(storeSku.id(), request.packageId());
        var leaseMultiplier = request.leaseMultiplier() == null
            ? normalizeLeaseMultiplier(order.leaseMultiplier())
            : normalizeLeaseMultiplier(request.leaseMultiplier());
        validateRequestAssets(request.frameAssetId(), request.batteryAssetId(), sku);
        var expectedReturnAt = request.expectedReturnAt() == null
            ? calculateExpectedReturnAt(request.rentStartedAt(), packageTemplate, leaseMultiplier)
            : request.expectedReturnAt();
        if (expectedReturnAt != null && expectedReturnAt.isBefore(request.rentStartedAt())) {
            throw BusinessException.badRequest("预计归还时间不能早于起租时间");
        }

        if (order.orderStatus() == ExternalRentalOrderStatus.ACTIVE) {
            validateEditableAsset(request.frameAssetId(), order.frameAssetId(), storeSku, order);
            validateEditableAsset(request.batteryAssetId(), order.batteryAssetId(), storeSku, order);
            releaseEditedAsset(order.frameAssetId(), request.frameAssetId(), "补录订单编辑释放原主资产");
            releaseEditedAsset(order.batteryAssetId(), request.batteryAssetId(), "补录订单编辑释放原第二资产");
            occupyEditedAsset(request.frameAssetId(), order.frameAssetId(), "补录订单编辑绑定主资产");
            occupyEditedAsset(request.batteryAssetId(), order.batteryAssetId(), "补录订单编辑绑定第二资产");
        } else {
            validateHistoricalEditableAsset(request.frameAssetId(), order.frameAssetId(), storeSku);
            validateHistoricalEditableAsset(request.batteryAssetId(), order.batteryAssetId(), storeSku);
        }

        var externalRentalAmount = normalizeMoney(
            request.externalRentalAmount(),
            packagePricing.rentalAmount().multiply(BigDecimal.valueOf(leaseMultiplier))
        );
        var verificationAmount = normalizeVerificationAmount(request.verificationAmount(), externalRentalAmount);
        var updated = externalRentalOrderRepository.update(new ExternalRentalOrderRepository.UpdateRow(
            order.id(),
            parseSource(request.sourcePlatform()),
            blankToNull(request.externalOrderNo()),
            storeSku.merchantId(),
            storeSku.storeId(),
            storeSku.id(),
            storeSku.skuId(),
            packageTemplate.id(),
            request.customerName().trim(),
            request.customerPhone().trim(),
            request.frameAssetId(),
            request.batteryAssetId(),
            externalRentalAmount,
            verificationAmount,
            normalizeMoney(request.signFeeAmount(), storeSku.signFeeAmount()),
            normalizeMoney(request.depositAmount(), packagePricing.depositAmount()),
            packageTemplate.leaseUnit().name(),
            packageTemplate.leaseValue() * leaseMultiplier,
            packageTemplate.totalPeriods() * leaseMultiplier,
            leaseMultiplier,
            request.rentStartedAt(),
            expectedReturnAt,
            blankToNull(request.remark()),
            currentAccountId()
        ));
        updated = createAndSyncSettlement(updated);
        externalRentalOrderRepository.addLog(
            updated.id(),
            updated.orderStatus(),
            updated.orderStatus(),
            ExternalOrderOperationType.EDIT,
            currentAccountId(),
            "编辑补录订单资料"
        );
        return toResponse(ensureView(updated.id()));
    }

    @Transactional
    public void deleteOrder(Long id) {
        authorizationService.requirePermission("order.operate");
        var order = externalRentalOrderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("补录订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());

        if (settlementStatementRepository.hasLinesBySource(SnapshotSourceType.EXTERNAL_ORDER.name(), order.id())) {
            throw BusinessException.badRequest("补录订单已进入月结单，不能删除");
        }
        if (settlementIncomeRepository.hasNonPendingBySource(SnapshotSourceType.EXTERNAL_ORDER, order.id())) {
            throw BusinessException.badRequest("补录订单收益已结算或冻结，不能删除");
        }

        if (order.orderStatus() == ExternalRentalOrderStatus.ACTIVE) {
            releaseDeletedActiveAsset(order.frameAssetId(), order, "删除补录订单释放主资产");
            releaseDeletedActiveAsset(order.batteryAssetId(), order, "删除补录订单释放第二资产");
        }

        settlementIncomeRepository.deleteBySource(SnapshotSourceType.EXTERNAL_ORDER, order.id());
        externalRentalOrderRepository.deleteLogs(order.id());
        externalRentalOrderRepository.delete(order.id());
        settlementRepository.deleteSnapshotsBySource(SnapshotSourceType.EXTERNAL_ORDER, order.id());
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
        var leaseMultiplier = normalizeLeaseMultiplier(request.leaseMultiplier());
        validateRequestAssets(request.frameAssetId(), request.batteryAssetId(), sku);
        var expectedReturnAt = request.expectedReturnAt() == null
            ? calculateExpectedReturnAt(request.rentStartedAt(), packageTemplate, leaseMultiplier)
            : request.expectedReturnAt();
        if (expectedReturnAt != null && expectedReturnAt.isBefore(request.rentStartedAt())) {
            throw BusinessException.badRequest("预计归还时间不能早于起租时间");
        }
        var frameAsset = request.frameAssetId() == null ? null : occupyAsset(request.frameAssetId(), storeSku, "外部补录订单绑定主资产");
        var batteryAsset = request.batteryAssetId() == null ? null : occupyAsset(request.batteryAssetId(), storeSku, "外部补录订单绑定第二资产");
        var externalRentalAmount = normalizeMoney(
            request.externalRentalAmount(),
            packagePricing.rentalAmount().multiply(BigDecimal.valueOf(leaseMultiplier))
        );
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
            packageTemplate.leaseValue() * leaseMultiplier,
            packageTemplate.totalPeriods() * leaseMultiplier,
            leaseMultiplier,
            request.rentStartedAt(),
            expectedReturnAt,
            blankToNull(request.remark()),
            currentAccountId(),
            currentAccountId()
        ));
        order = createAndSyncSettlement(order);
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
            returnAssetToStore(order.batteryAssetId(), parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单归还第二资产"));
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
            returnAssetToStore(order.batteryAssetId(), parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE), order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "外部补录订单提前终止归还第二资产"));
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

    private void validateRequestAssets(Long frameAssetId, Long batteryAssetId, ProductSku sku) {
        if (frameAssetId != null) {
            ensureAsset(frameAssetId);
        }
        if (batteryAssetId != null) {
            ensureAsset(batteryAssetId);
        }
        if (Boolean.TRUE.equals(sku.needFrameAsset()) && frameAssetId == null) {
            throw BusinessException.badRequest("当前商品链接必须绑定主资产");
        }
        if (Boolean.TRUE.equals(sku.needBatteryAsset()) && batteryAssetId == null) {
            throw BusinessException.badRequest("当前商品链接必须绑定第二资产");
        }
        if (!Boolean.TRUE.equals(sku.needFrameAsset()) && frameAssetId != null) {
            throw BusinessException.badRequest("当前商品链接不需要绑定主资产");
        }
        if (!Boolean.TRUE.equals(sku.needBatteryAsset()) && batteryAssetId != null) {
            throw BusinessException.badRequest("当前商品链接不需要绑定第二资产");
        }
        if (frameAssetId != null && frameAssetId.equals(batteryAssetId)) {
            throw BusinessException.badRequest("主资产和第二资产不能选择同一条资产");
        }
    }

    private void validateEditableAsset(
        Long assetId,
        Long currentAssetId,
        StoreSku storeSku,
        ExternalRentalOrder order
    ) {
        if (assetId == null) {
            return;
        }
        var asset = ensureAsset(assetId);
        if (!storeSku.merchantId().equals(asset.currentMerchantId()) || !storeSku.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选资产不属于当前下单门店");
        }
        if (assetId.equals(currentAssetId)) {
            var activeOrder = externalRentalOrderRepository.findActiveByAsset(assetId).orElse(null);
            if (asset.status() != AssetStatus.RENTING || activeOrder == null || !activeOrder.id().equals(order.id())) {
                throw BusinessException.badRequest("订单当前绑定资产状态异常，暂不能编辑");
            }
            return;
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("所选资产不是空闲状态");
        }
        if (externalRentalOrderRepository.findActiveByAsset(assetId).isPresent()) {
            throw BusinessException.badRequest("所选资产已被其他补录订单占用");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("所选资产已被正式订单占用");
        }
    }

    private void validateHistoricalEditableAsset(
        Long assetId,
        Long currentAssetId,
        StoreSku storeSku
    ) {
        if (assetId == null) {
            return;
        }
        var asset = ensureAsset(assetId);
        if (assetId.equals(currentAssetId)) {
            return;
        }
        if (!storeSku.merchantId().equals(asset.currentMerchantId()) || !storeSku.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("所选资产不属于当前下单门店");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("已结束订单只能改绑当前门店的空闲资产");
        }
        if (externalRentalOrderRepository.findActiveByAsset(assetId).isPresent()) {
            throw BusinessException.badRequest("所选资产已被其他补录订单占用");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("所选资产已被正式订单占用");
        }
    }

    private ExternalRentalOrder createAndSyncSettlement(ExternalRentalOrder order) {
        var snapshot = settlementService.createOrderSnapshot(new SnapshotCreateRequest(
            "EXTERNAL_ORDER",
            order.id(),
            order.storeSkuId(),
            order.frameAssetId(),
            order.batteryAssetId(),
            order.verificationAmount(),
            order.sourcePlatform().name()
        ));
        var updated = externalRentalOrderRepository.updateSettlementSnapshot(order.id(), snapshot.id());
        settlementIncomeService.syncExternalOrder(updated);
        return updated;
    }

    public int backfillMissingSettlements() {
        var completed = 0;
        for (var id : externalRentalOrderRepository.listIdsWithoutSettlementSnapshot()) {
            try {
                var updated = transactionTemplate.execute(status -> {
                    var order = externalRentalOrderRepository.findByIdForUpdate(id).orElse(null);
                    if (order == null || order.settlementSnapshotId() != null) {
                        return false;
                    }
                    createAndSyncSettlement(order);
                    return true;
                });
                if (Boolean.TRUE.equals(updated)) {
                    completed++;
                }
            } catch (RuntimeException exception) {
                log.warn("补录订单 {} 自动补建分润失败: {}", id, exception.getMessage());
            }
        }
        return completed;
    }

    private void releaseEditedAsset(Long currentAssetId, Long nextAssetId, String remark) {
        if (currentAssetId == null || currentAssetId.equals(nextAssetId)) {
            return;
        }
        var asset = ensureAsset(currentAssetId);
        if (asset.status() != AssetStatus.IDLE) {
            assetRepository.updateStatus(asset.id(), AssetStatus.IDLE, LocalDateTime.now());
            assetRepository.insertStatusLog(asset.id(), asset.status(), AssetStatus.IDLE, currentAccountId(), remark);
        }
    }

    private void releaseDeletedActiveAsset(Long assetId, ExternalRentalOrder order, String remark) {
        if (assetId == null) {
            return;
        }
        if (externalRentalOrderRepository.existsOtherActiveByAsset(assetId, order.id())) {
            throw BusinessException.badRequest("订单资产仍被其他补录订单占用，不能删除");
        }
        var formalOrderOccupied = orderRepository.listByAsset(assetId).stream()
            .anyMatch(item -> item.orderStatus() != OrderStatus.COMPLETED && item.orderStatus() != OrderStatus.CANCELLED);
        if (formalOrderOccupied) {
            throw BusinessException.badRequest("订单资产已被正式订单占用，不能删除");
        }
        var asset = ensureAsset(assetId);
        if (asset.status() == AssetStatus.RENTING) {
            assetRepository.updateStatus(asset.id(), AssetStatus.IDLE, LocalDateTime.now());
            assetRepository.insertStatusLog(asset.id(), asset.status(), AssetStatus.IDLE, currentAccountId(), remark);
        }
    }

    private void occupyEditedAsset(Long nextAssetId, Long currentAssetId, String remark) {
        if (nextAssetId == null || nextAssetId.equals(currentAssetId)) {
            return;
        }
        var asset = ensureAsset(nextAssetId);
        assetRepository.updateStatus(asset.id(), AssetStatus.RENTING, LocalDateTime.now());
        assetRepository.insertStatusLog(asset.id(), asset.status(), AssetStatus.RENTING, currentAccountId(), remark);
    }

    private AssetItem occupyAsset(Long assetId, StoreSku storeSku, String remark) {
        var asset = ensureAsset(assetId);
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
        var snapshot = order.settlementSnapshotId() == null
            ? null
            : settlementRepository.findSnapshot(order.settlementSnapshotId()).orElse(null);
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
            order.settlementSnapshotId(),
            snapshot == null ? null : snapshot.snapshotNo(),
            snapshot == null ? null : snapshot.settlementBaseAmount(),
            snapshot == null ? null : snapshot.channelFeeAmount(),
            snapshot == null ? null : snapshot.platformFeeAmount(),
            snapshot == null ? null : snapshot.storeOperationAmount(),
            snapshot == null ? null : snapshot.maintenanceFundAmount(),
            snapshot == null ? null : snapshot.channelReferralAmount(),
            snapshot == null ? null : snapshot.investorShareAmount(),
            order.signFeeAmount(),
            order.depositAmount(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.leaseMultiplier(),
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
            order.verificationAmount(),
            order.signFeeAmount(),
            order.verificationAmount(),
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
            throw BusinessException.badRequest("只有进行中的补录订单才可以结束");
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
            row.leaseMultiplier(),
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

    private LocalDateTime calculateExpectedReturnAt(LocalDateTime startedAt, ProductPackage productPackage, Integer leaseMultiplier) {
        if (startedAt == null) {
            return null;
        }
        var multiplier = normalizeLeaseMultiplier(leaseMultiplier);
        var leaseValue = (long) productPackage.leaseValue() * multiplier;
        if ("MONTH".equals(productPackage.leaseUnit().name())) {
            return startedAt.plusDays(leaseValue * 30L);
        }
        return startedAt.plusDays(leaseValue);
    }

    private Integer normalizeLeaseMultiplier(Integer value) {
        var multiplier = value == null ? 1 : value;
        if (multiplier < 1 || multiplier > 120) {
            throw BusinessException.badRequest("租期倍数必须在 1 到 120 之间");
        }
        return multiplier;
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
