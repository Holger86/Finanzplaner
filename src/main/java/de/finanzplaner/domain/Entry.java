package de.finanzplaner.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Entry(String name, EntryType type, BigDecimal amount, Recurrence recurrence) {
    public Entry {
        name = Validation.name(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(recurrence, "recurrence");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
