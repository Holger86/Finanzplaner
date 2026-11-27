package de.finanzplaner.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** German formatting and parsing of euro amounts and dates for the user interface. */
public final class Formats {
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern GROUPED = Pattern.compile("\\d{1,3}(\\.\\d{3})+");
    private static final Pattern PLAIN = Pattern.compile("\\d+");

    private Formats() {
    }

    /** Formats an amount as {@code 1.234,56 €}. */
    public static String money(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + " €";
    }

    /** Formats an amount with explicit sign, e.g. {@code +1.234,56 €} or {@code -5,00 €}. */
    public static String signedMoney(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return (value.signum() < 0 ? "-" : "+") + money(value.abs());
    }

    /**
     * Parses a euro amount in German notation ({@code 1.234,56}, {@code -50}, {@code 12,5 €}).
     * Without a comma, a dot counts as thousands separator only in grouped form
     * ({@code 1.500}); otherwise it is the decimal separator ({@code 12.50}).
     * At most two decimal places are allowed.
     */
    public static BigDecimal parseMoney(String text) {
        Objects.requireNonNull(text, "text");
        String value = text.replace("€", "").replace("\u00a0", "").replace(" ", "").trim();
        boolean negative = false;
        if (value.startsWith("-") || value.startsWith("+")) {
            negative = value.charAt(0) == '-';
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Bitte einen Betrag eingeben.");
        }
        String integer = value;
        String fraction = null;
        int comma = value.indexOf(',');
        if (comma >= 0) {
            integer = value.substring(0, comma);
            fraction = value.substring(comma + 1);
        } else if (value.indexOf('.') >= 0 && !GROUPED.matcher(value).matches()) {
            int dot = value.indexOf('.');
            integer = value.substring(0, dot);
            fraction = value.substring(dot + 1);
        }
        if (GROUPED.matcher(integer).matches()) {
            integer = integer.replace(".", "");
        }
        if (integer.isEmpty() && fraction != null) {
            integer = "0";
        }
        if (!PLAIN.matcher(integer).matches() || (fraction != null && !PLAIN.matcher(fraction).matches())) {
            throw new IllegalArgumentException("Ungültiger Betrag: " + text.trim());
        }
        if (fraction != null && fraction.length() > 2) {
            throw new IllegalArgumentException("Höchstens zwei Nachkommastellen erlaubt: " + text.trim());
        }
        BigDecimal amount = new BigDecimal(fraction == null ? integer : integer + "." + fraction).setScale(2);
        return negative ? amount.negate() : amount;
    }

    public static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    /** Parses {@code dd.MM.yyyy}; returns {@code null} for blank input. */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Ungültiges Datum (TT.MM.JJJJ): " + text.trim(), exception);
        }
    }
}
