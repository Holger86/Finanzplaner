package de.finanzplaner.ui;

import de.finanzplaner.service.SimulationResult;
import de.finanzplaner.service.SimulationRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Balance curve for the trend chart. The horizontal position is measured in
 * months since the first day of the start month, so whole numbers mark the
 * first day of a month.
 */
public final class TrendSeries {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/uuuu");
    private static final int[] TICK_UNITS = {1, 2, 3, 6, 12, 24, 60, 120, 240, 600, 1200};
    private static final int MAX_TICKS = 15;

    public record Point(double month, BigDecimal balance) {
        public Point {
            Objects.requireNonNull(balance, "balance");
        }
    }

    private TrendSeries() {
    }

    /** Position of the start of {@code date} in months since the start month of {@code origin}. */
    public static double position(LocalDate origin, LocalDate date) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(date, "date");
        long months = monthNumber(date) - monthNumber(origin);
        return months + (date.getDayOfMonth() - 1) / (double) date.lengthOfMonth();
    }

    /** Position right after the inclusive plan end, i.e. the right edge of the chart. */
    public static double endPosition(LocalDate origin, LocalDate end) {
        return position(origin, end.plusDays(1));
    }

    /**
     * Step curve of the end-of-day balance: the balance stays constant between
     * booking days and jumps on each booking day. The curve ends after the plan end.
     */
    public static List<Point> steps(SimulationResult result, LocalDate start, LocalDate end) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        List<SimulationRow> rows = result.rows();
        List<Point> points = new ArrayList<>();
        if (rows.isEmpty()) {
            return points;
        }
        BigDecimal balance = rows.getFirst().balance();
        points.add(new Point(position(start, start), balance));
        for (int index = 1; index < rows.size(); index++) {
            SimulationRow row = rows.get(index);
            boolean lastOfDay = index + 1 == rows.size() || !rows.get(index + 1).date().equals(row.date());
            if (lastOfDay && row.balance().compareTo(balance) != 0) {
                double month = position(start, row.date());
                points.add(new Point(month, balance));
                balance = row.balance();
                points.add(new Point(month, balance));
            }
        }
        points.add(new Point(endPosition(start, end), balance));
        return points;
    }

    /** Months between axis ticks so that at most {@value #MAX_TICKS} ticks are shown. */
    public static int tickUnit(double months) {
        for (int unit : TICK_UNITS) {
            if (months / unit <= MAX_TICKS) {
                return unit;
            }
        }
        return TICK_UNITS[TICK_UNITS.length - 1];
    }

    /** Axis label: {@code MM/yyyy} at month starts, otherwise the exact date. */
    public static String label(LocalDate origin, double position) {
        Objects.requireNonNull(origin, "origin");
        long month = (long) Math.floor(position);
        YearMonth yearMonth = YearMonth.from(origin).plusMonths(month);
        double fraction = position - month;
        if (fraction < 1e-9) {
            return MONTH.format(yearMonth);
        }
        int day = (int) Math.min(yearMonth.lengthOfMonth(), Math.round(fraction * yearMonth.lengthOfMonth()) + 1);
        return Formats.date(yearMonth.atDay(day));
    }

    private static long monthNumber(LocalDate date) {
        return (long) date.getYear() * 12 + date.getMonthValue() - 1;
    }
}
