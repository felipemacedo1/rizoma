package io.github.felipemacedo1.rizoma.adoption;

import static org.junit.jupiter.api.Assertions.*;

import io.github.felipemacedo1.rizoma.api.InvalidSourceException;
import io.github.felipemacedo1.rizoma.api.IncompatiblePlanException;
import io.github.felipemacedo1.rizoma.api.ProcessObserver;
import io.github.felipemacedo1.rizoma.api.ProcessRequest;
import io.github.felipemacedo1.rizoma.api.ProcessRoute;
import io.github.felipemacedo1.rizoma.api.ProcessStatus;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.api.Sources;
import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.MappingPlan;
import io.github.felipemacedo1.rizoma.core.PhysicalType;
import io.github.felipemacedo1.rizoma.core.TargetField;
import io.github.felipemacedo1.rizoma.core.TargetSchema;
import io.github.felipemacedo1.rizoma.core.Validator;
import io.github.felipemacedo1.rizoma.core.ValueTransformer;
import io.github.felipemacedo1.rizoma.examples.CustomValidatorExample;
import io.github.felipemacedo1.rizoma.examples.KnownLayoutExample;
import io.github.felipemacedo1.rizoma.examples.WorkflowControlledExample;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class PublicApiConsumerTest {
    private static final AnalysisOptions HEADER = new AnalysisOptions(Map.of("header", "first"), 42L);

    @Test void simpleCsvRequiresReviewThenConfirmedPlanRunsWithoutInference() {
        var source = Sources.from(("Nome;E-mail;Observacao\nAna;ana@example.test;sintetico\n")
                .getBytes(StandardCharsets.UTF_8), "customers.csv");
        var rizoma = Rizoma.create();

        var review = rizoma.process(ProcessRequest.builder(source, schema())
                .analysisOptions(HEADER).build());
        assertEquals(ProcessStatus.REVIEW_REQUIRED, review.status());
        assertEquals(ProcessRoute.FULL_ANALYSIS, review.route());
        assertTrue(review.reviewRequired());
        assertTrue(review.mappingSummary().suggestedMappings() > 0);
        assertTrue(review.analysis().isPresent());
        assertTrue(review.dryRun().isEmpty());

        var plan = rizoma.plan(review.analysis().orElseThrow(), schema(),
                Map.of("c0", "customer.name", "c1", "customer.email"), List.of("c2"));
        assertEquals(List.of("c2"), plan.ignoredSourceColumns());
        var processed = rizoma.process(ProcessRequest.builder(source, schema())
                .analysisOptions(HEADER).mappingPlan(plan).build());

        assertEquals(ProcessStatus.SUCCESS, processed.status());
        assertEquals(ProcessRoute.CONFIRMED_PLAN, processed.route());
        assertEquals(1, processed.validRows());
        assertTrue(processed.analysis().isEmpty());
        assertTrue(processed.canContinue());
    }

    @Test void xlsxWorksThroughTheSameTwoPublicTypes() {
        var source = Sources.from(xlsx(), "customers.xlsx");
        var result = Rizoma.create().process(ProcessRequest.builder(source, schema())
                .analysisOptions(HEADER).build());

        assertEquals(ProcessStatus.REVIEW_REQUIRED, result.status());
        assertEquals(2, result.profilingSummary().columnsProfiled());
        assertEquals(2, result.profilingSummary().rowsProcessed());
    }

    @Test void knownLayoutCreatesCurrentPlanAndUsesFastRoute() throws Exception {
        var rizoma = Rizoma.create();
        var first = Sources.from(csv("Ana", "ana@example.test"), "day-one.csv");
        var analysis = rizoma.analyze(first, schema());
        var original = rizoma.plan(analysis, schema(),
                Map.of("c0", "customer.name", "c1", "customer.email"), List.of());
        var template = rizoma.createLayoutTemplate("customers", "1", analysis, original,
                "2026-09-12T00:00:00Z", "external-consumer-test");
        var second = Sources.from(csv("Bia", "bia@example.test"), "day-two.csv");

        var result = KnownLayoutExample.process(rizoma, second, schema(), template);

        assertEquals(ProcessRoute.FAST_REUSE, result.route());
        assertEquals(ProcessStatus.SUCCESS, result.status());
        assertNotEquals(original.sourceFingerprint(), result.mappingPlan().orElseThrow().sourceFingerprint());
        assertEquals(second.sha256(), result.mappingPlan().orElseThrow().sourceFingerprint());
        assertEquals(0, result.recognition().orElseThrow().candidatePairsEvaluated());
    }

    @Test void compiledWorkflowExampleAndObserverExposeNoPipelineComposition() {
        var events = new ArrayList<ProcessObserver.ProcessEvent>();
        var rizoma = Rizoma.builder().observer(events::add).build();
        var result = WorkflowControlledExample.reviewAndDryRun(rizoma,
                Sources.from(csv("Caio", "caio@example.test"), "customers.csv"), schema());

        assertEquals(ProcessStatus.SUCCESS, result.status());
        assertTrue(events.stream().anyMatch(event ->
                event.stage() == ProcessObserver.Stage.ROUTE_SELECTED));
        assertTrue(events.stream().allMatch(event -> event.metadata().values().stream()
                .noneMatch(value -> value.contains("caio@example.test"))));
    }

    @Test void customTransformerAndValidatorUseTheExtensionApi() {
        AtomicReference<String> observed = new AtomicReference<>();
        var rizoma = CustomValidatorExample.with(
                new UppercaseTransformer(), new StartsWithOkValidator(observed));
        var source = Sources.from("Code\nok-1\n".getBytes(StandardCharsets.UTF_8), "codes.csv");
        var customSchema = TargetSchema.builder("codes").field(
                TargetField.builder("code").name("Code").required().build()).build();
        var singleColumn = new AnalysisOptions(Map.of("header", "first", "delimiter", ","), 42L);
        var analysis = rizoma.analyze(new io.github.felipemacedo1.rizoma.core.AnalysisRequest(
                source, customSchema, singleColumn));
        var plan = rizoma.plan(analysis, customSchema, Map.of("c0", "code"), List.of());
        plan = rizoma.configurePlan(plan, "code",
                List.of(new MappingPlan.Step("example:uppercase")),
                List.of(new MappingPlan.Step("example:starts-ok")), "extension test");

        var result = rizoma.process(ProcessRequest.builder(source, customSchema)
                .analysisOptions(singleColumn).mappingPlan(plan).build());

        assertEquals(ProcessStatus.SUCCESS, result.status());
        assertEquals("OK-1", observed.get());
    }

    @Test void streamMaterializationIsExplicitAndTechnicalFailuresArePublic() {
        var stream = new ByteArrayInputStream(csv("Ana", "ana@example.test"));
        var source = Sources.from(stream, "customers.csv", 1024);
        assertEquals(1, Rizoma.create().process(ProcessRequest.builder(source, schema())
                .analysisOptions(HEADER).build()).profilingSummary().rowsProcessed());

        var unsupported = Sources.from(new byte[] {1, 2, 3}, "payload.bin");
        var failed = Rizoma.create().process(ProcessRequest.of(unsupported, schema()));
        assertEquals(ProcessStatus.FAILED, failed.status());
        assertEquals("UNSUPPORTED_SOURCE", failed.errors().getFirst().code());
        assertThrows(InvalidSourceException.class, () -> Rizoma.create().analyze(unsupported, schema()));
        assertThrows(io.github.felipemacedo1.rizoma.api.InvalidSourceException.class,
                () -> Sources.from(new ByteArrayInputStream(new byte[4]), "large.csv", 3));
        assertThrows(InvalidSourceException.class, () -> Sources.from((byte[]) null, "null.csv"));
        assertThrows(InvalidSourceException.class,
                () -> Sources.from((java.io.InputStream) null, "null.csv"));
        assertThrows(InvalidSourceException.class, () -> Sources.from(new byte[0], "empty.csv", 0));
    }

    @Test void invalidDataIsAResultRatherThanATechnicalException() {
        var source = Sources.from(csv("Ana", "not-an-email"), "invalid.csv");
        var rizoma = Rizoma.create();
        var analysis = rizoma.analyze(new io.github.felipemacedo1.rizoma.core.AnalysisRequest(source, schema(), HEADER));
        var plan = rizoma.plan(analysis, schema(),
                Map.of("c0", "customer.name", "c1", "customer.email"), List.of());

        var result = rizoma.process(ProcessRequest.builder(source, schema())
                .analysisOptions(HEADER).mappingPlan(plan).build());

        assertEquals(ProcessStatus.INVALID, result.status());
        assertEquals(1, result.invalidRows());
        assertEquals("REGEX_MISMATCH", result.errors().getFirst().code());
    }

    @Test void incompatibleHighLevelRecipeUsesThePublicExceptionHierarchy() {
        var source = Sources.from(csv("Ana", "ana@example.test"), "customers.csv");
        var rizoma = Rizoma.create();
        var analysis = rizoma.analyze(new io.github.felipemacedo1.rizoma.core.AnalysisRequest(source, schema(), HEADER));
        var plan = rizoma.plan(analysis, schema(),
                Map.of("c0", "customer.name", "c1", "customer.email"), List.of());
        var template = rizoma.createLayoutTemplate("customers", "1", analysis, plan,
                "2026-09-12T00:00:00Z", "external-consumer-test");

        assertThrows(IncompatiblePlanException.class, () -> ProcessRequest.builder(source, schema())
                .mappingPlan(plan).layoutTemplate(template).build());
        assertThrows(IncompatiblePlanException.class, () -> ProcessRequest.builder(source, schema())
                .mappingPlan(plan)
                .executionPreference(ProcessRequest.ExecutionPreference.ANALYZE_ONLY).build());
        assertThrows(IncompatiblePlanException.class, () -> rizoma.plan(analysis, schema(),
                Map.of("missing", "customer.name"), List.of()));

        var changed = Sources.from(csv("Bia", "bia@example.test"), "changed.csv");
        var rejected = rizoma.process(ProcessRequest.builder(changed, schema())
                .analysisOptions(HEADER).mappingPlan(plan).build());
        assertEquals(ProcessStatus.FAILED, rejected.status());
        assertEquals("INVALIDATE_PLAN", rejected.errors().getFirst().code());
    }

    private static TargetSchema schema() {
        return TargetSchema.builder("customer").version("1").locale("pt-BR")
                .field(TargetField.builder("customer.name").name("Nome")
                        .aliases("Nome Completo").required().build())
                .field(TargetField.builder("customer.email").name("E-mail")
                        .semanticType("core:email").required().build())
                .build();
    }

    private static byte[] csv(String name, String email) {
        return ("Nome;E-mail\n" + name + ';' + email + '\n').getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xlsx() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                entry(zip, "[Content_Types].xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        </Types>
                        """);
                entry(zip, "_rels/.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """);
                entry(zip, "xl/workbook.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <sheets><sheet name="Customers" sheetId="1" r:id="rId1"/></sheets>
                        </workbook>
                        """);
                entry(zip, "xl/_rels/workbook.xml.rels", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                        </Relationships>
                        """);
                entry(zip, "xl/worksheets/sheet1.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                          <sheetData>
                            <row r="1"><c r="A1" t="inlineStr"><is><t>Nome</t></is></c><c r="B1" t="inlineStr"><is><t>E-mail</t></is></c></row>
                            <row r="2"><c r="A2" t="inlineStr"><is><t>Ana</t></is></c><c r="B2" t="inlineStr"><is><t>ana@example.test</t></is></c></row>
                            <row r="3"><c r="A3" t="inlineStr"><is><t>Bia</t></is></c><c r="B3" t="inlineStr"><is><t>bia@example.test</t></is></c></row>
                          </sheetData>
                        </worksheet>
                        """);
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final class UppercaseTransformer implements ValueTransformer<String, String> {
        @Override public String id() { return "example:uppercase"; }
        @Override public String version() { return "1"; }
        @Override public Class<String> sourceType() { return String.class; }
        @Override public Class<String> targetType() { return String.class; }
        @Override public TransformationResult<String> transform(String value, TransformationContext context) {
            return TransformationResult.success(value, value.toUpperCase(java.util.Locale.ROOT), id(), version());
        }
    }

    private static final class StartsWithOkValidator implements Validator<String> {
        private final AtomicReference<String> observed;
        private StartsWithOkValidator(AtomicReference<String> observed) { this.observed = observed; }
        @Override public String id() { return "example:starts-ok"; }
        @Override public String version() { return "1"; }
        @Override public Class<String> valueType() { return String.class; }
        @Override public ValidationResult validate(String value, ValidationContext context) {
            observed.set(value);
            return value.startsWith("OK") ? ValidationResult.pass(id(), version())
                    : ValidationResult.failure(id(), version(), "NOT_OK", "value must start with OK");
        }
    }
}
