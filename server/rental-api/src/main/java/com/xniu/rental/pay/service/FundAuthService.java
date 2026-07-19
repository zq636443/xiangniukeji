package com.xniu.rental.pay.service;

import com.xniu.rental.auth.security.AuthContext;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.overdue.service.OverdueService;
import com.xniu.rental.pay.dto.FundAuthCaptureRequest;
import com.xniu.rental.pay.dto.FundAuthCreateResponse;
import com.xniu.rental.pay.dto.FundAuthNotifyResponse;
import com.xniu.rental.pay.dto.FundAuthOperationResponse;
import com.xniu.rental.pay.dto.FundAuthResponse;
import com.xniu.rental.pay.dto.FundAuthUnfreezeRequest;
import com.xniu.rental.pay.model.FundAuthNotify;
import com.xniu.rental.pay.model.FundAuthOperation;
import com.xniu.rental.pay.model.FundAuthOperationStatus;
import com.xniu.rental.pay.model.FundAuthOperationType;
import com.xniu.rental.pay.model.FundAuthOrder;
import com.xniu.rental.pay.model.FundAuthStatus;
import com.xniu.rental.pay.model.FundAuthType;
import com.xniu.rental.pay.model.PayChannel;
import com.xniu.rental.pay.model.PayStatus;
import com.xniu.rental.pay.repository.FundAuthRepository;
import com.xniu.rental.pay.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundAuthService {

    private final FundAuthRepository fundAuthRepository;
    private final OrderRepository orderRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final AuthorizationService authorizationService;
    private final AlipayGatewayClient alipayGatewayClient;
    private final OverdueService overdueService;

    public FundAuthService(
        FundAuthRepository fundAuthRepository,
        OrderRepository orderRepository,
        BillRepository billRepository,
        PaymentRepository paymentRepository,
        AuthorizationService authorizationService,
        AlipayGatewayClient alipayGatewayClient,
        OverdueService overdueService
    ) {
        this.fundAuthRepository = fundAuthRepository;
        this.orderRepository = orderRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.authorizationService = authorizationService;
        this.alipayGatewayClient = alipayGatewayClient;
        this.overdueService = overdueService;
    }

    public List<FundAuthResponse> listAdminAuths(String status, Long orderId, Long userAccountId) {
        authorizationService.requirePermission("order.read");
        return fundAuthRepository.listAuthOrders(parseStatusNullable(status), orderId, userAccountId).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<FundAuthResponse> listUserAuths(Long orderId) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return fundAuthRepository.listAuthOrders(null, orderId, current.account().id()).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<FundAuthOperationResponse> listOperations(Long authOrderId) {
        authorizationService.requirePermission("order.read");
        var auth = ensureAuth(authOrderId);
        authorizationService.requireStoreAccess(auth.merchantId(), auth.storeId());
        return fundAuthRepository.listOperations(authOrderId).stream().map(this::toOperationResponse).toList();
    }

    public List<FundAuthNotifyResponse> listNotifies() {
        authorizationService.requirePermission("order.read");
        return fundAuthRepository.listNotifies().stream().map(this::toNotifyResponse).toList();
    }

    @Transactional
    public FundAuthResponse queryAndSync(Long authOrderId) {
        authorizationService.requirePermission("order.read");
        var auth = ensureAuth(authOrderId);
        authorizationService.requireStoreAccess(auth.merchantId(), auth.storeId());
        var operation = fundAuthRepository.createOperation(new FundAuthRepository.OperationCreateRow(
            nextNo("FAOP"),
            auth.id(),
            null,
            null,
            FundAuthOperationType.QUERY,
            FundAuthOperationStatus.PENDING,
            BigDecimal.ZERO,
            nextNo("FAQ"),
            "查询支付宝授权状态"
        ));
        try {
            var result = alipayGatewayClient.queryFundAuth(auth.authOrderNo(), auth.outRequestNo(), auth.alipayAuthNo(), auth.alipayOperationId());
            fundAuthRepository.markOperationSuccess(operation.id(), null, result.operationId(), null);
            if (result.authNo() != null && !result.authNo().isBlank()) {
                auth = fundAuthRepository.markAuthorized(
                    auth.id(),
                    result.authNo(),
                    result.operationId(),
                    parseAmount(result.totalFreezeAmount(), auth.authAmount())
                );
            }
            return toResponse(auth);
        } catch (BusinessException exception) {
            fundAuthRepository.markOperationFailed(operation.id(), exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public FundAuthCreateResponse createUserFundAuth(Long orderId, BigDecimal amount) {
        var current = AuthContext.get();
        if (current == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        var order = ensureOrder(orderId);
        if (order.userAccountId() == null || !order.userAccountId().equals(current.account().id())) {
            throw BusinessException.forbidden("不能为其他用户订单授权");
        }
        if (current.account().alipayUserId() == null || current.account().alipayUserId().isBlank()) {
            throw BusinessException.badRequest("当前账号未绑定支付宝用户 ID");
        }
        var authAmount = money(amount);
        var active = fundAuthRepository.findActiveByOrderId(order.id());
        if (active.isPresent() && active.get().orderStr() != null && active.get().authStatus() != FundAuthStatus.FAILED) {
            return new FundAuthCreateResponse(toResponse(active.get()), active.get().orderStr());
        }
        var auth = active.orElseGet(() -> fundAuthRepository.createAuthOrder(new FundAuthRepository.AuthCreateRow(
            nextNo("FAO"),
            order.id(),
            current.account().id(),
            current.account().alipayUserId(),
            order.merchantId(),
            order.storeId(),
            FundAuthType.ALIPAY_FUND_AUTH,
            FundAuthStatus.CREATED,
            authAmount,
            nextNo("FAR"),
            "享牛租赁押金/逾期授权 " + order.orderNo()
        )));
        var operation = fundAuthRepository.createOperation(new FundAuthRepository.OperationCreateRow(
            nextNo("FAOP"),
            auth.id(),
            null,
            null,
            FundAuthOperationType.FREEZE,
            FundAuthOperationStatus.PENDING,
            authAmount,
            auth.outRequestNo(),
            "用户发起资金授权冻结"
        ));
        try {
            var result = alipayGatewayClient.createFundAuthFreeze(auth.authOrderNo(), auth.outRequestNo(), auth.authAmount(), auth.subject());
            auth = fundAuthRepository.markAuthorizing(auth.id(), result.orderStr());
            if (result.authNo() != null && !result.authNo().isBlank()) {
                auth = fundAuthRepository.markAuthorized(auth.id(), result.authNo(), result.operationId(), parseAmount(result.amount(), auth.authAmount()));
                fundAuthRepository.markOperationSuccess(operation.id(), null, result.operationId(), null);
            }
            return new FundAuthCreateResponse(toResponse(auth), result.orderStr());
        } catch (BusinessException exception) {
            fundAuthRepository.markOperationFailed(operation.id(), exception.getMessage());
            auth = fundAuthRepository.markFailed(auth.id(), exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public FundAuthResponse capture(Long authOrderId, FundAuthCaptureRequest request) {
        authorizationService.requirePermission("order.operate");
        var auth = ensureAuth(authOrderId);
        authorizationService.requireStoreAccess(auth.merchantId(), auth.storeId());
        if (auth.authStatus() != FundAuthStatus.AUTHORIZED) {
            throw BusinessException.badRequest("只有已授权订单可以扣费");
        }
        var amount = money(request.amount());
        if (remaining(auth).compareTo(amount) < 0) {
            throw BusinessException.badRequest("授权剩余额度不足");
        }
        var bill = request.billId() == null ? null : billRepository.findBill(request.billId())
            .orElseThrow(() -> BusinessException.badRequest("账单不存在"));
        if (bill != null && !bill.orderId().equals(auth.orderId())) {
            throw BusinessException.badRequest("账单不属于当前授权订单");
        }
        if (bill != null && bill.billStatus() == BillStatus.PAID) {
            throw BusinessException.badRequest("账单已支付");
        }
        var operation = fundAuthRepository.createOperation(new FundAuthRepository.OperationCreateRow(
            nextNo("FAOP"),
            auth.id(),
            request.billId(),
            null,
            FundAuthOperationType.CAPTURE,
            FundAuthOperationStatus.PENDING,
            amount,
            nextNo("FAP"),
            defaultRemark(request.remark(), "授权扣费")
        ));
        var paymentNo = nextNo("PAY");
        var payment = bill == null ? null : paymentRepository.createPayment(new PaymentRepository.PaymentCreateRow(
            paymentNo,
            bill.id(),
            bill.orderId(),
            bill.userAccountId(),
            bill.merchantId(),
            bill.storeId(),
            PayChannel.ALIPAY,
            PayStatus.CREATED,
            amount,
            defaultRemark(request.remark(), "享牛授权扣费 " + bill.billNo()),
            auth.alipayUserId()
        ));
        try {
            Long paymentId = null;
            var result = alipayGatewayClient.payWithFundAuth(paymentNo, amount, defaultRemark(request.remark(), auth.subject()), auth.alipayUserId(), auth.alipayAuthNo());
            if (bill != null) {
                payment = paymentRepository.markPaid(payment.id(), amount, result.tradeNo());
                paymentId = payment.id();
                billRepository.markPaid(bill.id(), amount);
                billRepository.addLog(bill.id(), bill.billStatus(), BillStatus.PAID, BillOperationType.PAYMENT_SUCCESS, currentAccountId(), "资金授权扣费成功");
                orderRepository.increasePaidAmount(bill.orderId(), amount);
                overdueService.resolveByBillId(bill.id());
            }
            fundAuthRepository.markOperationSuccess(operation.id(), result.tradeNo(), null, paymentId);
            return toResponse(fundAuthRepository.addCaptured(auth.id(), amount));
        } catch (BusinessException exception) {
            if (payment != null) {
                paymentRepository.markFailed(payment.id(), exception.getMessage());
            }
            fundAuthRepository.markOperationFailed(operation.id(), exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public FundAuthResponse unfreeze(Long authOrderId, FundAuthUnfreezeRequest request) {
        authorizationService.requirePermission("order.operate");
        var auth = ensureAuth(authOrderId);
        authorizationService.requireStoreAccess(auth.merchantId(), auth.storeId());
        if (auth.alipayAuthNo() == null || auth.alipayAuthNo().isBlank()) {
            throw BusinessException.badRequest("支付宝授权号为空，不能解冻");
        }
        var amount = money(request.amount());
        if (remaining(auth).compareTo(amount) < 0) {
            throw BusinessException.badRequest("解冻金额超过剩余额度");
        }
        var operation = fundAuthRepository.createOperation(new FundAuthRepository.OperationCreateRow(
            nextNo("FAOP"),
            auth.id(),
            null,
            null,
            FundAuthOperationType.UNFREEZE,
            FundAuthOperationStatus.PENDING,
            amount,
            nextNo("FAU"),
            defaultRemark(request.remark(), "授权解冻")
        ));
        try {
            var result = alipayGatewayClient.unfreezeFundAuth(auth.alipayAuthNo(), operation.outRequestNo(), amount, defaultRemark(request.remark(), "享牛租赁授权解冻"));
            fundAuthRepository.markOperationSuccess(operation.id(), null, result.operationId(), null);
            return toResponse(fundAuthRepository.addReleased(auth.id(), parseAmount(result.amount(), amount)));
        } catch (BusinessException exception) {
            fundAuthRepository.markOperationFailed(operation.id(), exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public FundAuthResponse cancel(Long authOrderId, String remark) {
        authorizationService.requirePermission("order.operate");
        var auth = ensureAuth(authOrderId);
        authorizationService.requireStoreAccess(auth.merchantId(), auth.storeId());
        var operation = fundAuthRepository.createOperation(new FundAuthRepository.OperationCreateRow(
            nextNo("FAOP"),
            auth.id(),
            null,
            null,
            FundAuthOperationType.CANCEL,
            FundAuthOperationStatus.PENDING,
            BigDecimal.ZERO,
            nextNo("FAC"),
            defaultRemark(remark, "撤销授权")
        ));
        try {
            var result = alipayGatewayClient.cancelFundAuth(auth.authOrderNo(), operation.outRequestNo(), auth.alipayAuthNo(), auth.alipayOperationId(), defaultRemark(remark, "享牛租赁撤销授权"));
            fundAuthRepository.markOperationSuccess(operation.id(), null, result.operationId(), null);
            return toResponse(fundAuthRepository.markCancelled(auth.id()));
        } catch (BusinessException exception) {
            fundAuthRepository.markOperationFailed(operation.id(), exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public boolean handleNotify(Map<String, String> params) {
        var notifyId = params.get("notify_id");
        if (fundAuthRepository.findNotifyByNotifyId(notifyId).isPresent()) {
            return true;
        }
        var auth = fundAuthRepository.findByAuthOrderNo(params.get("out_order_no")).orElse(null);
        var verified = alipayGatewayClient.verifyNotify(params);
        if (!verified) {
            fundAuthRepository.createNotify(notifyRow(auth, params, false, false, "支付宝资金授权回调验签失败"));
            return false;
        }
        if (auth == null) {
            fundAuthRepository.createNotify(notifyRow(null, params, true, false, "授权单不存在"));
            return false;
        }
        if (params.get("auth_no") != null) {
            fundAuthRepository.markAuthorized(
                auth.id(),
                params.get("auth_no"),
                params.get("operation_id"),
                parseAmount(params.get("total_freeze_amount"), auth.authAmount())
            );
        }
        fundAuthRepository.createNotify(notifyRow(auth, params, true, true, null));
        return true;
    }

    private FundAuthRepository.NotifyCreateRow notifyRow(FundAuthOrder auth, Map<String, String> params, boolean verified, boolean processed, String failureReason) {
        return new FundAuthRepository.NotifyCreateRow(
            auth == null ? null : auth.id(),
            params.get("notify_id"),
            params.get("out_order_no"),
            params.get("out_request_no"),
            params.get("auth_no"),
            params.get("operation_id"),
            params.get("status"),
            parseAmount(params.get("total_freeze_amount"), null),
            parseAmount(params.get("rest_amount"), null),
            verified,
            processed,
            params.toString(),
            failureReason
        );
    }

    private RentalOrder ensureOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> BusinessException.badRequest("订单不存在"));
    }

    private FundAuthOrder ensureAuth(Long authOrderId) {
        return fundAuthRepository.findById(authOrderId).orElseThrow(() -> BusinessException.badRequest("授权单不存在"));
    }

    private BigDecimal remaining(FundAuthOrder auth) {
        return auth.frozenAmount()
            .subtract(auth.capturedAmount())
            .subtract(auth.releasedAmount())
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw BusinessException.badRequest("金额必须大于 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseAmount(String value, BigDecimal fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String defaultRemark(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long currentAccountId() {
        var current = AuthContext.get();
        return current == null ? null : current.account().id();
    }

    private FundAuthStatus parseStatusNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FundAuthStatus.valueOf(value);
        } catch (Exception exception) {
            throw BusinessException.badRequest("不支持的授权状态");
        }
    }

    private FundAuthResponse toResponse(FundAuthOrder auth) {
        return new FundAuthResponse(
            auth.id(),
            auth.authOrderNo(),
            auth.orderId(),
            auth.userAccountId(),
            auth.alipayUserId(),
            auth.merchantId(),
            auth.storeId(),
            auth.authType().name(),
            auth.authStatus().name(),
            auth.authAmount(),
            auth.frozenAmount(),
            auth.capturedAmount(),
            auth.releasedAmount(),
            auth.outRequestNo(),
            auth.alipayAuthNo(),
            auth.alipayOperationId(),
            auth.orderStr(),
            auth.subject(),
            auth.lastError(),
            auth.authorizedAt(),
            auth.closedAt(),
            auth.createdAt()
        );
    }

    private FundAuthOperationResponse toOperationResponse(FundAuthOperation operation) {
        return new FundAuthOperationResponse(
            operation.id(),
            operation.operationNo(),
            operation.authOrderId(),
            operation.billId(),
            operation.paymentId(),
            operation.operationType().name(),
            operation.operationStatus().name(),
            operation.amount(),
            operation.outRequestNo(),
            operation.alipayTradeNo(),
            operation.alipayOperationId(),
            operation.remark(),
            operation.failureReason(),
            operation.createdAt()
        );
    }

    private FundAuthNotifyResponse toNotifyResponse(FundAuthNotify notify) {
        return new FundAuthNotifyResponse(
            notify.id(),
            notify.authOrderId(),
            notify.notifyId(),
            notify.outOrderNo(),
            notify.outRequestNo(),
            notify.authNo(),
            notify.operationId(),
            notify.authStatus(),
            notify.totalFreezeAmount(),
            notify.restAmount(),
            notify.verified(),
            notify.processed(),
            notify.failureReason(),
            notify.receivedAt()
        );
    }

    private String nextNo(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
