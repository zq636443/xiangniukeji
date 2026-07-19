package com.xniu.rental.order.service;

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
    private final ProductRepository productRepository;
    private final SettlementService settlementService;
    private final AuthorizationService authorizationService;
    private final OrderStateMachine stateMachine;

    public OrderService(
        OrderRepository orderRepository,
        ProductRepository productRepository,
        SettlementService settlementService,
        AuthorizationService authorizationService,
        OrderStateMachine stateMachine
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.settlementService = settlementService;
        this.authorizationService = authorizationService;
        this.stateMachine = stateMachine;
    }

    public List<OrderResponse> listOrders(String status, Long storeId, Long userAccountId) {
        authorizationService.requirePermission("order.read");
        return orderRepository.list(parseStatusNullable(status), storeId, userAccountId).stream()
            .map(this::toResponse)
            .toList();
    }

    public OrderResponse getOrder(Long id) {
        authorizationService.requirePermission("order.read");
        return toResponse(ensureOrder(id));
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {
        authorizationService.requirePermission("order.operate");
        return createOrderInternal(request, request.userAccountId());
    }

    public List<OrderResponse> listUserOrders(String status) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return orderRepository.list(parseStatusNullable(status), null, current.account().id()).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<OrderResponse> listMerchantOrders(Long storeId, String status) {
        authorizationService.requirePermission("order.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var orders = orderRepository.list(parseStatusNullable(status), storeId, null);
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
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return createOrderInternal(request, current.account().id());
    }

    private OrderResponse createOrderInternal(OrderCreateRequest request, Long userAccountId) {
        var storeSku = productRepository.findStoreSku(request.storeSkuId())
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(request.packageId()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该套餐价格"));
        var packageTemplate = productRepository.findPackage(request.packageId())
            .orElseThrow(() -> BusinessException.badRequest("套餐不存在"));
        var payableAmount = packagePrice.rentalAmount().add(storeSku.signFeeAmount()).add(packagePrice.depositAmount());
        var order = orderRepository.create(new OrderRepository.OrderCreateRow(
            nextOrderNo(),
            userAccountId,
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
            packagePrice.rentalAmount()
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
        return new OrderResponse(
            order.id(),
            order.orderNo(),
            order.userAccountId(),
            order.merchantId(),
            order.storeId(),
            order.storeSkuId(),
            order.skuId(),
            order.packageId(),
            order.frameAssetId(),
            order.batteryAssetId(),
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
