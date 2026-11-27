package de.finanzplaner.domain;

import java.time.LocalDate;
import java.util.Objects;

public sealed interface Recurrence {
    /** A settled one-time entry is retained for reference but not posted again. */
    record OneTime(LocalDate date, boolean settled) implements Recurrence {
        public OneTime(LocalDate date) {
            this(date, false);
        }

        public OneTime {
            Objects.requireNonNull(date, "date");
        }
    }

    /** excludedThrough is an inclusive posting cut-off, independent of the interval anchor. */
    record Interval(LocalDate startDate, LocalDate endDate, int every, IntervalUnit unit, LocalDate excludedThrough)
            implements Recurrence {
        public Interval(LocalDate startDate, LocalDate endDate, int every, IntervalUnit unit) {
            this(startDate, endDate, every, unit, null);
        }

        public Interval {
            Validation.recurrenceDates(startDate, endDate);
            if (every <= 0) {
                throw new IllegalArgumentException("every must be positive");
            }
            Objects.requireNonNull(unit, "unit");
        }
    }

    /** excludedThrough is an inclusive posting cut-off. */
    record MonthlyBusinessDay(LocalDate startDate, LocalDate endDate, BusinessDay type, LocalDate excludedThrough)
            implements Recurrence {
        public MonthlyBusinessDay(LocalDate startDate, LocalDate endDate, BusinessDay type) {
            this(startDate, endDate, type, null);
        }

        public MonthlyBusinessDay {
            Validation.recurrenceDates(startDate, endDate);
            Objects.requireNonNull(type, "type");
        }
    }
}
