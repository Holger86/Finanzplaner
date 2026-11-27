package de.finanzplaner.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Dark red below the limit, red at the limit, yellow halfway, and green at or above maximum.
 * If maximum is not above limit, the scale is red at the limit and green above it.
 */
public final class ColorScale {
    /**
     * Upper end of the scale for a simulation: the highest balance, but at least
     * one euro above the limit so that the scale never degenerates.
     */
    public static BigDecimal maximum(SimulationResult result, BigDecimal limit) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(limit, "limit");
        BigDecimal maximum = limit;
        for (SimulationRow row : result.rows()) {
            if (row.balance().compareTo(maximum) > 0) {
                maximum = row.balance();
            }
        }
        return maximum.compareTo(limit) > 0 ? maximum : limit.add(BigDecimal.ONE);
    }

    public static RgbColor color(BigDecimal value, BigDecimal limit, BigDecimal maximum) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(maximum, "maximum");
        if (value.compareTo(limit) < 0) {
            return new RgbColor(128, 0, 0);
        }
        if (value.compareTo(limit) == 0) {
            return new RgbColor(255, 0, 0);
        }
        if (maximum.compareTo(limit) <= 0 || value.compareTo(maximum) >= 0) {
            return new RgbColor(0, 255, 0);
        }
        BigDecimal range = maximum.subtract(limit);
        BigDecimal position = value.subtract(limit).multiply(BigDecimal.valueOf(2));
        if (position.compareTo(range) <= 0) {
            return new RgbColor(255, channel(position, range), 0);
        }
        return new RgbColor(channel(range.multiply(BigDecimal.valueOf(2)).subtract(position), range), 255, 0);
    }

    private static int channel(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(BigDecimal.valueOf(255)).divide(denominator, 0, RoundingMode.HALF_UP).intValueExact();
    }
}
