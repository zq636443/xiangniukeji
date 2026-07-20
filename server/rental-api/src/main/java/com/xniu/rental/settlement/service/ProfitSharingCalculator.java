package com.xniu.rental.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProfitSharingCalculator {

    private ProfitSharingCalculator() {
    }

    public static Allocation calculate(
        BigDecimal settlementBaseAmount,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        var base = money(settlementBaseAmount);
        var channelFee = multiply(base, channelFeeRate);
        var platformFee = multiply(base, platformFeeRate);
        var distributable = base.subtract(channelFee).subtract(platformFee).setScale(2, RoundingMode.HALF_UP);
        var storeOperation = multiply(distributable, storeOperationRate);
        var maintenanceFund = multiply(distributable, maintenanceFundRate);
        var channelReferral = multiply(distributable, channelReferralRate);
        var investorShare = distributable
            .subtract(storeOperation)
            .subtract(maintenanceFund)
            .subtract(channelReferral)
            .setScale(2, RoundingMode.HALF_UP);
        return new Allocation(
            base,
            rate(channelFeeRate),
            channelFee,
            rate(platformFeeRate),
            platformFee,
            distributable,
            rate(storeOperationRate),
            storeOperation,
            rate(maintenanceFundRate),
            maintenanceFund,
            rate(channelReferralRate),
            channelReferral,
            rate(investorShareRate),
            investorShare
        );
    }

    private static BigDecimal multiply(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    public record Allocation(
        BigDecimal settlementBaseAmount,
        BigDecimal channelFeeRate,
        BigDecimal channelFeeAmount,
        BigDecimal platformFeeRate,
        BigDecimal platformFeeAmount,
        BigDecimal distributableAmount,
        BigDecimal storeOperationRate,
        BigDecimal storeOperationAmount,
        BigDecimal maintenanceFundRate,
        BigDecimal maintenanceFundAmount,
        BigDecimal channelReferralRate,
        BigDecimal channelReferralAmount,
        BigDecimal investorShareRate,
        BigDecimal investorShareAmount
    ) {
    }
}
