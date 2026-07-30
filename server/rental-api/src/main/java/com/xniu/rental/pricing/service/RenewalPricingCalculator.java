package com.xniu.rental.pricing.service;

import com.xniu.rental.common.BusinessException;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.pricing.model.RenewalBillingMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class RenewalPricingCalculator {

    public int periodDays(RentalOrder order) {
        if (order.renewalValue() == null || order.renewalValue() <= 0 || order.renewalUnit() == null) {
            throw BusinessException.badRequest("当前订单续租周期不完整");
        }
        return "MONTH".equals(order.renewalUnit()) ? Math.multiplyExact(order.renewalValue(), 30) : order.renewalValue();
    }

    public int elapsedBillableDays(RentalOrder order, LocalDateTime at) {
        if (order.expectedReturnAt() == null || at == null) {
            return 0;
        }
        var graceHours = order.renewalGraceHours() == null ? 0 : Math.max(order.renewalGraceHours(), 0);
        var billingStartsAt = order.expectedReturnAt().plusHours(graceHours);
        if (!at.isAfter(billingStartsAt)) {
            return 0;
        }
        var seconds = Duration.between(billingStartsAt, at).getSeconds();
        return Math.toIntExact((seconds + 86_399L) / 86_400L);
    }

    public RenewalQuote quoteDaily(RentalOrder order, int days, boolean overdueUsage) {
        if (!RenewalBillingMode.DAILY_CAPPED.name().equals(order.renewalBillingMode())) {
            throw BusinessException.badRequest("当前订单未开启按日续租");
        }
        if (days <= 0) {
            throw BusinessException.badRequest("按日续租天数必须大于 0");
        }
        if (order.renewalAmount() == null || order.renewalAmount().signum() <= 0) {
            throw BusinessException.badRequest("当前订单整期续租金额不完整");
        }
        var dailyRate = overdueUsage && order.overdueDailyAmount() != null
            ? order.overdueDailyAmount()
            : order.renewalDailyAmount();
        if (dailyRate == null || dailyRate.signum() <= 0) {
            throw BusinessException.badRequest(overdueUsage ? "当前订单逾期日占用费未配置" : "当前订单日续租价未配置");
        }
        var periodDays = periodDays(order);
        var fullPeriods = days / periodDays;
        var remainingDays = days % periodDays;
        var capEnabled = Boolean.TRUE.equals(order.renewalDailyCapEnabled());
        var amount = dailyRate.multiply(BigDecimal.valueOf(days));
        if (capEnabled) {
            var cappedFullPeriodAmount = dailyRate.multiply(BigDecimal.valueOf(periodDays)).min(order.renewalAmount());
            var fullPeriodAmount = cappedFullPeriodAmount.multiply(BigDecimal.valueOf(fullPeriods));
            var remainingAmount = dailyRate.multiply(BigDecimal.valueOf(remainingDays)).min(order.renewalAmount());
            amount = fullPeriodAmount.add(remainingAmount);
        }
        return new RenewalQuote(
            days,
            periodDays,
            dailyRate.setScale(2, RoundingMode.HALF_UP),
            amount.setScale(2, RoundingMode.HALF_UP),
            fullPeriods,
            remainingDays,
            capEnabled
        );
    }

    public record RenewalQuote(
        Integer days,
        Integer periodDays,
        BigDecimal unitPrice,
        BigDecimal amount,
        Integer fullPeriods,
        Integer remainingDays,
        Boolean capped
    ) {
    }
}
