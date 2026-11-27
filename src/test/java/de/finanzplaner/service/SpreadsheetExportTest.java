package de.finanzplaner.service;

import static org.junit.jupiter.api.Assertions.*;

import de.finanzplaner.domain.Account;
import de.finanzplaner.domain.FinancialPlan;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class SpreadsheetExportTest {
    private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    private static final String FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0";
    private final List<Path> paths = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void xlsxHasTypedDatesAndMoneyAndColorScale() throws Exception {
        Path path = path(".xlsx");
        SimulationResult result = sample();
        new XlsxExportService().export(plan(), result, path);
        try (InputStream input = Files.newInputStream(path); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheet("Finanzplan");
            assertEquals(plan().name(), sheet.getRow(0).getCell(0).getStringCellValue());
            for (int i = 0; i < 4; i++) {
                assertEquals(List.of("Datum", "Was", "Betrag", "Neue Gesamtsumme").get(i),
                        sheet.getRow(1).getCell(i).getStringCellValue());
            }
            for (int i = 0; i < result.rows().size(); i++) {
                var expected = result.rows().get(i);
                var row = sheet.getRow(i + 2);
                assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
                assertTrue(DateUtil.isCellDateFormatted(row.getCell(0)));
                assertEquals("dd.mm.yyyy", row.getCell(0).getCellStyle().getDataFormatString());
                assertEquals(expected.date(), row.getCell(0).getLocalDateTimeCellValue().toLocalDate());
                assertEquals(expected.description(), row.getCell(1).getStringCellValue());
                assertEquals(CellType.NUMERIC, row.getCell(2).getCellType());
                assertEquals(expected.amount().toPlainString(), row.getCell(2).getRawValue());
                assertEquals(CellType.NUMERIC, row.getCell(3).getCellType());
                assertEquals(expected.balance().toPlainString(), row.getCell(3).getRawValue());
                assertTrue(row.getCell(2).getCellStyle().getDataFormatString().contains("€"));
                assertTrue(row.getCell(3).getCellStyle().getDataFormatString().contains("€"));
                assertEquals(FillPatternType.SOLID_FOREGROUND, row.getCell(3).getCellStyle().getFillPattern());
                String actual = HexFormat.of().withUpperCase().formatHex(
                        row.getCell(3).getCellStyle().getFillForegroundXSSFColor().getRGB());
                assertEquals(color(expected).hex().replace("#", "").toUpperCase(), actual);
            }
        }
    }

    @Test
    void odsIsValidPackageWithEscapedTypedCellsAndMatchingColors() throws Exception {
        Path path = path(".ods");
        SimulationResult result = sample();
        new OdsExportService().export(plan(), result, path);
        try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            ZipEntry first = entries.nextElement();
            assertEquals("mimetype", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod());
            try (InputStream input = zip.getInputStream(first)) {
                assertEquals("application/vnd.oasis.opendocument.spreadsheet",
                        new String(input.readAllBytes(), StandardCharsets.US_ASCII));
            }
            Document content = xml(zip, "content.xml");
            Document manifest = xml(zip, "META-INF/manifest.xml");
            assertEquals("manifest", manifest.getDocumentElement().getLocalName());
            assertEquals(2, manifest.getElementsByTagNameNS(
                    "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0", "file-entry").getLength());
            Element dateStyle = (Element) content.getElementsByTagNameNS(
                    "urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0", "date-style").item(0);
            assertEquals("day", dateStyle.getChildNodes().item(0).getLocalName());
            assertEquals(".", dateStyle.getChildNodes().item(1).getTextContent());
            assertEquals("month", dateStyle.getChildNodes().item(2).getLocalName());
            assertEquals(".", dateStyle.getChildNodes().item(3).getTextContent());
            assertEquals("year", dateStyle.getChildNodes().item(4).getLocalName());
            NodeList rows = content.getElementsByTagNameNS(TABLE, "table-row");
            assertEquals(result.rows().size() + 2, rows.getLength());
            assertEquals(plan().name(), ((Element) rows.item(0)).getTextContent());
            NodeList headings = ((Element) rows.item(1)).getElementsByTagNameNS(TABLE, "table-cell");
            for (int i = 0; i < 4; i++) {
                assertEquals(List.of("Datum", "Was", "Betrag", "Neue Gesamtsumme").get(i),
                        headings.item(i).getTextContent());
            }
            for (int i = 0; i < result.rows().size(); i++) {
                SimulationRow expected = result.rows().get(i);
                NodeList cells = ((Element) rows.item(i + 2)).getElementsByTagNameNS(TABLE, "table-cell");
                Element date = (Element) cells.item(0);
                assertEquals("date", date.getAttributeNS(OFFICE, "value-type"));
                assertEquals(expected.date().toString(), date.getAttributeNS(OFFICE, "date-value"));
                assertEquals(expected.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        date.getTextContent());
                assertEquals(expected.description(), cells.item(1).getTextContent());
                for (int column = 2; column <= 3; column++) {
                    Element cell = (Element) cells.item(column);
                    assertEquals("currency", cell.getAttributeNS(OFFICE, "value-type"));
                    assertEquals("EUR", cell.getAttributeNS(OFFICE, "currency"));
                    assertEquals(column == 2 ? expected.amount() : expected.balance(),
                            new BigDecimal(cell.getAttributeNS(OFFICE, "value")));
                }
                String styleName = ((Element) cells.item(3)).getAttributeNS(TABLE, "style-name");
                assertEquals("#" + color(expected).hex().replace("#", "").toUpperCase(),
                        background(content, styleName).toUpperCase());
            }
        }
    }

    @Test
    void odsKeepsArbitraryPrecisionAndSignificantWhitespace() throws Exception {
        Path path = path(".ods");
        var amount = new BigDecimal("123456789012345678901234567890.00000000000001");
        var result = result(new SimulationRow(LocalDate.of(2026, 1, 1), "  A  B\tC\nD ", amount,
                amount.negate(), false));
        new OdsExportService().export(plan(), result, path);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Document document = xml(zip, "content.xml");
            Element row = (Element) document.getElementsByTagNameNS(TABLE, "table-row").item(2);
            NodeList cells = row.getElementsByTagNameNS(TABLE, "table-cell");
            assertEquals(amount.toPlainString(), ((Element) cells.item(2)).getAttributeNS(OFFICE, "value"));
            assertEquals(amount.negate().toPlainString(), ((Element) cells.item(3)).getAttributeNS(OFFICE, "value"));
            assertEquals(3, ((Element) cells.item(1)).getElementsByTagNameNS(
                    "urn:oasis:names:tc:opendocument:xmlns:text:1.0", "s").getLength());
            assertEquals(1, ((Element) cells.item(1)).getElementsByTagNameNS(
                    "urn:oasis:names:tc:opendocument:xmlns:text:1.0", "tab").getLength());
        }
    }

    @Test
    void xlsxRejectsLossyNumbersBeforeTouchingDestination() throws Exception {
        Path path = path(".xlsx");
        Files.writeString(path, "existing");
        FinancialPlan plan = plan();
        for (String value : List.of("1234567890123456", "0.1234567890123456", "1E+308", "1E-309", "1E+400", "1E-400")) {
            var row = new SimulationRow(LocalDate.of(2026, 1, 1), "loss", new BigDecimal(value),
                    BigDecimal.ZERO, false);
            assertThrows(IllegalArgumentException.class, () -> new XlsxExportService().export(plan, result(row), path));
            assertEquals("existing", Files.readString(path));
        }
    }

    @Test
    void invalidXmlCharactersAndExcelDatesFailBeforeWriting() throws Exception {
        Path ods = path(".ods");
        Path xlsx = path(".xlsx");
        Files.writeString(ods, "original");
        Files.writeString(xlsx, "original");
        FinancialPlan plan = plan();
        var badText = result(new SimulationRow(LocalDate.of(2026, 1, 1), "bad\u0001", BigDecimal.ZERO,
                BigDecimal.ZERO, false));
        assertThrows(IllegalArgumentException.class, () -> new OdsExportService().export(plan, badText, ods));
        assertThrows(IllegalArgumentException.class, () -> new XlsxExportService().export(plan, badText, xlsx));
        var oldDate = result(new SimulationRow(LocalDate.of(1899, 12, 31), "old", BigDecimal.ZERO,
                BigDecimal.ZERO, false));
        assertThrows(IllegalArgumentException.class, () -> new XlsxExportService().export(plan, oldDate, xlsx));
        assertEquals("original", Files.readString(ods));
        assertEquals("original", Files.readString(xlsx));
    }

    @Test
    void bothFormatsExportEmptyResultsAndPropagateBadPaths() throws Exception {
        SimulationResult empty = new SimulationResult(List.of(), Optional.empty());
        Path xlsx = path(".xlsx");
        Path ods = path(".ods");
        new XlsxExportService().export(plan(), empty, xlsx);
        new OdsExportService().export(plan(), empty, ods);
        try (InputStream input = Files.newInputStream(xlsx);
                XSSFWorkbook workbook = new XSSFWorkbook(input);
                ZipFile zip = new ZipFile(ods.toFile())) {
            assertEquals(2, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals(2, xml(zip, "content.xml").getElementsByTagNameNS(TABLE, "table-row").getLength());
        }
        Path bad = Path.of("target", "missing-" + UUID.randomUUID(), "export.xlsx");
        assertThrows(java.io.IOException.class, () -> new XlsxExportService().export(plan(), empty, bad));
        assertThrows(java.io.IOException.class, () -> new OdsExportService().export(plan(), empty, bad));
    }

    private static RgbColor color(SimulationRow row) {
        return ColorScale.color(row.balance(), new BigDecimal("-100"), new BigDecimal("100"));
    }

    private static FinancialPlan plan() {
        return new FinancialPlan("Haushalt & <Test> \"€\"", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), new BigDecimal("-100"),
                List.of(new Account("Giro", BigDecimal.ZERO)), List.of());
    }

    private static SimulationResult sample() {
        return result(
                new SimulationRow(LocalDate.of(2025, 12, 31), "=unter Grenze", new BigDecimal("-1"),
                        new BigDecimal("-101"), false),
                new SimulationRow(LocalDate.of(2026, 1, 1), "Start & <offen>", new BigDecimal("12.34"),
                        new BigDecimal("-100"), true),
                new SimulationRow(LocalDate.of(2026, 1, 2), "Miete \"€\"", new BigDecimal("-3.45"),
                        BigDecimal.ZERO, false),
                new SimulationRow(LocalDate.of(2026, 1, 3), "Gehalt", new BigDecimal("100.01"),
                        new BigDecimal("100"), false));
    }

    private static SimulationResult result(SimulationRow... rows) {
        return new SimulationResult(List.of(rows), Optional.empty());
    }

    private Path path(String suffix) throws Exception {
        Files.createDirectories(Path.of("target"));
        Path path = Path.of("target", "spreadsheet-" + UUID.randomUUID() + suffix).toAbsolutePath();
        paths.add(path);
        return path;
    }

    private static Document xml(ZipFile zip, String name) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static String background(Document document, String styleName) {
        NodeList styles = document.getElementsByTagNameNS(STYLE, "style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element style = (Element) styles.item(i);
            if (styleName.equals(style.getAttributeNS(STYLE, "name"))) {
                Element properties = (Element) style.getElementsByTagNameNS(STYLE, "table-cell-properties").item(0);
                return properties.getAttributeNS(FO, "background-color");
            }
        }
        fail("Missing balance style " + styleName);
        return null;
    }
}
