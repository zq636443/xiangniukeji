package com.xniu.rental.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
