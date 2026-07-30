package com.xniu.rental.order.service;

import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.model.BillGenerationType;
import com.xniu.rental.bill.model.BillItemType;
import com.xniu.rental.bill.model.BillOperationType;
import com.xniu.rental.bill.model.BillStatus;
import com.xniu.rental.bill.model.BillType;
import com.xniu.rental.bill.model.RentalBill;
import com.xniu.rental.bill.repository.BillRepository;
import com.xniu.rental.order.dto.RenewalRunResponse;
import com.xniu.rental.order.model.OrderOperationType;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import com.xniu.rental.pricing.model.RenewalChargeMode;
import com.xniu.rental.pricing.service.RenewalPricingCalculator;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderRenewalService {

    private static final int DEFAULT_RENEWAL_LIMIT = 50;

    private final OrderRepository orderRepository;
    private final BillRepository billRepository;
    private final AuthorizationService authorizationService;
    private final RenewalPricingCalculator renewalPricingCalculator;

    public OrderRenewalService(
        OrderRepository orderRepository,
        BillRepository billRepository,
        AuthorizationService authorizationService,
        RenewalPricingCalculator renewalPricingCalculator
    ) {
        this.orderRepository = orderRepository;
        this.billRepository = billRepository;
        this.authorizationService = authorizationService;
        this.renewalPricingCalculator = renewalPricingCalculator;
    }

    @Transactional
    public RenewalRunResponse runDueRenewals(Integer limit, String remark) {
        authorizationService.requirePermission("order.operate");
        return runDueRenewalsInternal(limit, remark);
    }

    @Transactional
    public RenewalRunResponse runDueRenewalsInternal(Integer limit, String remark) {
        var normalizedLimit = limit == null || limit <= 0 ? DEFAULT_RENEWAL_LIMIT : Math.min(limit, DEFAULT_RENEWAL_LIMIT);
        var now = LocalDateTime.now();
        var orders = orderRepository.listDueForAutoRenewal(now, normalizedLimit);
        var batchNo = nextBatchNo();
        var generatedCount = 0;
        for (var order : orders) {
            if (createRenewalBillIfNeeded(order, now, batchNo, remark)) {
                generatedCount++;
            }
        }
        if (generatedCount == 0) {
            return new RenewalRunResponse(orders.size(), 0, null, null);
        }
        var batch = billRepository.createBatch(batchNo, BillGenerationType.RENEWAL, null, generatedCount, defaultRemark(remark, "自动续租扫描"));
        return new RenewalRunResponse(orders.size(), generatedCount, batch.id(), batch.batchNo());
    }

    @Transactional
    public void handlePaidBill(RentalBill bill) {
        if (bill.billType() != BillType.RENEWAL) {
            return;
        }
        var order = orderRepository.findById(bill.orderId()).orElse(null);
        if (order == null || order.returnedAt() != null || isTerminal(order.orderStatus()) || !hasValidRenewalRule(order)) {
            return;
        }
        var nextExpectedReturnAt = nextExpectedReturnAt(order, bill);
        var hasOtherDueUnpaidBills = billRepository.hasDueUnpaidBills(order.id(), LocalDateTime.now());
        var updated = orderRepository.applyRenewalSuccess(order.id(), nextExpectedReturnAt, !hasOtherDueUnpaidBills);
        orderRepository.addLog(
            order.id(),
            order.orderStatus(),
            updated.orderStatus(),
            OrderOperationType.TRANSITION,
            null,
            "续租账单已支付，预计归还时间顺延至 " + nextExpectedReturnAt
        );
    }

    private boolean createRenewalBillIfNeeded(RentalOrder order, LocalDateTime now, String batchNo, String remark) {
        if (!hasValidRenewalRule(order) || order.expectedReturnAt() == null || order.expectedReturnAt().isAfter(now)) {
            return false;
        }
        var billingMode = parseBillingMode(order.renewalBillingMode());
        var renewalDays = renewalPricingCalculator.periodDays(order);
        var chargeMode = RenewalChargeMode.PERIOD;
        var unitPrice = order.renewalAmount();
        var payableAmount = order.renewalAmount().setScale(2, RoundingMode.HALF_UP);
        if (billingMode == RenewalBillingMode.DAILY_CAPPED) {
            var elapsedDays = renewalPricingCalculator.elapsedBillableDays(order, now);
            if (elapsedDays <= 0) {
                return false;
            }
            markOverdueIfNeeded(order);
            if (elapsedDays < renewalDays) {
                return false;
            }
            var quote = renewalPricingCalculator.quoteDaily(order, renewalDays, true);
            chargeMode = RenewalChargeMode.DAILY;
            unitPrice = quote.unitPrice();
            payableAmount = quote.amount();
        }
        if (billRepository.findOpenBillByOrderAndType(order.id(), BillType.RENEWAL).isPresent()) {
            markOverdueIfNeeded(order);
            return false;
        }
        var periodNo = nextRenewalPeriodNo(order);
        var bill = billRepository.createBill(new BillRepository.BillCreateRow(
            nextBillNo(),
            order.id(),
            order.userAccountId(),
            order.merchantId(),
            order.storeId(),
            BillType.RENEWAL,
            periodNo,
            BillStatus.PENDING_PAYMENT,
            order.expectedReturnAt(),
            payableAmount,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            defaultRemark(remark, "自动续租账单"),
            batchNo,
            chargeMode.name(),
            renewalDays,
            unitPrice
        ));
        var itemName = chargeMode == RenewalChargeMode.DAILY
            ? "按日累计达到整期封顶 " + renewalDays + " 天"
            : "第 " + periodNo + " 期续租租金";
        billRepository.addItem(bill.id(), BillItemType.RENEWAL_RENT, itemName, payableAmount);
        billRepository.addLog(bill.id(), null, BillStatus.PENDING_PAYMENT, BillOperationType.GENERATE, null, defaultRemark(remark, "自动续租账单"));
        markOverdueIfNeeded(order);
        return true;
    }

    private void markOverdueIfNeeded(RentalOrder order) {
        if (order.orderStatus() != OrderStatus.OVERDUE) {
            orderRepository.updateStatus(order.id(), OrderStatus.OVERDUE, null, null, null);
            orderRepository.addLog(
                order.id(),
                order.orderStatus(),
                OrderStatus.OVERDUE,
                OrderOperationType.TRANSITION,
                null,
                "到期未归还，进入自动续租处理"
            );
        }
    }

    private boolean hasValidRenewalRule(RentalOrder order) {
        return Boolean.TRUE.equals(order.autoRenewEnabled())
            && order.renewalUnit() != null
            && order.renewalValue() != null
            && order.renewalValue() > 0
            && order.renewalAmount() != null
            && order.renewalAmount().signum() > 0
            && (parseBillingMode(order.renewalBillingMode()) != RenewalBillingMode.DAILY_CAPPED
                || (order.renewalDailyAmount() != null && order.renewalDailyAmount().signum() > 0));
    }

    private LocalDateTime nextExpectedReturnAt(RentalOrder order, RentalBill bill) {
        var base = order.expectedReturnAt() == null ? LocalDateTime.now() : order.expectedReturnAt();
        if (bill.renewalDays() != null && bill.renewalDays() > 0) {
            return base.plusDays(bill.renewalDays());
        }
        if ("MONTH".equals(order.renewalUnit())) {
            return base.plusDays(30L * order.renewalValue());
        }
        return base.plusDays(order.renewalValue());
    }

    private RenewalBillingMode parseBillingMode(String value) {
        try {
            return value == null || value.isBlank() ? RenewalBillingMode.PERIOD : RenewalBillingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return RenewalBillingMode.PERIOD;
        }
    }

    private int nextRenewalPeriodNo(RentalOrder order) {
        return billRepository.findMaxPeriodNo(order.id(), BillType.RENEWAL).orElse(Math.max(order.totalPeriods() == null ? 1 : order.totalPeriods(), 1)) + 1;
    }

    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED || status == OrderStatus.EXCEPTION;
    }

    private String defaultRemark(String remark, String fallback) {
        return remark == null || remark.isBlank() ? fallback : remark;
    }

    private String nextBillNo() {
        return "BILL-R-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nextBatchNo() {
        return "BATCH-R-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
