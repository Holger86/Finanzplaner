package de.finanzplaner.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SimulationResult(List<SimulationRow> rows, Optional<SimulationRow> lowestBelowLimit) {
    public SimulationResult {
        rows = List.copyOf(rows);
        Objects.requireNonNull(lowestBelowLimit, "lowestBelowLimit");
    }
}
