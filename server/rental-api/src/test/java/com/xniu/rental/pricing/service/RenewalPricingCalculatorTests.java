package com.xniu.rental.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xniu.rental.order.model.RentalOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RenewalPricingCalculatorTests {

    private final RenewalPricingCalculator calculator = new RenewalPricingCalculator();

    @Test
    void cappedDailyPricingAppliesThePeriodCapForEachCompletePeriod() {
        var order = dailyOrder(true, "5.00", "7.00");

        assertThat(calculator.quoteDaily(order, 5, false).amount()).isEqualByComparingTo("25.00");
        assertThat(calculator.quoteDaily(order, 30, false).amount()).isEqualByComparingTo("129.00");
        assertThat(calculator.quoteDaily(order, 35, false).amount()).isEqualByComparingTo("154.00");
        assertThat(calculator.quoteDaily(order, 65, false).amount()).isEqualByComparingTo("283.00");
        assertThat(calculator.quoteDaily(order, 5, true).amount()).isEqualByComparingTo("35.00");
    }

    @Test
    void disablingTheCapKeepsPureDailyPricingAcrossCompletePeriods() {
        var order = dailyOrder(false, "5.00", null);

        var quote = calculator.quoteDaily(order, 35, false);

        assertThat(quote.amount()).isEqualByComparingTo("175.00");
        assertThat(quote.capped()).isFalse();
    }

    @Test
    void aPeriodCapNeverRaisesAccruedFeesWhenTheOverdueDailyRateIsLower() {
        var order = dailyOrder(true, "5.00", "2.00");

        assertThat(calculator.quoteDaily(order, 30, true).amount()).isEqualByComparingTo("60.00");
        assertThat(calculator.quoteDaily(order, 35, true).amount()).isEqualByComparingTo("70.00");
    }

    @Test
    void elapsedDaysStartAfterGraceAndRoundAnyStartedDayUp() {
        var order = dailyOrder(true, "5.00", null);
        var dueAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        when(order.expectedReturnAt()).thenReturn(dueAt);
        when(order.renewalGraceHours()).thenReturn(12);

        assertThat(calculator.elapsedBillableDays(order, dueAt.plusHours(12))).isZero();
        assertThat(calculator.elapsedBillableDays(order, dueAt.plusHours(12).plusSeconds(1))).isEqualTo(1);
        assertThat(calculator.elapsedBillableDays(order, dueAt.plusHours(12).plusDays(5))).isEqualTo(5);
    }

    private RentalOrder dailyOrder(boolean capEnabled, String dailyAmount, String overdueDailyAmount) {
        var order = mock(RentalOrder.class);
        when(order.renewalBillingMode()).thenReturn("DAILY_CAPPED");
        when(order.renewalUnit()).thenReturn("MONTH");
        when(order.renewalValue()).thenReturn(1);
        when(order.renewalAmount()).thenReturn(new BigDecimal("129.00"));
        when(order.renewalDailyAmount()).thenReturn(new BigDecimal(dailyAmount));
        when(order.renewalDailyCapEnabled()).thenReturn(capEnabled);
        when(order.overdueDailyAmount()).thenReturn(overdueDailyAmount == null ? null : new BigDecimal(overdueDailyAmount));
        return order;
    }
}
