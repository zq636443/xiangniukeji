package com.xniu.rental.asset.service;

import com.xniu.rental.asset.dto.AssetChangeResponse;
import com.xniu.rental.asset.dto.AssetHandoverResponse;
import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReplaceRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.model.AssetChange;
import com.xniu.rental.asset.model.AssetHandover;
import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.model.HandoverType;
import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetFulfillmentService {

    private final AssetFulfillmentRepository fulfillmentRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final AuthorizationService authorizationService;

    public AssetFulfillmentService(
        AssetFulfillmentRepository fulfillmentRepository,
        AssetRepository assetRepository,
        OrderRepository orderRepository,
        StoreRepository storeRepository,
        ProductRepository productRepository,
        AuthorizationService authorizationService
    ) {
        this.fulfillmentRepository = fulfillmentRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
    }

    public List<AssetHandoverResponse> listHandovers(Long orderId, Long storeId, String handoverType) {
        authorizationService.requirePermission("asset.read");
        return fulfillmentRepository.listHandovers(orderId, storeId, parseHandoverTypeNullable(handoverType)).stream()
            .map(this::toHandoverResponse)
            .toList();
    }

    public List<AssetChangeResponse> listChanges(Long orderId, Long storeId) {
        authorizationService.requirePermission("asset.read");
        return fulfillmentRepository.listChanges(orderId, storeId).stream()
            .map(this::toChangeResponse)
            .toList();
    }

    @Transactional
    public AssetHandoverResponse pickup(Long orderId, AssetPickupRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(orderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (order.orderStatus() != OrderStatus.PENDING_PICKUP) {
            throw BusinessException.badRequest("只有待取车订单可以取车绑定");
        }
        return fulfillPickup(order, request, "取车交接");
    }

    @Transactional
    public AssetHandoverResponse shipWithoutPayment(Long orderId, AssetPickupRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(orderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (order.orderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw BusinessException.badRequest("只有待支付订单可以选择免付款发货");
        }
        return fulfillPickup(order, request, "门店免付款发货");
    }

    private AssetHandoverResponse fulfillPickup(RentalOrder order, AssetPickupRequest request, String defaultRemark) {
        request = request == null ? new AssetPickupRequest(null, null, null) : request;
        var frameAssetId = request.frameAssetId() == null ? order.frameAssetId() : request.frameAssetId();
        var frameAsset = frameAssetId == null ? null : ensureAssetReadyForOrder(frameAssetId, AssetType.VEHICLE_FRAME, order);
        var integratedVehicle = frameAsset != null && frameAsset.assetType().isIntegratedVehicle();
        if (integratedVehicle && request.batteryAssetId() != null) {
            throw BusinessException.badRequest("车电一体资产只需绑定车架号，无需再选择电池资产");
        }
        var batteryAssetId = integratedVehicle ? null : request.batteryAssetId() == null ? order.batteryAssetId() : request.batteryAssetId();
        if (frameAssetId == null && batteryAssetId == null) {
            throw BusinessException.badRequest("请至少绑定主资产或电池资产");
        }
        var batteryAsset = batteryAssetId == null ? null : ensureAssetReadyForOrder(batteryAssetId, AssetType.BATTERY, order);
        if (frameAsset != null) {
            markAssetStatus(frameAsset.id(), AssetStatus.RENTING, defaultRemark + "：绑定主资产");
        }
        if (batteryAsset != null) {
            markAssetStatus(batteryAsset.id(), AssetStatus.RENTING, defaultRemark + "：绑定电池");
        }
        orderRepository.updateAssets(order.id(), frameAssetId, batteryAssetId);
        var now = LocalDateTime.now();
        var updatedOrder = orderRepository.updateStatus(order.id(), OrderStatus.RENTING, now, expectedReturnAt(now, order), null);
        if (frameAsset != null) {
            closeActiveFrameUsage(order.id(), "PICKUP_REBIND");
            fulfillmentRepository.startUsage(order.id(), frameAsset.id(), frameAsset.assetType(), frameAsset.investorId(), order.storeId(), "PICKUP");
        }
        if (batteryAsset != null) {
            fulfillmentRepository.closeActiveUsage(order.id(), AssetType.BATTERY, "PICKUP_REBIND");
            fulfillmentRepository.startUsage(order.id(), batteryAsset.id(), batteryAsset.assetType(), batteryAsset.investorId(), order.storeId(), "PICKUP");
        }
        orderRepository.addLog(order.id(), order.orderStatus(), OrderStatus.RENTING, OrderOperationType.TRANSITION, currentAccountId(), defaultRemark(request.remark(), defaultRemark));
        var handover = fulfillmentRepository.createHandover(new AssetFulfillmentRepository.HandoverCreateRow(
            nextHandoverNo(),
            updatedOrder.id(),
            updatedOrder.merchantId(),
            updatedOrder.storeId(),
            updatedOrder.userAccountId(),
            HandoverType.PICKUP,
            frameAssetId,
            batteryAssetId,
            AssetStatus.RENTING,
            AssetStatus.RENTING,
            currentAccountId(),
            defaultRemark(request.remark(), defaultRemark)
        ));
        return toHandoverResponse(handover);
    }

    @Transactional
    public AssetChangeResponse replaceAsset(Long orderId, AssetReplaceRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(orderId);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        if (order.orderStatus() != OrderStatus.RENTING && order.orderStatus() != OrderStatus.PENDING_RETURN && order.orderStatus() != OrderStatus.PENDING_SUPPLEMENT) {
            throw BusinessException.badRequest("只有履约中的订单可以更换资产");
        }
        var assetType = parseAssetType(request.assetType());
        if (assetType == AssetType.BATTERY && order.frameAssetId() != null && ensureAsset(order.frameAssetId()).assetType().isIntegratedVehicle()) {
            throw BusinessException.badRequest("车电一体资产无需绑定或更换独立电池");
        }
        var oldAssetId = assetType == AssetType.VEHICLE_FRAME ? order.frameAssetId() : order.batteryAssetId();
        var newAsset = ensureAssetReadyForOrder(request.newAssetId(), assetType, order);
        var oldStatus = parseReturnStatus(request.oldAssetResultStatus(), AssetStatus.IDLE);
        if (oldAssetId != null) {
            markAssetStatus(oldAssetId, oldStatus, defaultRemark(request.remark(), "更换资产，原资产回收"));
        }
        markAssetStatus(newAsset.id(), AssetStatus.RENTING, defaultRemark(request.remark(), "更换资产，新资产租赁中"));
        var frameAssetId = assetType == AssetType.VEHICLE_FRAME ? newAsset.id() : order.frameAssetId();
        var batteryAssetId = assetType == AssetType.BATTERY ? newAsset.id() : order.batteryAssetId();
        if (assetType == AssetType.VEHICLE_FRAME && newAsset.assetType().isIntegratedVehicle() && batteryAssetId != null) {
            markAssetStatus(batteryAssetId, AssetStatus.IDLE, defaultRemark(request.remark(), "换为车电一体，原电池解绑"));
            fulfillmentRepository.closeActiveUsage(order.id(), AssetType.BATTERY, "INTEGRATED_VEHICLE_REPLACE");
            batteryAssetId = null;
        }
        orderRepository.updateAssets(order.id(), frameAssetId, batteryAssetId);
        closeActiveSlotUsage(order.id(), assetType, "REPLACE");
        fulfillmentRepository.startUsage(order.id(), newAsset.id(), newAsset.assetType(), newAsset.investorId(), order.storeId(), "REPLACE");
        orderRepository.addLog(order.id(), order.orderStatus(), order.orderStatus(), OrderOperationType.TRANSITION, currentAccountId(), defaultRemark(request.remark(), "更换资产"));
        var change = fulfillmentRepository.createChange(new AssetFulfillmentRepository.ChangeCreateRow(
            nextChangeNo(),
            order.id(),
            order.merchantId(),
            order.storeId(),
            assetType,
            oldAssetId,
            newAsset.id(),
            oldStatus,
            currentAccountId(),
            defaultRemark(request.remark(), "更换资产")
        ));
        return toChangeResponse(change);
    }

    @Transactional
    public AssetHandoverResponse returnAssets(Long orderId, AssetReturnRequest request) {
        authorizationService.requirePermission("order.operate");
        request = request == null ? new AssetReturnRequest(null, null, null, null) : request;
        var order = ensureOrder(orderId);
        var returnStore = resolveReturnStore(order, request);
        authorizationService.requireStoreAccess(order.merchantId(), returnStore.id());
        if (order.orderStatus() != OrderStatus.RENTING
            && order.orderStatus() != OrderStatus.PENDING_RETURN
            && order.orderStatus() != OrderStatus.OVERDUE
            && order.orderStatus() != OrderStatus.PENDING_SUPPLEMENT) {
            throw BusinessException.badRequest("只有履约中的订单可以归还资产");
        }
        var frameStatus = parseReturnStatus(request.frameResultStatus(), AssetStatus.IDLE);
        var batteryStatus = parseReturnStatus(request.batteryResultStatus(), AssetStatus.IDLE);
        if (order.frameAssetId() != null) {
            returnAssetToStore(order.frameAssetId(), frameStatus, order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "归还主资产"));
        }
        if (order.batteryAssetId() != null) {
            returnAssetToStore(order.batteryAssetId(), batteryStatus, order.merchantId(), returnStore.id(), defaultRemark(request.remark(), "归还电池"));
        }
        fulfillmentRepository.closeAllActiveUsage(order.id(), "RETURN");
        var completed = orderRepository.completeReturn(order.id(), LocalDateTime.now());
        orderRepository.addLog(order.id(), order.orderStatus(), OrderStatus.COMPLETED, OrderOperationType.TRANSITION, currentAccountId(), defaultRemark(request.remark(), "归还资产并结束订单，归还门店：" + returnStore.storeName()));
        var handover = fulfillmentRepository.createHandover(new AssetFulfillmentRepository.HandoverCreateRow(
            nextHandoverNo(),
            completed.id(),
            completed.merchantId(),
            returnStore.id(),
            completed.userAccountId(),
            HandoverType.RETURN,
            completed.frameAssetId(),
            completed.batteryAssetId(),
            completed.frameAssetId() == null ? null : frameStatus,
            completed.batteryAssetId() == null ? null : batteryStatus,
            currentAccountId(),
            defaultRemark(request.remark(), "归还资产并结束订单")
        ));
        return toHandoverResponse(handover);
    }

    private MerchantStore resolveReturnStore(RentalOrder order, AssetReturnRequest request) {
        var current = AuthContext.get();
        Long returnStoreId = request.returnStoreId();
        if (returnStoreId == null && current != null && current.account().storeId() != null) {
            returnStoreId = current.account().storeId();
        }
        if (returnStoreId == null) {
            returnStoreId = order.storeId();
        }
        var store = storeRepository.findById(returnStoreId).orElseThrow(() -> BusinessException.badRequest("归还门店不存在"));
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
                "跨店归还自动调拨"
            );
        }
    }

    private AssetItem ensureAssetReadyForOrder(Long assetId, AssetType expectedType, RentalOrder order) {
        var asset = ensureAsset(assetId);
        if (!asset.assetType().canBindAs(expectedType)) {
            throw BusinessException.badRequest(expectedType == AssetType.VEHICLE_FRAME ? "请选择主资产或自定义资产" : "请选择电池资产");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("资产不是空闲状态");
        }
        if (!order.merchantId().equals(asset.currentMerchantId()) || !order.storeId().equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("资产不在订单门店");
        }
        return asset;
    }

    private void closeActiveSlotUsage(Long orderId, AssetType assetType, String endReason) {
        if (assetType == AssetType.VEHICLE_FRAME) {
            closeActiveFrameUsage(orderId, endReason);
            return;
        }
        fulfillmentRepository.closeActiveUsage(orderId, assetType, endReason);
    }

    private void closeActiveFrameUsage(Long orderId, String endReason) {
        fulfillmentRepository.closeActiveUsage(orderId, AssetType.VEHICLE_FRAME, endReason);
        fulfillmentRepository.closeActiveUsage(orderId, AssetType.INTEGRATED_VEHICLE, endReason);
        fulfillmentRepository.closeActiveUsage(orderId, AssetType.GENERAL, endReason);
    }

    private void markAssetStatus(Long assetId, AssetStatus nextStatus, String remark) {
        var asset = ensureAsset(assetId);
        if (asset.status() == nextStatus) {
            return;
        }
        assetRepository.updateStatus(assetId, nextStatus, LocalDateTime.now());
        assetRepository.insertStatusLog(assetId, asset.status(), nextStatus, currentAccountId(), remark);
    }

    private RentalOrder ensureOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private AssetItem ensureAsset(Long assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private LocalDateTime expectedReturnAt(LocalDateTime startedAt, RentalOrder order) {
        LocalDateTime expectedReturnAt;
        if ("MONTH".equals(order.leaseUnit())) {
            expectedReturnAt = startedAt.plusMonths(order.leaseValue());
        } else {
            expectedReturnAt = startedAt.plusDays(order.leaseValue());
        }
        return expectedReturnAt.plusDays(orderRepository.summarizeLeaseBonuses(order.id()).totalDays());
    }

    private HandoverType parseHandoverTypeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HandoverType.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的交接类型");
        }
    }

    private AssetType parseAssetType(String value) {
        try {
            var assetType = AssetType.valueOf(value);
            if (assetType == AssetType.INTEGRATED_VEHICLE) {
                throw BusinessException.badRequest("更换类型请选择车架或电池，车电一体按车架更换");
            }
            return assetType;
        } catch (Exception exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw BusinessException.badRequest("不支持的资产类型");
        }
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

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private String defaultRemark(String remark, String fallback) {
        return remark == null || remark.isBlank() ? fallback : remark;
    }

    private AssetHandoverResponse toHandoverResponse(AssetHandover item) {
        return new AssetHandoverResponse(
            item.id(),
            item.handoverNo(),
            item.orderId(),
            item.merchantId(),
            item.storeId(),
            item.userAccountId(),
            item.handoverType().name(),
            item.frameAssetId(),
            item.batteryAssetId(),
            item.frameResultStatus() == null ? null : item.frameResultStatus().name(),
            item.batteryResultStatus() == null ? null : item.batteryResultStatus().name(),
            item.operatorAccountId(),
            item.remark(),
            item.createdAt()
        );
    }

    private AssetChangeResponse toChangeResponse(AssetChange item) {
        return new AssetChangeResponse(
            item.id(),
            item.changeNo(),
            item.orderId(),
            item.merchantId(),
            item.storeId(),
            item.assetType().name(),
            item.oldAssetId(),
            item.newAssetId(),
            item.oldAssetResultStatus().name(),
            item.operatorAccountId(),
            item.remark(),
            item.createdAt()
        );
    }

    private String nextHandoverNo() {
        return "AH-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextChangeNo() {
        return "AC-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
