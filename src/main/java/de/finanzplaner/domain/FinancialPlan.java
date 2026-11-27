package de.finanzplaner.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record FinancialPlan(String name, LocalDate startDate, LocalDate endDate,
                            BigDecimal overdraftLimit, List<Account> accounts, List<Entry> entries) {
    public FinancialPlan {
        name = Validation.name(name, "name");
        Validation.dates(startDate, endDate);
        Objects.requireNonNull(overdraftLimit, "overdraftLimit");
        if (overdraftLimit.signum() > 0) {
            throw new IllegalArgumentException("overdraftLimit must not be positive");
        }
        accounts = List.copyOf(accounts);
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("at least one account is required");
        }
        entries = List.copyOf(entries);
    }
}
