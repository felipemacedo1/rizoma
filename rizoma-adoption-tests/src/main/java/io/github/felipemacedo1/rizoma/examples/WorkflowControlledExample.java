package io.github.felipemacedo1.rizoma.examples;

import io.github.felipemacedo1.rizoma.api.ProcessRequest;
import io.github.felipemacedo1.rizoma.api.ProcessResult;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.core.TabularSource;
import io.github.felipemacedo1.rizoma.core.TargetSchema;
import java.util.List;
import java.util.Map;

/** Explicit review, confirmation and dry-run flow using the workflow API. */
public final class WorkflowControlledExample {
    private WorkflowControlledExample() {}

    /** Confirms two positional mappings and executes their read-only dry run. */
    public static ProcessResult reviewAndDryRun(Rizoma rizoma, TabularSource source,
            TargetSchema schema) {
        ProcessResult review = rizoma.process(ProcessRequest.of(source, schema));
        var analysis = review.analysis().orElseThrow();
        var plan = rizoma.plan(analysis, schema,
                Map.of("c0", "customer.name", "c1", "customer.email"), List.of());
        return rizoma.process(ProcessRequest.builder(source, schema).mappingPlan(plan).build());
    }
}
