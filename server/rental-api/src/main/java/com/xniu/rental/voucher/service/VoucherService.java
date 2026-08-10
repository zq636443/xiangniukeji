package com.xniu.rental.voucher.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillItemType;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.model.OrderItemType;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.model.StoreSkuStatus;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.dto.SnapshotCreateRequest;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import com.xniu.rental.voucher.dto.VoucherPrepareRequest;
import com.xniu.rental.voucher.dto.VoucherResponse;
import com.xniu.rental.voucher.dto.VoucherVerificationAmountRequest;
import com.xniu.rental.voucher.dto.XianyuVoucherIssueRequest;
import com.xniu.rental.voucher.model.SourcePlatform;
import com.xniu.rental.voucher.model.VoucherVerification;
import com.xniu.rental.voucher.model.VoucherVerifyStatus;
import com.xniu.rental.voucher.repository.VoucherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final BillRepository billRepository;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final AuthorizationService authorizationService;
    private final VoucherGatewayClient voucherGatewayClient;

    public VoucherService(
        VoucherRepository voucherRepository,
        ProductRepository productRepository,
        StoreRepository storeRepository,
        OrderRepository orderRepository,
        BillRepository billRepository,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        AuthorizationService authorizationService,
        VoucherGatewayClient voucherGatewayClient
    ) {
        this.voucherRepository = voucherRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
        this.billRepository = billRepository;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.authorizationService = authorizationService;
        this.voucherGatewayClient = voucherGatewayClient;
    }

    public List<VoucherResponse> listAdmin(String platform, String status, Long userAccountId, Long storeId) {
        authorizationService.requirePermission("order.read");
        return voucherRepository.list(parsePlatformNullable(platform), parseStatusNullable(status), userAccountId, storeId).stream().map(this::toResponse).toList();
    }

    public List<VoucherResponse> listMine(String platform, String status) {
        return voucherRepository.list(parsePlatformNullable(platform), parseStatusNullable(status), currentUserId(), null).stream().map(this::toResponse).toList();
    }

    public List<VoucherResponse> listMerchant(Long storeId, String platform, String status) {
        authorizationService.requirePermission("order.read");
        if (storeId == null) {
            throw BusinessException.badRequest("请选择门店");
        }
        var store = storeRepository.findById(storeId).orElseThrow(() -> BusinessException.badRequest("门店不存在"));
        authorizationService.requireStoreAccess(store.merchantId(), store.id());
        var records = voucherRepository.list(parsePlatformNullable(platform), parseStatusNullable(status), null, storeId);
        return records.stream().map(this::toResponse).toList();
    }

    @Transactional
    public VoucherResponse issueXianyuCode(XianyuVoucherIssueRequest request) {
        authorizationService.requirePermission("order.read");
        var storeSku = productRepository.findStoreSku(request.storeSkuId()).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(request.packageId()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该 SKU 价格"));
        var packageTemplate = productRepository.findPackage(request.packageId()).orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        if (voucherRepository.findByPlatformAndCode(SourcePlatform.XIANYU, request.voucherCode().trim()).isPresent()) {
            throw BusinessException.badRequest("闲鱼核销码已存在");
        }
        var title = request.voucherTitle() == null || request.voucherTitle().isBlank()
            ? storeSku.displayName() + " / " + packageTemplate.packageName() + "（闲鱼）"
            : request.voucherTitle().trim();
        var record = voucherRepository.issueInternalCode(new VoucherRepository.IssueRow(
            SourcePlatform.XIANYU,
            request.voucherCode().trim(),
            storeSku.merchantId(),
            storeSku.storeId(),
            storeSku.id(),
            request.packageId(),
            title,
            request.voucherAmount().setScale(2, RoundingMode.HALF_UP),
            storeSku.signFeeAmount().setScale(2, RoundingMode.HALF_UP),
            "{\"mode\":\"INTERNAL_ISSUE\",\"platform\":\"XIANYU\"}"
        ));
        return toResponse(record);
    }

    @Transactional
    public VoucherResponse prepare(VoucherPrepareRequest request) {
        var current = currentUserId();
        var platform = parsePlatform(request.sourcePlatform());
        var verificationAmount = normalizeAmount(request.verificationAmount());
        var storeSku = productRepository.findStoreSku(request.storeSkuId()).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        if (storeSku.status() != StoreSkuStatus.ON_SHELF) {
            throw BusinessException.badRequest("门店商品未上架");
        }
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(request.packageId()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该 SKU 价格"));
        if (platform == SourcePlatform.XIANYU) {
            var issued = voucherRepository.findByPlatformAndCode(platform, request.voucherCode().trim())
                .orElseThrow(() -> BusinessException.badRequest("闲鱼核销码不存在或未下发"));
            ensureXianyuCodeAvailable(issued, current, request.storeSkuId(), request.packageId());
            var record = voucherRepository.updateSelection(
                issued.id(),
                current,
                issued.merchantId(),
                issued.storeId(),
                issued.storeSkuId(),
                issued.packageId(),
                issued.voucherAmount(),
                verificationAmount,
                issued.signFeeAmount()
            );
            if (record.verifyStatus() == VoucherVerifyStatus.WAITING_SIGN_FEE
                || record.verifyStatus() == VoucherVerifyStatus.VERIFIED
                || record.verifyStatus() == VoucherVerifyStatus.PREPARED
                || record.verifyStatus() == VoucherVerifyStatus.CONSUMED) {
                return toResponse(record);
            }
            var gateway = voucherGatewayClient.prepare(platform, record.voucherCode(), record.voucherAmount());
            if (!gateway.success()) {
                return toResponse(voucherRepository.markFailed(record.id(), gateway.failureReason(), gateway.rawPayload()));
            }
            return toResponse(voucherRepository.markPrepared(record.id(), toGatewayRow(gateway)));
        }
        var record = voucherRepository.findByPlatformAndCode(platform, request.voucherCode().trim())
            .map(item -> voucherRepository.updateSelection(item.id(), current, storeSku.merchantId(), storeSku.storeId(), storeSku.id(), request.packageId(), packagePrice.rentalAmount(), verificationAmount, storeSku.signFeeAmount()))
            .orElseGet(() -> voucherRepository.create(new VoucherRepository.CreateRow(platform, request.voucherCode().trim(), current, storeSku.merchantId(), storeSku.storeId(), storeSku.id(), request.packageId(), packagePrice.rentalAmount(), verificationAmount, storeSku.signFeeAmount())));
        ensureUserOwns(record);
        var gateway = voucherGatewayClient.prepare(platform, record.voucherCode(), packagePrice.rentalAmount());
        if (!gateway.success()) {
            return toResponse(voucherRepository.markFailed(record.id(), gateway.failureReason(), gateway.rawPayload()));
        }
        return toResponse(voucherRepository.markPrepared(record.id(), toGatewayRow(gateway)));
    }

    @Transactional
    public VoucherResponse verify(Long id) {
        var record = ensureMine(id);
        if (record.verifyStatus() == VoucherVerifyStatus.WAITING_SIGN_FEE || record.verifyStatus() == VoucherVerifyStatus.CONSUMED) {
            return toResponse(record);
        }
        if (record.verifyStatus() != VoucherVerifyStatus.PREPARED && record.verifyStatus() != VoucherVerifyStatus.VERIFIED) {
            throw BusinessException.badRequest("请先完成核销准备");
        }
        var verificationAmount = requireVerificationAmount(record);
        var gateway = voucherGatewayClient.verify(record.sourcePlatform(), record.voucherCode(), verificationAmount);
        if (!gateway.success()) {
            return toResponse(voucherRepository.markFailed(record.id(), gateway.failureReason(), gateway.rawPayload()));
        }
        var verified = voucherRepository.markVerified(record.id(), toGatewayRow(gateway));
        if (verified.orderId() != null) {
            return toResponse(verified);
        }
        var order = createVoucherOrder(verified);
        var billId = verified.signFeeAmount().signum() > 0 ? createSignFeeBill(order.id(), order.userAccountId(), order.merchantId(), order.storeId(), verified.signFeeAmount()) : null;
        return toResponse(voucherRepository.attachOrder(verified.id(), order.id(), billId));
    }

    @Transactional
    public VoucherResponse consume(Long id) {
        var record = ensureMine(id);
        if (record.verifyStatus() == VoucherVerifyStatus.CONSUMED) {
            if (record.orderId() != null) {
                var consumedOrder = orderRepository.findById(record.orderId()).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
                syncVoucherRentBill(consumedOrder.id(), consumedOrder.userAccountId(), consumedOrder.merchantId(), consumedOrder.storeId(), requireVerificationAmount(record));
            }
            return toResponse(record);
        }
        if (record.orderId() == null) {
            throw BusinessException.badRequest("请先验码并生成签单费订单");
        }
        assertSignFeePaid(record);
        var verificationAmount = requireVerificationAmount(record);
        voucherRepository.markConsuming(record.id());
        var gateway = voucherGatewayClient.consume(record.sourcePlatform(), record.voucherCode(), verificationAmount);
        if (!gateway.success()) {
            voucherRepository.markFailed(record.id(), gateway.failureReason(), gateway.rawPayload());
            orderRepository.markException(record.orderId(), platformText(record.sourcePlatform()) + "核销失败：" + gateway.failureReason());
            throw BusinessException.badRequest("核销失败：" + gateway.failureReason());
        }
        var consumed = voucherRepository.markConsumed(record.id(), toGatewayRow(gateway));
        var order = orderRepository.findById(record.orderId()).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        syncVoucherRentBill(order.id(), order.userAccountId(), order.merchantId(), order.storeId(), verificationAmount);
        if (order.orderStatus() == OrderStatus.PENDING_PAYMENT) {
            var updated = orderRepository.updateStatus(order.id(), OrderStatus.PENDING_REAL_NAME, null, null, null);
            orderRepository.addLog(order.id(), order.orderStatus(), updated.orderStatus(), OrderOperationType.TRANSITION, currentUserId(), platformText(record.sourcePlatform()) + "核销成功，进入待实名");
        }
        return toResponse(consumed);
    }

    @Transactional
    public VoucherResponse markException(Long id, String reason) {
        var record = voucherRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("核销记录不存在"));
        authorizationService.requireStoreAccess(record.merchantId(), record.storeId());
        return toResponse(voucherRepository.markException(id, reason));
    }

    @Transactional
    public VoucherResponse updateMineVerificationAmount(Long id, VoucherVerificationAmountRequest request) {
        return updateVerificationAmount(ensureMine(id), request.verificationAmount());
    }

    @Transactional
    public VoucherResponse updateMerchantVerificationAmount(Long id, VoucherVerificationAmountRequest request) {
        authorizationService.requirePermission("order.operate");
        var record = voucherRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("核销记录不存在"));
        authorizationService.requireStoreAccess(record.merchantId(), record.storeId());
        return updateVerificationAmount(record, request.verificationAmount());
    }

    @Transactional
    public VoucherResponse updateAdminVerificationAmount(Long id, VoucherVerificationAmountRequest request) {
        authorizationService.requirePermission("order.operate");
        var record = voucherRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("核销记录不存在"));
        return updateVerificationAmount(record, request.verificationAmount());
    }

    private com.xniu.rental.order.model.RentalOrder createVoucherOrder(VoucherVerification record) {
        var storeSku = productRepository.findStoreSku(record.storeSkuId()).orElseThrow(() -> BusinessException.badRequest("门店商品不存在"));
        var packageTemplate = productRepository.findPackage(record.packageId()).orElseThrow(() -> BusinessException.badRequest("SKU 不存在"));
        var packagePrice = productRepository.listStoreSkuPackages(storeSku.id()).stream()
            .filter(item -> item.packageId().equals(record.packageId()))
            .findFirst()
            .orElseThrow(() -> BusinessException.badRequest("门店商品未配置该 SKU 价格"));
        var sku = productRepository.findSku(storeSku.skuId()).orElseThrow(() -> BusinessException.badRequest("商品链接不存在"));
        var verificationAmount = requireVerificationAmount(record);
        var payableAmount = record.signFeeAmount().setScale(2, RoundingMode.HALF_UP);
        var current = AuthContext.get();
        var order = orderRepository.create(new OrderRepository.OrderCreateRow(
            "ORD-V-" + UUID.randomUUID().toString().substring(0, 8),
            record.userAccountId(),
            current == null ? null : current.account().displayName(),
            current == null ? null : current.account().phone(),
            record.merchantId(),
            record.storeId(),
            record.storeSkuId(),
            storeSku.skuId(),
            record.packageId(),
            null,
            null,
            OrderStatus.PENDING_PAYMENT,
            verificationAmount,
            verificationAmount,
            record.signFeeAmount().setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO,
            payableAmount,
            BigDecimal.ZERO,
            null,
            packageTemplate.leaseUnit().name(),
            packageTemplate.leaseValue(),
            packageTemplate.totalPeriods(),
            1,
            packageTemplate.billDayMode().name(),
            packageTemplate.billDay(),
            LocalDateTime.now(),
            packagePrice.autoRenewEnabled(),
            packagePrice.renewalUnit() == null ? null : packagePrice.renewalUnit().name(),
            packagePrice.renewalValue(),
            packagePrice.renewalAmount(),
            packagePrice.renewalBillingMode().name(),
            packagePrice.renewalDailyAmount(),
            packagePrice.renewalDailyCapEnabled(),
            packagePrice.renewalGraceHours(),
            packagePrice.overdueDailyAmount(),
            null
        ));
        orderRepository.addItem(order.id(), OrderItemType.SKU, storeSku.id(), storeSku.displayName() + "（外部平台已付）", 1, verificationAmount, verificationAmount);
        if (record.signFeeAmount().signum() > 0) {
            orderRepository.addItem(order.id(), OrderItemType.SIGN_FEE, null, "签单费", 1, record.signFeeAmount(), record.signFeeAmount());
        }
        orderRepository.addLog(order.id(), null, OrderStatus.PENDING_PAYMENT, OrderOperationType.CREATE, currentUserId(), platformText(record.sourcePlatform()) + "验码成功后创建签单费订单");
        var snapshot = settlementService.createOrderSnapshot(new SnapshotCreateRequest(
            "ORDER",
            order.id(),
            storeSku.id(),
            null,
            null,
            verificationAmount,
            record.sourcePlatform().name(),
            record.signFeeAmount(),
            BatteryCostCalculator.calculate(
                sku.batteryCostDailyAmount(),
                sku.batteryCostMonthlyAmount(),
                packageTemplate.leaseUnit(),
                packageTemplate.leaseValue(),
                1
            )
        ));
        return orderRepository.updateSettlementSnapshot(order.id(), snapshot.id());
    }

    private VoucherResponse updateVerificationAmount(VoucherVerification record, BigDecimal amount) {
        if (record.orderId() != null || record.verifyStatus() == VoucherVerifyStatus.CONSUMING || record.verifyStatus() == VoucherVerifyStatus.CONSUMED) {
            throw BusinessException.badRequest("核销订单已生成，不能再修改核销金额");
        }
        return toResponse(voucherRepository.updateVerificationAmount(record.id(), normalizeAmount(amount)));
    }

    private BigDecimal requireVerificationAmount(VoucherVerification record) {
        var amount = normalizeAmount(record.verificationAmount());
        if (amount == null) {
            throw BusinessException.badRequest("请先填写核销金额；客户未填写时可由门店补录");
        }
        return amount;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        var normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw BusinessException.badRequest("核销金额不能小于 0");
        }
        return normalized;
    }

    private Long createSignFeeBill(Long orderId, Long userAccountId, Long merchantId, Long storeId, BigDecimal signFeeAmount) {
        var bill = billRepository.createBill(new BillRepository.BillCreateRow(
            "BILL-V-" + UUID.randomUUID().toString().substring(0, 8),
            orderId,
            userAccountId,
            merchantId,
            storeId,
            BillType.INITIAL,
            1,
            BillStatus.PENDING_PAYMENT,
            LocalDateTime.now(),
            signFeeAmount.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "平台核销签单费",
            "VOUCHER-" + orderId
        ));
        billRepository.addItem(bill.id(), BillItemType.SIGN_FEE, "平台核销签单费", signFeeAmount.setScale(2, RoundingMode.HALF_UP));
        billRepository.addLog(bill.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, currentUserId(), "生成平台核销签单费账单");
        return bill.id();
    }

    private void syncVoucherRentBill(Long orderId, Long userAccountId, Long merchantId, Long storeId, BigDecimal verificationAmount) {
        var normalizedAmount = verificationAmount.setScale(2, RoundingMode.HALF_UP);
        var existing = billRepository.findExisting(orderId, BillType.VOUCHER_RENT, 1);
        var bill = existing.orElseGet(() -> {
            var created = billRepository.createBill(new BillRepository.BillCreateRow(
                "BILL-VR-" + UUID.randomUUID().toString().substring(0, 8),
                orderId,
                userAccountId,
                merchantId,
                storeId,
                BillType.VOUCHER_RENT,
                1,
                BillStatus.PENDING_PAYMENT,
                LocalDateTime.now(),
                normalizedAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "平台核销租金实收",
                "VOUCHER-RENT-" + orderId
            ));
            billRepository.addItem(created.id(), BillItemType.RENT, "平台核销租金实收", normalizedAmount);
            billRepository.addLog(created.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, currentUserId(), "生成平台核销租金实收账单");
            return created;
        });
        var paidBill = bill.billStatus() == BillStatus.PAID ? bill : billRepository.markPaid(bill.id(), normalizedAmount);
        if (bill.billStatus() != BillStatus.PAID) {
            billRepository.addLog(bill.id(), bill.billStatus(), BillStatus.PAID, BillOperationType.PAYMENT_SUCCESS, currentUserId(), "第三方平台核销成功，确认租金实收");
        }
        settlementIncomeService.syncPaidBill(paidBill);
    }

    private void assertSignFeePaid(VoucherVerification record) {
        if (record.signFeeBillId() == null || record.signFeeAmount().signum() <= 0) {
            return;
        }
        var bill = billRepository.findBill(record.signFeeBillId()).orElseThrow(() -> BusinessException.badRequest("签单费账单不存在"));
        if (bill.billStatus() != BillStatus.PAID) {
            throw BusinessException.badRequest("请先支付签单费，再执行第三方核销");
        }
    }

    private VoucherVerification ensureMine(Long id) {
        var record = voucherRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("核销记录不存在"));
        ensureUserOwns(record);
        return record;
    }

    private void ensureXianyuCodeAvailable(VoucherVerification record, Long currentUserId, Long storeSkuId, Long packageId) {
        if (record.verifyStatus() == VoucherVerifyStatus.CONSUMED || record.verifyStatus() == VoucherVerifyStatus.CONSUMING) {
            throw BusinessException.badRequest("该闲鱼核销码已使用");
        }
        if (record.verifyStatus() == VoucherVerifyStatus.EXCEPTION) {
            throw BusinessException.badRequest("该闲鱼核销码已被标记异常，请联系门店或总部处理");
        }
        if (!record.storeSkuId().equals(storeSkuId) || !record.packageId().equals(packageId)) {
            throw BusinessException.badRequest("闲鱼核销码与当前选择的商品或 SKU 不匹配");
        }
        if (record.userAccountId() != null && !record.userAccountId().equals(currentUserId)) {
            throw BusinessException.badRequest("该闲鱼核销码已被其他用户占用");
        }
    }

    private void ensureUserOwns(VoucherVerification record) {
        var current = currentUserId();
        if (record.userAccountId() == null || !record.userAccountId().equals(current)) {
            throw BusinessException.forbidden("不能操作其他用户的核销记录");
        }
    }

    private Long currentUserId() {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current.account().id();
    }

    private SourcePlatform parsePlatform(String value) {
        try {
            return SourcePlatform.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的核销平台");
        }
    }

    private SourcePlatform parsePlatformNullable(String value) {
        return value == null || value.isBlank() ? null : parsePlatform(value);
    }

    private VoucherVerifyStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return VoucherVerifyStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的核销状态");
        }
    }

    private VoucherRepository.GatewayRow toGatewayRow(VoucherGatewayClient.GatewayResult result) {
        return new VoucherRepository.GatewayRow(result.externalId(), result.voucherTitle(), result.voucherAmount(), result.validFrom(), result.validTo(), result.rawPayload());
    }

    private VoucherResponse toResponse(VoucherVerification record) {
        return new VoucherResponse(
            record.id(),
            record.sourcePlatform().name(),
            record.voucherCode(),
            record.userAccountId(),
            record.merchantId(),
            record.storeId(),
            record.storeSkuId(),
            record.packageId(),
            record.orderId(),
            record.signFeeBillId(),
            record.verifyStatus().name(),
            record.voucherTitle(),
            record.voucherAmount(),
            record.verificationAmount(),
            record.signFeeAmount(),
            record.externalPrepareId(),
            record.externalVerifyId(),
            record.externalConsumeId(),
            record.validFrom(),
            record.validTo(),
            record.retryCount(),
            record.failureReason(),
            record.verifiedAt(),
            record.consumedAt(),
            record.exceptionReason(),
            record.createdAt()
        );
    }

    private String platformText(SourcePlatform platform) {
        return switch (platform) {
            case DOUYIN -> "抖音";
            case MEITUAN -> "美团";
            case XIANYU -> "闲鱼";
        };
    }
}
