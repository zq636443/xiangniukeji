package com.xniu.rental.settlement.service;

import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProfitSharingCalculator {

    private static final BigDecimal ORDER_FEE_SERVICE_RATE = new BigDecimal("0.0300");

    private ProfitSharingCalculator() {
    }

    public static Allocation calculate(
        SettlementCalculationVersion calculationVersion,
        BigDecimal settlementBaseAmount,
        BigDecimal channelFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal batteryCostAmount,
        BigDecimal storeOperationRate,
        BigDecimal maintenanceFundRate,
        BigDecimal channelReferralRate,
        BigDecimal investorShareRate
    ) {
        var normalizedVersion = calculationVersion == null
            ? SettlementCalculationVersion.PROFIT_V2
            : calculationVersion;
        var base = money(settlementBaseAmount);
        var channelFee = multiply(base, channelFeeRate);
        var platformFee = multiply(base, platformFeeRate);
        var batteryCost = money(batteryCostAmount);
        var grossChannelReferral = normalizedVersion.usesGrossChannelReferral()
            ? multiply(base, channelReferralRate)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        var distributable = base
            .subtract(channelFee)
            .subtract(platformFee)
            .subtract(grossChannelReferral)
            .subtract(batteryCost)
            .setScale(2, RoundingMode.HALF_UP);
        if (distributable.signum() < 0) {
            distributable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        var grossReferralVersion = normalizedVersion.usesGrossChannelReferral();
        var remainingWeight = rate(storeOperationRate)
            .add(rate(maintenanceFundRate))
            .add(rate(investorShareRate));
        var storeOperation = grossReferralVersion
            ? multiplyByWeight(distributable, storeOperationRate, remainingWeight)
            : multiply(distributable, storeOperationRate);
        var maintenanceFund = grossReferralVersion
            ? multiplyByWeight(distributable, maintenanceFundRate, remainingWeight)
            : multiply(distributable, maintenanceFundRate);
        // PROFIT_V3 takes referral at gross level alongside 5% and 3%, then
        // distributes the post-battery balance by the remaining 15:10:55
        // weights. The investor receives the exact final-cent residual.
        var channelReferral = grossReferralVersion
            ? grossChannelReferral
            : multiply(distributable, channelReferralRate);
        var investorShare = distributable
            .subtract(storeOperation)
            .subtract(maintenanceFund)
            .subtract(grossReferralVersion ? BigDecimal.ZERO : channelReferral)
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
            investorShare,
            normalizedVersion
        );
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
        return calculate(
            SettlementCalculationVersion.PROFIT_V2,
            settlementBaseAmount,
            channelFeeRate,
            platformFeeRate,
            batteryCostAmount,
            storeOperationRate,
            maintenanceFundRate,
            channelReferralRate,
            investorShareRate
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
            SettlementCalculationVersion.PROFIT_V2,
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

    private static BigDecimal multiplyByWeight(BigDecimal amount, BigDecimal weight, BigDecimal totalWeight) {
        if (totalWeight == null || totalWeight.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount
            .multiply(rate(weight))
            .divide(totalWeight, 2, RoundingMode.HALF_UP);
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
        BigDecimal investorShareAmount,
        SettlementCalculationVersion calculationVersion
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
