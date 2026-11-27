package de.finanzplaner.service;

import de.finanzplaner.domain.FinancialPlan;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Exports a simulation as an OpenDocument spreadsheet without converting money to doubles. */
public final class OdsExportService {
    private static final String MIME = "application/vnd.oasis.opendocument.spreadsheet";
    private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    private static final String STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    private static final String NUMBER = "urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0";
    private static final String FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0";
    private static final String MANIFEST = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public void export(FinancialPlan plan, SimulationResult result, Path destination) throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(destination, "destination");
        ExportSupport.validateXmlText(plan.name());
        BigDecimal limit = ExportSupport.limit(plan);
        BigDecimal maximum = ExportSupport.maximum(result, limit);
        Map<String, String> styles = new LinkedHashMap<>();
        for (SimulationRow row : result.rows()) {
            if (row.date().getYear() < 1 || row.date().getYear() > 9999) {
                throw new IllegalArgumentException("Date is outside the OpenDocument date range");
            }
            String hex = ColorScale.color(row.balance(), limit, maximum).hex();
            styles.computeIfAbsent(hex, ignored -> "balance" + styles.size());
        }
        try (OutputStream output = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            byte[] mimeBytes = MIME.getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(mimeBytes);
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(mimeBytes.length);
            mimetype.setCompressedSize(mimeBytes.length);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(mimeBytes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            writeContent(zip, plan, result, limit, maximum, styles);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            writeManifest(zip);
            zip.closeEntry();
        } catch (XMLStreamException e) {
            throw new IOException("Unable to write OpenDocument XML", e);
        }
    }

    private static void writeContent(ZipOutputStream zip, FinancialPlan plan, SimulationResult result,
            BigDecimal limit, BigDecimal maximum, Map<String, String> styles) throws XMLStreamException {
        XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
        xml.writeStartDocument("UTF-8", "1.0");
        start(xml, "office", "document-content", OFFICE);
        xml.writeNamespace("office", OFFICE);
        xml.writeNamespace("table", TABLE);
        xml.writeNamespace("text", TEXT);
        xml.writeNamespace("style", STYLE);
        xml.writeNamespace("number", NUMBER);
        xml.writeNamespace("fo", FO);
        attr(xml, "office", OFFICE, "version", "1.2");
        start(xml, "office", "automatic-styles", OFFICE);
        start(xml, "number", "date-style", NUMBER);
        attr(xml, "style", STYLE, "name", "date-format");
        start(xml, "number", "day", NUMBER);
        attr(xml, "number", NUMBER, "style", "long");
        xml.writeEndElement();
        numberText(xml, ".");
        start(xml, "number", "month", NUMBER);
        attr(xml, "number", NUMBER, "style", "long");
        xml.writeEndElement();
        numberText(xml, ".");
        start(xml, "number", "year", NUMBER);
        attr(xml, "number", NUMBER, "style", "long");
        xml.writeEndElement();
        xml.writeEndElement();
        start(xml, "number", "currency-style", NUMBER);
        attr(xml, "style", STYLE, "name", "money-format");
        start(xml, "number", "number", NUMBER);
        attr(xml, "number", NUMBER, "decimal-places", "2");
        attr(xml, "number", NUMBER, "min-integer-digits", "1");
        attr(xml, "number", NUMBER, "grouping", "true");
        xml.writeEndElement();
        numberText(xml, " ");
        start(xml, "number", "currency-symbol", NUMBER);
        xml.writeCharacters("€");
        xml.writeEndElement();
        xml.writeEndElement();
        cellStyle(xml, "date", "date-format", null);
        cellStyle(xml, "money", "money-format", null);
        for (var entry : styles.entrySet()) {
            cellStyle(xml, entry.getValue(), "money-format",
                    entry.getKey().startsWith("#") ? entry.getKey() : "#" + entry.getKey());
        }
        xml.writeEndElement();
        start(xml, "office", "body", OFFICE);
        start(xml, "office", "spreadsheet", OFFICE);
        start(xml, "table", "table", TABLE);
        attr(xml, "table", TABLE, "name", "Finanzplan");
        start(xml, "table", "table-row", TABLE);
        textCell(xml, plan.name());
        xml.writeEndElement();
        start(xml, "table", "table-row", TABLE);
        for (String heading : new String[] {"Datum", "Was", "Betrag", "Neue Gesamtsumme"}) {
            textCell(xml, heading);
        }
        xml.writeEndElement();
        for (SimulationRow row : result.rows()) {
            start(xml, "table", "table-row", TABLE);
            start(xml, "table", "table-cell", TABLE);
            attr(xml, "table", TABLE, "style-name", "date");
            attr(xml, "office", OFFICE, "value-type", "date");
            attr(xml, "office", OFFICE, "date-value", row.date().toString());
            paragraph(xml, DATE_FORMAT.format(row.date()));
            xml.writeEndElement();
            textCell(xml, row.description());
            moneyCell(xml, row.amount(), "money");
            String color = ColorScale.color(row.balance(), limit, maximum).hex();
            moneyCell(xml, row.balance(), styles.get(color));
            xml.writeEndElement();
        }
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndDocument();
        xml.close();
    }

