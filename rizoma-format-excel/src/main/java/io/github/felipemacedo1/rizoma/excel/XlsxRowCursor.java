package io.github.felipemacedo1.rizoma.excel;

import io.github.felipemacedo1.rizoma.core.EngineException;
import io.github.felipemacedo1.rizoma.core.EngineLimits;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;

final class XlsxRowCursor implements ExcelRowCursor {
    private final XlsxPackageHandle packageHandle;
    private final OPCPackage packageFile;
    private final InputStream sheetInput;
    private final XMLStreamReader xml;
    private final SharedStrings sharedStrings;
    private final StylesTable styles;
    private final FormulaPolicy formulaPolicy;
    private final EngineLimits limits;
    private final boolean date1904;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private final List<String> warnings = new ArrayList<>();
    private boolean closed;
    private long inferredRow;

    XlsxRowCursor(TabularSource source, int sheetIndex, FormulaPolicy formulaPolicy,
                  EngineLimits limits) {
        XlsxPackageHandle openedHandle = null;
        OPCPackage openedPackage = null;
        InputStream openedSheet = null;
        XMLStreamReader openedXml = null;
        try {
            openedHandle = XlsxPackageHandle.open(source, limits.maxBytes());
            openedPackage = openedHandle.packageFile();
            rejectExternalRelationships(openedPackage);
            XSSFReader reader = new XSSFReader(openedPackage);
            reader.setUseReadOnlySharedStringsTable(true);
            this.date1904 = readDateWindowing(reader);
            this.sharedStrings = reader.getSharedStringsTable();
            this.styles = reader.getStylesTable();
            openedSheet = selectSheet(reader, sheetIndex);
            openedXml = secureFactory().createXMLStreamReader(openedSheet);
            this.packageHandle = openedHandle;
            this.packageFile = openedPackage;
            this.sheetInput = openedSheet;
            this.xml = openedXml;
            this.formulaPolicy = formulaPolicy;
            this.limits = limits;
        } catch (Exception exception) {
            closeXmlQuietly(openedXml);
            closeQuietly(openedSheet);
            closeQuietly(openedHandle);
            if (exception instanceof EngineException engineException) throw engineException;
            throw new EngineException("XLSX_OPEN_FAILED", "XLSX source could not be opened", exception);
        }
    }

    static List<String> sheetNames(TabularSource source, int maxSheets, long maxBytes) {
        try (XlsxPackageHandle handle = XlsxPackageHandle.open(source, maxBytes)) {
            OPCPackage packageFile = handle.packageFile();
            rejectExternalRelationships(packageFile);
            XSSFReader reader = new XSSFReader(packageFile);
            XSSFReader.SheetIterator iterator = reader.getSheetIterator();
            var names = new ArrayList<String>();
            while (iterator.hasNext()) {
                try (InputStream ignored = iterator.next()) {
                    names.add(iterator.getSheetName());
                    if (names.size() > maxSheets) {
                        throw new EngineException("EXCEL_SHEET_LIMIT", "workbook exceeds configured sheet limit");
                    }
                }
            }
            return List.copyOf(names);
        } catch (EngineException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EngineException("XLSX_OPEN_FAILED", "XLSX workbook metadata could not be read", exception);
        }
    }

