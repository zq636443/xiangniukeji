package com.xniu.rental.asset.service;

import com.xniu.rental.asset.dto.AssetDetailResponse;
import com.xniu.rental.asset.dto.AssetMaintenancePartRequest;
import com.xniu.rental.asset.dto.AssetMaintenancePartResponse;
import com.xniu.rental.asset.dto.AssetMaintenanceRequest;
import com.xniu.rental.asset.dto.AssetMaintenanceResponse;
import com.xniu.rental.asset.dto.AssetRentalBillResponse;
import com.xniu.rental.asset.dto.AssetRentalRecordResponse;
import com.xniu.rental.asset.dto.AssetResponse;
import com.xniu.rental.asset.dto.SparePartRequest;
import com.xniu.rental.asset.dto.SparePartResponse;
import com.xniu.rental.asset.dto.SparePartStockAdjustRequest;
import com.xniu.rental.asset.dto.SparePartStockLogResponse;
import com.xniu.rental.asset.dto.SparePartTransferRequest;
import com.xniu.rental.asset.dto.StoreSparePartStockResponse;
import com.xniu.rental.asset.model.AssetItem;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.asset.repository.MaintenanceRepository;
import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.externalorder.service.ExternalRentalOrderService;
import com.xniu.rental.investor.repository.InvestorRepository;
import com.xniu.rental.merchant.repository.MerchantRepository;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final BillRepository billRepository;
    private final InvestorRepository investorRepository;
    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;
    private final AuthorizationService authorizationService;
    private final ExternalRentalOrderService externalRentalOrderService;

    public MaintenanceService(
        MaintenanceRepository maintenanceRepository,
        AssetRepository assetRepository,
        OrderRepository orderRepository,
        BillRepository billRepository,
        InvestorRepository investorRepository,
        MerchantRepository merchantRepository,
        StoreRepository storeRepository,
        AuthorizationService authorizationService,
        ExternalRentalOrderService externalRentalOrderService
    ) {
        this.maintenanceRepository = maintenanceRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.billRepository = billRepository;
        this.investorRepository = investorRepository;
        this.merchantRepository = merchantRepository;
        this.storeRepository = storeRepository;
        this.authorizationService = authorizationService;
        this.externalRentalOrderService = externalRentalOrderService;
    }

    public List<SparePartResponse> listParts(String keyword, String status) {
        authorizationService.requirePermission("inventory.read");
        return maintenanceRepository.listParts(keyword, status).stream().map(this::toPartResponse).toList();
    }

    @Transactional
    public SparePartResponse createPart(SparePartRequest request) {
        authorizationService.requirePermission("inventory.operate");
        var procurementPrice = money(request.procurementPrice());
        var unitPrice = money(request.unitPrice());
        var buybackPrice = money(request.buybackPrice() == null ? request.unitPrice() : request.buybackPrice());
        var initialQuantity = request.initialQuantity() == null ? 0 : request.initialQuantity();
        var part = maintenanceRepository.createPart(nextPartCode(), request.partName(), request.spec(), request.unit(), procurementPrice, unitPrice, buybackPrice, initialQuantity);
        if (initialQuantity > 0) {
            maintenanceRepository.addStockLog(part.id(), null, null, "PLATFORM_INBOUND", initialQuantity, procurementPrice, procurementPrice.multiply(BigDecimal.valueOf(initialQuantity)), "PART", part.id(), currentAccountId(), "新建配件平台初始库存");
        }
        return toPartResponse(part);
    }

    @Transactional
    public SparePartResponse updatePart(Long id, SparePartRequest request) {
        authorizationService.requirePermission("inventory.operate");
        ensurePart(id);
        return toPartResponse(maintenanceRepository.updatePart(
            id,
            request.partName(),
            request.spec(),
            request.unit(),
            money(request.procurementPrice()),
            money(request.unitPrice()),
            money(request.buybackPrice() == null ? request.unitPrice() : request.buybackPrice())
        ));
    }

    @Transactional
    public SparePartResponse inbound(Long id, SparePartStockAdjustRequest request) {
        authorizationService.requirePermission("inventory.operate");
        var part = ensurePart(id);
        if (request.quantity() == null || request.quantity() <= 0) {
            throw BusinessException.badRequest("入库数量必须大于 0");
        }
        var unitPrice = money(request.unitPrice() == null ? part.procurementPrice() : request.unitPrice());
        changePlatformStock(part.id(), "PLATFORM_INBOUND", request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "平台配件入库"));
        return toPartResponse(maintenanceRepository.findPart(id).orElseThrow());
    }

    @Transactional
    public SparePartResponse adjust(Long id, SparePartStockAdjustRequest request) {
        authorizationService.requirePermission("inventory.operate");
        var part = ensurePart(id);
        if (request.quantity() == null || request.quantity() == 0) {
            throw BusinessException.badRequest("调整数量不能为 0");
        }
        var unitPrice = money(request.unitPrice() == null ? part.unitPrice() : request.unitPrice());
        if (request.storeId() == null) {
            if (maintenanceRepository.getPlatformStock(part.id()) + request.quantity() < 0) {
                throw BusinessException.badRequest("平台库存不足，不能调整为负数");
            }
            changePlatformStock(part.id(), "PLATFORM_ADJUST", request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "平台库存调整"));
        } else {
            var store = resolveStockStore(request.storeId());
            if (maintenanceRepository.getStoreStock(store.id(), part.id()) + request.quantity() < 0) {
                throw BusinessException.badRequest("门店库存不足，不能调整为负数");
            }
            changeStoreStock(part.id(), store.merchantId(), store.id(), "STORE_ADJUST", request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "门店库存调整"));
        }
        return toPartResponse(maintenanceRepository.findPart(id).orElseThrow());
    }

    @Transactional
    public SparePartResponse purchase(Long id, SparePartStockAdjustRequest request) {
        authorizationService.requirePermission("inventory.operate");
        var part = ensurePart(id);
        if (request.quantity() == null || request.quantity() <= 0) {
            throw BusinessException.badRequest("采购数量必须大于 0");
        }
        var store = resolveStockStore(request.storeId());
        if (maintenanceRepository.getPlatformStock(part.id()) < request.quantity()) {
            throw BusinessException.badRequest("平台库存不足，不能下发到门店");
        }
        var unitPrice = money(request.unitPrice() == null ? part.unitPrice() : request.unitPrice());
        changePlatformStock(part.id(), "STORE_PURCHASE_OUT", -request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "门店采购出库"));
        changeStoreStock(part.id(), store.merchantId(), store.id(), "STORE_PURCHASE_IN", request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "门店采购入库"));
        return toPartResponse(maintenanceRepository.findPart(id).orElseThrow());
    }

    @Transactional
    public SparePartResponse buyback(Long id, SparePartStockAdjustRequest request) {
        authorizationService.requirePermission("inventory.operate");
        var part = ensurePart(id);
        if (request.quantity() == null || request.quantity() <= 0) {
            throw BusinessException.badRequest("退仓数量必须大于 0");
        }
        var store = resolveStockStore(request.storeId());
        if (maintenanceRepository.getStoreStock(store.id(), part.id()) < request.quantity()) {
            throw BusinessException.badRequest("门店库存不足，不能回收");
        }
        var unitPrice = money(request.unitPrice() == null ? part.buybackPrice() : request.unitPrice());
        changeStoreStock(part.id(), store.merchantId(), store.id(), "STORE_BUYBACK_OUT", -request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "门店退仓出库"));
        changePlatformStock(part.id(), "STORE_BUYBACK_IN", request.quantity(), unitPrice, "PART", part.id(), defaultRemark(request.remark(), "平台回收入库"));
        return toPartResponse(maintenanceRepository.findPart(id).orElseThrow());
    }

    @Transactional
    public List<StoreSparePartStockResponse> transferStoreStock(SparePartTransferRequest request) {
        authorizationService.requirePermission("inventory.operate");
        var part = ensurePart(request.partId());
        if (request.quantity() == null || request.quantity() <= 0) {
            throw BusinessException.badRequest("调拨数量必须大于 0");
        }
        var fromStore = resolveStockStore(request.fromStoreId());
        var toStore = storeRepository.findById(request.toStoreId()).orElseThrow(() -> BusinessException.badRequest("调入门店不存在"));
        authorizationService.requireStoreAccess(toStore.merchantId(), toStore.id());
        if (!fromStore.merchantId().equals(toStore.merchantId())) {
            throw BusinessException.badRequest("只支持同一商户下门店之间调拨配件");
        }
        if (fromStore.id().equals(toStore.id())) {
            throw BusinessException.badRequest("调出门店和调入门店不能相同");
        }
        if (maintenanceRepository.getStoreStock(fromStore.id(), part.id()) < request.quantity()) {
            throw BusinessException.badRequest(part.partName() + "库存不足，无法调拨");
        }
        var unitPrice = money(request.unitPrice() == null ? part.unitPrice() : request.unitPrice());
        changeStoreStock(part.id(), fromStore.merchantId(), fromStore.id(), "STORE_TRANSFER_OUT", -request.quantity(), unitPrice, "STORE_TRANSFER", toStore.id(), defaultRemark(request.remark(), "门店配件调拨出库"));
        changeStoreStock(part.id(), toStore.merchantId(), toStore.id(), "STORE_TRANSFER_IN", request.quantity(), unitPrice, "STORE_TRANSFER", fromStore.id(), defaultRemark(request.remark(), "门店配件调拨入库"));
        return listStoreStocks(part.id(), fromStore.merchantId(), null).stream()
            .filter(row -> row.storeId().equals(fromStore.id()) || row.storeId().equals(toStore.id()))
            .toList();
    }

    public List<SparePartStockLogResponse> listStockLogs(Long partId, Long merchantId, Long storeId) {
        authorizationService.requirePermission("inventory.read");
        Long resolvedMerchantId = merchantId;
        if (storeId != null) {
            var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
            authorizationService.requireStoreAccess(store.merchantId(), store.id());
            resolvedMerchantId = store.merchantId();
        }
        if (resolvedMerchantId != null && storeId == null && AuthContext.get() != null && !AuthContext.get().hasPermission("system.admin")) {
            final Long targetMerchantId = resolvedMerchantId;
            var hasMerchantAccess = AuthContext.get().account().storeScopes().stream().anyMatch(scope -> scope.merchantId().equals(targetMerchantId));
            if (!hasMerchantAccess) {
                throw BusinessException.forbidden("没有该商户数据权限");
            }
        }
        return maintenanceRepository.listStockLogs(partId, resolvedMerchantId, storeId).stream().map(this::toStockLogResponse).toList();
    }

    public List<StoreSparePartStockResponse> listStoreStocks(Long partId, Long merchantId, Long storeId) {
        authorizationService.requirePermission("inventory.read");
        if (storeId != null) {
            var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
            authorizationService.requireStoreAccess(store.merchantId(), store.id());
        }
        if (merchantId != null && storeId == null && AuthContext.get() != null && !AuthContext.get().hasPermission("system.admin")) {
            var hasMerchantAccess = AuthContext.get().account().storeScopes().stream().anyMatch(scope -> scope.merchantId().equals(merchantId));
            if (!hasMerchantAccess) {
                throw BusinessException.forbidden("没有该商户数据权限");
            }
        }
        return maintenanceRepository.listStoreStocks(partId, merchantId, storeId).stream()
            .map(row -> new StoreSparePartStockResponse(
                row.merchantId(),
                row.merchantName(),
                row.storeId(),
                row.storeName(),
                row.partId(),
                row.partName(),
                row.stockQuantity(),
                row.avgUnitPrice(),
                row.avgUnitPrice().multiply(BigDecimal.valueOf(row.stockQuantity())).setScale(2, RoundingMode.HALF_UP)
            ))
            .toList();
    }

    public List<AssetMaintenanceResponse> listMaintenances(Long assetId, Long orderId, Long storeId) {
        authorizationService.requirePermission("maintenance.read");
        if (assetId != null) {
            ensureAssetStoreAccess(ensureAsset(assetId));
        }
        if (orderId != null) {
            var order = orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
            authorizationService.requireStoreAccess(order.merchantId(), order.storeId());
        }
        if (storeId != null) {
            var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
            authorizationService.requireStoreAccess(store.merchantId(), store.id());
        }
        var rows = maintenanceRepository.listMaintenances(assetId, orderId);
        var current = AuthContext.get();
        if (current != null && !current.hasPermission("system.admin")) {
            var allowedStoreIds = current.account().storeScopes().stream()
                .flatMap(scope -> {
                    if ("ALL_MERCHANT_STORES".equals(scope.scopeType())) {
                        return storeRepository.findByMerchantId(scope.merchantId()).stream().map(store -> store.id());
                    }
                    return scope.storeId() == null ? java.util.stream.Stream.<Long>empty() : java.util.stream.Stream.of(scope.storeId());
                })
                .toList();
            rows = rows.stream().filter(row -> row.storeId() != null && allowedStoreIds.contains(row.storeId())).toList();
        }
        if (storeId != null) {
            rows = rows.stream().filter(row -> storeId.equals(row.storeId())).toList();
        }
        return rows.stream().map(this::toMaintenanceResponse).toList();
    }

    @Transactional
    public AssetMaintenanceResponse createMaintenance(AssetMaintenanceRequest request) {
        authorizationService.requirePermission("maintenance.operate");
        var asset = ensureAsset(request.assetId());
        ensureAssetStoreAccess(asset);
        if (request.orderId() != null) {
            var order = orderRepository.findById(request.orderId()).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
            if (!request.assetId().equals(order.frameAssetId()) && !request.assetId().equals(order.batteryAssetId())) {
                throw BusinessException.badRequest("该订单未使用所选资产");
            }
        }

        var storeId = resolveMaintenanceStoreId(asset, request.storeId());
        var order = request.orderId() == null ? null : orderRepository.findById(request.orderId()).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        var responsibilityType = resolveResponsibilityType(request.responsibilityType());
        var costBearerType = resolveCostBearerType(request.costBearerType(), responsibilityType);
        var costBearerId = resolveCostBearerId(costBearerType, request.costBearerId(), asset, order);
        var partsCost = calculateAndCheckParts(storeId, request.parts());
        var laborCost = money(request.laborCost());
        var externalCost = money(request.externalCost());
        var totalCost = partsCost.add(laborCost).add(externalCost).setScale(2, RoundingMode.HALF_UP);
        var merchantReimbursementAmount = merchantReimbursementAmount(responsibilityType, partsCost);
        var investorDeductAmount = investorDeductAmount();
        var customerChargeAmount = customerChargeAmount(responsibilityType, totalCost);
        var record = maintenanceRepository.createMaintenance(
            nextMaintenanceNo(),
            asset.id(),
            request.orderId(),
            storeId,
            request.maintenanceType(),
            request.maintenanceStatus() == null || request.maintenanceStatus().isBlank() ? "COMPLETED" : request.maintenanceStatus(),
            request.startedAt() == null ? LocalDateTime.now() : request.startedAt(),
            request.completedAt(),
            laborCost,
            externalCost,
            partsCost,
            totalCost,
            merchantReimbursementAmount,
            investorDeductAmount,
            customerChargeAmount,
            responsibilityType,
            costBearerType,
            costBearerId,
            currentAccountId(),
            request.remark()
        );
        consumeParts(record.id(), storeId, request.parts());
        assetRepository.insertStatusLog(asset.id(), asset.status(), asset.status(), currentAccountId(), defaultRemark(request.remark(), "新增维修记录"));
        return toMaintenanceResponse(maintenanceRepository.findMaintenance(record.id()).orElseThrow());
    }

    public AssetDetailResponse getAssetDetail(Long assetId) {
        authorizationService.requirePermission("asset.read");
        var asset = ensureAsset(assetId);
        var current = AuthContext.get();
        if (current != null && current.account().investorId() != null && !current.account().investorId().equals(asset.investorId())) {
            throw BusinessException.forbidden("没有该资产权限");
        }
        if (current != null && current.account().investorId() == null && !current.hasPermission("system.admin")) {
            ensureAssetStoreAccess(asset);
        }
        var rentals = new java.util.ArrayList<AssetRentalRecordResponse>();
        rentals.addAll(orderRepository.listByAsset(assetId).stream().map(this::toRentalRecordResponse).toList());
        rentals.addAll(externalRentalOrderService.listAssetRentalRecords(assetId));
        rentals.sort((left, right) -> {
            var leftTime = left.createdAt() == null ? LocalDateTime.MIN : left.createdAt();
            var rightTime = right.createdAt() == null ? LocalDateTime.MIN : right.createdAt();
            return rightTime.compareTo(leftTime);
        });
        var maintenances = maintenanceRepository.listMaintenances(assetId, null).stream().map(this::toMaintenanceResponse).toList();
        return new AssetDetailResponse(toAssetResponse(asset), rentals, maintenances);
    }

    private BigDecimal calculateAndCheckParts(Long storeId, List<AssetMaintenancePartRequest> parts) {
        if (parts == null || parts.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        var total = BigDecimal.ZERO;
        for (var item : parts) {
            var part = ensurePart(item.partId());
            var quantity = item.quantity() == null ? 0 : item.quantity();
            if (quantity <= 0) {
                throw BusinessException.badRequest("配件消耗数量必须大于 0");
            }
            if (maintenanceRepository.getStoreStock(storeId, part.id()) < quantity) {
                throw BusinessException.badRequest(part.partName() + "库存不足");
            }
            var unitPrice = money(item.unitPrice() == null ? part.unitPrice() : item.unitPrice());
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void consumeParts(Long maintenanceId, Long storeId, List<AssetMaintenancePartRequest> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        for (var item : parts) {
            var part = ensurePart(item.partId());
            var quantity = item.quantity();
            var unitPrice = money(item.unitPrice() == null ? part.unitPrice() : item.unitPrice());
            var amount = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            maintenanceRepository.addMaintenancePart(maintenanceId, part.id(), part.partName(), quantity, unitPrice, amount, item.remark());
            var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
            changeStoreStock(part.id(), store.merchantId(), storeId, "STORE_CONSUME", -quantity, unitPrice, "MAINTENANCE", maintenanceId, defaultRemark(item.remark(), "维修消耗"));
        }
    }

    private void changePlatformStock(Long partId, String changeType, Integer quantityChange, BigDecimal unitPrice, String refType, Long refId, String remark) {
        maintenanceRepository.changePlatformStock(partId, quantityChange);
        var amount = unitPrice.multiply(BigDecimal.valueOf(Math.abs(quantityChange))).setScale(2, RoundingMode.HALF_UP);
        maintenanceRepository.addStockLog(partId, null, null, changeType, quantityChange, unitPrice, amount, refType, refId, currentAccountId(), remark);
    }

    private void changeStoreStock(Long partId, Long merchantId, Long storeId, String changeType, Integer quantityChange, BigDecimal unitPrice, String refType, Long refId, String remark) {
        maintenanceRepository.changeStoreStock(merchantId, storeId, partId, quantityChange, unitPrice);
        var amount = unitPrice.multiply(BigDecimal.valueOf(Math.abs(quantityChange))).setScale(2, RoundingMode.HALF_UP);
        maintenanceRepository.addStockLog(partId, merchantId, storeId, changeType, quantityChange, unitPrice, amount, refType, refId, currentAccountId(), remark);
    }

    private com.xniu.rental.merchant.model.MerchantStore resolveStockStore(Long requestedStoreId) {
        var current = AuthContext.get();
        Long resolvedStoreId = requestedStoreId;
        if (resolvedStoreId == null && current != null) {
            resolvedStoreId = current.account().storeId();
        }
        if (resolvedStoreId == null) {
            throw BusinessException.badRequest("请选择配件所属门店");
        }
        var store = storeRepository.findById(resolvedStoreId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return store;
    }

    private Long resolveMaintenanceStoreId(AssetItem asset, Long requestedStoreId) {
        var storeId = requestedStoreId == null ? asset.currentStoreId() : requestedStoreId;
        if (storeId == null || asset.currentMerchantId() == null) {
            throw BusinessException.badRequest("资产未分配门店，不能登记维修");
        }
        if (!storeId.equals(asset.currentStoreId())) {
            throw BusinessException.badRequest("维修门店必须为资产当前所在门店");
        }
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        return store.id();
    }

    private String resolveResponsibilityType(String value) {
        var type = value == null || value.isBlank() ? "ROUTINE_MAINTENANCE" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ROUTINE_MAINTENANCE", "CUSTOMER_DAMAGE", "MERCHANT_RESPONSIBILITY", "PLATFORM_SUBSIDY").contains(type)) {
            throw BusinessException.badRequest("不支持的维修归因");
        }
        return type;
    }

    private String resolveCostBearerType(String value, String responsibilityType) {
        var expectedType = defaultCostBearerType(responsibilityType);
        var type = value == null || value.isBlank() ? expectedType : value.trim().toUpperCase(Locale.ROOT);
        if ("INVESTOR".equals(type)) {
            throw BusinessException.badRequest("维修费用不再由出资方承担");
        }
        if (!Set.of("USER", "MERCHANT", "PLATFORM").contains(type)) {
            throw BusinessException.badRequest("不支持的维修成本承担方");
        }
        if (!expectedType.equals(type)) {
            throw BusinessException.badRequest("维修责任归因与成本承担方不一致");
        }
        return type;
    }

    private String defaultCostBearerType(String responsibilityType) {
        return switch (responsibilityType) {
            case "CUSTOMER_DAMAGE" -> "USER";
            case "MERCHANT_RESPONSIBILITY" -> "MERCHANT";
            case "PLATFORM_SUBSIDY" -> "PLATFORM";
            default -> "MERCHANT";
        };
    }

    private Long resolveCostBearerId(String costBearerType, Long requestedId, AssetItem asset, RentalOrder order) {
        return switch (costBearerType) {
            case "INVESTOR" -> asset.investorId();
            case "MERCHANT" -> asset.currentMerchantId();
            case "USER" -> {
                if (requestedId != null) {
                    yield requestedId;
                }
                if (order != null && order.userAccountId() != null) {
                    yield order.userAccountId();
                }
                throw BusinessException.badRequest("用户承担维修费用时必须绑定订单或填写用户 ID");
            }
            case "PLATFORM" -> requestedId == null ? 0L : requestedId;
            default -> throw BusinessException.badRequest("不支持的维修成本承担方");
        };
    }

    private MaintenanceRepository.SparePartRow ensurePart(Long id) {
        return maintenanceRepository.findPart(id).orElseThrow(() -> BusinessException.badRequest("配件不存在"));
    }

    private AssetItem ensureAsset(Long id) {
        return assetRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("资产不存在"));
    }

    private void ensureAssetStoreAccess(AssetItem asset) {
        if (asset.currentMerchantId() == null || asset.currentStoreId() == null) {
            if (AuthContext.get() != null && AuthContext.get().hasPermission("system.admin")) {
                return;
            }
            throw BusinessException.forbidden("资产未分配门店，当前账号不能操作");
        }
        authorizationService.requireStoreAccess(asset.currentMerchantId(), asset.currentStoreId());
    }

    private SparePartResponse toPartResponse(MaintenanceRepository.SparePartRow row) {
        return new SparePartResponse(
            row.id(),
            row.partCode(),
            row.partName(),
            row.spec(),
            row.unit(),
            row.procurementPrice(),
            row.unitPrice(),
            row.buybackPrice(),
            row.stockQuantity(),
            row.unitPrice().multiply(BigDecimal.valueOf(row.stockQuantity())).setScale(2, RoundingMode.HALF_UP),
            row.status(),
            row.createdAt(),
            row.updatedAt()
        );
    }

    private SparePartStockLogResponse toStockLogResponse(MaintenanceRepository.SparePartStockLogRow row) {
        return new SparePartStockLogResponse(row.id(), row.partId(), row.merchantId(), row.merchantName(), row.storeId(), row.storeName(), row.partName(), row.changeType(), row.quantityChange(), row.unitPrice(), row.amount(), row.refType(), row.refId(), row.operatorAccountId(), row.remark(), row.createdAt());
    }

    private AssetMaintenanceResponse toMaintenanceResponse(MaintenanceRepository.MaintenanceRow row) {
        return new AssetMaintenanceResponse(
            row.id(),
            row.maintenanceNo(),
            row.assetId(),
            row.assetCode(),
            row.assetType(),
            row.assetTypeName(),
            row.serialNo(),
            row.orderId(),
            row.storeId(),
            row.maintenanceType(),
            row.maintenanceStatus(),
            row.responsibilityType(),
            row.startedAt(),
            row.completedAt(),
            row.laborCost(),
            row.externalCost(),
            row.partsCost(),
            row.totalCost(),
            row.merchantReimbursementAmount(),
            row.investorDeductAmount(),
            row.customerChargeAmount(),
            row.costBearerType(),
            row.costBearerId(),
            row.operatorAccountId(),
            row.remark(),
            row.createdAt(),
            maintenanceRepository.listMaintenanceParts(row.id()).stream().map(this::toMaintenancePartResponse).toList()
        );
    }

    private AssetMaintenancePartResponse toMaintenancePartResponse(MaintenanceRepository.MaintenancePartRow row) {
        return new AssetMaintenancePartResponse(row.id(), row.maintenanceId(), row.partId(), row.partNameSnapshot(), row.quantity(), row.unitPrice(), row.totalAmount(), row.remark());
    }

    private AssetRentalRecordResponse toRentalRecordResponse(RentalOrder order) {
        return new AssetRentalRecordResponse(
            "FORMAL",
            order.id(),
            order.orderNo(),
            null,
            null,
            order.userAccountId(),
            order.storeId(),
            order.customerName(),
            order.customerPhone(),
            order.orderStatus().name(),
            order.frameAssetId(),
            order.batteryAssetId(),
            order.rentalAmount(),
            order.verificationAmount(),
            order.signFeeAmount(),
            order.paidAmount(),
            order.leaseUnit(),
            order.leaseValue(),
            order.totalPeriods(),
            order.leaseStartedAt(),
            order.expectedReturnAt(),
            order.returnedAt(),
            order.createdAt(),
            billRepository.listBills(null, order.id(), null).stream().map(this::toRentalBillResponse).toList()
        );
    }

    private AssetRentalBillResponse toRentalBillResponse(RentalBill bill) {
        return new AssetRentalBillResponse(bill.id(), bill.billNo(), bill.billType().name(), bill.periodNo(), bill.billStatus().name(), bill.dueAt(), bill.payableAmount(), bill.paidAmount(), bill.overdueAmount());
    }

    private AssetResponse toAssetResponse(AssetItem asset) {
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

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal merchantReimbursementAmount(String responsibilityType, BigDecimal partsCost) {
        return switch (responsibilityType) {
            case "PLATFORM_SUBSIDY" -> partsCost;
            default -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal investorDeductAmount() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal customerChargeAmount(String responsibilityType, BigDecimal totalCost) {
        return "CUSTOMER_DAMAGE".equals(responsibilityType)
            ? totalCost
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultRemark(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private String nextPartCode() {
        return "PART-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextMaintenanceNo() {
        return "MT-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
