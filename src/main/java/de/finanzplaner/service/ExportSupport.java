package de.finanzplaner.service;

import de.finanzplaner.domain.FinancialPlan;
import java.math.BigDecimal;
import java.util.Objects;

final class ExportSupport {
    private ExportSupport() {
    }

    static BigDecimal limit(FinancialPlan plan) {
        return Objects.requireNonNull(plan.overdraftLimit(), "overdraftLimit");
    }

    static BigDecimal maximum(SimulationResult result, BigDecimal limit) {
        for (SimulationRow row : result.rows()) {
            Objects.requireNonNull(row, "row");
            Objects.requireNonNull(row.date(), "date");
            Objects.requireNonNull(row.description(), "description");
            Objects.requireNonNull(row.amount(), "amount");
            Objects.requireNonNull(row.balance(), "balance");
            validateXmlText(row.description());
        }
        return ColorScale.maximum(result, limit);
    }

    static void validateXmlText(String text) {
        Objects.requireNonNull(text, "text");
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (!(codePoint == 0x9 || codePoint == 0xa || codePoint == 0xd
                    || (codePoint >= 0x20 && codePoint <= 0xd7ff)
                    || (codePoint >= 0xe000 && codePoint <= 0xfffd)
                    || (codePoint >= 0x10000 && codePoint <= 0x10ffff))) {
                throw new IllegalArgumentException("Text contains a character not permitted in XML 1.0");
            }
            i += Character.charCount(codePoint);
        }
    }
}