    @Override public RawExcelRow nextRow() {
        ensureOpen();
        try {
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT && xml.getLocalName().equals("row")) {
                    return readRow();
                }
            }
            return null;
        } catch (EngineException exception) {
            close();
            throw exception;
        } catch (XMLStreamException exception) {
            close();
            throw new EngineException("XLSX_READ_FAILED", "XLSX worksheet could not be read", exception);
        }
    }

    @Override public List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Exception failure = null;
        try { xml.close(); } catch (Exception exception) { failure = exception; }
        try { sheetInput.close(); } catch (Exception exception) { if (failure == null) failure = exception; }
        try { packageHandle.close(); } catch (Exception exception) { if (failure == null) failure = exception; }
        if (failure != null) throw new EngineException("XLSX_CLOSE_FAILED", "XLSX resource could not be closed", failure);
    }

    private RawExcelRow readRow() throws XMLStreamException {
        String rowReference = xml.getAttributeValue(null, "r");
        long physicalLine = rowReference == null ? ++inferredRow : parseRow(rowReference);
        inferredRow = physicalLine;
        var values = new ArrayList<String>();
        int nextColumn = 0;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT && xml.getLocalName().equals("row")) break;
            if (event != XMLStreamConstants.START_ELEMENT || !xml.getLocalName().equals("c")) continue;
            String reference = xml.getAttributeValue(null, "r");
            int column = reference == null ? nextColumn : column(reference);
            if (column >= limits.maxColumns()) {
                throw new EngineException("COLUMN_LIMIT", "worksheet exceeds configured column limit");
            }
            while (values.size() <= column) values.add("");
            values.set(column, readCell(physicalLine, column));
            nextColumn = column + 1;
        }
        return new RawExcelRow(physicalLine, trimTrailingBlanks(values));
    }

    private String readCell(long row, int column) throws XMLStreamException {
        String type = xml.getAttributeValue(null, "t");
        String styleValue = xml.getAttributeValue(null, "s");
        int styleIndex = styleValue == null ? -1 : parseNonNegative(styleValue, "cell style");
        String raw = null;
        String formula = null;
        var inline = new StringBuilder();
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT && xml.getLocalName().equals("c")) break;
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            switch (xml.getLocalName()) {
                case "v" -> raw = readText();
                case "f" -> formula = readText();
                case "t" -> inline.append(readText());
                default -> { }
            }
        }
        if (formula != null) {
            if (formulaPolicy == FormulaPolicy.REJECT) {
                throw new EngineException("EXCEL_FORMULA_REJECTED", "workbook contains a formula cell");
            }
            if (formulaPolicy == FormulaPolicy.EXPRESSION) return checked("=" + formula);
            if (raw == null || raw.isEmpty()) {
                addWarning("formula without cached value at row " + row + " column c" + column + " treated as blank");
                return "";
            }
        }
        if ("inlineStr".equals(type)) return checked(inline.toString());
        if (raw == null) return "";
        try {
            return checked(switch (type == null ? "n" : type) {
                case "s" -> sharedStrings.getItemAt(Integer.parseInt(raw)).getString();
                case "b" -> raw.equals("1") ? "true" : "false";
                case "e" -> {
                    addWarning("error cell at row " + row + " column c" + column + " treated as blank");
                    yield "";
                }
                case "str" -> raw;
                default -> formatNumber(raw, styleIndex);
            });
        } catch (EngineException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EngineException("XLSX_CELL_INVALID", "XLSX cell value is malformed", exception);
        }
    }

    private String formatNumber(String raw, int styleIndex) {
        double number = Double.parseDouble(raw);
        if (styleIndex >= 0) {
            var style = styles.getStyleAt(styleIndex);
            int formatIndex = style.getDataFormat();
            String formatString = style.getDataFormatString();
            if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(number)) {
                return formatDate(DateUtil.getLocalDateTime(number, date1904));
            }
            return formatter.formatRawCellContents(number, formatIndex, formatString, date1904);
        }
        return formatter.formatRawCellContents(number, 0, "General", date1904);
    }

    private String readText() throws XMLStreamException {
        var text = new StringBuilder();
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                if (text.length() + xml.getTextLength() > limits.maxFieldChars()) {
                    throw new EngineException("FIELD_LIMIT", "field exceeds configured character limit");
                }
                text.append(xml.getTextCharacters(), xml.getTextStart(), xml.getTextLength());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                return text.toString();
            }
        }
        throw new EngineException("XLSX_XML_INVALID", "XLSX XML text is incomplete");
    }

    private String checked(String value) {
        if (value.length() > limits.maxFieldChars()) {
            throw new EngineException("FIELD_LIMIT", "field exceeds configured character limit");
        }
        return value;
    }

    private void addWarning(String warning) {
        if (warnings.size() < limits.maxWarnings()) warnings.add(warning);
    }

    private static InputStream selectSheet(XSSFReader reader, int selected) throws Exception {
        XSSFReader.SheetIterator iterator = reader.getSheetIterator();
        int index = 0;
        while (iterator.hasNext()) {
            InputStream input = iterator.next();
            if (index++ == selected) return input;
            input.close();
        }
        throw new EngineException("EXCEL_SHEET_NOT_FOUND", "selected sheet does not exist");
    }

    private static boolean readDateWindowing(XSSFReader reader) throws Exception {
        try (InputStream workbook = reader.getWorkbookData()) {
            XMLStreamReader xml = secureFactory().createXMLStreamReader(workbook);
            try {
                while (xml.hasNext()) {
                    if (xml.next() == XMLStreamConstants.START_ELEMENT && xml.getLocalName().equals("workbookPr")) {
                        String value = xml.getAttributeValue(null, "date1904");
                        return "1".equals(value) || "true".equalsIgnoreCase(value);
                    }
                }
                return false;
            } finally {
                xml.close();
            }
        }
    }

    private static void rejectExternalRelationships(OPCPackage packageFile) throws Exception {
        reject(packageFile.getRelationships());
        for (PackagePart part : packageFile.getParts()) {
            if (!part.isRelationshipPart()) reject(part.getRelationships());
        }
    }

    private static void reject(Iterable<PackageRelationship> relationships) {
        for (PackageRelationship relationship : relationships) {
            if (relationship.getTargetMode() == TargetMode.EXTERNAL) {
                throw new EngineException("EXCEL_EXTERNAL_RELATIONSHIP",
                        "external workbook relationships are not supported");
            }
        }
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    private static void set(XMLInputFactory factory, String property, Object value) {
        try { factory.setProperty(property, value); }
        catch (IllegalArgumentException exception) {
            throw new EngineException("XLSX_XML_SECURITY_UNAVAILABLE",
                    "required XML security control is unavailable", exception);
        }
    }

    private static int column(String reference) {
        int end = 0;
        while (end < reference.length() && Character.isLetter(reference.charAt(end))) end++;
        if (end == 0) throw new EngineException("XLSX_CELL_REFERENCE_INVALID", "XLSX cell reference is malformed");
        return CellReference.convertColStringToIndex(reference.substring(0, end));
    }

    private static long parseRow(String value) {
        try {
            long row = Long.parseLong(value);
            if (row <= 0) throw new NumberFormatException();
            return row;
        } catch (NumberFormatException exception) {
            throw new EngineException("XLSX_ROW_REFERENCE_INVALID", "XLSX row reference is malformed", exception);
        }
    }

    private static int parseNonNegative(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new EngineException("XLSX_CELL_INVALID", "XLSX " + label + " is malformed", exception);
        }
    }

    private static String formatDate(LocalDateTime value) {
        return value.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                ? value.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static List<String> trimTrailingBlanks(List<String> values) {
        int size = values.size();
        while (size > 0 && values.get(size - 1).isBlank()) size--;
        return new ArrayList<>(values.subList(0, size));
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("dataset is closed");
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            throw new EngineException("CANCELLED", "analysis was interrupted");
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) { }
    }

    private static void closeXmlQuietly(XMLStreamReader reader) {
        if (reader == null) return;
        try { reader.close(); } catch (Exception ignored) { }
    }
}
