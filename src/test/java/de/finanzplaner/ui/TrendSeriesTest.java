package de.finanzplaner.ui;

import static org.junit.jupiter.api.Assertions.*;

import de.finanzplaner.service.SimulationResult;
import de.finanzplaner.service.SimulationRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrendSeriesTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void measuresPositionsInMonths() {
        assertEquals(0.0, TrendSeries.position(START, START));
        assertEquals(1.0, TrendSeries.position(START, LocalDate.of(2026, 2, 1)));
        assertEquals(1.5, TrendSeries.position(START, LocalDate.of(2026, 2, 15)));
        assertEquals(-1.0, TrendSeries.position(START, LocalDate.of(2025, 12, 1)));
        assertEquals(12.0, TrendSeries.endPosition(START, LocalDate.of(2026, 12, 31)));
        assertEquals(1 + 15.0 / 28, TrendSeries.endPosition(START, LocalDate.of(2026, 2, 15)));
        assertEquals(2.0, TrendSeries.endPosition(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29)));
        assertEquals(14.0 / 31, TrendSeries.position(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15)));
    }

    @Test
    void buildsStepCurveFromEndOfDayBalances() {
        var result = new SimulationResult(List.of(
                row(START, "Anfangsbestand", "100", "100", true),
                row(LocalDate.of(2026, 2, 1), "Gehalt", "50", "150", false),
                row(LocalDate.of(2026, 2, 1), "Miete", "-30", "120", false),
                row(LocalDate.of(2026, 3, 1), "Hin", "10", "130", false),
                row(LocalDate.of(2026, 3, 1), "Zurück", "-10", "120", false)), Optional.empty());

        var points = TrendSeries.steps(result, START, LocalDate.of(2026, 3, 31));

        assertEquals(List.of(
                new TrendSeries.Point(0.0, new BigDecimal("100")),
                new TrendSeries.Point(1.0, new BigDecimal("100")),
                new TrendSeries.Point(1.0, new BigDecimal("120")),
                new TrendSeries.Point(3.0, new BigDecimal("120"))), points);
    }

    @Test
    void choosesReadableTicksAndLabels() {
        assertEquals(1, TrendSeries.tickUnit(12));
        assertEquals(2, TrendSeries.tickUnit(24));
        assertEquals(6, TrendSeries.tickUnit(60));
        assertEquals(120, TrendSeries.tickUnit(1200));
        assertEquals("01/2026", TrendSeries.label(START, 0));
        assertEquals("03/2027", TrendSeries.label(START, 14));
        assertEquals("15.02.2026", TrendSeries.label(START, 1.5));
    }

    @Test
    void handlesMaximumEndDate() {
        LocalDate start = LocalDate.MAX.minusDays(2);
        var result = new SimulationResult(List.of(
                row(start, "Anfangsbestand", "100", "100", true),
                row(LocalDate.MAX, "Gehalt", "50", "150", false)), Optional.empty());

        assertEquals(1.0, TrendSeries.endPosition(start, LocalDate.MAX));
        assertEquals("01/+1000000000", TrendSeries.label(start, TrendSeries.endPosition(start, LocalDate.MAX)));
        assertEquals("12/+999999999", TrendSeries.label(start, 0));
        assertEquals("31.12.+999999999", TrendSeries.label(start, TrendSeries.position(start, LocalDate.MAX)));
        assertEquals(List.of(
                new TrendSeries.Point(28.0 / 31, new BigDecimal("100")),
                new TrendSeries.Point(30.0 / 31, new BigDecimal("100")),
                new TrendSeries.Point(30.0 / 31, new BigDecimal("150")),
                new TrendSeries.Point(1.0, new BigDecimal("150"))),
                TrendSeries.steps(result, start, LocalDate.MAX));
    }

    private static SimulationRow row(LocalDate date, String text, String amount, String balance, boolean opening) {
        return new SimulationRow(date, text, new BigDecimal(amount), new BigDecimal(balance), opening);
    }
}
