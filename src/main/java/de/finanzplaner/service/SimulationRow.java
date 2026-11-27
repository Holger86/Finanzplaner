package de.finanzplaner.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record SimulationRow(LocalDate date, String description, BigDecimal amount,
                            BigDecimal balance, boolean opening) {
    public SimulationRow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(balance, "balance");
    }
}
