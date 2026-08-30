package com.xniu.rental.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProfitSharingCalculator {

    private static final BigDecimal ORDER_FEE_SERVICE_RATE = new BigDecimal("0.0300");

    private ProfitSharingCalculator() {
    }

    public static Allocation calculate(
        BigDecimal settlementBaseAmount,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal batteryCostAmount,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        var base = money(settlementBaseAmount);
        var channelFee = multiply(base, channelFeeRate);
        var platformFee = multiply(base, platformFeeRate);
        var batteryCost = money(batteryCostAmount);
        var distributable = base.subtract(channelFee).subtract(platformFee).subtract(batteryCost).setScale(2, RoundingMode.HALF_UP);
        if (distributable.signum() < 0) {
            distributable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
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
            batteryCost,
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

    public static Allocation calculate(
        BigDecimal settlementBaseAmount,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        return calculate(
            settlementBaseAmount,
            channelFeeRate,
            platformFeeRate,
            BigDecimal.ZERO,
            storeOperationRate,
            maintenanceFundRate,
            channelReferralRate,
            investorShareRate
        );
    }

    public static OrderFeeAllocation calculateOrderFee(BigDecimal orderFeeAmount) {
        var gross = money(orderFeeAmount);
        // The confirmed business rule is a direct 97% merchant entitlement.
        // Calculate that side first, then assign the exact cent remainder to
        // the platform so both rows always add back to the gross fee.
        var merchantNet = multiply(gross, BigDecimal.ONE.subtract(ORDER_FEE_SERVICE_RATE));
        var serviceFee = gross.subtract(merchantNet).setScale(2, RoundingMode.HALF_UP);
        return new OrderFeeAllocation(
            gross,
            ORDER_FEE_SERVICE_RATE,
            serviceFee,
            merchantNet
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
        BigDecimal batteryCostAmount,
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

    public record OrderFeeAllocation(
        BigDecimal orderFeeAmount,
        BigDecimal serviceFeeRate,
        BigDecimal serviceFeeAmount,
        BigDecimal merchantNetAmount
    ) {
    }
}
