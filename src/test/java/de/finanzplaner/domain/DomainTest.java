package de.finanzplaner.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTest {
    private static final LocalDate DATE = LocalDate.of(2024, 1, 1);

    @Test
    void checksAllRequiredFieldsAndConstraints() {
        assertThrows(NullPointerException.class, () -> new Account(null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Account("  ", BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new Account("a", null));
        assertThrows(IllegalArgumentException.class, () -> new Entry("", EntryType.EINNAHME, BigDecimal.ZERO, new Recurrence.OneTime(DATE)));
        assertThrows(NullPointerException.class, () -> new Entry("e", null, BigDecimal.ZERO, new Recurrence.OneTime(DATE)));
        assertThrows(NullPointerException.class, () -> new Entry("e", EntryType.EINNAHME, null, new Recurrence.OneTime(DATE)));
        assertThrows(NullPointerException.class, () -> new Entry("e", EntryType.EINNAHME, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> new Entry("e", EntryType.AUSGABE, BigDecimal.ONE.negate(), new Recurrence.OneTime(DATE)));
        assertThrows(IllegalArgumentException.class, () -> new Entry("e", EntryType.AUSGABE, BigDecimal.ZERO, new Recurrence.OneTime(DATE)));
        assertThrows(NullPointerException.class, () -> new Recurrence.OneTime(null));
        assertThrows(NullPointerException.class, () -> new Recurrence.Interval(null, DATE, 1, IntervalUnit.TAG));
        assertNull(new Recurrence.Interval(DATE, null, 1, IntervalUnit.TAG).endDate());
        assertNull(new Recurrence.MonthlyBusinessDay(DATE, null, BusinessDay.FIRST).endDate());
        assertThrows(IllegalArgumentException.class, () -> new Recurrence.Interval(DATE.plusDays(1), DATE, 1, IntervalUnit.TAG));
        assertThrows(IllegalArgumentException.class, () -> new Recurrence.Interval(DATE, DATE, 0, IntervalUnit.TAG));
        assertThrows(NullPointerException.class, () -> new Recurrence.Interval(DATE, DATE, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new Recurrence.MonthlyBusinessDay(DATE.plusDays(1), DATE, BusinessDay.FIRST));
        assertThrows(NullPointerException.class, () -> new Recurrence.MonthlyBusinessDay(DATE, DATE, null));
        assertThrows(IllegalArgumentException.class, () -> new FinancialPlan("p", DATE.plusDays(1), DATE, BigDecimal.ZERO, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new FinancialPlan("p", DATE, DATE, BigDecimal.ONE, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new FinancialPlan("p", DATE, DATE, BigDecimal.ZERO, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new FinancialPlan("p", DATE, DATE, null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new FinancialPlan("p", DATE, DATE, BigDecimal.ZERO, null, List.of()));
        assertThrows(NullPointerException.class, () -> new FinancialPlan("p", DATE, DATE, BigDecimal.ZERO, List.of(new Account("a", BigDecimal.ZERO)), null));
        assertThrows(NullPointerException.class, () -> new FinancialPlan("p", DATE, DATE, BigDecimal.ZERO, java.util.Arrays.asList((Account) null), List.of()));
    }

    @Test
    void makesDefensiveCopies() {
        var accounts = new ArrayList<>(List.of(new Account("a", BigDecimal.ONE)));
        var entries = new ArrayList<Entry>();
        var plan = new FinancialPlan("p", DATE, DATE, BigDecimal.ZERO, accounts, entries);
        accounts.clear();
        entries.add(new Entry("e", EntryType.EINNAHME, BigDecimal.ONE, new Recurrence.OneTime(DATE)));
        assertEquals(1, plan.accounts().size());
        assertTrue(plan.entries().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plan.accounts().clear());
    }
}
