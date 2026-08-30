package com.xniu.rental.externalorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevision;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevisionType;
import com.xniu.rental.externalorder.service.ExternalOrderRenewalAmountCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalOrderRenewalAmountCalculatorTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime END = START.plusDays(30);

    @Test
    void noManualEditUsesSystemRenewalAmount() {
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"), START, END, List.of(initial("96.00"))
        );

        assertThat(amount).isEqualByComparingTo("129.00");
    }

    @Test
    void manualEditSplitsPeriodByExactSeconds() {
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            END,
            List.of(initial("129.00"), revision(START.plusDays(15), "96.00"))
        );

        // 15 days at 129 + 15 days at 96, rounded once at the end.
        assertThat(amount).isEqualByComparingTo("112.50");
    }

    @Test
    void manualEditHonorsSubSecondDatabaseTimestamp() {
        var periodEnd = START.plusSeconds(10);
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            periodEnd,
            List.of(revision(START.plusNanos(3_500_000_000L), "96.00"))
        );

        // 3.5 seconds at 129 + 6.5 seconds at 96.
        assertThat(amount).isEqualByComparingTo("107.55");
    }

    @Test
    void laterEditSupersedesEarlierEditAndBoundaryEditsDoNotLeak() {
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            END,
            List.of(
                revision(START.minusSeconds(1), "120.00"),
                revision(START.plusDays(10), "96.00"),
                revision(END, "80.00")
            )
        );

        // 10 days at 120 + 20 days at 96.
        assertThat(amount).isEqualByComparingTo("104.00");
    }

    @Test
    void editBeforePeriodUsesTheManualVerificationAmountForTheWholePeriod() {
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            END,
            List.of(initial("88.00"), revision(START.minusSeconds(1), "96.00"))
        );

        assertThat(amount).isEqualByComparingTo("96.00");
    }

    @Test
    void veryLongPeriodsDoNotOverflowDurationNanoseconds() {
        var amount = ExternalOrderRenewalAmountCalculator.calculate(
            new BigDecimal("129.00"),
            START,
            START.plusYears(400),
            List.of(revision(START.plusYears(200), "96.00"))
        );

        assertThat(amount).isBetween(new BigDecimal("112.49"), new BigDecimal("112.51"));
    }

    private ExternalOrderVerificationRevision initial(String amount) {
        return new ExternalOrderVerificationRevision(
            1L, 1L, new BigDecimal(amount), START.minusDays(30),
            ExternalOrderVerificationRevisionType.INITIAL, 1L, null, START.minusDays(30)
        );
    }

    private ExternalOrderVerificationRevision revision(LocalDateTime effectiveAt, String amount) {
        return new ExternalOrderVerificationRevision(
            null, 1L, new BigDecimal(amount), effectiveAt,
            ExternalOrderVerificationRevisionType.ORDER_EDIT, null, 1L, effectiveAt
        );
    }
}