    private static void cellStyle(XMLStreamWriter xml, String name, String format, String color)
            throws XMLStreamException {
        start(xml, "style", "style", STYLE);
        attr(xml, "style", STYLE, "name", name);
        attr(xml, "style", STYLE, "family", "table-cell");
        attr(xml, "style", STYLE, "data-style-name", format);
        if (color != null) {
            start(xml, "style", "table-cell-properties", STYLE);
            attr(xml, "fo", FO, "background-color", color);
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private static void numberText(XMLStreamWriter xml, String value) throws XMLStreamException {
        start(xml, "number", "text", NUMBER);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private static void textCell(XMLStreamWriter xml, String value) throws XMLStreamException {
        start(xml, "table", "table-cell", TABLE);
        attr(xml, "office", OFFICE, "value-type", "string");
        paragraph(xml, value);
        xml.writeEndElement();
    }

    private static void moneyCell(XMLStreamWriter xml, BigDecimal value, String style) throws XMLStreamException {
        start(xml, "table", "table-cell", TABLE);
        attr(xml, "table", TABLE, "style-name", style);
        attr(xml, "office", OFFICE, "value-type", "currency");
        attr(xml, "office", OFFICE, "currency", "EUR");
        attr(xml, "office", OFFICE, "value", value.toPlainString());
        paragraph(xml, value.toPlainString() + " €");
        xml.writeEndElement();
    }

    private static void paragraph(XMLStreamWriter xml, String text) throws XMLStreamException {
        start(xml, "text", "p", TEXT);
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == ' ') {
                int end = i + 1;
                while (end < text.length() && text.charAt(end) == ' ') {
                    end++;
                }
                if (end - i == 1 && i > 0 && end < text.length()) {
                    xml.writeCharacters(" ");
                } else {
                    start(xml, "text", "s", TEXT);
                    if (end - i > 1) {
                        attr(xml, "text", TEXT, "c", Integer.toString(end - i));
                    }
                    xml.writeEndElement();
                }
                i = end;
            } else if (c == '\t' || c == '\n' || c == '\r') {
                start(xml, "text", c == '\t' ? "tab" : "line-break", TEXT);
                xml.writeEndElement();
                i += c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? 2 : 1;
            } else {
                int end = i + 1;
                while (end < text.length() && " \t\n\r".indexOf(text.charAt(end)) < 0) {
                    end++;
                }
                xml.writeCharacters(text.substring(i, end));
                i = end;
            }
        }
        xml.writeEndElement();
    }

    private static void writeManifest(ZipOutputStream zip) throws XMLStreamException {
        XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(zip, "UTF-8");
        xml.writeStartDocument("UTF-8", "1.0");
        start(xml, "manifest", "manifest", MANIFEST);
        xml.writeNamespace("manifest", MANIFEST);
        attr(xml, "manifest", MANIFEST, "version", "1.2");
        manifestEntry(xml, "/", MIME);
        manifestEntry(xml, "content.xml", "text/xml");
        xml.writeEndElement();
        xml.writeEndDocument();
        xml.close();
    }

    private static void manifestEntry(XMLStreamWriter xml, String path, String type) throws XMLStreamException {
        start(xml, "manifest", "file-entry", MANIFEST);
        attr(xml, "manifest", MANIFEST, "full-path", path);
        attr(xml, "manifest", MANIFEST, "media-type", type);
        xml.writeEndElement();
    }

    private static void start(XMLStreamWriter xml, String prefix, String name, String namespace)
            throws XMLStreamException {
        xml.writeStartElement(prefix, name, namespace);
    }

    private static void attr(XMLStreamWriter xml, String prefix, String namespace, String name, String value)
            throws XMLStreamException {
        xml.writeAttribute(prefix, namespace, name, value);
    }
}
