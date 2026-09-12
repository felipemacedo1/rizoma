package io.github.felipemacedo1.rizoma.examples;

import io.github.felipemacedo1.rizoma.api.ProcessRequest;
import io.github.felipemacedo1.rizoma.api.ProcessStatus;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.api.Sources;
import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.AnalysisRequest;
import io.github.felipemacedo1.rizoma.core.DryRunOptions;
import io.github.felipemacedo1.rizoma.core.DryRunRequest;
import io.github.felipemacedo1.rizoma.core.InMemoryLayoutRegistry;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionRequest;
import io.github.felipemacedo1.rizoma.core.PhysicalType;
import io.github.felipemacedo1.rizoma.core.TargetField;
import io.github.felipemacedo1.rizoma.core.TargetSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * End-to-end smoke probe comparing detailed workflow calls with the 0.6 facade.
 * Timings include file I/O and are observations, not benchmark assertions.
 */
public final class ApiOverheadProbe {
    private ApiOverheadProbe() {}

    /** Runs the probe against a synthetic CSV path supplied by the caller. */
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("expected one synthetic CSV path");
        var source = Sources.from(Path.of(args[0]));
        var schema = schema();
        var options = new AnalysisOptions(Map.of("header", "first", "delimiter", ";"), 42L);
        var dryOptions = DryRunOptions.defaults();
        var rizoma = Rizoma.create();

        long started = System.nanoTime();
        var workflowAnalysis = rizoma.analyze(new AnalysisRequest(source, schema, options));
        long workflowAnalyzeNanos = System.nanoTime() - started;
        var plan = rizoma.plan(workflowAnalysis, schema,
                Map.of("c0", "customer.code", "c1", "customer.email",
                        "c2", "customer.birthDate"), List.of());

        started = System.nanoTime();
        var workflowDry = rizoma.dryRun(new DryRunRequest(source, schema, plan, options, dryOptions));
        long workflowDryNanos = System.nanoTime() - started;

        started = System.nanoTime();
        var simpleDry = rizoma.process(ProcessRequest.builder(source, schema)
                .analysisOptions(options).dryRunOptions(dryOptions).mappingPlan(plan).build());
        long simpleDryNanos = System.nanoTime() - started;

        var template = rizoma.createLayoutTemplate("api-overhead", "1", workflowAnalysis, plan,
                "2026-09-12T00:00:00Z", "synthetic API overhead probe");
        var registry = new InMemoryLayoutRegistry(1);
        registry.register(template);

        started = System.nanoTime();
        var workflowRecognition = rizoma.recognizeLayout(
                new LayoutRecognitionRequest(source, schema, options, registry));
        var workflowFastDry = rizoma.dryRun(new DryRunRequest(source, schema,
                workflowRecognition.mappingPlan(), options, dryOptions));
        long workflowFastNanos = System.nanoTime() - started;

        started = System.nanoTime();
        var simpleFast = rizoma.process(ProcessRequest.builder(source, schema)
                .analysisOptions(options).dryRunOptions(dryOptions).layoutTemplate(template).build());
        long simpleFastNanos = System.nanoTime() - started;

        started = System.nanoTime();
        var simpleAnalysis = rizoma.process(ProcessRequest.builder(source, schema)
                .analysisOptions(options)
                .executionPreference(ProcessRequest.ExecutionPreference.ANALYZE_ONLY).build());
        long simpleAnalyzeNanos = System.nanoTime() - started;

        require(workflowDry.rowsProcessed() == workflowAnalysis.rowsProcessed(), "workflow dry rows");
        require(simpleDry.status() == ProcessStatus.SUCCESS, "simple confirmed plan status");
        require(simpleDry.validRows() == workflowDry.rowsValid(), "simple confirmed plan rows");
        require(workflowFastDry.rowsValid() == workflowDry.rowsValid(), "workflow fast rows");
        require(simpleFast.status() == ProcessStatus.SUCCESS, "simple fast status");
        require(simpleFast.validRows() == workflowFastDry.rowsValid(), "simple fast rows");
        require(simpleAnalysis.status() == ProcessStatus.REVIEW_REQUIRED, "simple analysis status");
        require(simpleAnalysis.profilingSummary().rowsProcessed() == workflowAnalysis.rowsProcessed(),
                "simple analysis rows");

        System.out.printf("rows=%d workflowAnalyzeMs=%d simpleAnalyzeMs=%d "
                        + "workflowDryMs=%d simpleDryMs=%d workflowFastMs=%d simpleFastMs=%d%n",
                workflowAnalysis.rowsProcessed(), millis(workflowAnalyzeNanos), millis(simpleAnalyzeNanos),
                millis(workflowDryNanos), millis(simpleDryNanos),
                millis(workflowFastNanos), millis(simpleFastNanos));
    }

    private static TargetSchema schema() {
        return TargetSchema.builder("volume").version("1").locale("en-US")
                .field(TargetField.builder("customer.code").name("Codigo Cliente")
                        .type(PhysicalType.TEXT).required().build())
                .field(TargetField.builder("customer.email").name("E-mail")
                        .type(PhysicalType.TEXT).semanticType("core:email").required().build())
                .field(TargetField.builder("customer.birthDate").name("Nascimento")
                        .type(PhysicalType.DATE).semanticType("core:date").required().build())
                .build();
    }

    private static long millis(long nanos) { return nanos / 1_000_000L; }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("probe assertion failed: " + label);
    }
}
