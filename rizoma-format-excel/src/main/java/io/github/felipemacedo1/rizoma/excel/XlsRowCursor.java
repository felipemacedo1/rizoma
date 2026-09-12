package io.github.felipemacedo1.rizoma.excel;

import io.github.felipemacedo1.rizoma.core.EngineException;
import io.github.felipemacedo1.rizoma.core.EngineLimits;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

final class XlsRowCursor implements ExcelRowCursor {
    private final InputStream sourceInput;
    private final POIFSFileSystem fileSystem;
    private final HSSFWorkbook workbook;
    private final Iterator<Row> rows;
    private final FormulaPolicy formulaPolicy;
    private final EngineLimits limits;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);
    private final List<String> warnings = new ArrayList<>();
    private boolean closed;

    XlsRowCursor(TabularSource source, int sheetIndex, FormulaPolicy formulaPolicy,
                 EngineLimits limits) {
        InputStream openedSource = null;
        POIFSFileSystem openedFileSystem = null;
        HSSFWorkbook openedWorkbook = null;
        try {
            openedSource = source.openStream();
            openedFileSystem = new POIFSFileSystem(openedSource);
            if (containsMacro(openedFileSystem.getRoot())) {
                throw new EngineException("EXCEL_MACRO_NOT_SUPPORTED", "macro-enabled workbooks are not supported");
            }
            openedWorkbook = new HSSFWorkbook(openedFileSystem);
            if (sheetIndex < 0 || sheetIndex >= openedWorkbook.getNumberOfSheets()) {
                throw new EngineException("EXCEL_SHEET_NOT_FOUND", "selected sheet does not exist");
            }
            this.sourceInput = openedSource;
            this.fileSystem = openedFileSystem;
            this.workbook = openedWorkbook;
            this.rows = openedWorkbook.getSheetAt(sheetIndex).rowIterator();
            this.formulaPolicy = formulaPolicy;
            this.limits = limits;
            formatter.setUseCachedValuesForFormulaCells(true);
            formatter.setUse4DigitYearsInAllDateFormats(true);
        } catch (Exception exception) {
            closeQuietly(openedWorkbook);
            closeQuietly(openedFileSystem);
            closeQuietly(openedSource);
            if (exception instanceof EngineException engineException) throw engineException;
            throw new EngineException("XLS_OPEN_FAILED", "XLS source could not be opened", exception);
        }
    }

    static List<String> sheetNames(TabularSource source, int maxSheets) {
        try (InputStream input = source.openStream(); POIFSFileSystem fileSystem = new POIFSFileSystem(input)) {
            if (containsMacro(fileSystem.getRoot())) {
                throw new EngineException("EXCEL_MACRO_NOT_SUPPORTED", "macro-enabled workbooks are not supported");
            }
            try (HSSFWorkbook workbook = new HSSFWorkbook(fileSystem)) {
                if (workbook.getNumberOfSheets() > maxSheets) {
                    throw new EngineException("EXCEL_SHEET_LIMIT", "workbook exceeds configured sheet limit");
                }
                var names = new ArrayList<String>();
                for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                    names.add(workbook.getSheetName(index));
                }
                return List.copyOf(names);
            }
        } catch (EngineException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EngineException("XLS_OPEN_FAILED", "XLS workbook metadata could not be read", exception);
        }
    }

    @Override public RawExcelRow nextRow() {
        ensureOpen();
        if (!rows.hasNext()) return null;
        try {
            Row row = rows.next();
            int width = Math.max(0, row.getLastCellNum());
            if (width > limits.maxColumns()) {
                throw new EngineException("COLUMN_LIMIT", "worksheet exceeds configured column limit");
            }
            var values = new ArrayList<String>(width);
            for (int column = 0; column < width; column++) {
                Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                values.add(cell == null ? "" : value(cell, row.getRowNum() + 1L, column));
            }
            return new RawExcelRow(row.getRowNum() + 1L, trimTrailingBlanks(values));
        } catch (EngineException exception) {
            close();
            throw exception;
        } catch (RuntimeException exception) {
            close();
            throw new EngineException("XLS_READ_FAILED", "XLS worksheet could not be read", exception);
        }
    }

    @Override public List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Exception failure = null;
        try { workbook.close(); } catch (Exception exception) { failure = exception; }
        try { fileSystem.close(); } catch (Exception exception) { if (failure == null) failure = exception; }
        try { sourceInput.close(); } catch (Exception exception) { if (failure == null) failure = exception; }
        if (failure != null) throw new EngineException("XLS_CLOSE_FAILED", "XLS resource could not be closed", failure);
    }

    private String value(Cell cell, long row, int column) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            if (formulaPolicy == FormulaPolicy.REJECT) {
                throw new EngineException("EXCEL_FORMULA_REJECTED", "workbook contains a formula cell");
            }
            if (formulaPolicy == FormulaPolicy.EXPRESSION) return checked("=" + cell.getCellFormula());
            type = cell.getCachedFormulaResultType();
        }
        String value = switch (type) {
            case BLANK, _NONE -> "";
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case STRING -> cell.getRichStringCellValue().getString();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? formatDate(cell.getLocalDateTimeCellValue()) : formatter.formatCellValue(cell);
            case ERROR -> {
                addWarning("error cell at row " + row + " column c" + column + " treated as blank");
                yield "";
            }
            case FORMULA -> throw new IllegalStateException("formula cache type was not resolved");
        };
        return checked(value);
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

    private static boolean containsMacro(DirectoryEntry directory) {
        for (Entry entry : directory) {
            String name = entry.getName().toUpperCase(Locale.ROOT);
            if (name.contains("VBA") || name.equals("_VBA_PROJECT_CUR")) return true;
            if (entry.isDirectoryEntry() && containsMacro((DirectoryEntry) entry)) return true;
        }
        return false;
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
}
