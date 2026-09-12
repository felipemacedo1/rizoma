package io.github.felipemacedo1.rizoma.examples;

import io.github.felipemacedo1.rizoma.api.ProcessRequest;
import io.github.felipemacedo1.rizoma.api.ProcessResult;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.core.LayoutTemplate;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import io.github.felipemacedo1.rizoma.core.TargetSchema;

/** Applies an explicitly confirmed layout template to a new source. */
public final class KnownLayoutExample {
    private KnownLayoutExample() {}

    /** Recognizes the current source and runs only when the template is safe. */
    public static ProcessResult process(Rizoma rizoma, TabularSource currentSource,
            TargetSchema schema, LayoutTemplate template) {
        return rizoma.process(ProcessRequest.builder(currentSource, schema)
                .layoutTemplate(template).build());
    }
}
