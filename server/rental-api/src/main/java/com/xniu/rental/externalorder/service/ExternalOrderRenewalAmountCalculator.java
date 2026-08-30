package com.xniu.rental.externalorder.service;

import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevision;
import com.xniu.rental.externalorder.model.ExternalOrderVerificationRevisionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates a renewal period from a system amount and effective-dated manual
 * verification revisions.  Amounts are split by elapsed seconds, then rounded
 * once at the end to avoid a cent of drift between segments.
 */
public final class ExternalOrderRenewalAmountCalculator {

    private static final int CALCULATION_SCALE = 12;

    private ExternalOrderRenewalAmountCalculator() {
    }

    public static BigDecimal calculate(
        BigDecimal systemRenewalAmount,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        List<ExternalOrderVerificationRevision> revisions
    ) {
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            return money(systemRenewalAmount);
        }
        var totalNanos = durationNanos(Duration.between(periodStart, periodEnd));
        if (totalNanos.signum() <= 0) {
            return money(systemRenewalAmount);
        }

        var current = money(systemRenewalAmount).setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
        var cursor = periodStart;
        var total = BigDecimal.ZERO.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
        var ordered = revisions == null ? List.<ExternalOrderVerificationRevision>of() : revisions.stream()
            .filter(revision -> revision != null
                && revision.effectiveAt() != null
                && revision.verificationAmount() != null
                && revision.revisionType() != ExternalOrderVerificationRevisionType.INITIAL)
            .sorted(Comparator.comparing(ExternalOrderVerificationRevision::effectiveAt)
                .thenComparing(revision -> revision.id() == null ? 0L : revision.id()))
            .toList();

        for (var revision : ordered) {
            var effectiveAt = revision.effectiveAt();
            if (!effectiveAt.isAfter(periodStart)) {
                current = money(revision.verificationAmount()).setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
                continue;
            }
            if (!effectiveAt.isBefore(periodEnd)) {
                break;
            }
            total = total.add(weighted(current, durationNanos(Duration.between(cursor, effectiveAt)), totalNanos));
            cursor = effectiveAt;
            current = money(revision.verificationAmount()).setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
        }
        total = total.add(weighted(current, durationNanos(Duration.between(cursor, periodEnd)), totalNanos));
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal weighted(BigDecimal amount, BigDecimal nanos, BigDecimal totalNanos) {
        if (nanos.signum() <= 0) {
            return BigDecimal.ZERO.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(nanos)
            .divide(totalNanos, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal durationNanos(Duration duration) {
        return BigDecimal.valueOf(duration.getSeconds())
            .multiply(BigDecimal.valueOf(1_000_000_000L))
            .add(BigDecimal.valueOf(duration.getNano()));
    }

    private static BigDecimal money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
