package com.xniu.rental.externalorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevision;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevisionType;
import com.xniu.rental.externalorder.service.ExternalOrderRenewalAmountCalculator;
import com.xniu.rental.settlement.service.BatteryCostCalculator;
import com.xniu.rental.settlement.service.ProfitSharingCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Numeric contracts shared by the one-off manual renewal workflow.
 *
 * <p>The integration layer remains responsible for locking the order, rejecting
 * financially locked periods, and ensuring that an event-scoped manual amount
 * is not copied back to the order's automatic renewal rule.</p>
 */
class ExternalOrderManualRenewalPolicyContractTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Test
    void exactTwentyDayManualPeriodKeepsTheConfirmedGrossAmount() {
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            START.plusDays(20),
            List.of(manualRevision(START, "96.00"))
        );

        assertThat(amount).isEqualByComparingTo("96.00");
    }

    @Test
    void exactThirtyOneDayPeriodUsesOneMonthlyBatteryCostAndOneDailyCost() {
        var cost = BatteryCostCalculator.calculateExactPeriod(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            START,
            START.plusDays(31)
        );

        assertThat(cost).isEqualByComparingTo("206.80");
    }

    @Test
    void exactPeriodPreservesHalfDayPrecisionInsteadOfRoundingToAWholeDay() {
        var cost = BatteryCostCalculator.calculateExactPeriod(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            START,
            START.plusDays(30).plusHours(12)
        );

        assertThat(cost).isEqualByComparingTo("203.40");
    }

    @Test
    void profitSharingDeductsBothFeesAndTheFullExactPeriodBatteryCostBeforeShares() {
        var batteryCost = BatteryCostCalculator.calculateExactPeriod(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            START,
            START.plusDays(31)
        );
        var allocation = ProfitSharingCalculator.calculate(
            new BigDecimal("397.30"),
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            batteryCost,
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        );

        assertThat(allocation.channelFeeAmount()).isEqualByComparingTo("19.87");
        assertThat(allocation.platformFeeAmount()).isEqualByComparingTo("11.92");
        assertThat(allocation.batteryCostAmount()).isEqualByComparingTo("206.80");
        assertThat(allocation.distributableAmount()).isEqualByComparingTo("158.71");
        assertThat(allocation.storeOperationAmount()).isEqualByComparingTo("23.81");
        assertThat(allocation.maintenanceFundAmount()).isEqualByComparingTo("15.87");
        assertThat(allocation.channelReferralAmount()).isEqualByComparingTo("31.74");
        assertThat(allocation.investorShareAmount()).isEqualByComparingTo("87.29");
    }

    @Test
    void oneOffManualAmountIsNotReusedByTheFollowingAutomaticPeriod() {
        var manualAmount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            START.plusDays(20),
            List.of(manualRevision(START, "96.00"))
        );
        var followingAutomaticAmount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START.plusDays(20),
            START.plusDays(50),
            List.of()
        );

        assertThat(manualAmount).isEqualByComparingTo("96.00");
        assertThat(followingAutomaticAmount).isEqualByComparingTo("129.00");
    }

    @Test
    void lowGrossDemonstratesWhyTheServiceMustRejectAnUnfundedBatteryPeriod() {
        var allocation = ProfitSharingCalculator.calculate(
            new BigDecimal("96.00"),
            new BigDecimal("0.05"),
            new BigDecimal("0.03"),
            new BigDecimal("136.00"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            new BigDecimal("0.20"),
            new BigDecimal("0.55")
        );
        var requiredBeforeShares = allocation.channelFeeAmount()
            .add(allocation.platformFeeAmount())
            .add(allocation.batteryCostAmount());

        assertThat(allocation.distributableAmount()).isZero();
        assertThat(requiredBeforeShares).isEqualByComparingTo("143.68");
        assertThat(requiredBeforeShares.subtract(allocation.settlementBaseAmount()))
            .isEqualByComparingTo("47.68");
    }

    private ExternalOrderVerificationRevision manualRevision(LocalDateTime effectiveAt, String amount) {
        return new ExternalOrderVerificationRevision(
            null,
            1L,
            new BigDecimal(amount),
            effectiveAt,
            ExternalOrderVerificationRevisionType.ORDER_EDIT,
            null,
            1L,
            effectiveAt
        );
    }
}
