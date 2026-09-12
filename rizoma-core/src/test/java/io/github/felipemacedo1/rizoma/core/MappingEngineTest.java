package io.github.felipemacedo1.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MappingEngineTest {
    @Test void libraryApiProfilesScoresExplainsMasksAndCloses() {
        var closed = new AtomicBoolean();
        var rows = new ArrayList<List<String>>();
        for (int i = 0; i < 25; i++) rows.add(List.of("person" + i + "@example.test", "0000" + i));
        var reader = new MemoryReader(List.of("E-mail", "Código Cliente"), rows, closed);
        var schema = new TargetSchema("customer", "1", "customer", "pt-BR", List.of(
                new TargetField("customer.email", "E-mail", List.of("email"), PhysicalType.TEXT,
                        Set.of(new SemanticType("core:email")), true),
                new TargetField("customer.id", "Código Cliente", List.of("cod cli"), PhysicalType.TEXT,
                        Set.of(), true)));
        MappingEngine engine = MappingEngine.builder().readers(List.of(reader))
                .semanticDetectors(CoreSemanticDetectors.defaults()).build();

        AnalysisResult result = engine.analyze(new AnalysisRequest(new MemorySource(), schema, null));

        assertEquals(25, result.rowsProcessed());
        assertTrue(closed.get());
        assertEquals("customer.email", result.candidatesByColumn().get("c0").getFirst().targetFieldId());
        assertEquals("UNCALIBRATED", result.calibration());
        assertNotEquals(AnalysisResult.DecisionStatus.AUTO_MAP, result.decisionsByColumn().get("c0").status());
        assertTrue(result.decisionsByColumn().get("c0").blockers().contains("AUTO_MAP is disabled by core configuration"));
        assertTrue(result.profiles().stream().flatMap(profile -> profile.protectedSamples().stream())
                .allMatch(sample -> sample.startsWith("<redacted:length=")));
        assertTrue(result.candidatesByColumn().values().stream().flatMap(List::stream)
                .flatMap(candidate -> candidate.components().stream())
                .allMatch(component -> Double.isFinite(component.contribution())));
        assertScoreExplanationConsistent(result.candidatesByColumn().get("c0").getFirst());
        assertEquals(new PathTabularSource(java.nio.file.Path.of(".")).getClass(), PathTabularSource.class);
    }

    @Test void emptyEvidenceAbstainsWithoutNaNAndCandidateCollectionsAreBounded() {
        var limits = new EngineLimits(1000, 10, 5, 100, 100, 0, 1, 2, 2, Duration.ofSeconds(5));
        var defaults = EngineConfig.defaults();
        var config = new EngineConfig("test", limits, defaults.weights(), 2, .9, .7, .5, .1, .6, false);
        var reader = new MemoryReader(List.of(""), List.of(List.of("")), new AtomicBoolean());
        var schema = schemaWithThreeTargets();
        var result = MappingEngine.builder().readers(List.of(reader)).configuration(config).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, AnalysisOptions.defaults()));
        var decision = result.decisionsByColumn().get("c0");
        assertEquals(AnalysisResult.DecisionStatus.NO_MATCH, decision.status());
        assertNull(decision.targetFieldId());
        assertEquals(1, result.candidatesByColumn().get("c0").size());
        assertTrue(Double.isFinite(decision.score()));
        assertTrue(result.profiles().getFirst().protectedSamples().isEmpty());
        assertEquals(0, result.profiles().getFirst().statistics().cardinality().value());
        assertEquals(ColumnProfile.MeasureAccuracy.EXACT,
                result.profiles().getFirst().statistics().cardinality().accuracy());
        assertNull(result.profiles().getFirst().statistics().entropyBits());
    }

    @Test void equalCandidatesTieDeterministicallyAndExclusiveCollisionRequiresReview() {
        var reader = new MemoryReader(List.of("Nome", "Nome"), List.of(List.of("Ana", "Bia")), new AtomicBoolean());
        var target = new TargetField("customer.name", "Nome", List.of(), PhysicalType.TEXT, Set.of(), false);
        var result = MappingEngine.builder().readers(List.of(reader)).build().analyze(new AnalysisRequest(
                new MemorySource(), new TargetSchema("x", "1", "", "", List.of(target)), null));
        assertEquals(List.of("exclusive target collision: customer.name"), result.conflicts());
        assertTrue(result.decisionsByColumn().values().stream()
                .allMatch(decision -> decision.blockers().contains("exclusive target collision")));
        assertTrue(result.decisionsByColumn().values().stream().noneMatch(d -> d.status() == AnalysisResult.DecisionStatus.AUTO_MAP));
    }

    @Test void limitsUnsupportedSourcesAndFailuresHaveSafeCodes() {
        var schema = schemaWithThreeTargets();
        MappingEngine engine = MappingEngine.builder().readers(List.of(new MemoryReader(List.of("A"), List.of(), new AtomicBoolean()))).build();
        var huge = new TabularSource() {
            public String id() { return "huge"; } public String fileName() { return "x"; }
            public long size() { return Long.MAX_VALUE; } public InputStream openStream() { return InputStream.nullInputStream(); }
            public String sha256() { return "x"; }
        };
        assertEquals("SOURCE_TOO_LARGE", assertThrows(EngineException.class,
                () -> engine.analyze(new AnalysisRequest(huge, schema, null))).code());
        var unsupported = new MemorySource() { @Override public String fileName() { return "unsupported"; } };
        MappingEngine noReader = MappingEngine.builder().readers(List.of(new MemoryReader(List.of("A"), List.of(), new AtomicBoolean()) {
            @Override public boolean supports(TabularSource source) { return false; }
        })).build();
        assertEquals("UNSUPPORTED_SOURCE", assertThrows(EngineException.class,
                () -> noReader.analyze(new AnalysisRequest(unsupported, schema, null))).code());
        assertThrows(IllegalArgumentException.class, () -> MappingEngine.builder().build());
        assertThrows(IllegalArgumentException.class, () -> MappingEngine.builder().readers(List.of(
                        new MemoryReader(List.of("A"), List.of(), new AtomicBoolean())))
                .semanticDetectors(List.of(CoreSemanticDetectors.email(), CoreSemanticDetectors.email())).build());

        MappingKnowledgeBase invalidKnowledge = new MappingKnowledgeBase() {
            @Override public KnowledgeSnapshot snapshot() { return new KnowledgeSnapshot() {
                public String id() { return ""; }
                public String version() { return "1.0"; }
                public long eventCount() { return 0; }
                public HistoricalEvidence find(KnowledgeQuery query) { throw new AssertionError(); }
            }; }
            @Override public void record(MappingFeedback feedback) { throw new AssertionError(); }
        };
        assertEquals("INVALID_KNOWLEDGE_SNAPSHOT", assertThrows(EngineException.class,
                () -> engine.analyze(new AnalysisRequest(new MemorySource(), schema, null,
                        invalidKnowledge))).code());
    }

    @Test void recordStructureFieldTimeAndCancellationLimitsAreEnforcedAndResourcesClose() {
        var schema = schemaWithThreeTargets();
        var defaults = EngineConfig.defaults();
        var closed = new AtomicBoolean();
        var twoRows = new MemoryReader(List.of("A"), List.of(List.of("x"), List.of("y")), closed);
        var recordLimits = new EngineLimits(100, 1, 5, 10, 10, 1, 2, 2, 2, Duration.ofSeconds(5));
        var recordConfig = config(defaults, recordLimits, false, .6);
        assertEquals("RECORD_LIMIT", assertThrows(EngineException.class, () -> MappingEngine.builder()
                .readers(List.of(twoRows)).configuration(recordConfig).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null))).code());
        assertTrue(closed.get());

        var tooManyColumns = new MemoryReader(List.of("A", "B"), List.of(), new AtomicBoolean());
        var oneColumn = new EngineLimits(100, 2, 1, 10, 10, 1, 2, 2, 2, Duration.ofSeconds(5));
        assertEquals("COLUMN_LIMIT", assertThrows(EngineException.class, () -> MappingEngine.builder()
                .readers(List.of(tooManyColumns)).configuration(config(defaults, oneColumn, false, .6)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null))).code());

        var longHeader = new MemoryReader(List.of("too-long-header"), List.of(), new AtomicBoolean());
        var shortHeader = new EngineLimits(100, 2, 2, 10, 3, 1, 2, 2, 2, Duration.ofSeconds(5));
        assertEquals("HEADER_LIMIT", assertThrows(EngineException.class, () -> MappingEngine.builder()
                .readers(List.of(longHeader)).configuration(config(defaults, shortHeader, false, .6)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null))).code());

        var longField = new MemoryReader(List.of("A"), List.of(List.of("too-long")), new AtomicBoolean());
        var shortField = new EngineLimits(100, 2, 2, 3, 10, 1, 2, 2, 2, Duration.ofSeconds(5));
        assertEquals("FIELD_LIMIT", assertThrows(EngineException.class, () -> MappingEngine.builder()
                .readers(List.of(longField)).configuration(config(defaults, shortField, false, .6)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null))).code());

        var immediate = new EngineLimits(100, 2, 2, 10, 10, 1, 2, 2, 2, Duration.ofNanos(1));
        assertEquals("TIME_LIMIT", assertThrows(EngineException.class, () -> MappingEngine.builder()
                .readers(List.of(new MemoryReader(List.of("A"), List.of(List.of("x")), new AtomicBoolean())))
                .configuration(config(defaults, immediate, false, .6)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null))).code());

        Thread.currentThread().interrupt();
        try {
            assertEquals("CANCELLED", assertThrows(EngineException.class, () -> MappingEngine.builder()
                    .readers(List.of(new MemoryReader(List.of("A"), List.of(List.of("x")), new AtomicBoolean())))
                    .build().analyze(new AnalysisRequest(new MemorySource(), schema, null))).code());
        } finally { Thread.interrupted(); }
    }

    @Test void profilesAllPhysicalKindsPreservesLeadingZerosAndCanAutoMapOnlyWhenExplicitlyEnabled() {
        var rows = List.of(
                List.of("true", "12", "1.5", "2026-09-11", "00123", "1"),
                List.of("false", "13", "2,5", "10/09/2026", "00456", "mixed"));
        var reader = new MemoryReader(List.of("Flag", "Count", "Amount", "Date", "Identifier", "Mixed"), rows, new AtomicBoolean());
        var targets = List.of(
                new TargetField("flag", "Flag", List.of(), PhysicalType.BOOLEAN, Set.of(), false, false),
                new TargetField("count", "Count", List.of(), PhysicalType.INTEGER, Set.of(), false, false),
                new TargetField("amount", "Amount", List.of(), PhysicalType.DECIMAL, Set.of(), false, false),
                new TargetField("date", "Date", List.of(), PhysicalType.DATE, Set.of(), false, false),
                new TargetField("identifier", "Identifier", List.of(), PhysicalType.TEXT, Set.of(), false, false),
                new TargetField("mixed", "Mixed", List.of(), PhysicalType.TEXT, Set.of(), false, false),
                new TargetField("zzz", "Unrelated", List.of(), PhysicalType.DATE, Set.of(), false, false));
        var defaults = EngineConfig.defaults();
        var enabled = new EngineConfig("enabled", defaults.limits(), defaults.weights(), 2,
                .9, .7, .5, .15, .3, true);
        var result = MappingEngine.builder().readers(List.of(reader)).configuration(enabled).build().analyze(
                new AnalysisRequest(new MemorySource(), new TargetSchema("x", "1", "", "", targets), null));
        assertEquals(List.of(PhysicalType.BOOLEAN, PhysicalType.INTEGER, PhysicalType.DECIMAL,
                        PhysicalType.DATE, PhysicalType.TEXT, PhysicalType.MIXED),
                result.profiles().stream().map(ColumnProfile::inferredType).toList());
        assertEquals(AnalysisResult.DecisionStatus.AUTO_MAP, result.decisionsByColumn().get("c0").status());
        assertEquals("00123".length(), result.profiles().get(4).minLength());
    }

    @Test void unconfiguredSemanticsAreUnavailableAndStrongContradictionAbstains() {
        SemanticType strong = new SemanticType("test:strong");
        SemanticDetector detector = new SemanticDetector() {
            public SemanticType type() { return strong; }
            public Accumulator newAccumulator() { return new Accumulator() {
                public void accept(String rawValue) {}
                public SemanticEvidence finish(int minimum) { return new SemanticEvidence(strong, 20, 20, 20, 0, 1, 1, 1, true, "strong test evidence"); }
            }; }
        };
        var reader = new MemoryReader(List.of("Mystery"), List.of(List.of("secret")), new AtomicBoolean());
        var schema = new TargetSchema("x", "1", "", "", List.of(
                new TargetField("unknown", "Mystery", List.of(), PhysicalType.TEXT, Set.of(new SemanticType("other:type")), false)));
        var result = MappingEngine.builder().readers(List.of(reader)).semanticDetectors(List.of(detector)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null));
        assertEquals(AnalysisResult.DecisionStatus.NO_MATCH, result.decisionsByColumn().get("c0").status());
        assertNull(result.decisionsByColumn().get("c0").targetFieldId());
        assertFalse(result.candidatesByColumn().get("c0").getFirst().eligible());
        assertTrue(result.candidatesByColumn().get("c0").getFirst().components().stream()
                .anyMatch(c -> "semantic".equals(c.id()) && !c.available()));
    }

    @Test void advancedProfileMeasuresEntropyNumericDistributionAndAnomaliesWithoutRawValues() {
        var rows = new ArrayList<List<String>>();
        for (int i = 1; i <= 20; i++) {
            rows.add(List.of(i <= 10 ? "active" : "inactive", i == 20 ? "oops" : Integer.toString(i),
                    i == 20 ? "SEM EMAIL" : "synthetic" + i + "@example.test"));
        }
        var reader = new MemoryReader(List.of("Status", "Quantidade", "Contato"), rows, new AtomicBoolean());
        var schema = new TargetSchema("profile", "1", "", "pt-BR", List.of(
                new TargetField("status", "Status", List.of(), PhysicalType.TEXT, Set.of(), false),
                new TargetField("quantity", "Quantidade", List.of(), PhysicalType.INTEGER, Set.of(), false),
                new TargetField("email", "Email", List.of("Contato"), PhysicalType.TEXT,
                        Set.of(new SemanticType("core:email")), false)));

        AnalysisResult result = MappingEngine.builder().readers(List.of(reader))
                .semanticDetectors(CoreSemanticDetectors.defaults()).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null));

        var status = result.profiles().get(0).statistics();
        assertEquals(2, status.cardinality().value());
        assertEquals(ColumnProfile.MeasureAccuracy.EXACT, status.cardinality().accuracy());
        assertEquals(.1, status.uniqueRatio(), 1e-12);
        assertEquals(1, status.entropyBits(), 1e-12);
        assertEquals(ColumnProfile.MeasureAccuracy.EXACT, status.entropyAccuracy());
        assertEquals(2, status.topValues().size());
        assertTrue(status.topValues().stream().allMatch(item -> item.protectedValue().startsWith("<redacted:length=")));

        var quantity = result.profiles().get(1).statistics();
        assertEquals(19, quantity.numericSummary().count());
        assertEquals(10, quantity.numericSummary().mean(), 1e-12);
        assertEquals(30, quantity.numericSummary().variance(), 1e-12);
        assertTrue(quantity.anomalies().stream().anyMatch(item ->
                item.code().equals("PHYSICAL_TYPE_OUTLIER") && item.count() == 1));
        assertTrue(quantity.anomalies().stream().anyMatch(item ->
                item.code().equals("RARE_FORMAT") && item.count() == 1));

        var contact = result.profiles().get(2).statistics();
        assertEquals("core:email", contact.dominantSemanticType());
        assertEquals(.95, contact.semanticValidRatio(), 1e-12);
        assertEquals(.05, contact.semanticInvalidRatio(), 1e-12);
        assertTrue(contact.anomalies().stream().anyMatch(item ->
                item.code().equals("SEMANTIC_INVALID") && item.count() == 1));
        assertTrue(contact.anomalies().stream().flatMap(item -> item.locations().stream())
                .allMatch(location -> location.protectedValue().startsWith("<redacted:length=")));
    }

    @Test void singleValueProfileHasExactZeroEntropyAndUniqueRatioOne() {
        var reader = new MemoryReader(List.of("Only"), List.of(List.of("00123"), List.of("")), new AtomicBoolean());
        var result = MappingEngine.builder().readers(List.of(reader)).build().analyze(new AnalysisRequest(
                new MemorySource(), new TargetSchema("single", "1", "", "", List.of(
                        new TargetField("id", "Only", List.of(), PhysicalType.TEXT, Set.of(), false))), null));
        var statistics = result.profiles().getFirst().statistics();
        assertEquals(1, statistics.cardinality().value());
        assertEquals(1, statistics.uniqueRatio());
        assertEquals(0, statistics.entropyBits());
        assertEquals(PhysicalType.TEXT, result.profiles().getFirst().inferredType());
        assertTrue(statistics.anomalies().stream().anyMatch(item ->
                item.code().equals("NULL_PRESENT") && item.count() == 1));
    }

    @Test void highCardinalitySwitchesToDeclaredBoundedEstimates() {
        var rows = new ArrayList<List<String>>();
        for (int i = 0; i < 100; i++) rows.add(List.of("synthetic-" + i));
        var defaults = EngineConfig.defaults();
        var limits = new EngineLimits(10_000, 200, 2, 100, 100, 2, 3, 10, 10,
                Duration.ofSeconds(5), 3, 2, 5, 10);
        var config = new EngineConfig("estimated", limits, defaults.weights(), 2,
                .9, .7, .5, .15, .3, false);
        var reader = new MemoryReader(List.of("Identifier"), rows, new AtomicBoolean());
        var result = MappingEngine.builder().readers(List.of(reader)).configuration(config).build()
                .analyze(new AnalysisRequest(new MemorySource(), new TargetSchema("high", "1", "", "", List.of(
                        new TargetField("id", "Identifier", List.of(), PhysicalType.TEXT, Set.of(), false))), null));
        var statistics = result.profiles().getFirst().statistics();
        assertEquals(ColumnProfile.MeasureAccuracy.ESTIMATED, statistics.cardinality().accuracy());
        assertEquals("HyperLogLog p=10, 1024 registers", statistics.cardinality().method());
        assertEquals(HyperLogLogSketch.EXPECTED_RELATIVE_ERROR,
                statistics.cardinality().expectedRelativeError(), 1e-12);
        assertEquals(2, statistics.topValues().size());
        assertTrue(statistics.topValues().stream()
                .allMatch(item -> item.accuracy() == ColumnProfile.MeasureAccuracy.ESTIMATED));
        assertEquals(ColumnProfile.MeasureAccuracy.ESTIMATED, statistics.entropyAccuracy());
        assertNotNull(statistics.entropyErrorBound());
    }

    @Test void largeSchemaPruningIsBoundedExplainedAndNeverRemovesExactAlias() {
        var defaults = EngineConfig.defaults();
        var boundedLimits = new EngineLimits(10_000, 20, 10, 100, 100, 2, 3, 10, 10,
                Duration.ofSeconds(5), 100, 3, 5, 2);
        var config = new EngineConfig("pruning-test", boundedLimits, defaults.weights(), 2,
                .9, .7, .5, .15, .3, false, 3, 1,
                EngineConfig.LexicalStrategy.ENHANCED_0_2);
        var fields = List.of(
                new TargetField("a", "Other Alpha", List.of("Exact Other Alpha"), PhysicalType.TEXT, Set.of(), false),
                new TargetField("b", "Other Beta", List.of("Exact Other Beta"), PhysicalType.TEXT, Set.of(), false),
                new TargetField("c", "Other Gamma", List.of("Exact Other Gamma"), PhysicalType.TEXT, Set.of(), false),
                new TargetField("d", "Other Delta", List.of("Exact Other Delta"), PhysicalType.TEXT, Set.of(), false),
                new TargetField("exact", "Exact", List.of("Exact Alias"), PhysicalType.TEXT, Set.of(), false));
        var reader = new MemoryReader(List.of("Exact Alias"), List.of(List.of("synthetic")), new AtomicBoolean());
        AnalysisResult result = MappingEngine.builder().readers(List.of(reader)).configuration(config).build()
                .analyze(new AnalysisRequest(new MemorySource(),
                        new TargetSchema("large", "1", "", "", fields), null));

        assertEquals("exact", result.candidatesByColumn().get("c0").getFirst().targetFieldId());
        assertTrue(result.candidatesByColumn().get("c0").size() <= 2);
        assertEquals(2, result.prunedCandidatesByColumn().get("c0").size());
        assertTrue(result.prunedCandidatesByColumn().get("c0").stream()
                .noneMatch(item -> item.targetFieldId().equals("exact")));
        assertTrue(result.prunedCandidatesByColumn().get("c0").stream()
                .allMatch(item -> !item.reason().isBlank()));
        assertTrue(result.warnings().stream().anyMatch(item -> item.contains("retained 2 of 3")));
    }

    @Test void sourceMutationDuringAnalysisInvalidatesResult() {
        var calls = new AtomicInteger();
        var changing = new MemorySource() {
            @Override public String sha256() { return calls.getAndIncrement() == 0 ? "before" : "after"; }
        };
        var reader = new MemoryReader(List.of("A"), List.of(List.of("x")), new AtomicBoolean());
        var error = assertThrows(EngineException.class, () -> MappingEngine.builder().readers(List.of(reader)).build()
                .analyze(new AnalysisRequest(changing, schemaWithThreeTargets(), null)));
        assertEquals("SOURCE_CHANGED", error.code());
    }

    @Test void explicitPlanDryRunStreamsCountsMasksAndNeverNeedsADestination() {
        var closed = new AtomicBoolean();
        var reader = new MemoryReader(List.of("Name", "Quantity"), List.of(
                List.of("Synthetic One", "2"), List.of("", "not-a-number")), closed);
        var schema = new TargetSchema("items", "1", "", "en-US", List.of(
                new TargetField("item.name", "Name", List.of(), PhysicalType.TEXT, Set.of(), true),
                new TargetField("item.quantity", "Quantity", List.of(), PhysicalType.INTEGER, Set.of(), true)));
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var source = new MemorySource();
        AnalysisResult analysis = engine.analyze(new AnalysisRequest(source, schema, null));
        MappingPlan defaults = new MappingPlanner().create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "item.name", "test confirmation"),
                new MappingPlanner.Selection("c1", "item.quantity", "test confirmation")));

        DryRunResult result = engine.dryRun(new DryRunRequest(source, schema, defaults,
                AnalysisOptions.defaults(), new DryRunOptions(DryRunOptions.ErrorPolicy.COLLECT_ERRORS, 10, 2)));

        assertTrue(closed.get());
        assertEquals(2, result.rowsProcessed());
        assertEquals(1, result.rowsValid());
        assertEquals(1, result.rowsInvalid());
        assertEquals(4, result.cellsProcessed());
        assertEquals(3, result.nonEmptyCells());
        assertEquals(2, result.fieldsMapped());
        assertEquals(2, result.transformationsApplied());
        assertEquals(3, result.validationsExecuted());
        assertEquals(2, result.totalErrorCount());
        assertEquals(2, result.issueSamples().size());
        assertTrue(result.issueSamples().stream().allMatch(issue -> issue.protectedValue().startsWith("<redacted:length=")));
        assertTrue(result.issueSamples().stream().noneMatch(issue -> issue.toString().contains("not-a-number")));
        assertFalse(result.terminatedEarly());
    }

    @Test void dryRunRejectsChangedSourceAndBoundsErrorsWithControlledTermination() {
        var closed = new AtomicBoolean();
        var reader = new MemoryReader(List.of("Required"), List.of(List.of(""), List.of("")), closed);
        var schema = new TargetSchema("required", "1", "", "", List.of(
                new TargetField("required.value", "Required", List.of(), PhysicalType.TEXT, Set.of(), true)));
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var source = new MemorySource();
        var analysis = engine.analyze(new AnalysisRequest(source, schema, null));
        var plan = new MappingPlanner().create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "required.value", "test confirmation")));

        var changed = new MemorySource() { @Override public String sha256() { return "different"; } };
        assertEquals("INVALIDATE_PLAN", assertThrows(EngineException.class, () -> engine.dryRun(
                new DryRunRequest(changed, schema, plan, null, null))).code());

        closed.set(false);
        DryRunResult bounded = engine.dryRun(new DryRunRequest(source, schema, plan, null,
                new DryRunOptions(DryRunOptions.ErrorPolicy.COLLECT_ERRORS, 1, 1)));
        assertTrue(bounded.terminatedEarly());
        assertEquals("MAX_ERRORS_REACHED", bounded.terminationCode());
        assertEquals(1, bounded.rowsProcessed());
        assertEquals(1, bounded.issueSamples().size());
        assertEquals(1, bounded.totalErrorCount());
        assertTrue(closed.get(), "maxErrors termination must close the dataset");
    }

    @Test void mappingPlannerRequiresExplicitValidSelections() {
        var reader = new MemoryReader(List.of("Name"), List.of(List.of("Synthetic")), new AtomicBoolean());
        var schema = new TargetSchema("items", "1", "", "", List.of(
                new TargetField("item.name", "Name", List.of(), PhysicalType.TEXT, Set.of(), true)));
        var analysis = MappingEngine.builder().readers(List.of(reader)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null));
        var planner = new MappingPlanner();
        assertThrows(IllegalArgumentException.class, () -> planner.create(analysis, schema, List.of(
                new MappingPlanner.Selection("missing", "item.name", "test"))));
        assertThrows(IllegalArgumentException.class, () -> planner.create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "missing", "test"))));
        assertThrows(IllegalArgumentException.class, () -> planner.create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "item.name", "first"),
                new MappingPlanner.Selection("c0", "item.name", "second"))));

        var changedSchema = new TargetSchema("different", "1", "", "", schema.fields());
        assertEquals("PLAN_SCHEMA_MISMATCH", assertThrows(EngineException.class,
                () -> planner.create(analysis, changedSchema, List.of())).code());
        var oldAnalysis = new AnalysisResult(analysis.formatVersion(), "0.2.0-SNAPSHOT", analysis.calibration(),
                analysis.sourceId(), analysis.sourceFingerprint(), analysis.schemaId(), analysis.schemaVersion(),
                analysis.schemaFingerprint(), analysis.configurationVersion(), analysis.configurationFingerprint(),
                analysis.structure(), analysis.rowsProcessed(), analysis.profiles(), analysis.candidatesByColumn(),
                analysis.prunedCandidatesByColumn(), analysis.decisionsByColumn(), analysis.unmatchedColumns(),
                analysis.conflicts(), analysis.warnings(), analysis.errors());
        assertEquals("PLAN_ENGINE_MISMATCH", assertThrows(EngineException.class,
                () -> planner.create(oldAnalysis, schema, List.of())).code());

        var base = planner.create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "item.name", "test")));
        var configured = planner.configure(base, "c0", List.of(new MappingPlan.Step("core:string-normalize")),
                List.of(new MappingPlan.Step("core:length", "1", Map.of("max", "20"))), "reviewed steps");
        assertNotEquals(base.planId(), configured.planId());
        assertEquals("core:length", configured.mappings().getFirst().validations().getFirst().id());
        assertEquals(configured, planner.configure(base, "c0",
                List.of(new MappingPlan.Step("core:string-normalize")),
                List.of(new MappingPlan.Step("core:length", "1", Map.of("max", "20"))), "reviewed steps"));
        assertThrows(IllegalArgumentException.class, () -> planner.configure(base, "missing", List.of(), List.of(), "test"));
    }

    @Test void dryRunErrorPoliciesDistinguishInvalidSkippedAndFailFast() {
        var reader = new MemoryReader(List.of("Required"), List.of(List.of(""), List.of("")), new AtomicBoolean());
        var schema = new TargetSchema("required", "1", "", "", List.of(
                new TargetField("required.value", "Required", List.of(), PhysicalType.TEXT, Set.of(), true)));
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var source = new MemorySource();
        var analysis = engine.analyze(new AnalysisRequest(source, schema, null));
        var plan = new MappingPlanner().create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "required.value", "test")));

        var skipped = engine.dryRun(new DryRunRequest(source, schema, plan, null,
                new DryRunOptions(DryRunOptions.ErrorPolicy.SKIP_ROW, 10, 0)));
        assertEquals(2, skipped.rowsSkipped());
        assertEquals(0, skipped.rowsInvalid());
        assertEquals(2, skipped.totalErrorCount());
        assertTrue(skipped.issueSamples().isEmpty());
        assertFalse(skipped.errorCodes().isEmpty());

        var failFast = engine.dryRun(new DryRunRequest(source, schema, plan, null,
                new DryRunOptions(DryRunOptions.ErrorPolicy.FAIL_FAST, 10, 1)));
        assertEquals(1, failFast.rowsProcessed());
        assertEquals(1, failFast.rowsInvalid());
        assertTrue(failFast.terminatedEarly());
        assertEquals("FAIL_FAST_DATA_ERROR", failFast.terminationCode());
    }

    @Test void dryRunRejectsConfigurationSchemaAndIncompletePlanMismatches() {
        var reader = new MemoryReader(List.of("Name"), List.of(List.of("Synthetic")), new AtomicBoolean());
        var schema = new TargetSchema("items", "1", "", "", List.of(
                new TargetField("item.name", "Name", List.of(), PhysicalType.TEXT, Set.of(), true)));
        var source = new MemorySource();
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var analysis = engine.analyze(new AnalysisRequest(source, schema, null));
        var plan = new MappingPlanner().create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "item.name", "test")));

        var differentOptions = new AnalysisOptions(Map.of("header", "first"), 42);
        assertEquals("REQUIRE_REANALYSIS", assertThrows(EngineException.class, () -> engine.dryRun(
                new DryRunRequest(source, schema, plan, differentOptions, null))).code());
        var otherSchema = new TargetSchema("items", "2", "", "", schema.fields());
        assertEquals("REQUIRE_REANALYSIS", assertThrows(EngineException.class, () -> engine.dryRun(
                new DryRunRequest(source, otherSchema, plan, null, null))).code());

        var incomplete = new MappingPlanner().create(analysis, schema, List.of());
        assertEquals("INCOMPLETE_PLAN", assertThrows(EngineException.class, () -> engine.dryRun(
                new DryRunRequest(source, schema, incomplete, null, null))).code());
    }

    @Test void plannerDerivesOnlyImplementedStepsForEverySupportedTargetKind() {
        var semantics = List.of("br:cpf", "br:phone", "br:cep", "core:email", "", "", "", "");
        var physical = List.of(PhysicalType.TEXT, PhysicalType.TEXT, PhysicalType.TEXT, PhysicalType.TEXT,
                PhysicalType.INTEGER, PhysicalType.DECIMAL, PhysicalType.DATE, PhysicalType.BOOLEAN);
        var fields = new ArrayList<TargetField>();
        var headers = new ArrayList<String>();
        var values = new ArrayList<String>();
        for (int index = 0; index < physical.size(); index++) {
            headers.add("Field " + index); values.add("value");
            Set<SemanticType> accepted = semantics.get(index).isEmpty()
                    ? Set.of() : Set.of(new SemanticType(semantics.get(index)));
            fields.add(new TargetField("target." + index, "Field " + index, List.of(), physical.get(index),
                    accepted, true, false));
        }
        var schema = new TargetSchema("all-types", "1", "", "pt-BR", fields);
        var reader = new MemoryReader(headers, List.of(values), new AtomicBoolean());
        var analysis = MappingEngine.builder().readers(List.of(reader)).build()
                .analyze(new AnalysisRequest(new MemorySource(), schema, null));
        var selections = new ArrayList<MappingPlanner.Selection>();
        for (int index = 0; index < fields.size(); index++)
            selections.add(new MappingPlanner.Selection("c" + index, fields.get(index).id(), "test"));
        var plan = new MappingPlanner().create(analysis, schema, selections);

        assertEquals("br:cpf-canonical", plan.mappings().get(0).transformations().getFirst().id());
        assertEquals("br:cpf-checksum", plan.mappings().get(0).validations().get(1).id());
        assertEquals("br:phone-canonical", plan.mappings().get(1).transformations().getFirst().id());
        assertEquals("br:cep-canonical", plan.mappings().get(2).transformations().getFirst().id());
        assertEquals("core:regex", plan.mappings().get(3).validations().get(1).id());
        assertEquals("core:long", plan.mappings().get(4).transformations().getFirst().id());
        assertEquals("pt-BR", plan.mappings().get(5).transformations().getFirst().options().get("locale"));
        assertEquals("uuuu-MM-dd|dd/MM/uuuu", plan.mappings().get(6).transformations().getFirst().options().get("formats"));
        assertEquals("core:boolean", plan.mappings().get(7).transformations().getFirst().id());
        assertTrue(plan.unmappedSourceColumns().isEmpty());
        assertEquals(8, plan.confirmedSourceColumns().size());
    }

    @Test void dryRunAggregatesDifferentIssueKindsAndWarningsWithoutValues() {
        var headers = List.of("Normalize", "Regex", "Length", "Enum", "Number", "Date");
        var row = List.of("  SECRET  ", "abc", "abcd", "other", "11", "2026-01-01");
        var fields = List.of(
                new TargetField("normalize", "Normalize", List.of(), PhysicalType.TEXT, Set.of(), false, false),
                new TargetField("regex", "Regex", List.of(), PhysicalType.TEXT, Set.of(), false, false),
                new TargetField("length", "Length", List.of(), PhysicalType.TEXT, Set.of(), false, false),
                new TargetField("enum", "Enum", List.of(), PhysicalType.TEXT, Set.of(), false, false),
                new TargetField("number", "Number", List.of(), PhysicalType.DECIMAL, Set.of(), false, false),
                new TargetField("date", "Date", List.of(), PhysicalType.DATE, Set.of(), false, false));
        var schema = new TargetSchema("issues", "1", "", "en-US", fields);
        var source = new MemorySource();
        var engine = MappingEngine.builder().readers(List.of(
                new MemoryReader(headers, List.of(row), new AtomicBoolean()))).build();
        var analysis = engine.analyze(new AnalysisRequest(source, schema, null));
        var base = new MappingPlanner().create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "normalize", "test"),
                new MappingPlanner.Selection("c1", "regex", "test"),
                new MappingPlanner.Selection("c2", "length", "test"),
                new MappingPlanner.Selection("c3", "enum", "test"),
                new MappingPlanner.Selection("c4", "number", "test"),
                new MappingPlanner.Selection("c5", "date", "test")));
        var mappings = List.of(
                configured(base, 0, List.of(new MappingPlan.Step("core:string-normalize")), List.of()),
                configured(base, 1, List.of(), List.of(new MappingPlan.Step("core:regex", "1", Map.of("pattern", "\\d+")))),
                configured(base, 2, List.of(), List.of(new MappingPlan.Step("core:length", "1", Map.of("max", "3")))),
                configured(base, 3, List.of(), List.of(new MappingPlan.Step("core:enum", "1", Map.of("values", "new|done")))),
                configured(base, 4, List.of(new MappingPlan.Step("core:big-decimal", "1", Map.of("locale", "en-US"))),
                        List.of(new MappingPlan.Step("core:numeric-range", "1", Map.of("max", "10")))),
                configured(base, 5, List.of(new MappingPlan.Step("core:local-date")),
                        List.of(new MappingPlan.Step("core:date-range", "1", Map.of("min", "2026-02-01")))));
        var plan = new MappingPlan(base.formatVersion(), base.planId(), base.engineVersion(), base.sourceId(),
                base.sourceFingerprint(), base.schemaId(), base.schemaVersion(), base.schemaFingerprint(),
                base.configurationVersion(), base.configurationFingerprint(), mappings, List.of(),
                base.confirmedSourceColumns());

        var result = engine.dryRun(new DryRunRequest(source, schema, plan, null,
                new DryRunOptions(DryRunOptions.ErrorPolicy.COLLECT_ERRORS, 20, 10, 2)));
        assertEquals(1, result.rowsInvalid());
        assertEquals(5, result.totalErrorCount());
        assertEquals(1, result.totalWarningCount());
        assertEquals(2, result.fieldErrors().size());
        assertEquals(2, result.errorCodes().size());
        assertTrue(result.warningCodes().containsKey("STRING_NORMALIZED"));
        assertTrue(result.issueSamples().stream().allMatch(issue -> !issue.protectedValue().contains("SECRET")));
    }

    @Test void transformationWarningProducesValidWithWarningsUnlessAnotherRuleFails() {
        var reader = new MemoryReader(List.of("Name"), List.of(List.of("  Synthetic   Name  ")), new AtomicBoolean());
        var schema = new TargetSchema("warning", "1", "", "", List.of(
                new TargetField("name", "Name", List.of(), PhysicalType.TEXT, Set.of(), false, false)));
        var source = new MemorySource();
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var analysis = engine.analyze(new AnalysisRequest(source, schema, null));
        var base = new MappingPlanner().create(analysis, schema, List.of(
                new MappingPlanner.Selection("c0", "name", "test")));
        var mapping = new MappingPlan.FieldMapping("c0", "name",
                List.of(new MappingPlan.Step("core:string-normalize")), List.of(), true, "test");
        var plan = new MappingPlan(base.formatVersion(), base.planId(), base.engineVersion(), base.sourceId(),
                base.sourceFingerprint(), base.schemaId(), base.schemaVersion(), base.schemaFingerprint(),
                base.configurationVersion(), base.configurationFingerprint(), List.of(mapping), List.of(), List.of("c0"));

        var result = engine.dryRun(new DryRunRequest(source, schema, plan, null, null));
        assertEquals(1, result.rowsValidWithWarnings());
        assertEquals(0, result.rowsInvalid());
        assertEquals(1, result.totalWarningCount());
        assertEquals(1, result.manualReviewRequiredCount());
    }

    @Test void requestScopedHistoryExplainsInfluenceAndStrongCurrentEvidenceWins() {
        var normalizer = new HeaderNormalizer(Map.of(
                "cod", List.of("codigo"), "cli", List.of("cliente")));
        var schema = new TargetSchema("customer", "1", "customer", "pt-BR", List.of(
                new TargetField("customer.code", "Customer identifier", List.of(),
                        PhysicalType.TEXT, Set.of(), false),
                new TargetField("customer.document", "CPF", List.of("Documento"),
                        PhysicalType.TEXT, Set.of(new SemanticType("br:cpf")), false)));
        var knowledge = new InMemoryMappingKnowledgeBase();
        var firstEngine = MappingEngine.builder().readers(List.of(new MemoryReader(
                        List.of("Cod Cli"), List.of(List.of("A-001")), new AtomicBoolean())))
                .normalizer(normalizer).build();
        AnalysisResult first = firstEngine.analyze(new AnalysisRequest(new MemorySource(), schema, null));
        MappingFeedback confirmation = MappingFeedback.confirmed("confirm-code", "2026-01-01T00:00:00Z",
                first, schema, "c0", "customer.code", normalizer, "synthetic-test");
        knowledge.record(confirmation);

        var futureReader = new MemoryReader(List.of("Cod. Cliente"), List.of(List.of("A-002")), new AtomicBoolean());
        var futureEngine = MappingEngine.builder().readers(List.of(futureReader)).normalizer(normalizer).build();
        AnalysisResult without = futureEngine.analyze(new AnalysisRequest(new MemorySource(), schema, null));
        AnalysisResult with = futureEngine.analyze(new AnalysisRequest(
                new MemorySource(), schema, null, knowledge));
        var withoutCode = without.candidatesByColumn().get("c0").stream()
                .filter(item -> item.targetFieldId().equals("customer.code")).findFirst().orElseThrow();
        var withCode = with.candidatesByColumn().get("c0").stream()
                .filter(item -> item.targetFieldId().equals("customer.code")).findFirst().orElseThrow();
        var history = withCode.components().stream().filter(item -> item.id().equals("history")).findFirst().orElseThrow();
        assertTrue(history.available());
        assertTrue(history.contribution() > 0);
        assertEquals(1, with.historicalEvidenceByColumn().get("c0").getFirst().confirmedCount());
        assertNotEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID, with.knowledgeSnapshotId());
        assertTrue(withCode.score() >= withoutCode.score());

        var conflictingKnowledge = new InMemoryMappingKnowledgeBase();
        for (int index = 0; index < 20; index++) {
            conflictingKnowledge.record(MappingFeedback.confirmed("wrong-" + index,
                    "2026-01-01T00:00:" + String.format("%02d", index) + "Z",
                    first, schema, "c0", "customer.code", normalizer, "synthetic-test"));
        }
        var cpfReader = new MemoryReader(List.of("Cod Cli"), List.of(
                List.of("529.982.247-25"), List.of("111.444.777-35")), new AtomicBoolean());
        SemanticType cpfType = new SemanticType("br:cpf");
        MappingEngine cpfEngine = MappingEngine.builder().readers(List.of(cpfReader))
                .semanticDetectors(List.of(new SemanticDetector() {
                    @Override public SemanticType type() { return cpfType; }
                    @Override public Accumulator newAccumulator() {
                        return new Accumulator() {
                            long count;
                            @Override public void accept(String raw) { count++; }
                            @Override public SemanticEvidence finish(int minimumEvidenceValues) {
                                return new SemanticEvidence(cpfType, count, count, count, 0, 1, 1,
                                        1, true, "synthetic strong CPF evidence");
                            }
                        };
                    }
                })).normalizer(normalizer).build();
        AnalysisResult currentWins = cpfEngine.analyze(new AnalysisRequest(
                new MemorySource(), schema, null, conflictingKnowledge));
        assertEquals("customer.document", currentWins.candidatesByColumn().get("c0").getFirst().targetFieldId());
        assertTrue(currentWins.candidatesByColumn().get("c0").stream()
                .filter(candidate -> candidate.targetFieldId().equals("customer.code"))
                .findFirst().orElseThrow().eligible() == false);
    }

    @Test void noOpIsTheBackwardCompatibleDefaultAndPlanFreezesKnowledgeSnapshot() {
        var reader = new MemoryReader(List.of("Name"), List.of(List.of("Synthetic")), new AtomicBoolean());
        var schema = new TargetSchema("items", "1", "items", "", List.of(
                new TargetField("item.name", "Name", List.of(), PhysicalType.TEXT, Set.of(), true)));
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var source = new MemorySource();
        AnalysisResult implicit = engine.analyze(new AnalysisRequest(source, schema, null));
        AnalysisResult explicit = engine.analyze(new AnalysisRequest(
                source, schema, null, NoOpMappingKnowledgeBase.INSTANCE));
        assertEquals(implicit.candidatesByColumn(), explicit.candidatesByColumn());
        assertEquals(implicit.decisionsByColumn(), explicit.decisionsByColumn());
        assertEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID, implicit.knowledgeSnapshotId());

        MappingPlan plan = new MappingPlanner().create(implicit, schema, List.of(
                new MappingPlanner.Selection("c0", "item.name", "test")));
        assertEquals("1.2", plan.formatVersion());
        assertEquals(implicit.knowledgeSnapshotId(), plan.knowledgeSnapshotId());
        assertEquals(implicit.knowledgeVersion(), plan.knowledgeVersion());
        var knowledge = new InMemoryMappingKnowledgeBase();
        knowledge.record(MappingFeedback.confirmed("later", "2026-01-01T00:00:00Z", implicit,
                schema, "c0", "item.name", new HeaderNormalizer(Map.of()), "synthetic-test"));
        assertEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID, plan.knowledgeSnapshotId(),
                "later feedback must not mutate an existing plan");
        long eventsBeforeDryRun = knowledge.snapshot().eventCount();
        assertEquals(1, engine.dryRun(new DryRunRequest(source, schema, plan, null, null)).rowsValid());
        assertEquals(eventsBeforeDryRun, knowledge.snapshot().eventCount(),
                "dry run must not record or change feedback");
    }

    private static MappingPlan.FieldMapping configured(MappingPlan base, int index,
            List<MappingPlan.Step> transformations, List<MappingPlan.Step> validations) {
        var mapping = base.mappings().get(index);
        return new MappingPlan.FieldMapping(mapping.sourceColumnId(), mapping.targetFieldId(), transformations,
                validations, true, "explicit test configuration");
    }

    private static EngineConfig config(EngineConfig defaults, EngineLimits limits, boolean autoMap, double minCoverage) {
        return new EngineConfig("test", limits, defaults.weights(), 2, .9, .7, .5, .1, minCoverage, autoMap);
    }

    private static TargetSchema schemaWithThreeTargets() {
        return new TargetSchema("x", "1", "", "", List.of(
                new TargetField("a", "Alpha", List.of(), PhysicalType.TEXT, Set.of(), false),
                new TargetField("b", "Beta", List.of(), PhysicalType.TEXT, Set.of(), false),
                new TargetField("c", "Gamma", List.of(), PhysicalType.TEXT, Set.of(), false)));
    }

    private static void assertScoreExplanationConsistent(AnalysisResult.MappingCandidate candidate) {
        double numerator = candidate.components().stream().mapToDouble(AnalysisResult.ScoreComponent::contribution).sum();
        double denominator = candidate.components().stream().filter(AnalysisResult.ScoreComponent::available)
                .mapToDouble(c -> c.weight() * c.reliability()).sum();
        assertEquals(numerator / denominator, candidate.score(), 1e-12);
    }

    private static class MemorySource implements TabularSource {
        private final byte[] bytes = "fixture".getBytes(StandardCharsets.UTF_8);
        public String id() { return "memory.csv"; } public String fileName() { return "memory.csv"; }
        public long size() { return bytes.length; } public InputStream openStream() { return new ByteArrayInputStream(bytes); }
        public String sha256() { return "fixture-sha256"; }
    }

    private static class MemoryReader implements DataReader {
        private final List<String> headers; private final List<List<String>> data; private final AtomicBoolean closed;
        MemoryReader(List<String> headers, List<List<String>> data, AtomicBoolean closed) {
            this.headers = headers; this.data = data; this.closed = closed;
        }
        public boolean supports(TabularSource source) { return source.fileName().endsWith(".csv"); }
        public SourceStructure detect(TabularSource source, AnalysisOptions options, EngineLimits limits) {
            var columns = new ArrayList<SourceColumn>();
            for (int i = 0; i < headers.size(); i++) columns.add(new SourceColumn("c" + i, i, headers.get(i), headers.get(i).toLowerCase()));
            return new SourceStructure("MEMORY", "UTF-8", ",", true, columns, List.of());
        }
        public Dataset open(TabularSource source, SourceStructure structure, AnalysisOptions options, EngineLimits limits) {
            return new Dataset() {
                boolean used;
                public SourceStructure structure() { return structure; }
                public Iterator<Row> rows() {
                    if (used) throw new IllegalStateException("single pass"); used = true;
                    var converted = new ArrayList<Row>(); long number = 1;
                    for (var values : data) converted.add(new Row(number, number++, values));
                    return converted.iterator();
                }
                public List<String> warnings() { return List.of(); }
                public void close() { closed.set(true); }
            };
        }
    }
}
