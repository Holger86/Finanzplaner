package de.finanzplaner.service;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.BusinessDay;
import de.finanzplaner.domain.Entry;
import de.finanzplaner.domain.EntryType;
import de.finanzplaner.domain.FinancialPlan;
import de.finanzplaner.domain.Recurrence;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Simulates inclusive date ranges, with same-day entries in plan order.
 * Business days are Monday through Friday; public holidays are not modeled.
 */
public final class SimulationEngine {
    /**
     * Estimates the aggregate balance through the inclusive cut-off, even beyond
     * the original plan end. Individual recurrence end dates remain binding.
     * This does not estimate balances for individual accounts.
     */
    public static SimulationResult estimateAt(FinancialPlan plan, LocalDate cutOff) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(cutOff, "cutOff");
        if (cutOff.isBefore(plan.startDate())) {
            throw new IllegalArgumentException("cutOff must not precede the plan start");
        }
        return simulate(new FinancialPlan(plan.name(), plan.startDate(), cutOff, plan.overdraftLimit(),
                plan.accounts(), plan.entries()));
    }

    public static SimulationResult simulate(FinancialPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<Event> events = new ArrayList<>();
        for (Entry entry : plan.entries()) {
            occurrences(entry.recurrence(), plan.startDate(), plan.endDate(),
                    date -> events.add(new Event(date, entry)));
        }
        events.sort(Comparator.comparing(Event::date));

        BigDecimal balance = plan.accounts().stream().map(Account::openingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SimulationRow> rows = new ArrayList<>();
        SimulationRow opening = new SimulationRow(plan.startDate(), "Anfangsbestand", balance, balance, true);
        rows.add(opening);
        BigDecimal floor = plan.overdraftLimit();
        SimulationRow lowest = balance.compareTo(floor) < 0 ? opening : null;
        for (Event event : events) {
            Entry entry = event.entry();
            BigDecimal amount = entry.type() == EntryType.AUSGABE ? entry.amount().negate() : entry.amount();
            balance = balance.add(amount);
            SimulationRow row = new SimulationRow(event.date(), entry.name(), amount, balance, false);
            rows.add(row);
            if (balance.compareTo(floor) < 0
                    && (lowest == null || balance.compareTo(lowest.balance()) < 0)) {
                lowest = row;
            }
        }
        return new SimulationResult(rows, Optional.ofNullable(lowest));
    }

    private static void occurrences(Recurrence recurrence, LocalDate from, LocalDate to,
                                    Consumer<LocalDate> add) {
        if (recurrence instanceof Recurrence.OneTime oneTime) {
            if (!oneTime.settled() && !oneTime.date().isBefore(from) && !oneTime.date().isAfter(to)) {
                add.accept(oneTime.date());
            }
        } else if (recurrence instanceof Recurrence.Interval interval) {
            LocalDate first = later(from, interval.startDate());
            LocalDate last = interval.endDate() == null ? to : earlier(to, interval.endDate());
            if (interval.excludedThrough() != null) {
                if (!interval.excludedThrough().isBefore(last)) return;
                first = later(first, interval.excludedThrough().plusDays(1));
            }
            if (first.isAfter(last)) {
                return;
            }
            switch (interval.unit()) {
                case TAG -> dayIntervals(interval.startDate(), first, last, interval.every(), add);
                case WOCHE -> dayIntervals(interval.startDate(), first, last, 7L * interval.every(), add);
                case MONAT -> monthIntervals(interval.startDate(), first, last, interval.every(), true, add);
                case QUARTAL -> monthIntervals(interval.startDate(), first, last, 3L * interval.every(), true, add);
                // Always the anchor's month, so 28.02. stays 28.02. and 29.02. falls back to 28.02.
                case JAHR -> monthIntervals(interval.startDate(), first, last, 12L * interval.every(), false, add);
            }
        } else if (recurrence instanceof Recurrence.MonthlyBusinessDay monthly) {
            LocalDate first = later(from, monthly.startDate());
            LocalDate last = monthly.endDate() == null ? to : earlier(to, monthly.endDate());
            if (monthly.excludedThrough() != null) {
                if (!monthly.excludedThrough().isBefore(last)) return;
                first = later(first, monthly.excludedThrough().plusDays(1));
            }
            if (!first.isAfter(last)) {
                long endMonth = monthNumber(last);
                for (long month = monthNumber(first); month <= endMonth; month++) {
                    YearMonth ym = yearMonth(month);
                    LocalDate day = monthly.type() == BusinessDay.FIRST ? ym.atDay(1) : ym.atEndOfMonth();
                    if (monthly.type() == BusinessDay.FIRST) {
                        if (day.getDayOfWeek() == DayOfWeek.SATURDAY) day = day.plusDays(2);
                        else if (day.getDayOfWeek() == DayOfWeek.SUNDAY) day = day.plusDays(1);
                    } else {
                        if (day.getDayOfWeek() == DayOfWeek.SATURDAY) day = day.minusDays(1);
                        else if (day.getDayOfWeek() == DayOfWeek.SUNDAY) day = day.minusDays(2);
                    }
                    if (!day.isBefore(first) && !day.isAfter(last)) add.accept(day);
                }
            }
        }
    }

    private static void dayIntervals(LocalDate anchor, LocalDate first, LocalDate last, long step,
                                     Consumer<LocalDate> add) {
        long startEpoch = anchor.toEpochDay();
        long offset = first.toEpochDay() - startEpoch;
        long index = (offset + step - 1) / step;
        long endEpoch = last.toEpochDay();
        for (long epoch = startEpoch + index * step; epoch <= endEpoch; epoch += step) {
            add.accept(LocalDate.ofEpochDay(epoch));
        }
    }

    private static void monthIntervals(LocalDate anchor, LocalDate first, LocalDate last, long step,
                                       boolean keepMonthEnd, Consumer<LocalDate> add) {
        long startMonth = monthNumber(anchor);
        long offset = monthNumber(first) - startMonth;
        long index = (offset + step - 1) / step;
        long endMonth = monthNumber(last);
        boolean endOfMonth = keepMonthEnd && anchor.getDayOfMonth() == anchor.lengthOfMonth();
        for (long month = startMonth + index * step; month <= endMonth; month += step) {
            YearMonth ym = yearMonth(month);
            LocalDate date = endOfMonth ? ym.atEndOfMonth()
                    : ym.atDay(Math.min(anchor.getDayOfMonth(), ym.lengthOfMonth()));
            if (!date.isBefore(first) && !date.isAfter(last)) add.accept(date);
        }
    }

    private static long monthNumber(LocalDate date) {
        return (long) date.getYear() * 12 + date.getMonthValue() - 1;
    }

    private static YearMonth yearMonth(long month) {
        return YearMonth.of(Math.toIntExact(Math.floorDiv(month, 12)), (int) Math.floorMod(month, 12) + 1);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private record Event(LocalDate date, Entry entry) {
    }
}
