package de.finanzplaner.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ColorScaleTest {
    private static BigDecimal b(String value) {
        return new BigDecimal(value);
    }

    @Test
    void interpolatesAndClampsWithExactArithmetic() {
        assertEquals("#800000", ColorScale.color(b("-11"), b("-10"), b("10")).hex());
        assertEquals("#FF0000", ColorScale.color(b("-10"), b("-10"), b("10")).hex());
        assertEquals("#FF8000", ColorScale.color(b("-5"), b("-10"), b("10")).hex());
        assertEquals("#FFFF00", ColorScale.color(b("0"), b("-10"), b("10")).hex());
        assertEquals("#80FF00", ColorScale.color(b("5"), b("-10"), b("10")).hex());
        assertEquals("#00FF00", ColorScale.color(b("100"), b("-10"), b("10")).hex());
        assertEquals("#FFFF00", ColorScale.color(b("0.00000000000000000001"), b("0"), b("0.00000000000000000002")).hex());
    }

    @Test
    void handlesDegenerateScaleAndValidatesInput() {
        assertEquals("#FF0000", ColorScale.color(b("0"), b("0"), b("0")).hex());
        assertEquals("#00FF00", ColorScale.color(b("1"), b("0"), b("-1")).hex());
        assertThrows(NullPointerException.class, () -> ColorScale.color(null, b("0"), b("1")));
        assertThrows(NullPointerException.class, () -> ColorScale.color(b("0"), null, b("1")));
        assertThrows(NullPointerException.class, () -> ColorScale.color(b("0"), b("0"), null));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(0, 256, 0));
    }
}
