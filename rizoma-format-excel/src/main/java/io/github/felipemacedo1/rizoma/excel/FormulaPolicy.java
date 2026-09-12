package io.github.felipemacedo1.rizoma.excel;

import io.github.felipemacedo1.rizoma.core.EngineException;
import java.util.Locale;

enum FormulaPolicy {
    CACHED, EXPRESSION, REJECT;

    static FormulaPolicy from(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new EngineException("INVALID_READER_OPTION",
                    "formula must be cached, expression or reject");
        }
    }
}
