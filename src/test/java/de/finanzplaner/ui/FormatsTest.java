package de.finanzplaner.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FormatsTest {
    @Test
    void formatsGermanCurrencyWithSign() {
        assertEquals("1.234,56 €", Formats.money(new BigDecimal("1234.56")));
        assertEquals("-0,50 €", Formats.money(new BigDecimal("-0.5")));
        assertEquals("1.000.000,00 €", Formats.money(new BigDecimal("1000000")));
        assertEquals("0,01 €", Formats.money(new BigDecimal("0.005")));
        assertEquals("+12,00 €", Formats.signedMoney(new BigDecimal("12")));
        assertEquals("-1.234,50 €", Formats.signedMoney(new BigDecimal("-1234.5")));
        assertEquals("+0,00 €", Formats.signedMoney(BigDecimal.ZERO));
    }

    @Test
    void parsesGermanAndPlainNotationExactly() {
        assertEquals(new BigDecimal("1234.56"), Formats.parseMoney("1.234,56"));
        assertEquals(new BigDecimal("1234.56"), Formats.parseMoney(" 1.234,56 € "));
        assertEquals(new BigDecimal("-500.00"), Formats.parseMoney("-500"));
        assertEquals(new BigDecimal("12.50"), Formats.parseMoney("12,5"));
        assertEquals(new BigDecimal("12.50"), Formats.parseMoney("12.50"));
        assertEquals(new BigDecimal("1500.00"), Formats.parseMoney("1.500"));
        assertEquals(new BigDecimal("1234567.00"), Formats.parseMoney("1.234.567"));
        assertEquals(new BigDecimal("0.50"), Formats.parseMoney(",5"));
        assertEquals(new BigDecimal("7.00"), Formats.parseMoney("+7"));
        assertEquals(new BigDecimal("1234.56"), Formats.parseMoney(Formats.money(new BigDecimal("1234.56"))));
        assertEquals(new BigDecimal("-1234.56"), Formats.parseMoney(Formats.signedMoney(new BigDecimal("-1234.56"))));
    }

    @Test
    void rejectsInvalidAmounts() {
        for (String text : new String[] {"", " ", "-", "abc", "1,234", "1.234.56", "12,3,4", "1e5", ".", "1,"}) {
            assertThrows(IllegalArgumentException.class, () -> Formats.parseMoney(text), text);
        }
        assertThrows(NullPointerException.class, () -> Formats.parseMoney(null));
    }

    @Test
    void formatsAndParsesDates() {
        assertEquals("05.03.2026", Formats.date(LocalDate.of(2026, 3, 5)));
        assertEquals("", Formats.date(null));
        assertEquals(LocalDate.of(2026, 3, 5), Formats.parseDate("05.03.2026"));
        assertNull(Formats.parseDate(" "));
        assertThrows(IllegalArgumentException.class, () -> Formats.parseDate("31.02.2026"));
        assertThrows(IllegalArgumentException.class, () -> Formats.parseDate("2026-03-05"));
    }
}
