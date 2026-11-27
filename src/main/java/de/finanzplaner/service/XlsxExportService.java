package de.finanzplaner.service;

import de.finanzplaner.domain.FinancialPlan;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Exports a simulation to an Excel workbook. */
public final class XlsxExportService {
    private static final BigDecimal MAX_EXCEL_NUMBER = new BigDecimal("9.99999999999999E+307");
    private static final BigDecimal MIN_EXCEL_NUMBER = new BigDecimal("2.2250738585072E-308");
    /**
     * Writes to exactly the caller-supplied path. XLSX values are written as decimal XML,
     * but Excel itself uses binary numbers with a maximum of 15 significant decimal digits.
     * Values outside Excel's supported precision and range are rejected.
     */
    public void export(FinancialPlan plan, SimulationResult result, Path destination) throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(destination, "destination");
        ExportSupport.validateXmlText(plan.name());
        BigDecimal limit = ExportSupport.limit(plan);
        BigDecimal maximum = ExportSupport.maximum(result, limit);
        validateText(plan.name());
        for (SimulationRow row : result.rows()) {
            validateText(row.description());
            if (row.date().isBefore(LocalDate.of(1900, 1, 1)) || row.date().getYear() > 9999) {
                throw new IllegalArgumentException("Date is outside the Excel date range");
            }
            validateExcelNumber(row.amount());
            validateExcelNumber(row.balance());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Finanzplan");
            var formats = workbook.createDataFormat();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(formats.getFormat("dd.mm.yyyy"));
            XSSFCellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(formats.getFormat("#,##0.00 \"€\""));
            Map<String, XSSFCellStyle> balanceStyles = new HashMap<>();
            sheet.createRow(0).createCell(0).setCellValue(plan.name());
            Row header = sheet.createRow(1);
            String[] headings = {"Datum", "Was", "Betrag", "Neue Gesamtsumme"};
            for (int i = 0; i < headings.length; i++) {
                header.createCell(i).setCellValue(headings[i]);
            }
            int index = 2;
            for (SimulationRow simulationRow : result.rows()) {
                var row = sheet.createRow(index++);
                var date = row.createCell(0);
                date.setCellValue(simulationRow.date());
                date.setCellStyle(dateStyle);
                row.createCell(1).setCellValue(simulationRow.description());
                var amount = row.createCell(2, CellType.NUMERIC);
                amount.getCTCell().setV(simulationRow.amount().toPlainString());
                amount.setCellStyle(moneyStyle);
                var balance = row.createCell(3, CellType.NUMERIC);
                balance.getCTCell().setV(simulationRow.balance().toPlainString());
                var color = ColorScale.color(simulationRow.balance(), limit, maximum);
                XSSFCellStyle style = balanceStyles.get(color.hex());
                if (style == null) {
                    style = workbook.createCellStyle();
                    style.cloneStyleFrom(moneyStyle);
                    style.setFillForegroundColor(new XSSFColor(
                            new byte[] {(byte) color.red(), (byte) color.green(), (byte) color.blue()},
                            new DefaultIndexedColorMap()));
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    balanceStyles.put(color.hex(), style);
                }
                balance.setCellStyle(style);
            }
            sheet.setColumnWidth(0, 13 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 18 * 256);
            try (OutputStream output = Files.newOutputStream(destination)) {
                workbook.write(output);
            }
        }
    }

    private static void validateExcelNumber(BigDecimal value) {
        BigDecimal magnitude = value.abs();
        if (value.stripTrailingZeros().precision() > 15
                || magnitude.compareTo(MAX_EXCEL_NUMBER) > 0
                || (magnitude.signum() != 0 && magnitude.compareTo(MIN_EXCEL_NUMBER) < 0)) {
            throw new IllegalArgumentException("Amount cannot be represented safely in Excel: " + value);
        }
    }

    private static void validateText(String text) {
        if (text.length() > 32767) {
            throw new IllegalArgumentException("Text exceeds the Excel cell limit");
        }
    }
}
