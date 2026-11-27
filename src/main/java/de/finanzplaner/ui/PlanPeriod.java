package de.finanzplaner.ui;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.OptionalInt;

/** Conversion between a planning period in months and its inclusive end date. */
public final class PlanPeriod {
    public static final int DEFAULT_MONTHS = 12;

    private PlanPeriod() {
    }

    /** Inclusive end date: 12 months from 01.01.2026 end on 31.12.2026. */
    public static LocalDate end(LocalDate start, int months) {
        Objects.requireNonNull(start, "start");
        if (months < 1) {
            throw new IllegalArgumentException("months must be positive");
        }
        return start.plusMonths(months).minusDays(1);
    }

    /** The number of whole months if the period matches {@link #end} exactly. */
    public static OptionalInt months(LocalDate start, LocalDate end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        long estimate = ChronoUnit.MONTHS.between(start, end.plusDays(1));
        // Month-end clamping (31.01. + 1 Monat = 28.02.) can make the estimate one too small.
        for (long months = estimate; months <= estimate + 1; months++) {
            if (months >= 1 && months <= Integer.MAX_VALUE && end(start, (int) months).equals(end)) {
                return OptionalInt.of((int) months);
            }
        }
        return OptionalInt.empty();
    }
}
