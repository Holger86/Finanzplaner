package de.finanzplaner.ui;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.Recurrence;
import de.finanzplaner.service.PlanCloner;
import de.finanzplaner.service.SimulationEngine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Proposal for continuing a stored plan at a cut-off date (usually today): the
 * balance estimated by the old plan and the one-time entries that will be dropped.
 * The estimate is only a suggestion; the new plan always uses the actual balances
 * entered by the user.
 */
public record ContinuationProposal(FinancialPlan source, LocalDate cutOff, BigDecimal estimatedBalance,
                                   List<Entry> removedEntries) {
    public ContinuationProposal {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cutOff, "cutOff");
        Objects.requireNonNull(estimatedBalance, "estimatedBalance");
        removedEntries = List.copyOf(removedEntries);
    }

    public static ContinuationProposal of(FinancialPlan plan, LocalDate cutOff) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cutOff, "cutOff");
        if (cutOff.isBefore(plan.startDate())) {
            throw new IllegalArgumentException("Der Plan beginnt erst am " + Formats.date(plan.startDate())
                    + " und kann daher nicht zum " + Formats.date(cutOff) + " fortgeschrieben werden.");
        }
        BigDecimal estimate = SimulationEngine.estimateAt(plan, cutOff).rows().getLast().balance();
        List<Entry> removed = plan.entries().stream()
                .filter(entry -> entry.recurrence() instanceof Recurrence.OneTime oneTime
                        && oneTime.date().isBefore(cutOff))
                .toList();
        return new ContinuationProposal(plan, cutOff, estimate, removed);
    }

    /** Creates the continued plan with the confirmed actual account balances. */
    public FinancialPlan apply(List<Account> actualAccounts) {
        return new PlanCloner().clonePlan(source, cutOff, actualAccounts);
    }
}
