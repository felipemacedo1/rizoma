package io.github.rizoma.csv;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.DataReader;
import io.github.rizoma.core.Dataset;
import io.github.rizoma.core.EngineException;
import io.github.rizoma.core.EngineLimits;
import io.github.rizoma.core.HeaderNormalizer;
import io.github.rizoma.core.TabularSource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

/** Streaming RFC-style CSV adapter backed by Apache Commons CSV. */
public final class CsvDataReader implements DataReader {
    private static final List<Character> DELIMITERS = List.of(',', ';', '\t', '|');
    private static final Set<String> CHARSETS = Set.of("UTF-8", "ISO-8859-1", "WINDOWS-1252");
    private static final HeaderNormalizer NORMALIZER = new HeaderNormalizer(java.util.Map.of());

    @Override public boolean supports(TabularSource source) {
        return source.fileName().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override public SourceStructure detect(TabularSource source, AnalysisOptions options, EngineLimits limits) {
        validateOptions(options);
        Charset charset = charset(options);
        Character delimiter = explicitDelimiter(options);
        var warnings = new ArrayList<String>();
        if (delimiter == null) delimiter = isEmpty(source) ? ',' : detectDelimiter(source, charset, limits);
        List<List<String>> preview = preview(source, charset, delimiter, limits, 20);
        if (preview.isEmpty()) {
            warnings.add("empty CSV source");
            return new SourceStructure("CSV", charset.name(), printable(delimiter), false, List.of(), warnings);
        }
        boolean header = detectHeader(preview, options, warnings);
        List<String> names = header ? preview.get(0) : synthetic(preview.get(0).size());
        if (names.size() > limits.maxColumns()) throw new EngineException("COLUMN_LIMIT", "source exceeds configured column limit");
        var columns = new ArrayList<SourceColumn>();
        var counts = new HashMap<String, Integer>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i) == null ? "" : names.get(i);
            if (name.length() > limits.maxHeaderChars()) throw new EngineException("HEADER_LIMIT", "header exceeds configured character limit");
            if (name.isBlank()) warnings.add("empty header at column c" + i);
            int count = counts.merge(name, 1, Integer::sum);
            if (count == 2) warnings.add("duplicate header: " + safeHeader(name));
            columns.add(new SourceColumn("c" + i, i, name, NORMALIZER.normalize(name).comparable()));
        }
        return new SourceStructure("CSV", charset.name(), printable(delimiter), header, columns, warnings);
    }

    @Override public Dataset open(TabularSource source, SourceStructure structure,
                                  AnalysisOptions options, EngineLimits limits) {
        validateOptions(options);
        try {
            Charset charset = Charset.forName(structure.charset());
            char delimiter = parseDelimiter(structure.delimiter());
            Reader reader = newReader(source, charset, limits.maxBytes());
            CSVParser parser = format(delimiter).parse(reader);
            return new CsvDataset(structure, parser, limits);
        } catch (IOException e) {
            throw new EngineException("CSV_OPEN_FAILED", "CSV source could not be opened", e);
        }
    }

    private Character detectDelimiter(TabularSource source, Charset charset, EngineLimits limits) {
        Character best = null; int bestWidth = 0; double bestConsistency = -1;
        for (char candidate : DELIMITERS) {
            List<List<String>> rows;
            try { rows = preview(source, charset, candidate, limits, 12); }
            catch (EngineException malformedForCandidate) { continue; }
            if (rows.isEmpty()) continue;
            var frequencies = new HashMap<Integer, Integer>();
            rows.forEach(row -> frequencies.merge(row.size(), 1, Integer::sum));
            var mode = frequencies.entrySet().stream().max(java.util.Map.Entry.<Integer, Integer>comparingByValue()
                    .thenComparing(java.util.Map.Entry.comparingByKey())).orElseThrow();
            double consistency = (double) mode.getValue() / rows.size();
            if (mode.getKey() > 1 && (consistency > bestConsistency
                    || consistency == bestConsistency && mode.getKey() > bestWidth)) {
                best = candidate; bestWidth = mode.getKey(); bestConsistency = consistency;
            }
        }
        if (best == null) throw new EngineException("CSV_DELIMITER_AMBIGUOUS",
                "delimiter could not be detected; provide reader option delimiter");
        return best;
    }

    private List<List<String>> preview(TabularSource source, Charset charset, char delimiter,
                                       EngineLimits limits, int maximum) {
        try (Reader reader = newReader(source, charset, limits.maxBytes());
             CSVParser parser = format(delimiter).parse(reader)) {
            var rows = new ArrayList<List<String>>();
            for (CSVRecord record : parser) {
                if (rows.size() == maximum) break;
                rows.add(record.stream().toList());
            }
            return rows;
        } catch (IOException | UncheckedIOException e) {
            throw new EngineException("CSV_DETECTION_FAILED", "CSV structure detection failed", e);
        }
    }

    private static boolean detectHeader(List<List<String>> rows, AnalysisOptions options, List<String> warnings) {
        String override = options.readerOptions().getOrDefault("header", "detect").toLowerCase(Locale.ROOT);
        if (override.equals("first")) return true;
        if (override.equals("none")) return false;
        if (!override.equals("detect")) throw new EngineException("INVALID_READER_OPTION", "header must be detect, first or none");
        List<String> first = rows.get(0);
        boolean unique = new HashSet<>(first).size() == first.size();
        boolean nonblank = first.stream().noneMatch(String::isBlank);
        boolean textual = first.stream().allMatch(v -> !v.strip().matches("[-+]?\\d+(?:[.,]\\d+)?"));
        if (rows.size() == 1) warnings.add("header detection is ambiguous for a single record; selected first row");
        boolean header = unique && nonblank && textual;
        if (!header) warnings.add("header was not confidently detected; synthetic column names used");
        return header;
    }

    private static CSVFormat format(char delimiter) {
        return CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(false).get();
    }

    private static Reader newReader(TabularSource source, Charset charset, long maxBytes) throws IOException {
        InputStream limited = new LimitedInputStream(source.openStream(), maxBytes);
        BOMInputStream bom = BOMInputStream.builder().setInputStream(limited).setInclude(false)
                .setByteOrderMarks(ByteOrderMark.UTF_8).get();
        return new InputStreamReader(bom, charset);
    }

    private static Charset charset(AnalysisOptions options) {
        String name = options.readerOptions().getOrDefault("charset", StandardCharsets.UTF_8.name()).toUpperCase(Locale.ROOT);
        if (!CHARSETS.contains(name)) throw new EngineException("UNSUPPORTED_CHARSET", "supported charsets: " + CHARSETS);
        return Charset.forName(name);
    }

    private static Character explicitDelimiter(AnalysisOptions options) {
        String value = options.readerOptions().get("delimiter");
        if (value == null) return null;
        if (value.equals("\\t")) return '\t';
        if (value.length() != 1 || !DELIMITERS.contains(value.charAt(0)))
            throw new EngineException("INVALID_READER_OPTION", "delimiter must be comma, semicolon, tab or pipe");
        return value.charAt(0);
    }

    private static char parseDelimiter(String value) { return value.equals("\\t") ? '\t' : value.charAt(0); }
    private static String printable(char delimiter) { return delimiter == '\t' ? "\\t" : String.valueOf(delimiter); }
    private static List<String> synthetic(int size) {
        var names = new ArrayList<String>(); for (int i = 0; i < size; i++) names.add("column_" + (i + 1)); return names;
    }
    private static String safeHeader(String value) { return value.isBlank() ? "<empty>" : "<redacted:length=" + value.length() + ">"; }
    private static boolean isEmpty(TabularSource source) {
        try { return source.size() == 0; }
        catch (IOException e) { throw new EngineException("CSV_DETECTION_FAILED", "CSV source size could not be read", e); }
    }

    private static void validateOptions(AnalysisOptions options) {
        Set<String> supported = Set.of("charset", "delimiter", "header");
        for (String option : options.readerOptions().keySet()) {
            if (!supported.contains(option)) {
                throw new EngineException("INVALID_READER_OPTION", "reader option is not supported for CSV: " + option);
            }
        }
    }

    private static final class CsvDataset implements Dataset {
        private final SourceStructure structure; private final CSVParser parser; private final EngineLimits limits;
        private final List<String> warnings = new ArrayList<>(); private boolean requested; private boolean closed;
        CsvDataset(SourceStructure structure, CSVParser parser, EngineLimits limits) {
            this.structure = structure; this.parser = parser; this.limits = limits;
        }
        @Override public SourceStructure structure() { return structure; }
        @Override public List<String> warnings() { return List.copyOf(warnings); }

        @Override public Iterator<Row> rows() {
            if (closed) throw new IllegalStateException("dataset is closed");
            if (requested) throw new IllegalStateException("dataset rows are single-pass");
            requested = true;
            Iterator<CSVRecord> delegate = parser.iterator();
            if (structure.headerPresent() && delegate.hasNext()) delegate.next();
            return new Iterator<>() {
                @Override public boolean hasNext() { ensureOpen(); return delegate.hasNext(); }
                @Override public Row next() {
                    ensureOpen();
                    if (!delegate.hasNext()) throw new NoSuchElementException();
                    try {
                        CSVRecord record = delegate.next();
                        if (record.size() != structure.columns().size()) {
                            throw new EngineException("IRREGULAR_ROW", "CSV record has a different column count");
                        }
                        var values = record.stream().toList();
                        for (String value : values) if (value.length() > limits.maxFieldChars())
                            throw new EngineException("FIELD_LIMIT", "field exceeds configured character limit");
                        return new Row(record.getRecordNumber(), parser.getCurrentLineNumber(), values);
                    } catch (UncheckedIOException e) {
                        throw new EngineException("CSV_READ_FAILED", "CSV record could not be read", e);
                    }
                }
                private void ensureOpen() { if (closed) throw new IllegalStateException("dataset is closed"); }
            };
        }
        @Override public void close() {
            if (!closed) { closed = true; try { parser.close(); } catch (IOException e) {
                throw new EngineException("CSV_CLOSE_FAILED", "CSV resource could not be closed", e); } }
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximum; private long consumed;
        LimitedInputStream(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        @Override public int read() throws IOException { int value = super.read(); if (value >= 0) count(1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, (int) Math.min(length, maximum - consumed + 1));
            if (read > 0) count(read); return read;
        }
        private void count(long amount) throws IOException {
            consumed += amount; if (consumed > maximum) throw new IOException("configured byte limit exceeded");
        }
    }
}
