package com.xniu.rental.pay.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.overdue.service.OverdueService;
import com.xniu.rental.pay.dto.AlipayTradeCreateResponse;
import com.xniu.rental.pay.dto.PaymentCallbackResponse;
import com.xniu.rental.pay.dto.PaymentResponse;
import com.xniu.rental.pay.model.PayChannel;
import com.xniu.rental.pay.model.PayStatus;
import com.xniu.rental.pay.model.PaymentCallback;
import com.xniu.rental.pay.model.PaymentOrder;
import com.xniu.rental.pay.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final OrderRepository orderRepository;
    private final AuthorizationService authorizationService;
    private final AlipayGatewayClient alipayGatewayClient;
    private final OverdueService overdueService;

    public PaymentService(
        PaymentRepository paymentRepository,
        BillRepository billRepository,
        OrderRepository orderRepository,
        AuthorizationService authorizationService,
        AlipayGatewayClient alipayGatewayClient,
        OverdueService overdueService
    ) {
        this.paymentRepository = paymentRepository;
        this.billRepository = billRepository;
        this.orderRepository = orderRepository;
        this.authorizationService = authorizationService;
        this.alipayGatewayClient = alipayGatewayClient;
        this.overdueService = overdueService;
    }

    public List<PaymentResponse> listPayments(String status, Long billId, Long orderId) {
        authorizationService.requirePermission("order.read");
        return paymentRepository.listPayments(parseStatusNullable(status), billId, orderId).stream().map(this::toResponse).toList();
    }

    public List<PaymentCallbackResponse> listCallbacks() {
        authorizationService.requirePermission("order.read");
        return paymentRepository.listCallbacks().stream().map(this::toCallbackResponse).toList();
    }

    @Transactional
    public AlipayTradeCreateResponse createAlipayTrade(Long billId) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        var bill = billRepository.findBill(billId).orElseThrow(() -> BusinessException.badRequest("账单不存在"));
        if (bill.billStatus() == BillStatus.PAID) {
            throw BusinessException.badRequest("账单已支付");
        }
        if (bill.billStatus() == BillStatus.CANCELLED) {
            throw BusinessException.badRequest("账单已关闭");
        }
        if (bill.userAccountId() != null && !bill.userAccountId().equals(current.account().id()) && !current.hasPermission("system.admin")) {
            throw BusinessException.forbidden("不能支付其他用户的账单");
        }
        var payerAlipayUserId = current.account().alipayUserId();
        if (payerAlipayUserId == null || payerAlipayUserId.isBlank()) {
            throw BusinessException.badRequest("当前账号未绑定支付宝用户 ID");
        }
        var active = paymentRepository.findActiveByBillId(bill.id());
        if (active.isPresent() && active.get().payStatus() == PayStatus.PAID) {
            throw BusinessException.badRequest("该账单已有已支付的支付单");
        }
        if (active.isPresent() && active.get().payStatus() != PayStatus.PAID && active.get().alipayTradeNo() != null) {
            return new AlipayTradeCreateResponse(toResponse(active.get()), active.get().alipayTradeNo());
        }
        var payment = active.orElseGet(() -> paymentRepository.createPayment(new PaymentRepository.PaymentCreateRow(
            nextPaymentNo(),
            bill.id(),
            bill.orderId(),
            bill.userAccountId(),
            bill.merchantId(),
            bill.storeId(),
            PayChannel.ALIPAY,
            PayStatus.CREATED,
            bill.payableAmount().subtract(bill.paidAmount()).setScale(2, RoundingMode.HALF_UP),
            "享牛租赁账单 " + bill.billNo(),
            payerAlipayUserId
        )));
        try {
            var trade = alipayGatewayClient.createTrade(payment.paymentNo(), payment.payAmount(), payment.subject(), payerAlipayUserId);
            payment = paymentRepository.markPaying(payment.id(), trade.tradeNo());
            return new AlipayTradeCreateResponse(toResponse(payment), trade.tradeNo());
        } catch (BusinessException exception) {
            paymentRepository.markFailed(payment.id(), exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public PaymentResponse queryAndSync(Long paymentId) {
        authorizationService.requirePermission("order.read");
        var payment = ensurePayment(paymentId);
        var result = alipayGatewayClient.queryTrade(payment.paymentNo());
        if (isSuccessTrade(result.tradeStatus())) {
            payment = applyPaid(payment, result.totalAmount(), result.tradeNo(), "支付宝主动查询同步成功");
        }
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse refund(Long paymentId, BigDecimal refundAmount) {
        authorizationService.requirePermission("order.operate");
        var payment = ensurePayment(paymentId);
        if (payment.payStatus() != PayStatus.PAID) {
            throw BusinessException.badRequest("只有已支付支付单可以退款");
        }
        if (refundAmount.compareTo(payment.paidAmount().subtract(payment.refundAmount())) > 0) {
            throw BusinessException.badRequest("退款金额不能超过可退金额");
        }
        alipayGatewayClient.refund(payment.paymentNo(), refundAmount.setScale(2, RoundingMode.HALF_UP), "RF-" + UUID.randomUUID().toString().substring(0, 8));
        return toResponse(paymentRepository.markRefunded(payment.id(), refundAmount.setScale(2, RoundingMode.HALF_UP)));
    }

    @Transactional
    public boolean handleAlipayNotify(Map<String, String> params) {
        var notifyId = params.get("notify_id");
        var existing = paymentRepository.findCallbackByNotifyId(notifyId);
        if (existing.isPresent()) {
            return true;
        }
        var outTradeNo = params.get("out_trade_no");
        var payment = outTradeNo == null ? null : paymentRepository.findByPaymentNo(outTradeNo).orElse(null);
        var verified = alipayGatewayClient.verifyNotify(params);
        if (!verified) {
            paymentRepository.createCallback(callbackRow(payment, params, false, false, "支付宝回调验签失败"));
            return false;
        }
        if (payment == null) {
            paymentRepository.createCallback(callbackRow(null, params, true, false, "支付单不存在"));
            return false;
        }
        var totalAmount = parseAmount(params.get("total_amount"));
        if (totalAmount == null || totalAmount.compareTo(payment.payAmount()) != 0) {
            paymentRepository.createCallback(callbackRow(payment, params, true, false, "回调金额和支付单金额不一致"));
            return false;
        }
        var tradeStatus = params.get("trade_status");
        if (isSuccessTrade(tradeStatus)) {
            applyPaid(payment, totalAmount, params.get("trade_no"), "支付宝异步通知支付成功");
        }
        paymentRepository.createCallback(callbackRow(payment, params, true, true, null));
        return true;
    }

    private PaymentOrder applyPaid(PaymentOrder payment, BigDecimal paidAmount, String alipayTradeNo, String remark) {
        if (payment.payStatus() == PayStatus.PAID) {
            return payment;
        }
        var bill = billRepository.findBill(payment.billId()).orElseThrow(() -> BusinessException.badRequest("账单不存在"));
        var updatedPayment = paymentRepository.markPaid(payment.id(), paidAmount.setScale(2, RoundingMode.HALF_UP), alipayTradeNo);
        billRepository.markPaid(bill.id(), paidAmount.setScale(2, RoundingMode.HALF_UP));
        billRepository.addLog(bill.id(), bill.billStatus(), BillStatus.PAID, BillOperationType.PAYMENT_SUCCESS, null, remark);
        orderRepository.increasePaidAmount(payment.orderId(), paidAmount.setScale(2, RoundingMode.HALF_UP));
        overdueService.resolveByBillId(bill.id());
        return updatedPayment;
    }

    private PaymentRepository.CallbackCreateRow callbackRow(PaymentOrder payment, Map<String, String> params, boolean verified, boolean processed, String failureReason) {
        return new PaymentRepository.CallbackCreateRow(
            payment == null ? null : payment.id(),
            params.get("notify_id"),
            params.get("out_trade_no"),
            params.get("trade_no"),
            params.get("trade_status"),
            parseAmount(params.get("total_amount")),
            verified,
            processed,
            params.toString(),
            failureReason
        );
    }

    private PaymentOrder ensurePayment(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("支付单不存在"));
    }

    private PaymentResponse toResponse(PaymentOrder payment) {
        return new PaymentResponse(
            payment.id(),
            payment.paymentNo(),
            payment.billId(),
            payment.orderId(),
            payment.userAccountId(),
            payment.merchantId(),
            payment.storeId(),
            payment.payChannel().name(),
            payment.payStatus().name(),
            payment.payAmount(),
            payment.paidAmount(),
            payment.subject(),
            payment.payerAlipayUserId(),
            payment.alipayTradeNo(),
            payment.refundAmount(),
            payment.paidAt(),
            payment.closedAt(),
            payment.lastError(),
            payment.createdAt()
        );
    }

    private PaymentCallbackResponse toCallbackResponse(PaymentCallback callback) {
        return new PaymentCallbackResponse(
            callback.id(),
            callback.paymentId(),
            callback.notifyId(),
            callback.outTradeNo(),
            callback.alipayTradeNo(),
            callback.tradeStatus(),
            callback.totalAmount(),
            callback.verified(),
            callback.processed(),
            callback.failureReason(),
            callback.receivedAt()
        );
    }

    private PayStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PayStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的支付状态");
        }
    }

    private boolean isSuccessTrade(String tradeStatus) {
        return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String nextPaymentNo() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
