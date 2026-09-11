package io.github.rizoma.excel;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.DataReader;
import io.github.rizoma.core.Dataset;
import io.github.rizoma.core.EngineException;
import io.github.rizoma.core.EngineLimits;
import io.github.rizoma.core.HeaderNormalizer;
import io.github.rizoma.core.TabularSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Read-only XLS/XLSX adapter with streaming XLSX rows and bounded legacy XLS loading. */
public final class ExcelDataReader implements DataReader {
    private static final int PREVIEW_ROWS = 20;
    private static final int MAX_PREVIEW_PHYSICAL_ROWS = 1_000;
    private static final int DEFAULT_MAX_PREAMBLE_ROWS = 20;
    private static final HeaderNormalizer NORMALIZER = new HeaderNormalizer(Map.of());
    private static final Set<String> OPTIONS = Set.of("header", "sheet", "formula",
            "maxExpandedBytes", "maxZipEntryBytes", "maxLegacyBytes", "maxZipEntries",
            "maxSheets", "minInflateRatio", "maxPreambleRows");

    @Override public boolean supports(TabularSource source) {
        return ExcelSecurity.kind(source) != ExcelSecurity.Kind.UNKNOWN;
    }

    @Override public SourceStructure detect(TabularSource source, AnalysisOptions options, EngineLimits limits) {
        validateOptions(options);
        ExcelSecurity.Kind kind = requireKind(source);
        ExcelSecurity.Policy security = ExcelSecurity.Policy.from(options, limits);
        ExcelSecurity.checkSourceSize(source, limits, kind, security);
        var warnings = new ArrayList<String>();
        if (kind == ExcelSecurity.Kind.XLSX) warnings.addAll(ExcelSecurity.inspectXlsx(source, security));
        else warnings.add("legacy XLS uses a bounded in-memory model; maxLegacyBytes is enforced");

        List<String> sheets = sheetNames(source, kind, security.maxSheets(), limits.maxBytes());
        if (sheets.isEmpty()) throw new EngineException("EXCEL_EMPTY_WORKBOOK", "workbook contains no worksheets");
        int sheetIndex = selectSheet(sheets, options.readerOptions().get("sheet"));
        FormulaPolicy formula = FormulaPolicy.from(options.readerOptions().getOrDefault("formula", "cached"));
        int maxPreambleRows = nonNegativeInt(options, "maxPreambleRows", DEFAULT_MAX_PREAMBLE_ROWS);
        List<RawExcelRow> preview = preview(source, kind, sheetIndex, formula, limits, maxPreambleRows);
        if (preview.isEmpty()) {
            warnings.add("selected worksheet is empty");
            return new SourceStructure(kind.name(), null, null, false, List.of(), warnings,
                    attributes(sheetIndex, formula, null, null));
        }

        boolean header = detectHeader(preview, options, warnings);
        long firstLine = preview.getFirst().physicalLine();
        int width = preview.stream().mapToInt(row -> row.values().size()).max().orElse(0);
        if (width > limits.maxColumns()) {
            throw new EngineException("COLUMN_LIMIT", "worksheet exceeds configured column limit");
        }
        List<String> names = header ? padded(preview.getFirst().values(), width) : synthetic(width);
        var columns = columns(names, limits, warnings);
        long dataStart = header ? firstLine + 1 : firstLine;
        return new SourceStructure(kind.name(), null, null, header, columns, warnings,
                attributes(sheetIndex, formula, header ? firstLine : null, dataStart));
    }

    @Override public Dataset open(TabularSource source, SourceStructure structure,
                                  AnalysisOptions options, EngineLimits limits) {
        validateOptions(options);
        ExcelSecurity.Kind kind = requireKind(source);
        if (!kind.name().equals(structure.format())) {
            throw new EngineException("EXCEL_FORMAT_CHANGED", "Excel container type changed after detection");
        }
        ExcelSecurity.Policy security = ExcelSecurity.Policy.from(options, limits);
        ExcelSecurity.checkSourceSize(source, limits, kind, security);
        if (kind == ExcelSecurity.Kind.XLSX) ExcelSecurity.inspectXlsx(source, security);
        int sheetIndex = requiredInt(structure.attributes(), "sheetIndex");
        FormulaPolicy formula = FormulaPolicy.from(structure.attributes().getOrDefault("formulaPolicy", "cached"));
        ExcelRowCursor cursor = cursor(source, kind, sheetIndex, formula, limits);
        long dataStart = requiredLong(structure.attributes(), "dataStartRow", Long.MAX_VALUE);
        return new ExcelDataset(structure, cursor, limits, dataStart);
    }

