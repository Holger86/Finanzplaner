package de.finanzplaner.domain;

import java.time.LocalDate;
import java.util.Objects;

final class Validation {
    private Validation() {
    }

    static String name(String name, String field) {
        Objects.requireNonNull(name, field);
        if (name.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return name;
    }

    static void dates(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not precede startDate");
        }
    }

    static void recurrenceDates(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "startDate");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not precede startDate");
        }
    }
}
