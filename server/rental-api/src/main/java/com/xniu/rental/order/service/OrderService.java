package com.xniu.rental.order.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.dto.OrderCancelRequest;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderExceptionRequest;
import com.xniu.rental.order.dto.OrderItemResponse;
import com.xniu.rental.order.dto.OrderLogResponse;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.model.OrderItemType;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.model.RentalOrderItem;
import com.xniu.rental.order.model.RentalOrderOperationLog;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.service.SettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final SettlementService settlementService;
    private final AuthorizationService authorizationService;
    private final OrderStateMachine stateMachine;

    public OrderService(
        OrderRepository orderRepository,
        AccountRepository accountRepository,
        ProductRepository productRepository,
        AssetRepository assetRepository,
        SettlementService settlementService,
        AuthorizationService authorizationService,
        OrderStateMachine stateMachine
    ) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.assetRepository = assetRepository;
        this.settlementService = settlementService;
        this.authorizationService = authorizationService;
        this.stateMachine = stateMachine;
    }

    public List<OrderResponse> listOrders(String status, Long storeId, Long userAccountId, String keyword) {
        authorizationService.requirePermission("order.read");
        return orderRepository.list(parseStatusNullable(status), storeId, userAccountId, keyword).stream()
            .map(this::toResponse)
            .toList();
    }

    public OrderResponse getOrder(Long id) {
        authorizationService.requirePermission("order.read");
        return toResponse(ensureOrder(id));
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("order.operate");
        return createOrderInternal(request, request.userAccountId(), true);
    }

    @Transactional
    public OrderResponse createMerchantOrder(OrderCreateRequest request) {
        authorizationService.requirePermission("order.create");
        var storeSku = productRepository.findStoreSku(request.storeSkuId())
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        authorizationService.requireStoreAccess(storeSku.merchantId(), storeSku.storeId());
        return createOrderInternal(request, request.userAccountId(), true);
    }

    public List<OrderResponse> listUserOrders(String status) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return orderRepository.list(parseStatusNullable(status), null, current.account().id(), null).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<OrderResponse> listMerchantOrders(Long storeId, String status, String keyword) {
        authorizationService.requirePermission("order.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var orders = orderRepository.list(parseStatusNullable(status), storeId, null, keyword);
        if (orders.isEmpty()) {
            return List.of();
        }
        var first = orders.get(0);
        authorizationService.requireStoreAccess(first.merchantId(), first.storeId());
        return orders.stream().map(this::toResponse).toList();
    }

    public OrderResponse getMerchantOrder(Long id) {
        authorizationService.requirePermission("order.read");
        var order = ensureOrder(id);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        return toResponse(order);
    }

    public OrderResponse getUserOrder(Long id) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        var order = ensureOrder(id);
        if (order.userAccountId() == null || !order.userAccountId().equals(current.account().id())) {
            throw BusinessException.forbidden("不能查看其他用户订单");
        }
        return toResponse(order);
    }

    @Transactional
    public OrderResponse createUserOrder(OrderCreateRequest request) {
        authorizationService.requireConsumerAccount();
        var current = AuthContext.get();
        return createOrderInternal(request, current.account().id(), false);
    }

    private AssetItem validateOrderAsset(Long assetId, AssetType expectedType, Long merchantId, Long storeId) {
        if (assetId == null) {
            return null;
        }
        var asset = assetRepository.findById(assetId)
            .orElseThrow(() -> BusinessException.badRequest("资产不存在"));
        if (!asset.assetType().canBindAs(expectedType)) {
            throw BusinessException.badRequest(expectedType == AssetType.VEHICLE_FRAME ? "请选择车架或车电一体资产" : "请选择电池资产");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("资产不是空闲状态");
        }
        if (!merchantId.equals(asset.currentMerchantId()) || !storeId.equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("资产不在订单门店");
        }
        return asset;
    }

    private OrderResponse createOrderInternal(OrderCreateRequest request, Long userAccountId, boolean allowCustomOrderedAt) {
        var storeSku = productRepository.findStoreSku(request.storeSkuId())
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var frameAsset = validateOrderAsset(request.frameAssetId(), AssetType.VEHICLE_FRAME, storeSku.merchantId(), storeSku.storeId());
        if (frameAsset != null && frameAsset.assetType().isIntegratedVehicle() && request.batteryAssetId() != null) {
            throw BusinessException.badRequest("车电一体资产只需绑定车架号，无需再选择电池资产");
        }
        validateOrderAsset(request.batteryAssetId(), AssetType.BATTERY, storeSku.merchantId(), storeSku.storeId());
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(request.packageId()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该 SKU 价格"));
        var packageTemplate = productRepository.findPackage(request.packageId())
            .orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        var customer = resolveCustomer(userAccountId, request.customerName(), request.customerPhone());
        var orderedAt = resolveOrderedAt(request.orderedAt(), allowCustomOrderedAt);
        var payableAmount = packagePrice.rentalAmount().add(storeSku.signFeeAmount()).add(packagePrice.depositAmount());
        var order = orderRepository.create(new OrderRepository.OrderCreateRow(
            nextOrderNo(),
            userAccountId,
            customer.name(),
            customer.phone(),
            storeSku.merchantId(),
            storeSku.storeId(),
            storeSku.id(),
            storeSku.skuId(),
            packageTemplate.id(),
            request.frameAssetId(),
            request.batteryAssetId(),
            OrderStatus.PENDING_PAYMENT,
            packagePrice.rentalAmount(),
            storeSku.signFeeAmount(),
            packagePrice.depositAmount(),
            payableAmount,
            BigDecimal.ZERO,
            null,
            packageTemplate.leaseUnit().name(),
            packageTemplate.leaseValue(),
            packageTemplate.totalPeriods(),
            packageTemplate.billDayMode().name(),
            packageTemplate.billDay(),
            orderedAt,
            packagePrice.autoRenewEnabled(),
            packagePrice.renewalUnit() == null ? null : packagePrice.renewalUnit().name(),
            packagePrice.renewalValue(),
            packagePrice.renewalAmount(),
            request.expectedPickupAt()
        ));
        orderRepository.addItem(order.id(), OrderItemType.SKU, storeSku.id(), storeSku.displayName(), 1, packagePrice.rentalAmount(), packagePrice.rentalAmount());
        orderRepository.addItem(order.id(), OrderItemType.SIGN_FEE, null, "签单费", 1, storeSku.signFeeAmount(), storeSku.signFeeAmount());
        if (packagePrice.depositAmount().signum() > 0) {
            orderRepository.addItem(order.id(), OrderItemType.DEPOSIT, null, "押金", 1, packagePrice.depositAmount(), packagePrice.depositAmount());
        }
        if (request.frameAssetId() != null) {
            orderRepository.addItem(order.id(), OrderItemType.ASSET_FRAME, request.frameAssetId(), "车架资产", 1, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (request.batteryAssetId() != null) {
            orderRepository.addItem(order.id(), OrderItemType.ASSET_BATTERY, request.batteryAssetId(), "电池资产", 1, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        orderRepository.addLog(order.id(), null, OrderStatus.PENDING_PAYMENT, OrderOperationType.CREATE, currentAccountId(), "创建订单");
        var snapshot = settlementService.createOrderSnapshot(new SnapshotCreateRequest(
            "ORDER",
            order.id(),
            storeSku.id(),
            request.frameAssetId(),
            request.batteryAssetId(),
            packagePrice.rentalAmount(),
            "DIRECT"
        ));
        order = orderRepository.updateSettlementSnapshot(order.id(), snapshot.id());
        return toResponse(order);
    }

    @Transactional
    public OrderResponse transition(Long id, OrderTransitionRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        var target = parseStatus(request.targetStatus());
        stateMachine.assertCanTransit(order.orderStatus(), target);
        var now = LocalDateTime.now();
        var startedAt = target == OrderStatus.RENTING ? now : null;
        var expectedReturnAt = target == OrderStatus.RENTING ? expectedReturnAt(now, order.leaseUnit(), order.leaseValue()) : null;
        var returnedAt = target == OrderStatus.COMPLETED ? now : null;
        var updated = orderRepository.updateStatus(id, target, startedAt, expectedReturnAt, returnedAt);
        orderRepository.addLog(id, order.orderStatus(), target, OrderOperationType.TRANSITION, currentAccountId(), request.remark());
        return toResponse(updated);
    }

    @Transactional
    public OrderResponse cancel(Long id, OrderCancelRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        stateMachine.assertCanCancel(order.orderStatus());
        var updated = orderRepository.cancel(id, request.reason());
        orderRepository.addLog(id, order.orderStatus(), OrderStatus.CANCELLED, OrderOperationType.CANCEL, currentAccountId(), request.reason());
        return toResponse(updated);
    }

    @Transactional
    public OrderResponse markException(Long id, OrderExceptionRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        stateMachine.assertCanMarkException(order.orderStatus());
        var updated = orderRepository.markException(id, request.reason());
        orderRepository.addLog(id, order.orderStatus(), OrderStatus.EXCEPTION, OrderOperationType.MARK_EXCEPTION, currentAccountId(), request.reason());
        return toResponse(updated);
    }

    private RentalOrder ensureOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private LocalDateTime expectedReturnAt(LocalDateTime startedAt, String leaseUnit, Integer leaseValue) {
        if ("MONTH".equals(leaseUnit)) {
            return startedAt.plusMonths(leaseValue);
        }
        return startedAt.plusDays(leaseValue);
    }

    private OrderResponse toResponse(RentalOrder order) {
        var display = orderRepository.findDisplayInfo(order.id()).orElse(new OrderRepository.OrderDisplayRow(null, null, null, null, null, null, null));
        return new OrderResponse(
            order.id(),
            order.orderNo(),
            order.userAccountId(),
            order.customerName(),
            order.customerPhone(),
            order.merchantId(),
            order.storeId(),
            display.storeName(),
            order.storeSkuId(),
            display.storeSkuName(),
            order.skuId(),
            order.packageId(),
            display.packageName(),
            order.frameAssetId(),
            display.frameAssetCode(),
            display.frameSerialNo(),
            order.batteryAssetId(),
            display.batteryAssetCode(),
            display.batterySerialNo(),
            order.orderStatus().name(),
            order.rentalAmount(),
            order.signFeeAmount(),
            order.depositAmount(),
            order.payableAmount(),
            order.paidAmount(),
            order.settlementSnapshotId(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.billDayMode(),
            order.billDay(),
            order.orderedAt(),
            order.autoRenewEnabled(),
            order.renewalUnit(),
            order.renewalValue(),
            order.renewalAmount(),
            order.renewalCount(),
            order.expectedPickupAt(),
            order.leaseStartedAt(),
            order.expectedReturnAt(),
            order.returnedAt(),
            order.cancelledAt(),
            order.cancelReason(),
            order.exceptionReason(),
            order.createdAt(),
            orderRepository.listItems(order.id()).stream().map(this::toItemResponse).toList(),
            orderRepository.listLogs(order.id()).stream().map(this::toLogResponse).toList()
        );
    }

    private CustomerSnapshot resolveCustomer(Long userAccountId, String customerName, String customerPhone) {
        var normalizedName = normalizeText(customerName);
        var normalizedPhone = normalizeText(customerPhone);
        if (userAccountId != null) {
            var account = accountRepository.findById(userAccountId).orElse(null);
            if (account != null) {
                if (normalizedName == null) {
                    normalizedName = normalizeText(account.displayName());
                }
                if (normalizedName == null) {
                    normalizedName = normalizeText(account.username());
                }
                if (normalizedPhone == null) {
                    normalizedPhone = normalizeText(account.phone());
                }
            }
        }
        if (userAccountId == null && normalizedName == null) {
            throw BusinessException.badRequest("请填写客户姓名");
        }
        if (userAccountId == null && normalizedPhone == null) {
            throw BusinessException.badRequest("请填写客户电话");
        }
        return new CustomerSnapshot(normalizedName, normalizedPhone);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime resolveOrderedAt(LocalDateTime requested, boolean allowCustomOrderedAt) {
        if (!allowCustomOrderedAt && requested != null) {
            throw BusinessException.badRequest("用户下单时间不能自定义");
        }
        var now = LocalDateTime.now();
        var orderedAt = requested == null ? now : requested;
        if (orderedAt.isAfter(now.plusMinutes(1))) {
            throw BusinessException.badRequest("下单时间不能晚于当前时间");
        }
        return orderedAt;
    }

    private record CustomerSnapshot(String name, String phone) {
    }

    private OrderItemResponse toItemResponse(RentalOrderItem item) {
        return new OrderItemResponse(item.id(), item.itemType().name(), item.refId(), item.itemName(), item.quantity(), item.unitAmount(), item.totalAmount());
    }

    private OrderLogResponse toLogResponse(RentalOrderOperationLog log) {
        return new OrderLogResponse(
            log.id(),
            log.orderId(),
            log.fromStatus() == null ? null : log.fromStatus().name(),
            log.toStatus().name(),
            log.operationType().name(),
            log.operatorAccountId(),
            log.remark(),
            log.createdAt()
        );
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的订单状态");
        }
    }

    private OrderStatus parseStatusNullable(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private String nextOrderNo() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
