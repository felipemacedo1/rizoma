package io.github.rizoma.core;

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
