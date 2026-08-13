package com.xniu.rental.externalorder.service;

import com.xniu.rental.externalorder.model.ExternalOrderOperationType;
import com.xniu.rental.externalorder.model.ExternalRentalOrderStatus;
import com.xniu.rental.externalorder.repository.ExternalOrderRenewalRepository;
import com.xniu.rental.externalorder.repository.ExternalRentalOrderRepository;
import com.xniu.rental.product.model.LeaseUnit;
import com.xniu.rental.product.repository.ProductRepository;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.SettlementIncomeService;
import com.xniu.rental.settlement.service.SettlementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExternalOrderAutoRenewalService {

    private static final Logger log = LoggerFactory.getLogger(ExternalOrderAutoRenewalService.class);
    private static final int SCAN_LIMIT = 500;
    private static final int CATCH_UP_LIMIT = 120;

    private final ExternalRentalOrderRepository orderRepository;
    private final ExternalOrderRenewalRepository renewalRepository;
    private final ProductRepository productRepository;
    private final SettlementService settlementService;
    private final SettlementIncomeService settlementIncomeService;
    private final TransactionTemplate transactionTemplate;

    public ExternalOrderAutoRenewalService(
        ExternalRentalOrderRepository orderRepository,
        ExternalOrderRenewalRepository renewalRepository,
        ProductRepository productRepository,
        SettlementService settlementService,
        SettlementIncomeService settlementIncomeService,
        TransactionTemplate transactionTemplate
    ) {
        this.orderRepository = orderRepository;
        this.renewalRepository = renewalRepository;
        this.productRepository = productRepository;
        this.settlementService = settlementService;
        this.settlementIncomeService = settlementIncomeService;
        this.transactionTemplate = transactionTemplate;
    }

    public int accrueDueOrders(LocalDateTime dueAt) {
        var accrued = 0;
        for (var orderId : renewalRepository.listDueOrderIds(dueAt, SCAN_LIMIT)) {
            try {
                accrued += transactionTemplate.execute(status -> accrueOrder(orderId, dueAt));
            } catch (RuntimeException exception) {
                log.error("补录订单 {} 自动续租记账失败，已跳过并保留原数据", orderId, exception);
            }
        }
        return accrued;
    }

    private int accrueOrder(Long orderId, LocalDateTime dueAt) {
        var accrued = 0;
        for (var iteration = 0; iteration < CATCH_UP_LIMIT; iteration += 1) {
            var order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (!isDue(order, dueAt)) {
                break;
            }
            var periodStartAt = order.expectedReturnAt();
            var periodEndAt = advance(periodStartAt, order.renewalUnit(), order.renewalValue());
            var sku = productRepository.findSku(order.skuId()).orElseThrow();
            var periodUnit = LeaseUnit.valueOf(order.renewalUnit());
            var batteryCost = BatteryCostCalculator.calculate(
                sku.batteryCostDailyAmount(),
                sku.batteryCostMonthlyAmount(),
                periodUnit,
                order.renewalValue(),
                1
            );
            var event = renewalRepository.create(
                order.id(),
                nextEventNo(),
                renewalRepository.nextPeriodNo(order.id()),
                periodStartAt,
                periodEndAt,
                money(order.renewalAmount()),
                batteryCost
            );
            var snapshot = settlementService.createExternalRenewalSnapshot(
                event.id(),
                order.settlementSnapshotId(),
                event.renewalAmount(),
                event.batteryCostAmount()
            );
            renewalRepository.attachSnapshot(event.id(), snapshot.id());
            settlementIncomeService.createExternalRenewalEntries(
                event.id(),
                event.eventNo(),
                snapshot.id(),
                event.periodStartAt(),
                event.renewalAmount()
            );
            orderRepository.advanceExpectedReturnAt(order.id(), periodEndAt);
            orderRepository.addLog(
                order.id(),
                order.orderStatus(),
                order.orderStatus(),
                ExternalOrderOperationType.AUTO_RENEW,
                null,
                "系统自动续租至 " + periodEndAt
            );
            accrued += 1;
        }
        return accrued;
    }

    private boolean isDue(com.xniu.rental.externalorder.model.ExternalRentalOrder order, LocalDateTime dueAt) {
        return order != null
            && order.orderStatus() == ExternalRentalOrderStatus.ACTIVE
            && Boolean.TRUE.equals(order.autoRenewEnabled())
            && order.expectedReturnAt() != null
            && !order.expectedReturnAt().isAfter(dueAt)
            && order.renewalAmount() != null
            && order.renewalAmount().signum() > 0
            && order.renewalUnit() != null
            && order.renewalValue() != null
            && order.renewalValue() > 0;
    }

    private LocalDateTime advance(LocalDateTime base, String unit, int value) {
        return "MONTH".equals(unit) ? base.plusDays(30L * value) : base.plusDays(value);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String nextEventNo() {
        return "ERN-" + UUID.randomUUID();
    }
}
