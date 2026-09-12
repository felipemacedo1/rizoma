package io.github.felipemacedo1.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LayoutAndProjectionTest {
    private static final AnalysisOptions OPTIONS = new AnalysisOptions(Map.of("header", "first"), 42L);

    @Test void knownLayoutCreatesNewSourceBoundPlanAndDryRunExecutesProjection() {
        Scenario dayOne = scenario("day-1.csv", headers(), List.of(
                List.of("Produto A", "10", "25,00", "interno", "azul")));
        AnalysisResult analysis = dayOne.engine.analyze(new AnalysisRequest(dayOne.source, schema(), OPTIONS));
        MappingPlan original = projectedPlan(analysis);
        LayoutTemplate template = LayoutTemplate.create("product-import", "1", analysis, original,
                "2026-09-12T00:00:00Z", "explicit-test-confirmation", new HeaderNormalizer(Map.of()));
        assertNotEquals(template.templateId(), LayoutTemplate.create("product-import", "1", analysis,
                original, "2026-09-12T00:00:01Z", "explicit-test-confirmation",
                new HeaderNormalizer(Map.of())).templateId());
        var registry = new InMemoryLayoutRegistry(); registry.register(template);

        Scenario dayTwo = scenario("day-2.csv", headers(), List.of(
                List.of("Produto B", "2", "7,50", "novo", "verde")));
        LayoutRecognitionResult recognized = dayTwo.engine.recognizeLayout(new LayoutRecognitionRequest(
                dayTwo.source, schema(), OPTIONS, registry));

        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FAST_REUSE, recognized.route());
        assertEquals(LayoutCompatibilityReport.Classification.EXACT, recognized.classification());
        assertTrue(recognized.inferenceSkipped());
        assertEquals(0, recognized.candidatePairsEvaluated());
        assertEquals(0, recognized.similarityMetricsExecuted());
        assertNotNull(recognized.mappingPlan());
        assertNotEquals(original.sourceFingerprint(), recognized.mappingPlan().sourceFingerprint());
        assertEquals(dayTwo.source.sha256(), recognized.mappingPlan().sourceFingerprint());
        assertEquals(template.templateId(), recognized.mappingPlan().layoutTemplateId());
        assertEquals(List.of("c3", "c4"), recognized.mappingPlan().ignoredSourceColumns());
        assertTrue(recognized.mappingPlan().unmappedSourceColumns().isEmpty());

        DryRunResult dryRun = dayTwo.engine.dryRun(new DryRunRequest(dayTwo.source, schema(),
                recognized.mappingPlan(), OPTIONS, DryRunOptions.defaults()));
        assertEquals(1, dryRun.rowsValid());
        assertEquals(5, dryRun.fieldsMapped());
        assertTrue(dryRun.transformationsApplied() >= 4,
                "numeric conversions plus derived multiply must execute");
    }

    @Test void reorderUsesFastPathWhileRenameAndAdditionUseOnlyAdaptiveChecks() {
        Scenario first = scenario("first.csv", headers(), rows());
        AnalysisResult analysis = first.engine.analyze(new AnalysisRequest(first.source, schema(), OPTIONS));
        MappingPlan plan = projectedPlan(analysis);
        LayoutTemplate template = LayoutTemplate.create("products", "1", analysis, plan,
                "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of()));
        var registry = new InMemoryLayoutRegistry(); registry.register(template);

        Scenario reordered = scenario("reordered.csv",
                List.of("Valor Unitário", "Produto", "Quantidade", "Comentário", "Cor da Linha"),
                List.of(List.of("25,00", "Produto B", "3", "x", "y")));
        LayoutRecognitionResult compatible = reordered.engine.recognizeLayout(
                new LayoutRecognitionRequest(reordered.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FAST_REUSE, compatible.route());
        assertEquals(LayoutCompatibilityReport.Classification.COMPATIBLE, compatible.classification());
        assertTrue(compatible.compatibility().drift().stream().allMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.REORDERED_COLUMN));
        assertEquals("c1", compatible.mappingPlan().mappings().stream()
                .filter(item -> item.targetFieldId().equals("product.name")).findFirst().orElseThrow()
                .projectionSource().sourceColumnId());

        Scenario renamed = scenario("renamed.csv",
                List.of("Item", "Quantidade", "Valor Unitário", "Comentário", "Cor da Linha"), rows());
        LayoutRecognitionResult adaptiveRename = renamed.engine.recognizeLayout(
                new LayoutRecognitionRequest(renamed.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.ADAPTIVE_REANALYSIS, adaptiveRename.route());
        assertEquals(List.of("product.name"), adaptiveRename.compatibility().affectedBindings());
        assertEquals(1, adaptiveRename.candidatePairsEvaluated());
        assertTrue(adaptiveRename.similarityMetricsExecuted() > 0);
        assertFalse(adaptiveRename.fullProfilingExecuted());

        Scenario added = scenario("added.csv",
                List.of("Produto", "Quantidade", "Valor Unitário", "Comentário", "Cor da Linha", "Extra"),
                List.of(List.of("Produto B", "3", "25,00", "x", "y", "z")));
        LayoutRecognitionResult adaptiveAdded = added.engine.recognizeLayout(
                new LayoutRecognitionRequest(added.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.ADAPTIVE_REANALYSIS, adaptiveAdded.route());
        assertEquals(List.of("c5"), adaptiveAdded.mappingPlan().unmappedSourceColumns());
    }

    @Test void dangerousDriftAndNoRegistryRequireFullAnalysis() {
        Scenario first = scenario("first.csv", headers(), rows());
        AnalysisResult analysis = first.engine.analyze(new AnalysisRequest(first.source, schema(), OPTIONS));
        LayoutTemplate template = LayoutTemplate.create("products", "1", analysis, projectedPlan(analysis),
                "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of()));
        var registry = new InMemoryLayoutRegistry(); registry.register(template);

        Scenario missing = scenario("missing.csv",
                List.of("Produto", "Valor Unitário", "Comentário", "Cor da Linha"),
                List.of(List.of("Produto B", "25,00", "x", "y")));
        LayoutRecognitionResult rejected = missing.engine.recognizeLayout(
                new LayoutRecognitionRequest(missing.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, rejected.route());
        assertNull(rejected.mappingPlan());
        assertTrue(rejected.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.REQUIRED_SOURCE_MISSING));

        Scenario typeChanged = scenario("type-changed.csv", headers(), List.of(
                List.of("Produto B", "not-a-number", "25,00", "x", "y")));
        LayoutRecognitionResult typeDrift = typeChanged.engine.recognizeLayout(
                new LayoutRecognitionRequest(typeChanged.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, typeDrift.route());
        assertTrue(typeDrift.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.TYPE_DRIFT));

        Scenario duplicate = scenario("duplicate.csv",
                List.of("Produto", "Quantidade", "Valor Unitário", "Comentário", "Produto", "Cor da Linha"),
                List.of(List.of("Produto B", "3", "25,00", "x", "duplicado", "y")));
        LayoutRecognitionResult duplicateDrift = duplicate.engine.recognizeLayout(
                new LayoutRecognitionRequest(duplicate.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, duplicateDrift.route());
        assertTrue(duplicateDrift.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.DUPLICATE_HEADER));

        LayoutRecognitionResult unknown = first.engine.recognizeLayout(new LayoutRecognitionRequest(
                first.source, schema(), OPTIONS, NoOpLayoutRegistry.INSTANCE));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, unknown.route());
        assertEquals(LayoutCompatibilityReport.Classification.UNKNOWN, unknown.classification());
        assertThrows(UnsupportedOperationException.class,
                () -> NoOpLayoutRegistry.INSTANCE.register(template));

        TargetSchema changedSchema = new TargetSchema("orders", "2", "product-order", "pt-BR", schema().fields());
        LayoutRecognitionResult schemaDrift = first.engine.recognizeLayout(new LayoutRecognitionRequest(
                first.source, changedSchema, OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, schemaDrift.route());
        assertTrue(schemaDrift.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.TARGET_SCHEMA_CHANGED));

        LayoutSignature expected = template.expectedLayout();
        LayoutSignature alteredMetadata = new LayoutSignature(expected.formatVersion(), expected.fingerprint(),
                expected.format(), expected.charset(), "|", expected.headerPresent(), expected.normalizationVersion(),
                expected.attributes(), expected.columns());
        LayoutTemplate metadataTemplate = new LayoutTemplate(template.formatVersion(), template.templateId(),
                template.templateVersion(), template.name(), alteredMetadata, template.targetSchemaId(),
                template.targetSchemaVersion(), template.targetSchemaFingerprint(), template.configurationVersion(),
                template.configurationFingerprint(), template.knowledgeSnapshotId(), template.knowledgeVersion(),
                template.originPlanId(), template.bindings(), template.ignoredSourceColumns(), template.createdAt(),
                template.provenance(), template.engineVersion());
        var metadataRegistry = new InMemoryLayoutRegistry(); metadataRegistry.register(metadataTemplate);
        LayoutRecognitionResult metadataDrift = first.engine.recognizeLayout(new LayoutRecognitionRequest(
                first.source, schema(), OPTIONS, metadataRegistry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, metadataDrift.route());
        assertTrue(metadataDrift.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.STRUCTURE_METADATA_CHANGED));

        LayoutSignature alteredAttributes = new LayoutSignature(expected.formatVersion(), expected.fingerprint(),
                expected.format(), expected.charset(), expected.delimiter(), expected.headerPresent(),
                expected.normalizationVersion(), Map.of("sheet", "other"), expected.columns());
        LayoutTemplate attributeTemplate = new LayoutTemplate(template.formatVersion(), template.templateId(),
                template.templateVersion(), template.name(), alteredAttributes, template.targetSchemaId(),
                template.targetSchemaVersion(), template.targetSchemaFingerprint(), template.configurationVersion(),
                template.configurationFingerprint(), template.knowledgeSnapshotId(), template.knowledgeVersion(),
                template.originPlanId(), template.bindings(), template.ignoredSourceColumns(), template.createdAt(),
                template.provenance(), template.engineVersion());
        var attributeRegistry = new InMemoryLayoutRegistry(); attributeRegistry.register(attributeTemplate);
        LayoutRecognitionResult attributeDrift = first.engine.recognizeLayout(new LayoutRecognitionRequest(
                first.source, schema(), OPTIONS, attributeRegistry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, attributeDrift.route());
        assertTrue(attributeDrift.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.STRUCTURE_METADATA_CHANGED));

        LayoutSignature alteredHeaderMode = new LayoutSignature(expected.formatVersion(), expected.fingerprint(),
                expected.format(), expected.charset(), expected.delimiter(), !expected.headerPresent(),
                expected.normalizationVersion(), expected.attributes(), expected.columns());
        LayoutTemplate headerTemplate = new LayoutTemplate(template.formatVersion(), template.templateId(),
                template.templateVersion(), template.name(), alteredHeaderMode, template.targetSchemaId(),
                template.targetSchemaVersion(), template.targetSchemaFingerprint(), template.configurationVersion(),
                template.configurationFingerprint(), template.knowledgeSnapshotId(), template.knowledgeVersion(),
                template.originPlanId(), template.bindings(), template.ignoredSourceColumns(), template.createdAt(),
                template.provenance(), template.engineVersion());
        var headerRegistry = new InMemoryLayoutRegistry(); headerRegistry.register(headerTemplate);
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS,
                first.engine.recognizeLayout(new LayoutRecognitionRequest(
                        first.source, schema(), OPTIONS, headerRegistry)).route());

        Scenario otherNormalizer = scenario("normalizer.csv", headers(), rows(), List.of(),
                new HeaderNormalizer(Map.of("produto", List.of("item"))));
        LayoutRecognitionResult configurationDrift = otherNormalizer.engine.recognizeLayout(
                new LayoutRecognitionRequest(otherNormalizer.source, schema(), OPTIONS, registry));
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, configurationDrift.route());
        assertTrue(configurationDrift.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.CONFIGURATION_CHANGED));
    }

    @Test void boundedGuardRejectsSameHeaderWhenCurrentContentContradictsKnownSemantics() {
        TargetSchema emailSchema = new TargetSchema("contacts", "1", "contacts", "en-US", List.of(
                new TargetField("contact.email", "Contact", List.of(), PhysicalType.TEXT,
                        Set.of(new SemanticType("core:email")), true)));
        Scenario first = scenario("email-day-1.csv", List.of("Contact"),
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> List.of("synthetic" + index + "@example.test")).toList(),
                List.of(CoreSemanticDetectors.email()));
        AnalysisResult analysis = first.engine.analyze(new AnalysisRequest(first.source, emailSchema, OPTIONS));
        MappingPlan plan = new MappingPlanner().create(analysis, emailSchema,
                List.of(new MappingPlanner.Selection("c0", "contact.email", "explicit synthetic confirmation")));
        LayoutTemplate template = LayoutTemplate.create("contact-import", "1", analysis, plan,
                "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of()));
        var registry = new InMemoryLayoutRegistry();
        registry.register(template);

        Scenario changed = scenario("email-day-2.csv", List.of("Contact"),
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(index -> List.of("INTERNAL-" + index)).toList(),
                List.of(CoreSemanticDetectors.email()));
        LayoutRecognitionResult result = changed.engine.recognizeLayout(
                new LayoutRecognitionRequest(changed.source, emailSchema, OPTIONS, registry));

        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS, result.route());
        assertNull(result.mappingPlan());
        assertTrue(result.compatibility().drift().stream().anyMatch(item ->
                item.type() == LayoutCompatibilityReport.DriftType.SEMANTIC_DRIFT));
        assertTrue(result.rowsReadForRecognition() <= 64);
    }

    @Test void projectionOperationsAreDeterministicLocaleAwareAndTypedOnFailure() {
        var evaluator = new ProjectionEvaluator();
        Map<String, String> row = Map.of("c0", "Ana", "c1", "Silva", "c2", "10", "c3", "25,00", "empty", "");
        assertEquals("Ana Silva", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.CONCAT, source("c0"), constant(" "), source("c1"))), row, "pt-BR").value());
        assertEquals("Ana", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.COALESCE, source("empty"), source("c0"))), row, "pt-BR").value());
        assertEquals("35", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("c2"), source("c3"))), row, "pt-BR").value());
        assertEquals("-15", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.SUBTRACT, source("c2"), source("c3"))), row, "pt-BR").value());
        assertEquals("250", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.MULTIPLY, source("c2"), source("c3"))), row, "pt-BR").value());
        assertEquals("2.5", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.DIVIDE, source("c3"), source("c2"))), row, "pt-BR").value());
        ProjectionEvaluator.Result zero = evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.DIVIDE, source("c2"), constant("0"))), row, "pt-BR");
        assertEquals(ProjectionEvaluator.Status.FAILURE, zero.status());
        assertEquals("DERIVED_DIVISION_BY_ZERO", zero.code());
        assertEquals(zero, evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.DIVIDE, source("c2"), constant("0"))), row, "pt-BR"));
        assertEquals("ACTIVE", evaluator.evaluate(ProjectionSource.constant("ACTIVE"), row, "pt-BR").value());
        assertThrows(IllegalArgumentException.class, () -> new ProjectionSource.DerivedExpression(
                ProjectionSource.Operation.DIVIDE, List.of(source("c0"))));
    }

    @Test void registryIsBoundedDeterministicAndContextIndexed() {
        Scenario first = scenario("first.csv", headers(), rows());
        AnalysisResult analysis = first.engine.analyze(new AnalysisRequest(first.source, schema(), OPTIONS));
        LayoutTemplate template = LayoutTemplate.create("products", "1", analysis, projectedPlan(analysis),
                "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of()));
        var registry = new InMemoryLayoutRegistry(1);
        registry.register(template); registry.register(template);
        assertEquals(template, registry.get(template.templateId(), "1").orElseThrow());
        assertEquals(1, registry.findCandidates(new LayoutRegistry.LayoutQuery(
                template.targetSchemaFingerprint(), "CSV")).size());
        assertTrue(registry.findCandidates(new LayoutRegistry.LayoutQuery("other", "CSV")).isEmpty());
        assertEquals(1, registry.findCandidates(new LayoutRegistry.LayoutQuery("other", "CSV",
                template.expectedLayout().columns().stream().map(LayoutSignature.Column::structuralKey).toList())).size());
        LayoutTemplate other = new LayoutTemplate(template.formatVersion(), template.templateId(), "2",
                template.name(), template.expectedLayout(), template.targetSchemaId(), template.targetSchemaVersion(),
                template.targetSchemaFingerprint(), template.configurationVersion(), template.configurationFingerprint(),
                template.knowledgeSnapshotId(), template.knowledgeVersion(), template.originPlanId(),
                template.bindings(), template.ignoredSourceColumns(), template.createdAt(), template.provenance(),
                template.engineVersion());
        assertThrows(IllegalStateException.class, () -> registry.register(other));
    }

    @Test void projectionAndLayoutContractsRejectUnsafeOrAmbiguousConfiguration() {
        HeaderNormalizer emptyNormalizer = new HeaderNormalizer(null);
        HeaderNormalizer expandedNormalizer = new HeaderNormalizer(Map.of("cod", List.of("codigo")));
        assertEquals(emptyNormalizer.fingerprint(), new HeaderNormalizer(Map.of()).fingerprint());
        assertNotEquals(emptyNormalizer.fingerprint(), expandedNormalizer.fingerprint());
        ProjectionSource.DerivedExpression concat = expression(
                ProjectionSource.Operation.CONCAT, constant("a"));
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectionSource(null, "", "", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.SOURCE_COLUMN, "", "", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.SOURCE_COLUMN, "c0", "x", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.SOURCE_COLUMN, "c0", "", concat)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.CONSTANT, "c0", "x", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.CONSTANT, "", "x", concat)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.DERIVED, "c0", "", concat)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.DERIVED, "", "x", concat)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.DERIVED, "", "", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.UNMAPPED, "c0", "", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.UNMAPPED, "", "x", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource(ProjectionSource.Kind.UNMAPPED, "", "", concat)),
                () -> assertThrows(IllegalArgumentException.class, () -> ProjectionSource.sourceColumn("c\n0")),
                () -> assertThrows(IllegalArgumentException.class, () -> ProjectionSource.constant("x\ry")),
                () -> assertThrows(IllegalArgumentException.class, () -> ProjectionSource.constant("x".repeat(16_385))),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectionSource.DerivedExpression(null, List.of(constant("a")))),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectionSource.DerivedExpression(ProjectionSource.Operation.ADD, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource.DerivedExpression(ProjectionSource.Operation.SUBTRACT,
                                List.of(constant("1")))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProjectionSource.DerivedExpression(ProjectionSource.Operation.CONCAT,
                                java.util.stream.IntStream.range(0, 33)
                                        .mapToObj(index -> constant("x")).toList())),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectionSource.Operand(null, "x")),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProjectionSource.Operand(ProjectionSource.OperandKind.CONSTANT, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ProjectionSource.Operand.sourceColumn("")));
        assertTrue(ProjectionSource.constant("").referencedSourceColumns().isEmpty());
        assertTrue(ProjectionSource.unmapped().referencedSourceColumns().isEmpty());
        assertEquals(List.of("c0", "c1"), new ArrayList<>(ProjectionSource.derived(expression(
                ProjectionSource.Operation.CONCAT, source("c0"), source("c1"), source("c0"),
                constant("x"))).referencedSourceColumns()));

        Scenario scenario = scenario("contracts.csv", List.of("A"), List.of(List.of("x")));
        LayoutRecognitionRequest defaults = new LayoutRecognitionRequest(scenario.source, schema(), null, null);
        assertEquals(AnalysisOptions.defaults(), defaults.options());
        assertSame(NoOpLayoutRegistry.INSTANCE, defaults.registry());
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new LayoutRecognitionRequest(null, schema(), OPTIONS, NoOpLayoutRegistry.INSTANCE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutRecognitionRequest(scenario.source, schema(), OPTIONS,
                                NoOpLayoutRegistry.INSTANCE, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutRecognitionRequest(scenario.source, schema(), OPTIONS,
                                NoOpLayoutRegistry.INSTANCE, 10_001)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutRecognitionResult(null,
                                LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS,
                                LayoutCompatibilityReport.Classification.UNKNOWN, "source", "sha",
                                new LayoutSignature("1.0", "fingerprint", "CSV", "", "", true,
                                        "normalizer", Map.of(), List.of()),
                                null, null, new LayoutCompatibilityReport("1.0",
                                        LayoutCompatibilityReport.Classification.UNKNOWN,
                                        LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS,
                                        null, null, null, null, false, false),
                                null, -1, 0, 0, false, false, null)));
        LayoutCompatibilityReport empty = new LayoutCompatibilityReport(null,
                LayoutCompatibilityReport.Classification.UNKNOWN,
                LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS,
                null, null, null, null, false, false);
        assertEquals("1.0", empty.formatVersion());
        assertTrue(empty.matchedColumns().isEmpty() && empty.drift().isEmpty()
                && empty.affectedBindings().isEmpty() && empty.reasons().isEmpty());
    }

    @Test void projectionEvaluatorRejectsLocaleAmbiguityWithoutLeakingValues() {
        var evaluator = new ProjectionEvaluator();
        Map<String, String> row = Map.of("br", "1.234,56", "us", "1,234.56", "plain", "1.5",
                "bad", "1,2,3", "zero", "0");
        assertEquals("1234.56", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("br"))), row, "pt-BR").value());
        assertEquals("1234.56", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("us"))), row, "en-US").value());
        assertEquals(ProjectionEvaluator.Status.FAILURE, evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("us"))), row, "").status());
        assertEquals("DERIVED_INVALID_NUMBER", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("bad"))), row, "en-US").code());
        assertEquals("DERIVED_INVALID_NUMBER", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("missing"))), row, "pt-BR").code());
        assertEquals("1.5", evaluator.evaluate(ProjectionSource.derived(expression(
                ProjectionSource.Operation.ADD, source("plain"))), row, null).value());
        assertEquals("UNMAPPED_TARGET", evaluator.evaluate(ProjectionSource.unmapped(), row, "pt-BR").code());
        assertNull(evaluator.evaluate(ProjectionSource.sourceColumn("missing"), row, null).value());
        ProjectionEvaluator.Result normalized = new ProjectionEvaluator.Result(
                ProjectionEvaluator.Status.SUCCESS, "x", null, null, 0);
        assertEquals("", normalized.code());
        assertEquals("", normalized.reason());
    }

    @Test void plannerAndSignatureContractsRejectUnresolvedProjectionState() {
        Scenario current = scenario("planner.csv", headers(), rows());
        AnalysisResult analysis = current.engine.analyze(new AnalysisRequest(current.source, schema(), OPTIONS));
        MappingPlanner planner = new MappingPlanner();
        MappingPlanner.ProjectionSelection name = selection("product.name", ProjectionSource.sourceColumn("c0"));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> planner.createProjected(analysis, schema(),
                        List.of(selection("missing.target", ProjectionSource.sourceColumn("c0"))), List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> planner.createProjected(analysis, schema(),
                        List.of(name, name), List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> planner.createProjected(analysis, schema(),
                        List.of(selection("product.name", ProjectionSource.sourceColumn("missing"))), List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> planner.createProjected(analysis, schema(),
                        List.of(name), List.of("missing"))),
                () -> assertThrows(IllegalArgumentException.class, () -> planner.createProjected(analysis, schema(),
                        List.of(name), List.of("c1", "c1"))),
                () -> assertThrows(IllegalArgumentException.class, () -> planner.createProjected(analysis, schema(),
                        List.of(name), List.of("c0"))));
        MappingPlan direct = planner.create(analysis, schema(),
                List.of(new MappingPlanner.Selection("c0", "product.name", null)));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> planner.configure(direct, "missing", List.of(), List.of(), "test")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> planner.configureTarget(direct, "missing", List.of(), List.of(), "test")));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutSignature.Column("c0", -1, "a", 0, PhysicalType.TEXT, "", 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutSignature.Column("c0", 0, "a", -1, PhysicalType.TEXT, "", 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutSignature.Column("c0", 0, "a", 0, PhysicalType.TEXT, "", -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new LayoutSignature("1.0", " ", "CSV", "", "", true, "normalizer",
                                Map.of(), List.of())));
        LayoutSignature safe = new LayoutSignature("1.0", "fingerprint", "CSV", null, null, true,
                "normalizer", null, null);
        assertEquals("", safe.charset());
        assertEquals("", safe.delimiter());
        assertTrue(safe.attributes().isEmpty() && safe.columns().isEmpty());

        MappingPlan projected = projectedPlan(analysis);
        LayoutTemplate validTemplate = LayoutTemplate.create("products", "1", analysis, projected,
                "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> LayoutTemplate.create("products", "1", analysis,
                new MappingPlan(projected.formatVersion(), projected.planId(), projected.engineVersion(),
                        "different-source", projected.sourceFingerprint(), projected.schemaId(), projected.schemaVersion(),
                        projected.schemaFingerprint(), projected.configurationVersion(),
                        projected.configurationFingerprint(), projected.knowledgeSnapshotId(), projected.knowledgeVersion(),
                        projected.layoutTemplateId(), projected.layoutTemplateVersion(), projected.layoutFingerprint(),
                        projected.executionRoute(), projected.mappings(), projected.ignoredSourceColumns(),
                        projected.unmappedSourceColumns(), projected.confirmedSourceColumns()),
                "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of())));
        assertThrows(IllegalArgumentException.class, () -> LayoutTemplate.create("products", "1", analysis,
                direct, "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of())));
        MappingPlan legacy = new MappingPlan("1.1", projected.planId(), projected.engineVersion(),
                projected.sourceId(), projected.sourceFingerprint(), projected.schemaId(), projected.schemaVersion(),
                projected.schemaFingerprint(), projected.configurationVersion(), projected.configurationFingerprint(),
                projected.knowledgeSnapshotId(), projected.knowledgeVersion(), projected.mappings(),
                projected.unmappedSourceColumns(), projected.confirmedSourceColumns());
        assertThrows(IllegalArgumentException.class, () -> LayoutTemplate.create("products", "1", analysis,
                legacy, "2026-09-12T00:00:00Z", "test", new HeaderNormalizer(Map.of())));
        MappingPlan.FieldMapping firstBinding = validTemplate.bindings().getFirst();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> copyTemplate(validTemplate,
                        List.of(firstBinding, firstBinding), validTemplate.ignoredSourceColumns(), "products")),
                () -> assertThrows(IllegalArgumentException.class, () -> copyTemplate(validTemplate,
                        validTemplate.bindings(), List.of("missing"), "products")),
                () -> assertThrows(IllegalArgumentException.class, () -> copyTemplate(validTemplate,
                        validTemplate.bindings(), List.of("c3", "c3", "c4"), "products")),
                () -> assertThrows(IllegalArgumentException.class, () -> copyTemplate(validTemplate,
                        validTemplate.bindings(), List.of("c0", "c3", "c4"), "products")),
                () -> assertThrows(IllegalArgumentException.class, () -> copyTemplate(validTemplate,
                        validTemplate.bindings(), List.of("c3"), "products")),
                () -> assertThrows(IllegalArgumentException.class, () -> copyTemplate(validTemplate,
                        validTemplate.bindings(), validTemplate.ignoredSourceColumns(), "bad\nname")));
    }

    private static LayoutTemplate copyTemplate(LayoutTemplate template,
            List<MappingPlan.FieldMapping> bindings, List<String> ignored, String name) {
        return new LayoutTemplate(template.formatVersion(), template.templateId(), template.templateVersion(),
                name, template.expectedLayout(), template.targetSchemaId(), template.targetSchemaVersion(),
                template.targetSchemaFingerprint(), template.configurationVersion(),
                template.configurationFingerprint(), template.knowledgeSnapshotId(), template.knowledgeVersion(),
                template.originPlanId(), bindings, ignored, template.createdAt(), template.provenance(),
                template.engineVersion());
    }

    private static MappingPlan projectedPlan(AnalysisResult analysis) {
        return new MappingPlanner().createProjected(analysis, schema(), List.of(
                selection("product.name", ProjectionSource.sourceColumn("c0")),
                selection("order.quantity", ProjectionSource.sourceColumn("c1")),
                selection("order.unitPrice", ProjectionSource.sourceColumn("c2")),
                selection("order.total", ProjectionSource.derived(expression(
                        ProjectionSource.Operation.MULTIPLY, source("c1"), source("c2")))),
                selection("order.status", ProjectionSource.constant("ACTIVE"))), List.of("c3", "c4"));
    }

    private static MappingPlanner.ProjectionSelection selection(String target, ProjectionSource source) {
        return new MappingPlanner.ProjectionSelection(target, source, "explicit synthetic confirmation");
    }
    private static ProjectionSource.DerivedExpression expression(ProjectionSource.Operation operation,
            ProjectionSource.Operand... operands) {
        return new ProjectionSource.DerivedExpression(operation, List.of(operands));
    }
    private static ProjectionSource.Operand source(String id) { return ProjectionSource.Operand.sourceColumn(id); }
    private static ProjectionSource.Operand constant(String value) { return ProjectionSource.Operand.constant(value); }

    private static TargetSchema schema() {
        return new TargetSchema("orders", "1", "product-order", "pt-BR", List.of(
                new TargetField("product.name", "Produto", List.of("Item"), PhysicalType.TEXT, Set.of(), true),
                new TargetField("order.quantity", "Quantidade", List.of(), PhysicalType.INTEGER, Set.of(), true),
                new TargetField("order.unitPrice", "Valor Unitário", List.of(), PhysicalType.DECIMAL, Set.of(), true),
                new TargetField("order.total", "Valor Total", List.of(), PhysicalType.DECIMAL, Set.of(), true),
                new TargetField("order.status", "Status", List.of(), PhysicalType.TEXT, Set.of(), true)));
    }
    private static List<String> headers() {
        return List.of("Produto", "Quantidade", "Valor Unitário", "Comentário", "Cor da Linha");
    }
    private static List<List<String>> rows() {
        return List.of(List.of("Produto A", "10", "25,00", "interno", "azul"));
    }

    private static Scenario scenario(String id, List<String> headers, List<List<String>> rows) {
        return scenario(id, headers, rows, List.of());
    }

    private static Scenario scenario(String id, List<String> headers, List<List<String>> rows,
            List<SemanticDetector> detectors) {
        return scenario(id, headers, rows, detectors, new HeaderNormalizer(Map.of()));
    }

    private static Scenario scenario(String id, List<String> headers, List<List<String>> rows,
            List<SemanticDetector> detectors, HeaderNormalizer normalizer) {
        MemorySource source = new MemorySource(id, headers + "|" + rows);
        MappingEngine engine = MappingEngine.builder().readers(List.of(new MemoryReader(headers, rows)))
                .semanticDetectors(detectors).normalizer(normalizer).build();
        return new Scenario(source, engine);
    }
    private record Scenario(MemorySource source, MappingEngine engine) {}

    private static final class MemorySource implements TabularSource {
        private final String id;
        private final byte[] bytes;
        private MemorySource(String id, String content) {
            this.id = id; this.bytes = content.getBytes(StandardCharsets.UTF_8);
        }
        public String id() { return id; }
        public String fileName() { return id; }
        public long size() { return bytes.length; }
        public InputStream openStream() { return new ByteArrayInputStream(bytes); }
        public String sha256() {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
    }

    private static final class MemoryReader implements DataReader {
        private final List<String> headers;
        private final List<List<String>> rows;
        private MemoryReader(List<String> headers, List<List<String>> rows) {
            this.headers = List.copyOf(headers); this.rows = rows.stream().map(List::copyOf).toList();
        }
        public boolean supports(TabularSource source) { return true; }
        public SourceStructure detect(TabularSource source, AnalysisOptions options, EngineLimits limits) {
            var columns = new ArrayList<SourceColumn>();
            var normalizer = new HeaderNormalizer(Map.of());
            for (int index = 0; index < headers.size(); index++) columns.add(new SourceColumn(
                    "c" + index, index, headers.get(index), normalizer.normalize(headers.get(index)).comparable()));
            return new SourceStructure("CSV", "UTF-8", ",", true, columns, List.of());
        }
        public Dataset open(TabularSource source, SourceStructure structure,
                AnalysisOptions options, EngineLimits limits) {
            return new Dataset() {
                private final AtomicBoolean requested = new AtomicBoolean();
                private boolean closed;
                public SourceStructure structure() { return structure; }
                public Iterator<Row> rows() {
                    if (closed || !requested.compareAndSet(false, true)) throw new IllegalStateException();
                    Iterator<List<String>> iterator = rows.iterator();
                    return new Iterator<>() {
                        private long index;
                        public boolean hasNext() { return iterator.hasNext(); }
                        public Row next() { index++; return new Row(index, index + 1, iterator.next()); }
                    };
                }
                public List<String> warnings() { return List.of(); }
                public void close() { closed = true; }
            };
        }
    }
}
