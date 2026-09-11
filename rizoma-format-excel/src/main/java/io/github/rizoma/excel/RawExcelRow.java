package io.github.rizoma.excel;

import java.util.List;

record RawExcelRow(long physicalLine, List<String> values) {
    RawExcelRow {
        values = List.copyOf(values);
    }

    boolean isBlank() {
        return values.stream().allMatch(String::isBlank);
    }
}
