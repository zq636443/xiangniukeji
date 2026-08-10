package com.xniu.rental.order.service;

import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.auth.repository.AccountRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillGenerationType;
import com.xniu.rental.bill.model.BillItemType;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.model.MerchantStatus;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.dto.OrderCancelRequest;
import com.xniu.rental.order.dto.OrderCreateRequest;
import com.xniu.rental.order.dto.OrderExceptionRequest;
import com.xniu.rental.order.dto.OrderItemResponse;
import com.xniu.rental.order.dto.OrderLeaseBonusRequest;
import com.xniu.rental.order.dto.OrderLeaseBonusResponse;
import com.xniu.rental.order.dto.OrderLogResponse;
import com.xniu.rental.order.dto.OrderResponse;
import com.xniu.rental.order.dto.OrderTransitionRequest;
import com.xniu.rental.order.dto.OrderUpdateRequest;
import com.xniu.rental.order.model.OrderItemType;
import com.xniu.rental.order.model.OrderLeaseBonus;
import com.xniu.rental.order.model.OrderLeaseBonusType;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.model.RentalOrderItem;
import com.xniu.rental.order.model.RentalOrderOperationLog;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.model.ProductPackage;
import com.xniu.rental.product.model.ProductStatus;
import com.xniu.rental.product.model.StoreSku;
import com.xniu.rental.product.model.StoreSkuPackage;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.SettlementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final AssetRepository assetRepository;
    private final BillRepository billRepository;
    private final SettlementService settlementService;
    private final AuthorizationService authorizationService;
    private final OrderStateMachine stateMachine;

    public OrderService(
        OrderRepository orderRepository,
        AccountRepository accountRepository,
        ProductRepository productRepository,
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        AssetRepository assetRepository,
        BillRepository billRepository,
        SettlementService settlementService,
        AuthorizationService authorizationService,
        OrderStateMachine stateMachine
    ) {
        this.orderRepository = orderRepository;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.assetRepository = assetRepository;
        this.billRepository = billRepository;
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

    @Transactional
    public OrderResponse updateOrder(Long id, OrderUpdateRequest request) {
        authorizationService.requirePlatformAccount();
        authorizationService.requirePermission("order.operate");
        return updateOrderInternal(id, request);
    }

    @Transactional
    public OrderResponse updateMerchantOrder(Long id, OrderUpdateRequest request) {
        authorizationService.requirePermission("order.create");
        return updateOrderInternal(id, request);
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
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        var orders = orderRepository.list(parseStatusNullable(status), storeId, null, keyword);
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
            throw BusinessException.badRequest(expectedType == AssetType.VEHICLE_FRAME ? "请选择主资产或自定义资产" : "请选择电池资产");
        }
        if (asset.status() != AssetStatus.IDLE) {
            throw BusinessException.badRequest("资产不是空闲状态");
        }
        if (!merchantId.equals(asset.currentMerchantId()) || !storeId.equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("资产不在订单门店");
        }
        return asset;
    }

    private void validateSingleInvestor(AssetItem frameAsset, AssetItem batteryAsset) {
        if (frameAsset != null
            && batteryAsset != null
            && !java.util.Objects.equals(frameAsset.investorId(), batteryAsset.investorId())) {
            throw BusinessException.badRequest("车架和电池属于不同出资方，请分别创建订单");
        }
    }

    private OrderResponse createOrderInternal(OrderCreateRequest request, Long userAccountId, boolean allowCustomOrderedAt) {
        var storeSku = productRepository.findStoreSku(request.storeSkuId())
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var merchant = merchantRepository.findById(storeSku.merchantId())
            .orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        if (merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        var store = storeRepository.findById(storeSku.storeId())
            .orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        if (!store.merchantId().equals(storeSku.merchantId())) {
            throw BusinessException.badRequest("门店商品商户关系异常");
        }
        var sku = productRepository.findSku(storeSku.skuId())
            .orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        if (sku.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("商品链接已停用");
        }
        var frameAsset = validateOrderAsset(request.frameAssetId(), AssetType.VEHICLE_FRAME, storeSku.merchantId(), storeSku.storeId());
        if (frameAsset != null && frameAsset.assetType().isIntegratedVehicle() && request.batteryAssetId() != null) {
            throw BusinessException.badRequest("车电一体资产只需绑定车架号，无需再选择电池资产");
        }
        var batteryAsset = validateOrderAsset(request.batteryAssetId(), AssetType.BATTERY, storeSku.merchantId(), storeSku.storeId());
        validateSingleInvestor(frameAsset, batteryAsset);
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(request.packageId()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该 SKU 价格"));
        if (packagePrice.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("门店 SKU 已停用");
        }
        var packageTemplate = productRepository.findPackage(request.packageId())
            .orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        if (packageTemplate.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("SKU 已停用");
        }
        if (!packageTemplate.skuId().equals(storeSku.skuId())) {
            throw BusinessException.badRequest("SKU 不属于所选商品链接");
        }
        var customer = resolveCustomer(userAccountId, request.customerName(), request.customerPhone());
        var orderedAt = resolveOrderedAt(request.orderedAt(), allowCustomOrderedAt);
        var leaseMultiplier = normalizeLeaseMultiplier(request.leaseMultiplier());
        var defaultRentalAmount = packagePrice.rentalAmount().multiply(BigDecimal.valueOf(leaseMultiplier));
        var verificationAmount = normalizeVerificationAmount(request.verificationAmount(), defaultRentalAmount);
        var signFeeAmount = effectiveSignFeeAmount(packageTemplate, storeSku);
        var batteryCostAmount = BatteryCostCalculator.calculate(
            sku.batteryCostDailyAmount(),
            sku.batteryCostMonthlyAmount(),
            packageTemplate.leaseUnit(),
            packageTemplate.leaseValue(),
            leaseMultiplier
        );
        var payableAmount = verificationAmount.add(signFeeAmount).add(packagePrice.depositAmount());
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
            verificationAmount,
            verificationAmount,
            signFeeAmount,
            packagePrice.depositAmount(),
            payableAmount,
            BigDecimal.ZERO,
            null,
            packageTemplate.leaseUnit().name(),
            packageTemplate.leaseValue() * leaseMultiplier,
            packageTemplate.totalPeriods() * leaseMultiplier,
            leaseMultiplier,
            packageTemplate.billDayMode().name(),
            packageTemplate.billDay(),
            orderedAt,
            packagePrice.autoRenewEnabled(),
            packagePrice.renewalUnit() == null ? null : packagePrice.renewalUnit().name(),
            packagePrice.renewalValue(),
            packagePrice.renewalAmount(),
            packagePrice.renewalBillingMode().name(),
            packagePrice.renewalDailyAmount(),
            packagePrice.renewalDailyCapEnabled(),
            packagePrice.renewalGraceHours(),
            packagePrice.overdueDailyAmount(),
            request.expectedPickupAt()
        ));
        orderRepository.addItem(
            order.id(),
            OrderItemType.SKU,
            storeSku.id(),
            storeSku.displayName(),
            leaseMultiplier,
            verificationAmount.divide(BigDecimal.valueOf(leaseMultiplier), 2, RoundingMode.HALF_UP),
            verificationAmount
        );
        orderRepository.addItem(order.id(), OrderItemType.SIGN_FEE, null, "办单费", 1, signFeeAmount, signFeeAmount);
        if (packagePrice.depositAmount().signum() > 0) {
            orderRepository.addItem(order.id(), OrderItemType.DEPOSIT, null, "押金", 1, packagePrice.depositAmount(), packagePrice.depositAmount());
        }
        if (request.frameAssetId() != null) {
            orderRepository.addItem(order.id(), OrderItemType.ASSET_FRAME, request.frameAssetId(), frameAsset.assetTypeName() + "资产", 1, BigDecimal.ZERO, BigDecimal.ZERO);
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
            verificationAmount,
            "DIRECT",
            signFeeAmount,
            batteryCostAmount
        ));
        order = orderRepository.updateSettlementSnapshot(order.id(), snapshot.id());
        return toResponse(order);
    }

    private OrderResponse updateOrderInternal(Long id, OrderUpdateRequest request) {
        var order = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        var bills = billRepository.listBills(null, order.id(), null);
        assertCanEditOrder(order, bills);

        var targetStoreSkuId = request.storeSkuId() == null ? order.storeSkuId() : request.storeSkuId();
        var targetPackageId = request.packageId() == null ? order.packageId() : request.packageId();
        var productSelectionChanged = !order.storeSkuId().equals(targetStoreSkuId)
            || !order.packageId().equals(targetPackageId);
        var product = resolveEditableProduct(targetStoreSkuId, targetPackageId, productSelectionChanged);
        authorizationService.requireStoreAccess(product.storeSku().merchantId(), product.storeSku().storeId());
        var frameAsset = validateOrderAsset(
            request.frameAssetId(),
            AssetType.VEHICLE_FRAME,
            product.storeSku().merchantId(),
            product.storeSku().storeId()
        );
        if (frameAsset != null && frameAsset.assetType().isIntegratedVehicle() && request.batteryAssetId() != null) {
            throw BusinessException.badRequest("车电一体资产只需绑定主资产，无需再选择电池资产");
        }
        var batteryAsset = validateOrderAsset(
            request.batteryAssetId(),
            AssetType.BATTERY,
            product.storeSku().merchantId(),
            product.storeSku().storeId()
        );
        validateSingleInvestor(frameAsset, batteryAsset);

        var customer = resolveCustomer(request.userAccountId(), request.customerName(), request.customerPhone());
        var orderedAt = resolveOrderedAt(request.orderedAt() == null ? order.orderedAt() : request.orderedAt(), true);
        var leaseMultiplier = request.leaseMultiplier() == null ? order.leaseMultiplier() : normalizeLeaseMultiplier(request.leaseMultiplier());
        var leaseSelectionChanged = productSelectionChanged || !leaseMultiplier.equals(order.leaseMultiplier());
        var signFeeAmount = productSelectionChanged
            ? effectiveSignFeeAmount(product.packageTemplate(), product.storeSku())
            : order.signFeeAmount();
        var depositAmount = productSelectionChanged ? product.packagePrice().depositAmount() : order.depositAmount();
        var verificationAmount = normalizeVerificationAmount(
            request.verificationAmount(),
            leaseSelectionChanged
                ? product.packagePrice().rentalAmount().multiply(BigDecimal.valueOf(leaseMultiplier))
                : order.verificationAmount()
        );
        var payableAmount = verificationAmount
            .add(signFeeAmount)
            .add(depositAmount);
        var updated = orderRepository.updateEditableDetails(new OrderRepository.EditableOrderRow(
            order.id(),
            request.userAccountId(),
            customer.name(),
            customer.phone(),
            product.storeSku().merchantId(),
            product.storeSku().storeId(),
            product.storeSku().id(),
            product.storeSku().skuId(),
            product.packageTemplate().id(),
            request.frameAssetId(),
            request.batteryAssetId(),
            verificationAmount,
            verificationAmount,
            signFeeAmount,
            depositAmount,
            payableAmount,
            leaseSelectionChanged ? product.packageTemplate().leaseUnit().name() : order.leaseUnit(),
            leaseSelectionChanged ? product.packageTemplate().leaseValue() * leaseMultiplier : order.leaseValue(),
            leaseSelectionChanged ? product.packageTemplate().totalPeriods() * leaseMultiplier : order.totalPeriods(),
            leaseMultiplier,
            productSelectionChanged ? product.packageTemplate().billDayMode().name() : order.billDayMode(),
            productSelectionChanged ? product.packageTemplate().billDay() : order.billDay(),
            orderedAt,
            productSelectionChanged ? product.packagePrice().autoRenewEnabled() : order.autoRenewEnabled(),
            productSelectionChanged
                ? product.packagePrice().renewalUnit() == null ? null : product.packagePrice().renewalUnit().name()
                : order.renewalUnit(),
            productSelectionChanged ? product.packagePrice().renewalValue() : order.renewalValue(),
            productSelectionChanged ? product.packagePrice().renewalAmount() : order.renewalAmount(),
            productSelectionChanged ? product.packagePrice().renewalBillingMode().name() : order.renewalBillingMode(),
            productSelectionChanged ? product.packagePrice().renewalDailyAmount() : order.renewalDailyAmount(),
            productSelectionChanged ? product.packagePrice().renewalDailyCapEnabled() : order.renewalDailyCapEnabled(),
            productSelectionChanged ? product.packagePrice().renewalGraceHours() : order.renewalGraceHours(),
            productSelectionChanged ? product.packagePrice().overdueDailyAmount() : order.overdueDailyAmount()
        ));

        replaceEditableOrderItems(updated, frameAsset);
        rebuildEditableOrderBills(updated);
        var targetSku = productRepository.findSku(updated.skuId())
            .orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        var batteryCostAmount = BatteryCostCalculator.calculate(
            targetSku.batteryCostDailyAmount(),
            targetSku.batteryCostMonthlyAmount(),
            product.packageTemplate().leaseUnit(),
            product.packageTemplate().leaseValue(),
            leaseMultiplier
        );
        var snapshot = settlementService.createOrderSnapshot(new SnapshotCreateRequest(
            "ORDER",
            updated.id(),
            updated.storeSkuId(),
            updated.frameAssetId(),
            updated.batteryAssetId(),
            updated.verificationAmount(),
            "DIRECT",
            updated.signFeeAmount(),
            batteryCostAmount
        ));
        updated = orderRepository.updateSettlementSnapshot(updated.id(), snapshot.id());
        orderRepository.addLog(
            updated.id(),
            updated.orderStatus(),
            updated.orderStatus(),
            OrderOperationType.EDIT,
            currentAccountId(),
            "编辑待支付订单资料并重建账单计划"
        );
        return toResponse(updated);
    }

    private EditableProductSelection resolveEditableProduct(Long storeSkuId, Long packageId, boolean requireActive) {
        var storeSku = productRepository.findStoreSku(storeSkuId)
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (requireActive && storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var merchant = merchantRepository.findById(storeSku.merchantId())
            .orElseThrow(() -> BusinessException.badRequest("商户不存在"));
        if (requireActive && merchant.status() != MerchantStatus.ENABLED) {
            throw BusinessException.badRequest("商户已停用");
        }
        var store = storeRepository.findById(storeSku.storeId())
            .orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        if (requireActive && store.status() != StoreStatus.ENABLED) {
            throw BusinessException.badRequest("门店已停用");
        }
        if (!store.merchantId().equals(storeSku.merchantId())) {
            throw BusinessException.badRequest("门店商品商户关系异常");
        }
        var sku = productRepository.findSku(storeSku.skuId())
            .orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        if (requireActive && sku.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("商品链接已停用");
        }
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(packageId))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该 SKU 价格"));
        if (requireActive && packagePrice.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("门店 SKU 已停用");
        }
        var packageTemplate = productRepository.findPackage(packageId)
            .orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        if (requireActive && packageTemplate.status() != ProductStatus.ENABLED) {
            throw BusinessException.badRequest("SKU 已停用");
        }
        if (!packageTemplate.skuId().equals(storeSku.skuId())) {
            throw BusinessException.badRequest("SKU 不属于所选商品链接");
        }
        return new EditableProductSelection(storeSku, packagePrice, packageTemplate);
    }

    private void assertCanEditOrder(RentalOrder order, List<RentalBill> bills) {
        if (order.orderStatus() != OrderStatus.PENDING_PAYMENT) {
            throw BusinessException.badRequest("只有待支付订单可以编辑；已进入履约流程的订单请使用对应业务操作");
        }
        if (order.paidAmount() != null && order.paidAmount().signum() > 0) {
            throw BusinessException.badRequest("订单已产生付款，不能直接编辑");
        }
        if (billRepository.hasFinancialActivity(order.id())) {
            throw BusinessException.badRequest("订单已发起支付、代扣或核销，不能直接编辑");
        }
        var unsafeBill = bills.stream().anyMatch(bill ->
            bill.billStatus() != BillStatus.PENDING_PAYMENT
                || bill.paidAmount().signum() > 0
                || (bill.billType() != BillType.INITIAL && bill.billType() != BillType.PERIODIC)
        );
        if (unsafeBill) {
            throw BusinessException.badRequest("订单账单已进入支付或异常流程，不能直接编辑");
        }
    }

    private void replaceEditableOrderItems(RentalOrder order, AssetItem frameAsset) {
        var storeSku = productRepository.findStoreSku(order.storeSkuId())
            .orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        orderRepository.deleteItems(order.id());
        var leaseMultiplier = normalizeLeaseMultiplier(order.leaseMultiplier());
        orderRepository.addItem(
            order.id(),
            OrderItemType.SKU,
            storeSku.id(),
            storeSku.displayName(),
            leaseMultiplier,
            order.verificationAmount().divide(BigDecimal.valueOf(leaseMultiplier), 2, RoundingMode.HALF_UP),
            order.verificationAmount()
        );
        orderRepository.addItem(order.id(), OrderItemType.SIGN_FEE, null, "签单费", 1, order.signFeeAmount(), order.signFeeAmount());
        if (order.depositAmount().signum() > 0) {
            orderRepository.addItem(order.id(), OrderItemType.DEPOSIT, null, "押金", 1, order.depositAmount(), order.depositAmount());
        }
        if (order.frameAssetId() != null) {
            orderRepository.addItem(order.id(), OrderItemType.ASSET_FRAME, order.frameAssetId(), frameAsset.assetTypeName() + "资产", 1, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (order.batteryAssetId() != null) {
            orderRepository.addItem(order.id(), OrderItemType.ASSET_BATTERY, order.batteryAssetId(), "电池资产", 1, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private void rebuildEditableOrderBills(RentalOrder order) {
        billRepository.deleteEditablePlan(order.id());
        var totalPeriods = Math.max(order.totalPeriods() == null ? 1 : order.totalPeriods(), 1);
        var batchNo = nextBillBatchNo();
        for (var periodNo = 1; periodNo <= totalPeriods; periodNo++) {
            var billType = periodNo == 1 ? BillType.INITIAL : BillType.PERIODIC;
            var rentAmount = editablePeriodRentAmount(order, periodNo);
            var payableAmount = billType == BillType.INITIAL
                ? rentAmount.add(order.signFeeAmount()).add(order.depositAmount()).setScale(2, RoundingMode.HALF_UP)
                : rentAmount;
            var dueAt = billType == BillType.INITIAL
                ? order.orderedAt()
                : editablePeriodDueAt(order, periodNo);
            var bill = billRepository.createBill(new BillRepository.BillCreateRow(
                nextBillNo(),
                order.id(),
                order.userAccountId(),
                order.merchantId(),
                order.storeId(),
                billType,
                periodNo,
                BillStatus.PENDING_PAYMENT,
                dueAt,
                payableAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "订单编辑后重建账单计划",
                batchNo
            ));
            billRepository.addItem(
                bill.id(),
                BillItemType.RENT,
                billType == BillType.INITIAL ? "首期租金" : "第 " + periodNo + " 期租金",
                rentAmount
            );
            if (billType == BillType.INITIAL && order.signFeeAmount().signum() > 0) {
                billRepository.addItem(bill.id(), BillItemType.SIGN_FEE, "签单费", order.signFeeAmount());
            }
            if (billType == BillType.INITIAL && order.depositAmount().signum() > 0) {
                billRepository.addItem(bill.id(), BillItemType.DEPOSIT, "押金", order.depositAmount());
            }
            billRepository.addLog(
                bill.id(),
                null,
                BillStatus.PENDING_PAYMENT,
                BillOperationType.EDIT,
                currentAccountId(),
                "订单编辑后重建第 " + periodNo + " 期账单"
            );
        }
        billRepository.createBatch(
            batchNo,
            BillGenerationType.PLAN,
            order.id(),
            totalPeriods,
            "订单编辑后重建账单计划"
        );
    }

    private BigDecimal editablePeriodRentAmount(RentalOrder order, int periodNo) {
        var totalPeriods = Math.max(order.totalPeriods() == null ? 1 : order.totalPeriods(), 1);
        var base = order.rentalAmount().divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.DOWN);
        if (periodNo == totalPeriods) {
            return order.rentalAmount().subtract(base.multiply(BigDecimal.valueOf(totalPeriods - 1))).setScale(2, RoundingMode.HALF_UP);
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime editablePeriodDueAt(RentalOrder order, int periodNo) {
        var base = order.expectedPickupAt();
        if (base == null) {
            base = order.orderedAt() != null ? order.orderedAt() : order.createdAt();
        }
        if ("MONTH".equals(order.leaseUnit())) {
            return base.plusDays(30L * (periodNo - 1L));
        }
        var totalPeriods = Math.max(order.totalPeriods() == null ? 1 : order.totalPeriods(), 1);
        var stepDays = Math.max(1, order.leaseValue() / totalPeriods);
        return base.plusDays((long) stepDays * (periodNo - 1L));
    }

    @Transactional
    public OrderResponse transition(Long id, OrderTransitionRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        var target = parseStatus(request.targetStatus());
        stateMachine.assertCanTransit(order.orderStatus(), target);
        var now = LocalDateTime.now();
        var startedAt = target == OrderStatus.RENTING ? now : null;
        var expectedReturnAt = target == OrderStatus.RENTING ? initialExpectedReturnAt(now, order) : null;
        var returnedAt = target == OrderStatus.COMPLETED ? now : null;
        var updated = orderRepository.updateStatus(id, target, startedAt, expectedReturnAt, returnedAt);
        orderRepository.addLog(id, order.orderStatus(), target, OrderOperationType.TRANSITION, currentAccountId(), request.remark());
        return toResponse(updated);
    }

    @Transactional
    public OrderResponse grantLeaseBonus(Long id, OrderLeaseBonusRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        assertCanGrantLeaseBonus(order);
        if (request == null || request.bonusDays() == null || request.bonusDays() < 1 || request.bonusDays() > 999) {
            throw BusinessException.badRequest("赠送天数必须在 1 到 999 天之间");
        }
        var bonusType = parseLeaseBonusType(request.bonusType());
        var bonusSummary = orderRepository.summarizeLeaseBonuses(order.id());
        var fromStatus = order.orderStatus();
        var expectedReturnBefore = order.expectedReturnAt();
        var expectedReturnAfter = expectedReturnBefore;
        var targetStatus = order.orderStatus();
        if (expectedReturnBefore != null || order.leaseStartedAt() != null) {
            if (expectedReturnBefore == null) {
                expectedReturnBefore = baseExpectedReturnAt(order.leaseStartedAt(), order)
                    .plusDays(bonusSummary.totalDays());
            }
            expectedReturnAfter = expectedReturnBefore.plusDays(request.bonusDays());
            if (order.orderStatus() == OrderStatus.PENDING_RETURN && expectedReturnAfter.isAfter(LocalDateTime.now())) {
                targetStatus = OrderStatus.RENTING;
            }
            order = orderRepository.updateLeaseBonusDeadline(order.id(), expectedReturnAfter, targetStatus);
        }
        var remark = normalizedBonusRemark(request.remark(), bonusType);
        orderRepository.addLeaseBonus(new OrderRepository.LeaseBonusCreateRow(
            order.id(),
            bonusType,
            request.bonusDays(),
            currentAccountId(),
            remark,
            expectedReturnBefore,
            expectedReturnAfter
        ));
        orderRepository.addLog(
            order.id(),
            fromStatus,
            targetStatus,
            OrderOperationType.LEASE_BONUS,
            currentAccountId(),
            leaseBonusLogRemark(bonusType, request.bonusDays(), remark, expectedReturnAfter)
        );
        return toResponse(orderRepository.findById(order.id()).orElseThrow());
    }

    @Transactional
    public OrderResponse cancel(Long id, OrderCancelRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        stateMachine.assertCanCancel(order.orderStatus());
        var updated = orderRepository.cancel(id, request.reason());
        orderRepository.addLog(id, order.orderStatus(), OrderStatus.CANCELLED, OrderOperationType.CANCEL, currentAccountId(), request.reason());
        return toResponse(updated);
    }

    @Transactional
    public OrderResponse markException(Long id, OrderExceptionRequest request) {
        authorizationService.requirePermission("order.operate");
        var order = ensureOrder(id);
        authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        stateMachine.assertCanMarkException(order.orderStatus());
        var updated = orderRepository.markException(id, request.reason());
        orderRepository.addLog(id, order.orderStatus(), OrderStatus.EXCEPTION, OrderOperationType.MARK_EXCEPTION, currentAccountId(), request.reason());
        return toResponse(updated);
    }

    private RentalOrder ensureOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private LocalDateTime initialExpectedReturnAt(LocalDateTime startedAt, RentalOrder order) {
        return baseExpectedReturnAt(startedAt, order)
            .plusDays(orderRepository.summarizeLeaseBonuses(order.id()).totalDays());
    }

    private LocalDateTime baseExpectedReturnAt(LocalDateTime startedAt, RentalOrder order) {
        if ("MONTH".equals(order.leaseUnit())) {
            return startedAt.plusDays(30L * order.leaseValue());
        }
        return startedAt.plusDays(order.leaseValue());
    }

    private OrderResponse toResponse(RentalOrder order) {
        var display = orderRepository.findDisplayInfo(order.id()).orElse(new OrderRepository.OrderDisplayRow(null, null, null, null, null, null, null));
        var leaseBonusSummary = orderRepository.summarizeLeaseBonuses(order.id());
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
            order.verificationAmount(),
            order.signFeeAmount(),
            order.depositAmount(),
            order.payableAmount(),
            order.paidAmount(),
            order.settlementSnapshotId(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.leaseMultiplier(),
            order.billDayMode(),
            order.billDay(),
            order.orderedAt(),
            order.autoRenewEnabled(),
            order.renewalUnit(),
            order.renewalValue(),
            order.renewalAmount(),
            order.renewalBillingMode(),
            order.renewalDailyAmount(),
            order.renewalDailyCapEnabled(),
            order.renewalGraceHours(),
            order.overdueDailyAmount(),
            order.renewalCount(),
            leaseBonusSummary.reviewDays(),
            leaseBonusSummary.campaignDays(),
            leaseBonusSummary.totalDays(),
            order.expectedPickupAt(),
            order.leaseStartedAt(),
            order.expectedReturnAt(),
            order.returnedAt(),
            order.cancelledAt(),
            order.cancelReason(),
            order.exceptionReason(),
            order.createdAt(),
            orderRepository.listItems(order.id()).stream().map(this::toItemResponse).toList(),
            orderRepository.listLeaseBonuses(order.id()).stream().map(this::toLeaseBonusResponse).toList(),
            orderRepository.listLogs(order.id()).stream().map(this::toLogResponse).toList()
        );
    }

    private BigDecimal normalizeVerificationAmount(BigDecimal value, BigDecimal fallback) {
        var amount = value == null ? fallback : value;
        if (amount == null) {
            throw BusinessException.badRequest("请输入实际核销金额");
        }
        var normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw BusinessException.badRequest("实际核销金额不能小于 0");
        }
        return normalized;
    }

    private Integer normalizeLeaseMultiplier(Integer value) {
        var multiplier = value == null ? 1 : value;
        if (multiplier < 1 || multiplier > 120) {
            throw BusinessException.badRequest("租期倍数必须在 1 到 120 之间");
        }
        return multiplier;
    }

    private void assertCanGrantLeaseBonus(RentalOrder order) {
        if (order.orderStatus() == OrderStatus.COMPLETED
            || order.orderStatus() == OrderStatus.CANCELLED
            || order.orderStatus() == OrderStatus.EXCEPTION) {
            throw BusinessException.badRequest("终态订单不能再赠送租期");
        }
        if (order.orderStatus() == OrderStatus.OVERDUE || order.orderStatus() == OrderStatus.PENDING_SUPPLEMENT) {
            throw BusinessException.badRequest("订单已进入逾期或补缴流程，请先处理相关账单");
        }
    }

    private OrderLeaseBonusType parseLeaseBonusType(String value) {
        try {
            return OrderLeaseBonusType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("赠送类型只支持好评赠送或活动赠送");
        }
    }

    private String normalizedBonusRemark(String remark, OrderLeaseBonusType bonusType) {
        var normalized = normalizeText(remark);
        if (normalized != null) {
            return normalized;
        }
        return bonusType == OrderLeaseBonusType.REVIEW ? "客户好评赠送" : "门店活动赠送";
    }

    private String leaseBonusLogRemark(
        OrderLeaseBonusType bonusType,
        Integer bonusDays,
        String remark,
        LocalDateTime expectedReturnAfter
    ) {
        var typeText = bonusType == OrderLeaseBonusType.REVIEW ? "好评赠送" : "活动赠送";
        var deadlineText = expectedReturnAfter == null
            ? "，将在起租时自动计入"
            : "，预计归还顺延至 " + expectedReturnAfter;
        return typeText + " " + bonusDays + " 天（" + remark + "）" + deadlineText;
    }

    private OrderLeaseBonusResponse toLeaseBonusResponse(OrderLeaseBonus item) {
        return new OrderLeaseBonusResponse(
            item.id(),
            item.orderId(),
            item.bonusType().name(),
            item.bonusDays(),
            item.operatorAccountId(),
            item.remark(),
            item.expectedReturnBefore(),
            item.expectedReturnAfter(),
            item.createdAt()
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

    private record EditableProductSelection(
        StoreSku storeSku,
        StoreSkuPackage packagePrice,
        ProductPackage packageTemplate
    ) {
    }

    private BigDecimal effectiveSignFeeAmount(ProductPackage packageTemplate, StoreSku storeSku) {
        return packageTemplate.signFeeAmount() == null ? storeSku.signFeeAmount() : packageTemplate.signFeeAmount();
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

    private String nextBillNo() {
        return "BIL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextBillBatchNo() {
        return "BGB-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
