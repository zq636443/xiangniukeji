package com.xniu.rental.pay.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.order.service.OrderRenewalService;
import com.xniu.rental.overdue.service.OverdueService;
import com.xniu.rental.pay.config.AlipayProperties;
import com.xniu.rental.pay.dto.AgreementNotifyResponse;
import com.xniu.rental.pay.dto.AgreementResponse;
import com.xniu.rental.pay.dto.AgreementSignResponse;
import com.xniu.rental.pay.dto.DeductBatchResponse;
import com.xniu.rental.pay.dto.DeductRecordResponse;
import com.xniu.rental.pay.model.AgreementNotify;
import com.xniu.rental.pay.model.AgreementStatus;
import com.xniu.rental.pay.model.AgreementType;
import com.xniu.rental.pay.model.DeductBatch;
import com.xniu.rental.pay.model.DeductRecord;
import com.xniu.rental.pay.model.DeductStatus;
import com.xniu.rental.pay.model.PayAgreement;
import com.xniu.rental.pay.model.PayChannel;
import com.xniu.rental.pay.model.PayStatus;
import com.xniu.rental.pay.repository.AgreementRepository;
import com.xniu.rental.pay.repository.DeductRepository;
import com.xniu.rental.pay.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgreementDeductService {

    private static final int DEFAULT_DEDUCT_LIMIT = 50;
    private static final int MAX_RETRY_COUNT = 3;

    private final AgreementRepository agreementRepository;
    private final DeductRepository deductRepository;
    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final OrderRepository orderRepository;
    private final AuthorizationService authorizationService;
    private final AlipayGatewayClient alipayGatewayClient;
    private final AlipayProperties alipayProperties;
    private final OverdueService overdueService;
    private final OrderRenewalService orderRenewalService;

    public AgreementDeductService(
        AgreementRepository agreementRepository,
        DeductRepository deductRepository,
        PaymentRepository paymentRepository,
        BillRepository billRepository,
        OrderRepository orderRepository,
        AuthorizationService authorizationService,
        AlipayGatewayClient alipayGatewayClient,
        AlipayProperties alipayProperties,
        OverdueService overdueService,
        OrderRenewalService orderRenewalService
    ) {
        this.agreementRepository = agreementRepository;
        this.deductRepository = deductRepository;
        this.paymentRepository = paymentRepository;
        this.billRepository = billRepository;
        this.orderRepository = orderRepository;
        this.authorizationService = authorizationService;
        this.alipayGatewayClient = alipayGatewayClient;
        this.alipayProperties = alipayProperties;
        this.overdueService = overdueService;
        this.orderRenewalService = orderRenewalService;
    }

    public List<AgreementResponse> listAgreements(String status, Long userAccountId, Long orderId) {
        authorizationService.requirePermission("order.read");
        return agreementRepository.listAgreements(parseAgreementStatusNullable(status), userAccountId, orderId).stream().map(this::toAgreementResponse).toList();
    }

    public List<AgreementNotifyResponse> listAgreementNotifies() {
        authorizationService.requirePermission("order.read");
        return agreementRepository.listNotifies().stream().map(this::toNotifyResponse).toList();
    }

    public List<DeductBatchResponse> listDeductBatches() {
        authorizationService.requirePermission("order.read");
        return deductRepository.listBatches().stream().map(this::toBatchResponse).toList();
    }

    public List<DeductRecordResponse> listDeductRecords(String status, Long billId, Long orderId) {
        authorizationService.requirePermission("order.read");
        return deductRepository.listRecords(parseDeductStatusNullable(status), billId, orderId).stream().map(this::toRecordResponse).toList();
    }

    @Transactional
    public AgreementSignResponse createAgreementSign(Long orderId, BigDecimal maxSingleAmount) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        if (!alipayProperties.agreementReady()) {
            throw BusinessException.badRequest("支付宝签约扣款配置未完成，请先配置签约产品码、签约场景、回调地址和密钥");
        }
        var order = orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
        if (order.userAccountId() != null && !order.userAccountId().equals(current.account().id()) && !current.hasPermission("system.admin")) {
            throw BusinessException.forbidden("不能为其他用户订单签约");
        }
        var alipayUserId = current.account().alipayUserId();
        if (alipayUserId == null || alipayUserId.isBlank()) {
            throw BusinessException.badRequest("当前账号未绑定支付宝用户 ID");
        }
        var externalAgreementNo = nextAgreementNo();
        var agreement = agreementRepository.createAgreement(new AgreementRepository.AgreementCreateRow(
            externalAgreementNo,
            current.account().id(),
            alipayUserId,
            order.id(),
            order.merchantId(),
            order.storeId(),
            AgreementType.CYCLE_PAY,
            AgreementStatus.SIGNING,
            alipayProperties.getAgreementPersonalProductCode(),
            alipayProperties.getAgreementSignScene(),
            maxSingleAmount.setScale(2, RoundingMode.HALF_UP),
            null
        ));
        var sign = alipayGatewayClient.createAgreementSign(externalAgreementNo);
        agreement = agreementRepository.updateSignUrl(agreement.id(), sign.signUrl());
        if (sign.agreementNo() != null && !sign.agreementNo().isBlank()) {
            agreement = agreementRepository.markSigned(agreement.id(), sign.agreementNo(), parseDateTime(sign.signTime()), parseDateTime(sign.validTime()), parseDateTime(sign.invalidTime()));
        }
        return new AgreementSignResponse(toAgreementResponse(agreement), sign.signUrl());
    }

    @Transactional
    public AgreementResponse queryAgreement(Long id) {
        authorizationService.requirePermission("order.read");
        var agreement = ensureAgreement(id);
        var result = alipayGatewayClient.queryAgreement(agreement.externalAgreementNo(), agreement.agreementNo());
        if (result.agreementNo() != null && isSignedStatus(result.status())) {
            agreement = agreementRepository.markSigned(agreement.id(), result.agreementNo(), parseDateTime(result.signTime()), parseDateTime(result.validTime()), parseDateTime(result.invalidTime()));
        } else if (result.status() != null && !isSignedStatus(result.status())) {
            agreement = agreementRepository.updateStatus(agreement.id(), AgreementStatus.INVALID, result.status());
        }
        return toAgreementResponse(agreement);
    }

    @Transactional
    public AgreementResponse unsignAgreement(Long id) {
        authorizationService.requirePermission("order.operate");
        var agreement = ensureAgreement(id);
        alipayGatewayClient.unsignAgreement(agreement.externalAgreementNo(), agreement.agreementNo());
        return toAgreementResponse(agreementRepository.updateStatus(id, AgreementStatus.UNSIGNED, null));
    }

    @Transactional
    public boolean handleAgreementNotify(Map<String, String> params) {
        var notifyId = params.get("notify_id");
        if (agreementRepository.findNotifyByNotifyId(notifyId).isPresent()) {
            return true;
        }
        var externalAgreementNo = params.get("external_agreement_no");
        var agreement = externalAgreementNo == null ? null : agreementRepository.findByExternalAgreementNo(externalAgreementNo).orElse(null);
        var verified = alipayGatewayClient.verifyNotify(params);
        if (!verified) {
            agreementRepository.createNotify(notifyRow(agreement, params, false, false, "支付宝签约通知验签失败"));
            return false;
        }
        if (agreement == null) {
            agreementRepository.createNotify(notifyRow(null, params, true, false, "签约记录不存在"));
            return false;
        }
        var status = params.get("status");
        if (isSignedStatus(status)) {
            agreementRepository.markSigned(agreement.id(), params.get("agreement_no"), parseDateTime(params.get("sign_time")), parseDateTime(params.get("valid_time")), parseDateTime(params.get("invalid_time")));
        } else if ("UNSIGN".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            agreementRepository.updateStatus(agreement.id(), AgreementStatus.UNSIGNED, status);
        }
        agreementRepository.createNotify(notifyRow(agreement, params, true, true, null));
        return true;
    }

    @Transactional
    public DeductBatchResponse runDueDeduct(Integer limit, String remark) {
        authorizationService.requirePermission("order.operate");
        return runDueDeductInternal(limit, remark);
    }

    @Transactional
    public DeductBatchResponse runDueDeductInternal(Integer limit, String remark) {
        var normalizedLimit = limit == null || limit <= 0 ? DEFAULT_DEDUCT_LIMIT : Math.min(limit, DEFAULT_DEDUCT_LIMIT);
        orderRenewalService.runDueRenewalsInternal(normalizedLimit, "扣款前自动生成到期续租账单");
        var now = LocalDateTime.now();
        var bills = billRepository.listDueBillsForDeduct(now, normalizedLimit);
        var batchNo = nextBatchNo();
        var batch = deductRepository.createBatch(batchNo, bills.size(), remark);
        var successCount = 0;
        var failedCount = 0;
        for (var bill : bills) {
            var success = deductBill(batchNo, bill, now);
            if (success) {
                successCount++;
            } else {
                failedCount++;
            }
        }
        batch = deductRepository.finishBatch(batch.batchNo(), successCount, failedCount);
        return toBatchResponse(batch);
    }

    private boolean deductBill(String batchNo, RentalBill bill, LocalDateTime now) {
        var existing = deductRepository.findLatestByBillId(bill.id());
        if (existing.isPresent()) {
            var record = existing.get();
            if (record.deductStatus() == DeductStatus.SUCCESS || record.deductStatus() == DeductStatus.PROCESSING) {
                return false;
            }
            if (record.retryCount() >= MAX_RETRY_COUNT) {
                return false;
            }
            if (record.nextRetryAt() != null && record.nextRetryAt().isAfter(now)) {
                return false;
            }
            return executeDeduct(record, bill, batchNo);
        }
        var agreement = agreementRepository.findSignedByOrderId(bill.orderId()).orElse(null);
        if (agreement == null || agreement.agreementNo() == null) {
            billRepository.updateStatus(bill.id(), BillStatus.FAILED);
            billRepository.addLog(bill.id(), bill.billStatus(), BillStatus.FAILED, BillOperationType.PAYMENT_FAILED, null, "未找到有效支付宝扣款协议");
            overdueService.upsertFromDeductFailure(bill, "未找到有效支付宝扣款协议");
            return false;
        }
        var amount = bill.payableAmount().subtract(bill.paidAmount()).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(agreement.maxSingleAmount()) > 0) {
            billRepository.updateStatus(bill.id(), BillStatus.FAILED);
            billRepository.addLog(bill.id(), bill.billStatus(), BillStatus.FAILED, BillOperationType.PAYMENT_FAILED, null, "账单金额超过协议单笔扣款上限");
            overdueService.upsertFromDeductFailure(bill, "账单金额超过协议单笔扣款上限");
            return false;
        }
        var record = deductRepository.createRecord(new DeductRepository.DeductCreateRow(
            nextDeductNo(),
            batchNo,
            bill.id(),
            bill.orderId(),
            agreement.id(),
            agreement.agreementNo(),
            DeductStatus.PENDING,
            amount,
            0,
            now
        ));
        return executeDeduct(record, bill, batchNo);
    }

    private boolean executeDeduct(DeductRecord record, RentalBill bill, String batchNo) {
        var agreement = ensureAgreement(record.agreementId());
        var payment = paymentRepository.createPayment(new PaymentRepository.PaymentCreateRow(
            "DPA-" + UUID.randomUUID().toString().substring(0, 8),
            bill.id(),
            bill.orderId(),
            bill.userAccountId(),
            bill.merchantId(),
            bill.storeId(),
            PayChannel.ALIPAY,
            PayStatus.CREATED,
            record.deductAmount(),
            "途派熊租赁自动扣款 " + bill.billNo(),
            agreement.alipayUserId()
        ));
        deductRepository.markProcessing(record.id(), batchNo, payment.id());
        var oldStatus = bill.billStatus();
        billRepository.updateStatus(bill.id(), BillStatus.PAYING);
        try {
            var result = alipayGatewayClient.payWithAgreement(payment.paymentNo(), record.deductAmount(), payment.subject(), agreement.alipayUserId(), record.agreementNo());
            var paidAmount = new BigDecimal(result.totalAmount()).setScale(2, RoundingMode.HALF_UP);
            paymentRepository.markPaid(payment.id(), paidAmount, result.tradeNo());
            var paidBill = billRepository.markPaid(bill.id(), paidAmount);
            billRepository.addLog(bill.id(), oldStatus, BillStatus.PAID, BillOperationType.PAYMENT_SUCCESS, null, "支付宝协议扣款成功");
            orderRepository.increasePaidAmount(bill.orderId(), paidAmount);
            deductRepository.markSuccess(record.id(), result.tradeNo());
            overdueService.resolveByBillId(bill.id());
            orderRenewalService.handlePaidBill(paidBill);
            return true;
        } catch (BusinessException exception) {
            paymentRepository.markFailed(payment.id(), exception.getMessage());
            billRepository.updateStatus(bill.id(), BillStatus.FAILED);
            billRepository.addLog(bill.id(), BillStatus.PAYING, BillStatus.FAILED, BillOperationType.PAYMENT_FAILED, null, exception.getMessage());
            deductRepository.markFailed(record.id(), exception.getMessage(), LocalDateTime.now().plusHours(nextRetryHours(record.retryCount())));
            overdueService.upsertFromDeductFailure(bill, exception.getMessage());
            return false;
        }
    }

    private long nextRetryHours(Integer retryCount) {
        return Math.max(1, retryCount == null ? 1 : retryCount + 1);
    }

    private AgreementRepository.NotifyCreateRow notifyRow(PayAgreement agreement, Map<String, String> params, boolean verified, boolean processed, String failureReason) {
        return new AgreementRepository.NotifyCreateRow(
            agreement == null ? null : agreement.id(),
            params.get("notify_id"),
            params.get("external_agreement_no"),
            params.get("agreement_no"),
            params.get("status"),
            verified,
            processed,
            params.toString(),
            failureReason
        );
    }

    private PayAgreement ensureAgreement(Long id) {
        return agreementRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("签约协议不存在"));
    }

    private AgreementStatus parseAgreementStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AgreementStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的协议状态");
        }
    }

    private DeductStatus parseDeductStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DeductStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的扣款状态");
        }
    }

    private boolean isSignedStatus(String status) {
        return "NORMAL".equalsIgnoreCase(status) || "SIGNED".equalsIgnoreCase(status);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception exception) {
            return null;
        }
    }

    private AgreementResponse toAgreementResponse(PayAgreement agreement) {
        return new AgreementResponse(
            agreement.id(),
            agreement.agreementNo(),
            agreement.externalAgreementNo(),
            agreement.userAccountId(),
            agreement.alipayUserId(),
            agreement.orderId(),
            agreement.merchantId(),
            agreement.storeId(),
            agreement.agreementType().name(),
            agreement.agreementStatus().name(),
            agreement.personalProductCode(),
            agreement.signScene(),
            agreement.maxSingleAmount(),
            agreement.signTime(),
            agreement.validTime(),
            agreement.invalidTime(),
            agreement.lastError(),
            agreement.createdAt()
        );
    }

    private AgreementNotifyResponse toNotifyResponse(AgreementNotify notify) {
        return new AgreementNotifyResponse(notify.id(), notify.agreementId(), notify.notifyId(), notify.externalAgreementNo(), notify.agreementNo(), notify.agreementStatus(), notify.verified(), notify.processed(), notify.failureReason(), notify.receivedAt());
    }

    private DeductBatchResponse toBatchResponse(DeductBatch batch) {
        return new DeductBatchResponse(batch.id(), batch.batchNo(), batch.batchStatus().name(), batch.plannedCount(), batch.successCount(), batch.failedCount(), batch.remark(), batch.startedAt(), batch.finishedAt(), batch.createdAt());
    }

    private DeductRecordResponse toRecordResponse(DeductRecord record) {
        return new DeductRecordResponse(record.id(), record.deductNo(), record.batchNo(), record.billId(), record.orderId(), record.agreementId(), record.agreementNo(), record.paymentId(), record.deductStatus().name(), record.deductAmount(), record.retryCount(), record.nextRetryAt(), record.alipayTradeNo(), record.lastError(), record.requestedAt(), record.successAt(), record.createdAt());
    }

    private String nextAgreementNo() {
        return "AGR-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextBatchNo() {
        return "DDB-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextDeductNo() {
        return "DD-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
