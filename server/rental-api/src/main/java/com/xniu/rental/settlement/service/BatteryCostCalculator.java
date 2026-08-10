package com.xniu.rental.settlement.service;

import com.xniu.rental.product.model.LeaseUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BatteryCostCalculator {

    private static final int DAYS_PER_MONTH = 30;

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