    private static List<RawExcelRow> preview(TabularSource source, ExcelSecurity.Kind kind,
                                              int sheet, FormulaPolicy formula, EngineLimits limits,
                                              int maxPreambleRows) {
        var rows = new ArrayList<RawExcelRow>();
        try (ExcelRowCursor cursor = cursor(source, kind, sheet, formula, limits)) {
            int scanned = 0;
            for (RawExcelRow row; rows.size() < PREVIEW_ROWS && (row = cursor.nextRow()) != null;) {
                if (++scanned > MAX_PREVIEW_PHYSICAL_ROWS) {
                    throw new EngineException("EXCEL_PREVIEW_LIMIT",
                            "worksheet preview exceeds its bounded physical row window");
                }
                if (row.isBlank()) continue;
                if (rows.isEmpty() && row.physicalLine() > (long) maxPreambleRows + 1) {
                    throw new EngineException("EXCEL_PREAMBLE_LIMIT",
                            "first populated row exceeds configured preamble limit");
                }
                rows.add(row);
            }
        }
        return List.copyOf(rows);
    }

    private static ExcelRowCursor cursor(TabularSource source, ExcelSecurity.Kind kind, int sheet,
                                          FormulaPolicy formula, EngineLimits limits) {
        return kind == ExcelSecurity.Kind.XLSX
                ? new XlsxRowCursor(source, sheet, formula, limits)
                : new XlsRowCursor(source, sheet, formula, limits);
    }

    private static List<String> sheetNames(TabularSource source, ExcelSecurity.Kind kind,
                                           int maximum, long maxBytes) {
        return kind == ExcelSecurity.Kind.XLSX
                ? XlsxRowCursor.sheetNames(source, maximum, maxBytes)
                : XlsRowCursor.sheetNames(source, maximum);
    }

    private static int selectSheet(List<String> names, String selector) {
        if (selector == null) {
            if (names.size() != 1) {
                throw new EngineException("EXCEL_SHEET_AMBIGUOUS",
                        "workbook contains multiple worksheets; provide reader option sheet");
            }
            return 0;
        }
        int exact = names.indexOf(selector);
        if (exact >= 0) return exact;
        try {
            int index = Integer.parseInt(selector);
            if (index >= 0 && index < names.size()) return index;
        } catch (NumberFormatException ignored) { }
        throw new EngineException("EXCEL_SHEET_NOT_FOUND", "selected sheet does not exist");
    }

    private static boolean detectHeader(List<RawExcelRow> rows, AnalysisOptions options, List<String> warnings) {
        String override = options.readerOptions().getOrDefault("header", "detect").toLowerCase(java.util.Locale.ROOT);
        if (override.equals("first")) return true;
        if (override.equals("none")) return false;
        if (!override.equals("detect")) {
            throw new EngineException("INVALID_READER_OPTION", "header must be detect, first or none");
        }
        List<String> first = rows.getFirst().values();
        boolean unique = new HashSet<>(first).size() == first.size();
        boolean nonblank = first.stream().noneMatch(String::isBlank);
        boolean textual = first.stream().allMatch(value -> !value.strip().matches("[-+]?\\d+(?:[.,]\\d+)?"));
        if (rows.size() == 1) warnings.add("header detection is ambiguous for a single populated row; selected first row");
        boolean header = unique && nonblank && textual;
        if (!header) warnings.add("header was not confidently detected; synthetic column names used");
        return header;
    }

