package io.github.felipemacedo1.rizoma.examples;

import io.github.felipemacedo1.rizoma.api.ProcessRequest;
import io.github.felipemacedo1.rizoma.api.ProcessResult;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.core.PhysicalType;
import io.github.felipemacedo1.rizoma.core.TargetField;
import io.github.felipemacedo1.rizoma.core.TargetSchema;
import java.nio.file.Path;

/** Minimal five-minute example for an application that has a local spreadsheet. */
public final class SimpleProcessingExample {
    private SimpleProcessingExample() {}

    /** Processes a CSV, XLS or XLSX and returns review suggestions or dry-run counts. */
    public static ProcessResult process(Path file) {
        TargetSchema schema = TargetSchema.builder("customer")
                .version("1").locale("pt-BR")
                .field(TargetField.builder("customer.name").name("Nome")
                        .aliases("Nome Completo").required().build())
                .field(TargetField.builder("customer.email").name("E-mail")
                        .type(PhysicalType.TEXT).semanticType("core:email").build())
                .build();
        return Rizoma.create().process(ProcessRequest.of(file, schema));
    }
}
