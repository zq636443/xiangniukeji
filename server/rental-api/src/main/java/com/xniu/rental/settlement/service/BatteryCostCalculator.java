package com.xniu.rental.settlement.service;

import com.xniu.rental.product.model.LeaseUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

public final class BatteryCostCalculator {

    private static final int DAYS_PER_MONTH = 30;
    private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(86_400L);
    private static final BigDecimal SECONDS_PER_COST_MONTH = SECONDS_PER_DAY.multiply(BigDecimal.valueOf(DAYS_PER_MONTH));
    private static final int CALCULATION_SCALE = 12;

    private BatteryCostCalculator() {
    }

    public static BigDecimal calculate(
        BigDecimal dailyAmount,
        BigDecimal monthlyAmount,
        LeaseUnit leaseUnit,
        int leaseValue,
        int leaseMultiplier
    ) {
        if (dailyAmount == null || monthlyAmount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        var multiplier = Math.max(leaseMultiplier, 1);
        var totalValue = Math.max(leaseValue, 0) * multiplier;
        if (leaseUnit == LeaseUnit.MONTH) {
            return money(monthlyAmount.multiply(BigDecimal.valueOf(totalValue)));
        }
        var fullMonths = totalValue / DAYS_PER_MONTH;
        var remainingDays = totalValue % DAYS_PER_MONTH;
        return money(monthlyAmount.multiply(BigDecimal.valueOf(fullMonths))
            .add(dailyAmount.multiply(BigDecimal.valueOf(remainingDays))));
    }

    /**
     * Calculates the complete battery cost for one concrete rental interval.
     * Thirty days is only the configured monthly-cost tier; the rental interval
     * itself always comes from its persisted timestamps.  Any remainder after
     * complete 30-day tiers is charged continuously by elapsed seconds so a
     * 20.5-day interval is not silently truncated to 20 days.
     */
    public static BigDecimal calculateExactPeriod(
        BigDecimal dailyAmount,
        BigDecimal monthlyAmount,
        LocalDateTime periodStartAt,
        LocalDateTime periodEndAt
    ) {
        if (dailyAmount == null || monthlyAmount == null
            || periodStartAt == null || periodEndAt == null || !periodEndAt.isAfter(periodStartAt)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        var duration = Duration.between(periodStartAt, periodEndAt);
        var totalSeconds = BigDecimal.valueOf(duration.getSeconds())
            .add(BigDecimal.valueOf(duration.getNano(), 9));
        var fullCostMonths = totalSeconds.divideToIntegralValue(SECONDS_PER_COST_MONTH);
        var remainderSeconds = totalSeconds.remainder(SECONDS_PER_COST_MONTH);
        var remainderDays = remainderSeconds.divide(SECONDS_PER_DAY, CALCULATION_SCALE, RoundingMode.HALF_UP);
        return money(monthlyAmount.multiply(fullCostMonths).add(dailyAmount.multiply(remainderDays)));
    }

    public static BigDecimal prorate(BigDecimal totalCost, BigDecimal sourceAmount, BigDecimal totalAmount) {
        if (totalCost == null || totalCost.signum() <= 0 || totalAmount == null || totalAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        var normalizedSource = sourceAmount == null ? BigDecimal.ZERO : sourceAmount;
        return money(totalCost.multiply(normalizedSource).divide(totalAmount, 8, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