    private static List<SourceColumn> columns(List<String> names, EngineLimits limits, List<String> warnings) {
        var columns = new ArrayList<SourceColumn>();
        var counts = new HashMap<String, Integer>();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index) == null ? "" : names.get(index);
            if (name.length() > limits.maxHeaderChars()) {
                throw new EngineException("HEADER_LIMIT", "header exceeds configured character limit");
            }
            if (name.isBlank()) warnings.add("empty header at column c" + index);
            if (counts.merge(name, 1, Integer::sum) == 2) warnings.add("duplicate header: " + safeHeader(name));
            columns.add(new SourceColumn("c" + index, index, name, NORMALIZER.normalize(name).comparable()));
        }
        return List.copyOf(columns);
    }

    private static Map<String, String> attributes(int sheetIndex, FormulaPolicy formula,
                                                   Long headerRow, Long dataStartRow) {
        var values = new LinkedHashMap<String, String>();
        values.put("sheetIndex", Integer.toString(sheetIndex));
        values.put("formulaPolicy", formula.name().toLowerCase(java.util.Locale.ROOT));
        if (headerRow != null) values.put("headerRow", Long.toString(headerRow));
        if (dataStartRow != null) values.put("dataStartRow", Long.toString(dataStartRow));
        return values;
    }

    private static List<String> padded(List<String> source, int width) {
        var result = new ArrayList<>(source);
        while (result.size() < width) result.add("");
        return result;
    }

    private static List<String> synthetic(int width) {
        var result = new ArrayList<String>();
        for (int index = 0; index < width; index++) result.add("column_" + (index + 1));
        return result;
    }

    private static void validateOptions(AnalysisOptions options) {
        for (String option : options.readerOptions().keySet()) {
            if (!OPTIONS.contains(option)) {
                throw new EngineException("INVALID_READER_OPTION", "reader option is not supported for Excel: " + option);
            }
        }
    }

    private static ExcelSecurity.Kind requireKind(TabularSource source) {
        ExcelSecurity.Kind kind = ExcelSecurity.kind(source);
        if (kind == ExcelSecurity.Kind.UNKNOWN) {
            throw new EngineException("EXCEL_INVALID_CONTAINER", "source is not an XLS or XLSX container");
        }
        return kind;
    }

    private static int requiredInt(Map<String, String> attributes, String name) {
        long value = requiredLong(attributes, name, -1);
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new EngineException("EXCEL_STRUCTURE_INVALID", "detected Excel structure is incomplete");
        }
        return (int) value;
    }

    private static long requiredLong(Map<String, String> attributes, String name, long fallback) {
        String value = attributes.get(name);
        if (value == null) return fallback;
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) {
            throw new EngineException("EXCEL_STRUCTURE_INVALID", "detected Excel structure is malformed", exception);
        }
    }

    private static int nonNegativeInt(AnalysisOptions options, String name, int fallback) {
        String value = options.readerOptions().get(name);
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new EngineException("INVALID_READER_OPTION", name + " must be a non-negative integer");
        }
    }

    private static String safeHeader(String value) {
        return value.isBlank() ? "<empty>" : "<redacted:length=" + value.length() + ">";
    }

    private static final class ExcelDataset implements Dataset {
        private final SourceStructure structure;
        private final ExcelRowCursor cursor;
        private final EngineLimits limits;
        private final long dataStartRow;
        private boolean requested;
        private boolean closed;

        private ExcelDataset(SourceStructure structure, ExcelRowCursor cursor,
                             EngineLimits limits, long dataStartRow) {
            this.structure = structure;
            this.cursor = cursor;
            this.limits = limits;
            this.dataStartRow = dataStartRow;
        }

        @Override public SourceStructure structure() { return structure; }
        @Override public List<String> warnings() { return cursor.warnings(); }

        @Override public Iterator<Row> rows() {
            if (closed) throw new IllegalStateException("dataset is closed");
            if (requested) throw new IllegalStateException("dataset rows are single-pass");
            requested = true;
            return new Iterator<>() {
                private RawExcelRow next;
                private boolean prepared;
                private boolean exhausted;
                private long record;

                @Override public boolean hasNext() {
                    ensureOpen();
                    prepare();
                    return !exhausted;
                }

                @Override public Row next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    RawExcelRow value = next;
                    next = null;
                    prepared = false;
                    List<String> normalized = padded(value.values(), structure.columns().size());
                    return new Row(++record, value.physicalLine(), normalized);
                }

                private void prepare() {
                    if (prepared || exhausted) return;
                    try {
                        for (RawExcelRow candidate; (candidate = cursor.nextRow()) != null;) {
                            if (candidate.physicalLine() < dataStartRow || candidate.isBlank()) continue;
                            if (candidate.values().size() > structure.columns().size()) {
                                throw new EngineException("IRREGULAR_ROW", "Excel row has cells beyond detected columns");
                            }
                            for (String value : candidate.values()) {
                                if (value.length() > limits.maxFieldChars()) {
                                    throw new EngineException("FIELD_LIMIT", "field exceeds configured character limit");
                                }
                            }
                            next = candidate;
                            prepared = true;
                            return;
                        }
                        exhausted = true;
                    } catch (RuntimeException exception) {
                        ExcelDataset.this.close();
                        throw exception;
                    }
                }

                private void ensureOpen() {
                    if (closed) throw new IllegalStateException("dataset is closed");
                }
            };
        }

        @Override public void close() {
            if (!closed) {
                closed = true;
                cursor.close();
            }
        }
    }
}
