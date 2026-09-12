package io.github.felipemacedo1.rizoma.excel;

import java.util.List;

interface ExcelRowCursor extends AutoCloseable {
    RawExcelRow nextRow();
    List<String> warnings();
    @Override void close();
}
