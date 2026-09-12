package io.github.felipemacedo1.rizoma.excel;

import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.EngineException;
import io.github.felipemacedo1.rizoma.core.EngineLimits;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

final class ExcelSecurity {
    private static final byte[] OLE2 = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};
    private static final long DEFAULT_MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final long DEFAULT_MAX_ZIP_ENTRY_BYTES = 128L * 1024 * 1024;
    private static final long DEFAULT_MAX_LEGACY_BYTES = 20L * 1024 * 1024;
    private static final int DEFAULT_MAX_ZIP_ENTRIES = 10_000;
    private static final int DEFAULT_MAX_SHEETS = 100;
    private static final double DEFAULT_MIN_INFLATE_RATIO = 0.01;

    private ExcelSecurity() {}

    enum Kind { XLSX, XLS, UNKNOWN }

    record Policy(long maxExpandedBytes, long maxZipEntryBytes, long maxLegacyBytes,
                  int maxZipEntries, int maxSheets, double minInflateRatio) {
        static Policy from(AnalysisOptions options, EngineLimits engineLimits) {
            long expanded = positiveLong(options, "maxExpandedBytes", DEFAULT_MAX_EXPANDED_BYTES);
            long entry = positiveLong(options, "maxZipEntryBytes", DEFAULT_MAX_ZIP_ENTRY_BYTES);
            long legacy = positiveLong(options, "maxLegacyBytes",
                    Math.min(DEFAULT_MAX_LEGACY_BYTES, engineLimits.maxBytes()));
            int entries = positiveInt(options, "maxZipEntries", DEFAULT_MAX_ZIP_ENTRIES);
            int sheets = positiveInt(options, "maxSheets", DEFAULT_MAX_SHEETS);
            double ratio = unitDouble(options, "minInflateRatio", DEFAULT_MIN_INFLATE_RATIO);
            return new Policy(expanded, entry, legacy, entries, sheets, ratio);
        }
    }

    static Kind kind(TabularSource source) {
        try (InputStream input = source.openStream()) {
            byte[] magic = input.readNBytes(8);
            if (magic.length >= OLE2.length && java.util.Arrays.equals(magic, OLE2)) return Kind.XLS;
            if (magic.length >= 4 && magic[0] == 'P' && magic[1] == 'K'
                    && (magic[2] == 3 && magic[3] == 4 || magic[2] == 5 && magic[3] == 6
                    || magic[2] == 7 && magic[3] == 8)) return Kind.XLSX;
            return Kind.UNKNOWN;
        } catch (IOException exception) {
            return Kind.UNKNOWN;
        }
    }

    static void checkSourceSize(TabularSource source, EngineLimits limits, Kind kind, Policy policy) {
        try {
            long size = source.size();
            if (size > limits.maxBytes()) {
                throw new EngineException("SOURCE_TOO_LARGE", "source exceeds configured byte limit");
            }
            if (kind == Kind.XLS && size > policy.maxLegacyBytes()) {
                throw new EngineException("XLS_LEGACY_SIZE_LIMIT",
                        "legacy XLS exceeds its configured in-memory safety limit");
            }
        } catch (IOException exception) {
            throw new EngineException("EXCEL_OPEN_FAILED", "Excel source size could not be read", exception);
        }
    }

    static List<String> inspectXlsx(TabularSource source, Policy policy) {
        int entries = 0;
        long total = 0;
        int unknownRatios = 0;
        boolean contentTypes = false;
        boolean workbook = false;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipArchiveInputStream(new BufferedInputStream(source.openStream()))) {
            for (ZipArchiveEntry entry; (entry = zip.getNextEntry()) != null;) {
                entries++;
                if (entries > policy.maxZipEntries()) {
                    throw new EngineException("XLSX_ZIP_ENTRY_LIMIT", "XLSX contains too many ZIP entries");
                }
                String normalized = entry.getName().replace('\\', '/');
                if (normalized.startsWith("/") || normalized.equals("..") || normalized.startsWith("../")
                        || normalized.contains("/../")) {
                    throw new EngineException("XLSX_UNSAFE_ENTRY", "XLSX contains an unsafe ZIP entry path");
                }
                String lower = normalized.toLowerCase(Locale.ROOT);
                if (entry.isUnixSymlink() || !zip.canReadEntryData(entry)) {
                    throw new EngineException("XLSX_UNSUPPORTED_ZIP_ENTRY",
                            "XLSX contains an unsupported or encrypted ZIP entry");
                }
                if (lower.equals("[content_types].xml")) contentTypes = true;
                if (lower.equals("xl/workbook.xml")) workbook = true;
                if (lower.endsWith("vbaproject.bin")) {
                    throw new EngineException("EXCEL_MACRO_NOT_SUPPORTED", "macro-enabled workbooks are not supported");
                }
                long entryBytes = 0;
                long compressedStart = zip.getCompressedCount();
                for (int read; (read = zip.read(buffer)) >= 0;) {
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        throw new EngineException("CANCELLED", "analysis was interrupted");
                    }
                    entryBytes += read;
                    total += read;
                    if (entryBytes > policy.maxZipEntryBytes()) {
                        throw new EngineException("XLSX_ZIP_ENTRY_SIZE_LIMIT", "XLSX ZIP entry exceeds configured expanded limit");
                    }
                    if (total > policy.maxExpandedBytes()) {
                        throw new EngineException("XLSX_EXPANDED_SIZE_LIMIT", "XLSX expanded data exceeds configured limit");
                    }
                }
                long compressed = entry.getCompressedSize() > 0
                        ? entry.getCompressedSize() : zip.getCompressedCount() - compressedStart;
                if (entryBytes > 0 && compressed > 0) {
                    double ratio = (double) compressed / entryBytes;
                    if (ratio < policy.minInflateRatio()) {
                        throw new EngineException("XLSX_INFLATE_RATIO_LIMIT", "XLSX compression ratio is below configured limit");
                    }
                } else if (entryBytes > 0 && compressed < 0) {
                    unknownRatios++;
                }
            }
        } catch (EngineException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new EngineException("XLSX_SECURITY_CHECK_FAILED", "XLSX security preflight failed", exception);
        }
        if (!contentTypes || !workbook) {
            throw new EngineException("EXCEL_INVALID_CONTAINER", "ZIP source is not an OOXML spreadsheet");
        }
        var warnings = new ArrayList<String>();
        if (unknownRatios > 0) {
            warnings.add("compression ratio unavailable for " + unknownRatios
                    + " ZIP entries; expanded byte limits were still enforced");
        }
        return warnings;
    }

    private static long positiveLong(AnalysisOptions options, String name, long fallback) {
        String value = options.readerOptions().get(name);
        if (value == null) return fallback;
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new EngineException("INVALID_READER_OPTION", name + " must be a positive integer");
        }
    }

    private static int positiveInt(AnalysisOptions options, String name, int fallback) {
        long parsed = positiveLong(options, name, fallback);
        if (parsed > Integer.MAX_VALUE) {
            throw new EngineException("INVALID_READER_OPTION", name + " is too large");
        }
        return (int) parsed;
    }

    private static double unitDouble(AnalysisOptions options, String name, double fallback) {
        String value = options.readerOptions().get(name);
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < 0 || parsed > 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new EngineException("INVALID_READER_OPTION", name + " must be in [0,1]");
        }
    }
}
