package de.finanzplaner.ui;

import static org.junit.jupiter.api.Assertions.*;

import de.finanzplaner.domain.*;
import de.finanzplaner.service.RgbColor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class UiSupportTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 31);

    @Test
    void describesRecurrences() {
        assertEquals("einmalig am 15.03.2026", RecurrenceText.describe(new Recurrence.OneTime(LocalDate.of(2026, 3, 15))));
        assertEquals("einmalig am 15.03.2026 (bereits gebucht)",
                RecurrenceText.describe(new Recurrence.OneTime(LocalDate.of(2026, 3, 15), true)));
        assertEquals("monatlich ab 31.01.2026",
                RecurrenceText.describe(new Recurrence.Interval(START, null, 1, IntervalUnit.MONAT)));
        assertEquals("alle 2 Wochen ab 31.01.2026 bis 30.06.2026",
                RecurrenceText.describe(new Recurrence.Interval(START, LocalDate.of(2026, 6, 30), 2, IntervalUnit.WOCHE)));
        assertEquals("vierteljährlich ab 31.01.2026 (erfasst bis 01.03.2026)",
                RecurrenceText.describe(new Recurrence.Interval(START, null, 1, IntervalUnit.QUARTAL, LocalDate.of(2026, 3, 1))));
        assertEquals("letzter Werktag im Monat ab 31.01.2026",
                RecurrenceText.describe(new Recurrence.MonthlyBusinessDay(START, null, BusinessDay.LAST)));
        assertEquals("erster Werktag im Monat ab 31.01.2026",
                RecurrenceText.describe(new Recurrence.MonthlyBusinessDay(START, null, BusinessDay.FIRST)));
        assertEquals("Tage", RecurrenceText.unit(IntervalUnit.TAG, 3));
    }

    @Test
    void convertsPlanPeriods() {
        assertEquals(LocalDate.of(2026, 12, 31), PlanPeriod.end(LocalDate.of(2026, 1, 1), 12));
        assertEquals(OptionalInt.of(12), PlanPeriod.months(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertEquals(OptionalInt.of(1), PlanPeriod.months(START, PlanPeriod.end(START, 1)));
        assertEquals(OptionalInt.empty(), PlanPeriod.months(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 30)));
        assertEquals(OptionalInt.empty(), PlanPeriod.months(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> PlanPeriod.end(START, 0));
    }

    @Test
    void choosesReadableHeatmapText() {
        assertEquals("-fx-background-color: #800000; -fx-text-fill: #FFFFFF;", HeatmapStyle.css(new RgbColor(128, 0, 0)));
        assertEquals("#000000", HeatmapStyle.textColor(new RgbColor(255, 255, 0)));
        assertEquals("#000000", HeatmapStyle.textColor(new RgbColor(0, 255, 0)));
    }

    @Test
    void proposesContinuationWithEstimateAndRemovedEntries() {
        var start = LocalDate.of(2026, 1, 1);
        var today = LocalDate.of(2026, 3, 10);
        var past = new Entry("Reparatur", EntryType.AUSGABE, new BigDecimal("200"), new Recurrence.OneTime(LocalDate.of(2026, 2, 1)));
        var future = new Entry("Urlaub", EntryType.AUSGABE, new BigDecimal("800"), new Recurrence.OneTime(LocalDate.of(2026, 7, 1)));
        var todayEntry = new Entry("Heute", EntryType.AUSGABE, new BigDecimal("5"), new Recurrence.OneTime(today));
        var salary = new Entry("Gehalt", EntryType.EINNAHME, new BigDecimal("1000"),
                new Recurrence.MonthlyBusinessDay(start, null, BusinessDay.FIRST));
        var plan = new FinancialPlan("Plan", start, PlanPeriod.end(start, 12), new BigDecimal("-100"),
                List.of(new Account("Giro", new BigDecimal("50")), new Account("Bar", new BigDecimal("10"))),
                List.of(past, future, todayEntry, salary));

        var proposal = ContinuationProposal.of(plan, today);
        // 60 + 3 * 1000 - 200 - 5
        assertEquals(new BigDecimal("2855"), proposal.estimatedBalance());
        assertEquals(List.of(past), proposal.removedEntries());

        var continued = proposal.apply(List.of(new Account("Giro", new BigDecimal("2700.00"))));
        assertEquals(today, continued.startDate());
        assertEquals(today.plusDays(364), continued.endDate());
        assertEquals(new BigDecimal("-100"), continued.overdraftLimit());
        assertEquals(List.of("Urlaub", "Heute", "Gehalt"), continued.entries().stream().map(Entry::name).toList());
        assertEquals(new Recurrence.OneTime(today, true), continued.entries().get(1).recurrence());

        assertThrows(IllegalArgumentException.class, () -> ContinuationProposal.of(plan, start.minusDays(1)));
    }
}
