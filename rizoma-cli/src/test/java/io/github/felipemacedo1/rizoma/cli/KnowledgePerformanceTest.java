package io.github.felipemacedo1.rizoma.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.AnalysisRequest;
import io.github.felipemacedo1.rizoma.core.AnalysisResult;
import io.github.felipemacedo1.rizoma.core.InMemoryMappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.MappingEngine;
import io.github.felipemacedo1.rizoma.core.MappingFeedback;
import io.github.felipemacedo1.rizoma.core.MappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.NoOpMappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.PathTabularSource;
import io.github.felipemacedo1.rizoma.csv.CsvDataReader;
import io.github.felipemacedo1.rizoma.ptbr.PtBrHeaderRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reproducible integration timing; deliberately not presented as a JMH benchmark. */
class KnowledgePerformanceTest {
    private static final int LARGE_EVENT_COUNT = 20_000;

    @Test void measuresNoOpSmallAndLargerIndexedKnowledgeSnapshots() throws Exception {
        Path root = Path.of("..", "corpus").toAbsolutePath().normalize();
        Path source = root.resolve("feedback/evaluation/code.csv");
        var schema = JsonSupport.readSchema(root.resolve("clientes.schema.json"));
        var engine = MappingEngine.builder().readers(List.of(new CsvDataReader()))
                .normalizer(PtBrHeaderRules.normalizer()).build();
        AnalysisOptions options = new AnalysisOptions(Map.of("header", "first", "delimiter", ","), 42L);
        AnalysisResult seed = engine.analyze(new AnalysisRequest(new PathTabularSource(source), schema,
                options, NoOpMappingKnowledgeBase.INSTANCE));

        var small = new InMemoryMappingKnowledgeBase();
        small.record(feedback("small-0", Instant.parse("2026-01-01T00:00:00Z"), seed, schema));
        var large = new InMemoryMappingKnowledgeBase(LARGE_EVENT_COUNT);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < LARGE_EVENT_COUNT; index++) {
            large.record(feedback("large-" + index, start.plusSeconds(index), seed, schema));
        }

        TimedResult noKnowledge = analyzeTimed(engine, source, schema, options,
                NoOpMappingKnowledgeBase.INSTANCE);
        TimedResult smallKnowledge = analyzeTimed(engine, source, schema, options, small);
        TimedResult largeKnowledge = analyzeTimed(engine, source, schema, options, large);

        assertEquals(0, noKnowledge.result().historicalEvidenceByColumn().get("c0").size());
        assertEquals(1, smallKnowledge.result().historicalEvidenceByColumn().get("c0")
                .getFirst().confirmedCount());
        assertEquals(LARGE_EVENT_COUNT, largeKnowledge.result().historicalEvidenceByColumn().get("c0")
                .getFirst().confirmedCount());
        assertTrue(noKnowledge.nanos() > 0 && smallKnowledge.nanos() > 0 && largeKnowledge.nanos() > 0);

        var report = new TimingReport("1.0", "integration-timing-not-microbenchmark",
                Runtime.version().toString(), 0, small.snapshot().eventCount(), large.snapshot().eventCount(),
                noKnowledge.nanos(), smallKnowledge.nanos(), largeKnowledge.nanos());
        Path output = Path.of(System.getProperty("basedir", ".")).resolve("target/knowledge-performance.json");
        Files.createDirectories(output.getParent());
        JsonSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private static MappingFeedback feedback(String id, Instant timestamp, AnalysisResult seed,
            io.github.felipemacedo1.rizoma.core.TargetSchema schema) {
        return MappingFeedback.confirmed(id, timestamp.toString(), seed, schema, "c0", "customer.code",
                PtBrHeaderRules.normalizer(), "synthetic-performance-fixture");
    }

    private static TimedResult analyzeTimed(MappingEngine engine, Path source,
            io.github.felipemacedo1.rizoma.core.TargetSchema schema, AnalysisOptions options,
            MappingKnowledgeBase knowledge) {
        long started = System.nanoTime();
        AnalysisResult result = engine.analyze(new AnalysisRequest(
                new PathTabularSource(source), schema, options, knowledge));
        return new TimedResult(System.nanoTime() - started, result);
    }

    private record TimedResult(long nanos, AnalysisResult result) {}
    private record TimingReport(String formatVersion, String purpose, String javaVersion,
            long noKnowledgeEvents, long smallKnowledgeEvents, long largeKnowledgeEvents,
            long noKnowledgeNanos, long smallKnowledgeNanos, long largeKnowledgeNanos) {}
}
