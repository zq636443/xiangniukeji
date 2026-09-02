package com.xniu.rental.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xniu.rental.product.model.LeaseUnit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BatteryCostCalculatorTests {

    @Test
    void monthlyRentalShouldUseThirtyDailyCosts() {
        var cost = BatteryCostCalculator.calculate(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            LeaseUnit.MONTH,
            1,
            1
        );

        assertThat(cost).isEqualByComparingTo("204.00");
    }

    @Test
    void partialMonthShouldUseDailyCost() {
        var cost = BatteryCostCalculator.calculate(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            LeaseUnit.DAY,
            7,
            1
        );

        assertThat(cost).isEqualByComparingTo("47.60");
    }

    @Test
    void thirtyDailyRentalDaysShouldNotUseMonthlyCap() {
        var cost = BatteryCostCalculator.calculate(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            LeaseUnit.DAY,
            30,
            1
        );

        assertThat(cost).isEqualByComparingTo("204.00");
    }

    @Test
    void thirtyOneDaysShouldUseDailyCostForEveryDay() {
        var cost = BatteryCostCalculator.calculate(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            LeaseUnit.DAY,
            31,
            1
        );

        assertThat(cost).isEqualByComparingTo("210.80");
    }

    @Test
    void exactPeriodShouldUseDailyCostForAllElapsedSeconds() {
        var start = LocalDateTime.of(2026, 8, 1, 10, 0);
        var cost = BatteryCostCalculator.calculateExactPeriod(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            start,
            start.plusDays(31)
        );

        assertThat(cost).isEqualByComparingTo("210.80");
    }

    @Test
    void exactPeriodShouldRetainFractionalDayCostUntilFinalRounding() {
        var start = LocalDateTime.of(2026, 8, 1, 10, 0);
        var cost = BatteryCostCalculator.calculateExactPeriod(
            new BigDecimal("6.80"),
            new BigDecimal("200.00"),
            start,
            start.plusDays(20).plusHours(12)
        );

        assertThat(cost).isEqualByComparingTo("139.40");
    }

    @Test
    void monthlyAmountMayBeNullBecauseItIsNotPartOfBatteryCost() {
        var fallbackCost = BatteryCostCalculator.calculate(
            new BigDecimal("6.80"),
            null,
            LeaseUnit.MONTH,
            1,
            1
        );
        var exactCost = BatteryCostCalculator.calculateExactPeriod(
            new BigDecimal("6.80"),
            null,
            LocalDateTime.of(2026, 8, 1, 10, 0),
            LocalDateTime.of(2026, 8, 3, 22, 0)
        );

        assertThat(fallbackCost).isEqualByComparingTo("204.00");
        assertThat(exactCost).isEqualByComparingTo("17.00");
    }

    @Test
    void multiPeriodCostShouldBeProratedPerPaidBill() {
        var cost = BatteryCostCalculator.prorate(
            new BigDecimal("600.00"),
            new BigDecimal("333.00"),
            new BigDecimal("999.00")
        );

        assertThat(cost).isEqualByComparingTo("200.00");
    }

    @Test
    void proratingShouldUseTheGrossFrozenRentalAsDenominator() {
        var cost = BatteryCostCalculator.prorate(
            new BigDecimal("6.60"),
            new BigDecimal("96.00"),
            new BigDecimal("129.00")
        );

        assertThat(cost).isEqualByComparingTo("4.91");
    }
}
