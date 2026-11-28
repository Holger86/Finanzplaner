package de.finanzplaner.ui;

import de.finanzplaner.domain.BusinessDay;
import de.finanzplaner.domain.IntervalUnit;
import de.finanzplaner.domain.Recurrence;
import java.time.LocalDate;
import java.util.Objects;

/** Human-readable German descriptions of recurrence rules. */
public final class RecurrenceText {
    private RecurrenceText() {
    }

    public static String describe(Recurrence recurrence) {
        Objects.requireNonNull(recurrence, "recurrence");
        return switch (recurrence) {
            case Recurrence.OneTime oneTime -> "einmalig am " + Formats.date(oneTime.date())
                    + (oneTime.settled() ? " (bereits gebucht)" : "");
            case Recurrence.Interval interval -> interval(interval.every(), interval.unit())
                    + range(interval.startDate(), interval.endDate(), interval.excludedThrough());
            case Recurrence.MonthlyBusinessDay monthly -> (monthly.type() == BusinessDay.FIRST
                    ? "erster Werktag im Monat" : "letzter Werktag im Monat")
                    + range(monthly.startDate(), monthly.endDate(), monthly.excludedThrough());
        };
    }

    public static String unit(IntervalUnit unit, int every) {
        boolean one = every == 1;
        return switch (unit) {
            case TAG -> one ? "Tag" : "Tage";
            case WOCHE -> one ? "Woche" : "Wochen";
            case MONAT -> one ? "Monat" : "Monate";
            case QUARTAL -> one ? "Quartal" : "Quartale";
            case JAHR -> one ? "Jahr" : "Jahre";
        };
    }

    private static String interval(int every, IntervalUnit unit) {
        if (every == 1) {
            return switch (unit) {
                case TAG -> "täglich";
                case WOCHE -> "wöchentlich";
                case MONAT -> "monatlich";
                case QUARTAL -> "vierteljährlich";
                case JAHR -> "jährlich";
            };
        }
        return "alle " + every + " " + unit(unit, every);
    }

    private static String range(LocalDate start, LocalDate end, LocalDate excludedThrough) {
        StringBuilder text = new StringBuilder(" ab ").append(Formats.date(start));
        if (end != null) {
            text.append(" bis ").append(Formats.date(end));
        }
        if (excludedThrough != null) {
            text.append(" (erfasst bis ").append(Formats.date(excludedThrough)).append(')');
        }
        return text.toString();
    }
}
