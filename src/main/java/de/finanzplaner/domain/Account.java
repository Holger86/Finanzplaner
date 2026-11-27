package de.finanzplaner.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Account(String name, BigDecimal openingBalance) {
    public Account {
        name = Validation.name(name, "name");
        Objects.requireNonNull(openingBalance, "openingBalance");
    }
}
