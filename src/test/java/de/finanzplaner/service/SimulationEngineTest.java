package de.finanzplaner.service;

import static org.junit.jupiter.api.Assertions.*;

import de.finanzplaner.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {
    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static Entry entry(String name, EntryType type, String amount, Recurrence recurrence) {
        return new Entry(name, type, new BigDecimal(amount), recurrence);
    }

    private static FinancialPlan plan(LocalDate from, LocalDate to, String limit, List<Account> accounts, Entry... entries) {
        return new FinancialPlan("Plan", from, to, new BigDecimal(limit),
                accounts.isEmpty() ? List.of(new Account("empty", BigDecimal.ZERO)) : accounts, List.of(entries));
    }

    @Test
    void simulatesInclusiveDatesAndStableOrderingWithExactDecimals() {
        var from = d(2024, 1, 1);
        var to = d(2024, 1, 2);
        var plan = plan(from, to, "-2", List.of(new Account("a", new BigDecimal("0.10")), new Account("b", new BigDecimal("0.20"))),
                entry("out", EntryType.AUSGABE, "3.30", new Recurrence.OneTime(from)),
                entry("in", EntryType.EINNAHME, "1.00", new Recurrence.OneTime(from)),
                entry("end", EntryType.EINNAHME, "0.01", new Recurrence.OneTime(to)),
                entry("outside", EntryType.EINNAHME, "50", new Recurrence.OneTime(to.plusDays(1))));
        var result = SimulationEngine.simulate(plan);
        assertEquals(List.of("Anfangsbestand", "out", "in", "end"), result.rows().stream().map(SimulationRow::description).toList());
        assertEquals(0, result.rows().get(0).balance().compareTo(new BigDecimal("0.30")));
        assertTrue(result.rows().get(0).opening());
        assertEquals(0, result.rows().get(1).amount().compareTo(new BigDecimal("-3.30")));
        assertEquals(0, result.rows().get(3).balance().compareTo(new BigDecimal("-1.99")));
        assertEquals(result.rows().get(1), result.lowestBelowLimit().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
    }

    @Test
    void considersOpeningAndUsesStrictLimit() {
        var date = d(2024, 1, 1);
        var result = SimulationEngine.simulate(plan(date, date, "-1", List.of(new Account("a", new BigDecimal("-1")))));
        assertTrue(result.lowestBelowLimit().isEmpty());
        result = SimulationEngine.simulate(plan(date, date, "0", List.of(new Account("a", new BigDecimal("-1")))));
        assertEquals(result.rows().get(0), result.lowestBelowLimit().orElseThrow());
    }

    @Test
    void retainsIntervalPhaseAndMonthEndAnchor() {
        var entries = List.of(
                entry("days", EntryType.EINNAHME, "1", new Recurrence.Interval(d(2024, 1, 1), d(2024, 1, 12), 3, IntervalUnit.TAG)),
                entry("weeks", EntryType.EINNAHME, "1", new Recurrence.Interval(d(2024, 1, 1), d(2024, 2, 1), 2, IntervalUnit.WOCHE)),
                entry("months", EntryType.EINNAHME, "1", new Recurrence.Interval(d(2024, 2, 29), d(2024, 6, 30), 1, IntervalUnit.MONAT)),
                entry("quarter", EntryType.EINNAHME, "1", new Recurrence.Interval(d(2024, 1, 31), d(2024, 10, 31), 1, IntervalUnit.QUARTAL)));
        var result = SimulationEngine.simulate(new FinancialPlan("p", d(2024, 1, 5), d(2024, 10, 31), BigDecimal.ZERO, List.of(new Account("a", BigDecimal.ZERO)), entries));
        assertEquals(List.of(d(2024, 1, 7), d(2024, 1, 10)), dates(result, "days"));
        assertEquals(List.of(d(2024, 1, 15), d(2024, 1, 29)), dates(result, "weeks"));
        assertEquals(List.of(d(2024, 2, 29), d(2024, 3, 31), d(2024, 4, 30), d(2024, 5, 31), d(2024, 6, 30)), dates(result, "months"));
        assertEquals(List.of(d(2024, 1, 31), d(2024, 4, 30), d(2024, 7, 31), d(2024, 10, 31)), dates(result, "quarter"));
    }

    @Test
    void yearlyIntervalKeepsAnchorAndLeapDay() {
        var entries = List.of(
                entry("yearly", EntryType.AUSGABE, "1", new Recurrence.Interval(d(2023, 3, 15), null, 1, IntervalUnit.JAHR)),
                entry("leap", EntryType.AUSGABE, "1", new Recurrence.Interval(d(2024, 2, 29), null, 1, IntervalUnit.JAHR)),
                entry("biennial", EntryType.AUSGABE, "1", new Recurrence.Interval(d(2024, 7, 1), null, 2, IntervalUnit.JAHR)),
                entry("february", EntryType.AUSGABE, "1", new Recurrence.Interval(d(2023, 2, 28), null, 1, IntervalUnit.JAHR)),
                entry("yearEnd", EntryType.AUSGABE, "1", new Recurrence.Interval(d(2024, 12, 31), null, 1, IntervalUnit.JAHR)));
        var result = SimulationEngine.simulate(new FinancialPlan("p", d(2024, 1, 1), d(2028, 12, 31), BigDecimal.ZERO,
                List.of(new Account("a", BigDecimal.ZERO)), entries));
        assertEquals(List.of(d(2024, 3, 15), d(2025, 3, 15), d(2026, 3, 15), d(2027, 3, 15), d(2028, 3, 15)), dates(result, "yearly"));
        assertEquals(List.of(d(2024, 2, 29), d(2025, 2, 28), d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 29)), dates(result, "leap"));
        assertEquals(List.of(d(2024, 7, 1), d(2026, 7, 1), d(2028, 7, 1)), dates(result, "biennial"));
        assertEquals(List.of(d(2024, 2, 28), d(2025, 2, 28), d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 28)), dates(result, "february"));
        assertEquals(List.of(d(2024, 12, 31), d(2025, 12, 31), d(2026, 12, 31), d(2027, 12, 31), d(2028, 12, 31)), dates(result, "yearEnd"));
    }

    @Test
    void nonEndOfMonthAnchorRecoversAfterShortMonth() {
        var result = SimulationEngine.simulate(plan(d(2024, 1, 1), d(2024, 4, 30), "0", List.of(),
                entry("monthly", EntryType.EINNAHME, "1", new Recurrence.Interval(d(2024, 1, 30), d(2024, 4, 30), 1, IntervalUnit.MONAT))));
        assertEquals(List.of(d(2024, 1, 30), d(2024, 2, 29), d(2024, 3, 30), d(2024, 4, 30)), dates(result, "monthly"));
    }

    @Test
    void businessDaysSkipWeekendsAndRespectPartialMonths() {
        var result = SimulationEngine.simulate(plan(d(2024, 6, 2), d(2024, 9, 29), "0", List.of(),
                entry("first", EntryType.EINNAHME, "1", new Recurrence.MonthlyBusinessDay(d(2024, 6, 4), d(2024, 9, 30), BusinessDay.FIRST)),
                entry("last", EntryType.EINNAHME, "1", new Recurrence.MonthlyBusinessDay(d(2024, 6, 1), d(2024, 9, 30), BusinessDay.LAST))));
        assertEquals(List.of(d(2024, 7, 1), d(2024, 8, 1), d(2024, 9, 2)), dates(result, "first"));
        assertEquals(List.of(d(2024, 6, 28), d(2024, 7, 31), d(2024, 8, 30)), dates(result, "last"));
    }

    @Test
    void jumpsOverHugeHistoryAndHandlesDateLimits() {
        var result = assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () ->
                SimulationEngine.simulate(plan(LocalDate.MAX.minusDays(2), LocalDate.MAX, "0", List.of(),
                        entry("daily", EntryType.EINNAHME, "1", new Recurrence.Interval(LocalDate.MIN, LocalDate.MAX, 1, IntervalUnit.TAG)),
                        entry("monthly", EntryType.EINNAHME, "1", new Recurrence.Interval(LocalDate.of(-999999999, Month.DECEMBER, 31), LocalDate.MAX, 1, IntervalUnit.MONAT)))));
        assertEquals(3, dates(result, "daily").size());
        assertEquals(List.of(LocalDate.MAX), dates(result, "monthly"));
    }

    @Test
    void estimatesThroughCutoffBeyondPlanEndWithoutExtendingExplicitRules() {
        var start = d(2024, 1, 1);
        var end = start.plusDays(2);
        var plan = plan(start, end, "-10", List.of(new Account("a", new BigDecimal("10"))),
                entry("open", EntryType.EINNAHME, "1", new Recurrence.Interval(start, null, 1, IntervalUnit.TAG)),
                entry("bounded", EntryType.AUSGABE, "2", new Recurrence.Interval(start, end, 1, IntervalUnit.TAG)),
                entry("business", EntryType.EINNAHME, "1", new Recurrence.MonthlyBusinessDay(start, null, BusinessDay.FIRST)),
                entry("once", EntryType.EINNAHME, "5", new Recurrence.OneTime(start.plusDays(4))));
        var result = SimulationEngine.estimateAt(plan, start.plusDays(4));
        assertEquals(5, dates(result, "open").size());
        assertEquals(3, dates(result, "bounded").size());
        assertEquals(List.of(start), dates(result, "business"));
        assertEquals(List.of(start.plusDays(4)), dates(result, "once"));
        assertEquals(0, result.rows().getLast().balance().compareTo(new BigDecimal("15")));
        assertThrows(IllegalArgumentException.class, () -> SimulationEngine.estimateAt(plan, start.minusDays(1)));
    }

    @Test
    void honorsSettledPaymentsAndInclusiveExclusionForBothRecurringTypes() {
        var start = d(2024, 6, 1);
        var end = d(2024, 7, 31);
        var result = SimulationEngine.simulate(plan(start, end, "0", List.of(),
                entry("settled", EntryType.EINNAHME, "1", new Recurrence.OneTime(start, true)),
                entry("interval", EntryType.EINNAHME, "1",
                        new Recurrence.Interval(start, null, 2, IntervalUnit.WOCHE, d(2024, 6, 15))),
                entry("first", EntryType.EINNAHME, "1",
                        new Recurrence.MonthlyBusinessDay(start, null, BusinessDay.FIRST, d(2024, 6, 3))),
                entry("excluded", EntryType.EINNAHME, "1",
                        new Recurrence.Interval(start, null, 1, IntervalUnit.TAG, LocalDate.MAX))));
        assertTrue(dates(result, "settled").isEmpty());
        assertTrue(dates(result, "excluded").isEmpty());
        assertEquals(List.of(d(2024, 6, 29), d(2024, 7, 13), d(2024, 7, 27)), dates(result, "interval"));
        assertEquals(List.of(d(2024, 7, 1)), dates(result, "first"));
    }

    private static List<LocalDate> dates(SimulationResult result, String name) {
        return result.rows().stream().filter(row -> row.description().equals(name)).map(SimulationRow::date).toList();
    }
}
