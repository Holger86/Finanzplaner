package de.finanzplaner.service;

import static org.junit.jupiter.api.Assertions.*;

import de.finanzplaner.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanClonerTest {
    @Test
    void advancesWithoutRepeatingCutoffAndRetainsIntervalPhase() {
        var start = LocalDate.of(2024, 1, 1);
        var cutoff = start.plusDays(3);
        var end = start.plusDays(10);
        var entries = List.of(
                new Entry("before", EntryType.AUSGABE, BigDecimal.ONE, new Recurrence.OneTime(start)),
                new Entry("cutoff", EntryType.AUSGABE, BigDecimal.ONE, new Recurrence.OneTime(cutoff)),
                new Entry("after", EntryType.EINNAHME, BigDecimal.ONE, new Recurrence.OneTime(cutoff.plusDays(1))),
                new Entry("interval", EntryType.EINNAHME, BigDecimal.ONE, new Recurrence.Interval(start, null, 3, IntervalUnit.TAG)));
        var original = new FinancialPlan("p", start, end, BigDecimal.TEN.negate(), List.of(new Account("old", BigDecimal.ZERO)), entries);
        var accounts = new ArrayList<>(List.of(new Account("new", new BigDecimal("12.34"))));
        var clone = new PlanCloner().clonePlan(original, cutoff, accounts);
        accounts.clear();
        assertEquals(cutoff, clone.startDate());
        assertEquals(cutoff.plusDays(10), clone.endDate());
        assertEquals(BigDecimal.TEN.negate(), clone.overdraftLimit());
        assertEquals("new", clone.accounts().get(0).name());
        assertEquals(List.of("cutoff", "after", "interval"), clone.entries().stream().map(Entry::name).toList());
        assertTrue(((Recurrence.OneTime) clone.entries().get(0).recurrence()).settled());
        assertEquals(start, ((Recurrence.Interval) clone.entries().get(2).recurrence()).startDate());
        var result = SimulationEngine.simulate(clone);
        assertEquals(List.of(cutoff.plusDays(3), cutoff.plusDays(6), cutoff.plusDays(9)),
                result.rows().stream().filter(row -> row.description().equals("interval")).map(SimulationRow::date).toList());
        assertFalse(result.rows().stream().anyMatch(row -> row.description().equals("cutoff")));
        assertEquals(4, original.entries().size());
        assertThrows(IllegalArgumentException.class, () -> new PlanCloner().clonePlan(original, start.minusDays(1), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlanCloner().clonePlan(original, cutoff, cutoff.minusDays(1), List.of(new Account("a", BigDecimal.ZERO))));
        assertThrows(NullPointerException.class, () -> new PlanCloner().clonePlan(original, cutoff, null));
    }

    @Test
    void advancesBeyondOldEndAndPreservesEarlierExplicitRecurrenceEnd() {
        var start = LocalDate.of(2024, 1, 1);
        var end = start.plusDays(5);
        var cutoff = start.plusDays(20);
        var original = new FinancialPlan("p", start, end, BigDecimal.ZERO,
                List.of(new Account("a", BigDecimal.ZERO)), List.of(
                new Entry("series", EntryType.EINNAHME, BigDecimal.ONE, new Recurrence.Interval(start, null, 2, IntervalUnit.TAG)),
                new Entry("ended", EntryType.EINNAHME, BigDecimal.ONE, new Recurrence.MonthlyBusinessDay(start, end, BusinessDay.FIRST))));
        var clone = new PlanCloner().clonePlan(original, cutoff, cutoff.plusDays(8), List.of(new Account("a", BigDecimal.TEN)));
        assertEquals(cutoff.plusDays(8), clone.endDate());
        assertEquals(end, ((Recurrence.MonthlyBusinessDay) clone.entries().get(1).recurrence()).endDate());
        assertEquals(List.of(cutoff.plusDays(2), cutoff.plusDays(4), cutoff.plusDays(6), cutoff.plusDays(8)),
                SimulationEngine.simulate(clone).rows().stream().filter(row -> row.description().equals("series")).map(SimulationRow::date).toList());
    }
}
