package com.xniu.rental.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xniu.rental.settlement.model.SettlementCalculationVersion;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProfitSharingCalculatorTests {

    @Test
    void defaultRuleAllocatesActualVerificationAmountExactly() {
        var allocation = ProfitSharingCalculator.calculate(
            new BigDecimal("1000.00"),
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        );

        assertThat(allocation.channelFeeAmount()).isEqualByComparingTo("50.00");
        assertThat(allocation.platformFeeAmount()).isEqualByComparingTo("30.00");
        assertThat(allocation.distributableAmount()).isEqualByComparingTo("920.00");
        assertThat(allocation.storeOperationAmount()).isEqualByComparingTo("138.00");
        assertThat(allocation.maintenanceFundAmount()).isEqualByComparingTo("92.00");
        assertThat(allocation.channelReferralAmount()).isEqualByComparingTo("184.00");
        assertThat(allocation.investorShareAmount()).isEqualByComparingTo("506.00");
        assertThat(allocation.channelFeeAmount()
            .add(allocation.platformFeeAmount())
            .add(allocation.storeOperationAmount())
            .add(allocation.maintenanceFundAmount())
            .add(allocation.channelReferralAmount())
            .add(allocation.investorShareAmount()))
            .isEqualByComparingTo(allocation.settlementBaseAmount());
    }

    @Test
    void orderFeeShouldReserveThreePercentServiceFee() {
        var allocation = ProfitSharingCalculator.calculateOrderFee(new BigDecimal("100.00"));

        assertThat(allocation.orderFeeAmount()).isEqualByComparingTo("100.00");
        assertThat(allocation.serviceFeeAmount()).isEqualByComparingTo("3.00");
        assertThat(allocation.merchantNetAmount()).isEqualByComparingTo("97.00");
    }

    @Test
    void orderFeeShouldRoundTheConfirmedNinetySevenPercentMerchantAmountFirst() {
        var allocation = ProfitSharingCalculator.calculateOrderFee(new BigDecimal("1.50"));

        assertThat(allocation.merchantNetAmount()).isEqualByComparingTo("1.46");
        assertThat(allocation.serviceFeeAmount()).isEqualByComparingTo("0.04");
        assertThat(allocation.merchantNetAmount().add(allocation.serviceFeeAmount()))
            .isEqualByComparingTo("1.50");
    }

    @Test
    void profitV2ShouldKeepItsHistoricalPostBatteryReferralFormula() {
        var allocation = ProfitSharingCalculator.calculate(
            new BigDecimal("399.00"),
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("200.00"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        );

        assertThat(allocation.channelFeeAmount()).isEqualByComparingTo("19.95");
        assertThat(allocation.platformFeeAmount()).isEqualByComparingTo("11.97");
        assertThat(allocation.batteryCostAmount()).isEqualByComparingTo("200.00");
        assertThat(allocation.distributableAmount()).isEqualByComparingTo("167.08");
        assertThat(allocation.storeOperationAmount()).isEqualByComparingTo("25.06");
        assertThat(allocation.maintenanceFundAmount()).isEqualByComparingTo("16.71");
        assertThat(allocation.channelReferralAmount()).isEqualByComparingTo("33.42");
        assertThat(allocation.investorShareAmount()).isEqualByComparingTo("91.89");
    }

    @Test
    void profitV3ShouldTakeReferralFromGrossThenRedistributeByRemainingWeights() {
        var allocation = ProfitSharingCalculator.calculate(
            SettlementCalculationVersion.PROFIT_V3,
            new BigDecimal("399.00"),
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("200.00"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        );

        assertThat(allocation.channelFeeAmount()).isEqualByComparingTo("19.95");
        assertThat(allocation.platformFeeAmount()).isEqualByComparingTo("11.97");
        assertThat(allocation.channelReferralAmount()).isEqualByComparingTo("79.80");
        assertThat(allocation.batteryCostAmount()).isEqualByComparingTo("200.00");
        assertThat(allocation.distributableAmount()).isEqualByComparingTo("87.28");
        assertThat(allocation.storeOperationAmount()).isEqualByComparingTo("16.37");
        assertThat(allocation.maintenanceFundAmount()).isEqualByComparingTo("10.91");
        assertThat(allocation.investorShareAmount()).isEqualByComparingTo("60.00");
        assertThat(allocation.channelFeeAmount()
            .add(allocation.platformFeeAmount())
            .add(allocation.channelReferralAmount())
            .add(allocation.batteryCostAmount())
            .add(allocation.storeOperationAmount())
            .add(allocation.maintenanceFundAmount())
            .add(allocation.investorShareAmount()))
            .isEqualByComparingTo(allocation.settlementBaseAmount());
    }

    @Test
    void profitV3ShouldUseTheConfiguredSnapshotWeightsInsteadOfHardcodedShares() {
        var allocation = ProfitSharingCalculator.calculate(
            SettlementCalculationVersion.PROFIT_V3,
            new BigDecimal("1000.00"),
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("200.00"),
            new BigDecimal("0.20"),
            new BigDecimal("0.05"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        );

        assertThat(allocation.distributableAmount()).isEqualByComparingTo("520.00");
        assertThat(allocation.storeOperationAmount()).isEqualByComparingTo("130.00");
        assertThat(allocation.maintenanceFundAmount()).isEqualByComparingTo("32.50");
        assertThat(allocation.investorShareAmount()).isEqualByComparingTo("357.50");
    }
}
