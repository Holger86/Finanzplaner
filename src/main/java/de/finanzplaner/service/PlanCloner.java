package de.finanzplaner.service;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.Recurrence;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public final class PlanCloner {
    /**
     * Advances a plan with the same duration as the original. The caller supplies
     * actual account balances including all movements on the cut-off date. The new
     * plan starts on the cut-off: today's one-time entries are retained as settled
     * records and are not posted twice. Older one-time entries are removed.
     * Repeating entries keep their anchor and exclude postings through the cut-off.
     * Explicit recurrence end dates always remain unchanged; open-ended rules
     * remain open-ended. The cut-off can be later
     * than the original end, but not before the original start. For an aggregate
     * estimate of the cut-off balance use {@link SimulationEngine#estimateAt};
     * the caller must distribute it among the new accounts as appropriate.
     */
    public FinancialPlan clonePlan(FinancialPlan plan, LocalDate cutOff, List<Account> accounts) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cutOff, "cutOff");
        long days = ChronoUnit.DAYS.between(plan.startDate(), plan.endDate());
        LocalDate end;
        try {
            end = cutOff.plusDays(days);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("new plan end exceeds the supported date range", exception);
        }
        return clonePlan(plan, cutOff, end, accounts);
    }

    /** As above, with an explicit inclusive end date for the new plan. */
    public FinancialPlan clonePlan(FinancialPlan plan, LocalDate cutOff, LocalDate newEndDate,
                                   List<Account> accounts) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cutOff, "cutOff");
        Objects.requireNonNull(newEndDate, "newEndDate");
        Objects.requireNonNull(accounts, "accounts");
        if (cutOff.isBefore(plan.startDate()) || newEndDate.isBefore(cutOff)) {
            throw new IllegalArgumentException("cutOff must not precede the old start or follow the new end");
        }
        List<Entry> entries = plan.entries().stream()
                .filter(entry -> !(entry.recurrence() instanceof Recurrence.OneTime oneTime)
                        || !oneTime.date().isBefore(cutOff))
                .map(entry -> new Entry(entry.name(), entry.type(), entry.amount(),
                        advance(entry.recurrence(), cutOff)))
                .toList();
        return new FinancialPlan(plan.name(), cutOff, newEndDate,
                plan.overdraftLimit(), accounts, entries);
    }

    private static Recurrence advance(Recurrence recurrence, LocalDate cutOff) {
        if (recurrence instanceof Recurrence.OneTime oneTime) {
            return new Recurrence.OneTime(oneTime.date(), oneTime.settled() || oneTime.date().equals(cutOff));
        }
        if (recurrence instanceof Recurrence.Interval interval) {
            return new Recurrence.Interval(interval.startDate(), interval.endDate(),
                    interval.every(), interval.unit(), later(interval.excludedThrough(), cutOff));
        }
        Recurrence.MonthlyBusinessDay monthly = (Recurrence.MonthlyBusinessDay) recurrence;
        return new Recurrence.MonthlyBusinessDay(monthly.startDate(), monthly.endDate(),
                monthly.type(), later(monthly.excludedThrough(), cutOff));
    }

    private static LocalDate later(LocalDate previous, LocalDate cutOff) {
        return previous != null && previous.isAfter(cutOff) ? previous : cutOff;
    }
}
