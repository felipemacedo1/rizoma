package io.github.rizoma.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.AnalysisRequest;
import io.github.rizoma.core.AnalysisResult;
import io.github.rizoma.core.CoreSemanticDetectors;
import io.github.rizoma.core.EngineConfig;
import io.github.rizoma.core.MappingEngine;
import io.github.rizoma.core.MappingQualityEvaluator;
import io.github.rizoma.core.PathTabularSource;
import io.github.rizoma.core.PhysicalType;
import io.github.rizoma.core.SemanticDetector;
import io.github.rizoma.csv.CsvDataReader;
import io.github.rizoma.ptbr.PtBrDetectors;
import io.github.rizoma.ptbr.PtBrHeaderRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CorpusEvaluationTest {
    @Test void evaluatesIndependentSyntheticDatasetsAndWritesReproducibleReport() throws Exception {
        Path module = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        Path corpus = module.getParent().resolve("corpus");
        CorpusManifest manifest = JsonSupport.MAPPER.readValue(corpus.resolve("manifest.json").toFile(),
                CorpusManifest.class);
        var configurations = new LinkedHashMap<String, EngineConfig>();
        configurations.put("baseline-0.1", config("baseline-0.1", weights(.30, .30, .10, .15),
                EngineConfig.LexicalStrategy.BASELINE_0_1));
        configurations.put("lexical-only", config("lexical-only", weights(1, 0, 0, 0),
                EngineConfig.LexicalStrategy.ENHANCED_0_2));
        configurations.put("lexical-semantic", config("lexical-semantic", weights(.5, .5, 0, 0),
                EngineConfig.LexicalStrategy.ENHANCED_0_2));
        configurations.put("lexical-semantic-pattern", config("lexical-semantic-pattern",
                weights(.4, .4, 0, .2), EngineConfig.LexicalStrategy.ENHANCED_0_2));
        configurations.put("full-0.2", EngineConfig.defaults());

        var reports = new LinkedHashMap<String, MappingQualityEvaluator.EvaluationReport>();
        for (var configuration : configurations.entrySet()) {
            reports.put(configuration.getKey(), evaluate(corpus, manifest, configuration.getValue()));
        }
        var repeated = evaluate(corpus, manifest, EngineConfig.defaults());
        assertEquals(reports.get("full-0.2"), repeated);

        var full = reports.get("full-0.2");
        assertEquals(5, full.datasets());
        assertEquals(28, full.columns());
        assertEquals(23, full.mappableColumns());
        assertTrue(Double.isFinite(full.top1Accuracy()));
        assertTrue(Double.isFinite(full.top3Recall()));
        assertTrue(full.semanticConfusion().containsKey("br:cnpj"));
        assertEquals(1, full.semanticConfusion().get("br:cnpj").get("UNKNOWN"));

        var output = new CorpusReport("1.0", "synthetic-evaluation-not-calibration",
                configurations.keySet().stream().toList(), reports,
                reports.get("full-0.2").top1Accuracy() - reports.get("baseline-0.1").top1Accuracy(),
                reports.get("full-0.2").top3Recall() - reports.get("baseline-0.1").top3Recall());
        Path reportPath = module.resolve("target/corpus-evaluation.json");
        Files.createDirectories(reportPath.getParent());
        JsonSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), output);
        assertTrue(Files.size(reportPath) > 0);
    }

    @Test void hardCasesExposeSemanticValidityAmbiguityAndProtectedAnomalies() throws Exception {
        Path module = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        Path corpus = module.getParent().resolve("corpus");
        MappingEngine engine = engine(EngineConfig.defaults());

        var clients = engine.analyze(new AnalysisRequest(new PathTabularSource(corpus.resolve("clientes.csv")),
                JsonSupport.readSchema(corpus.resolve("clientes.schema.json")), AnalysisOptions.defaults()));
        var document = clients.profiles().get(1).statistics();
        assertEquals("br:cpf", document.dominantSemanticType());
        assertEquals(.95, document.semanticValidRatio(), 1e-12);
        var invalid = document.anomalies().stream().filter(item -> item.code().equals("SEMANTIC_INVALID"))
                .findFirst().orElseThrow();
        assertEquals(1, invalid.count());
        assertEquals(21, invalid.locations().getFirst().recordNumber());
        assertEquals(21, invalid.locations().getFirst().physicalLine());
        assertTrue(invalid.locations().getFirst().protectedValue().startsWith("<redacted:length="));
        assertEquals(PhysicalType.TEXT, clients.profiles().get(4).inferredType(),
                "leading-zero identifiers must remain text");
        assertEquals("customer.code", clients.candidatesByColumn().get("c4").getFirst().targetFieldId());
        assertTrue(clients.profiles().get(3).statistics().dominantSemanticType().isEmpty(),
                "mostly ambiguous dates must not be promoted to a dominant semantic type");

        var finance = engine.analyze(new AnalysisRequest(new PathTabularSource(corpus.resolve("financeiro.csv")),
                JsonSupport.readSchema(corpus.resolve("financeiro.schema.json")), AnalysisOptions.defaults()));
        assertEquals(.5, finance.profiles().get(4).statistics().semanticInvalidRatio(), 1e-12);
        assertEquals(AnalysisResult.DecisionStatus.NO_MATCH, finance.decisionsByColumn().get("c4").status());

        var orders = engine.analyze(new AnalysisRequest(new PathTabularSource(corpus.resolve("pedidos.csv")),
                JsonSupport.readSchema(corpus.resolve("pedidos.schema.json")), AnalysisOptions.defaults()));
        assertTrue(orders.profiles().get(5).statistics().dominantSemanticType().isEmpty(),
                "bare eleven-digit codes must remain semantically unknown");
        assertEquals(AnalysisResult.DecisionStatus.NO_MATCH, orders.decisionsByColumn().get("c5").status());
    }

    private static MappingQualityEvaluator.EvaluationReport evaluate(Path corpus,
            CorpusManifest manifest, EngineConfig config) throws Exception {
        MappingEngine engine = engine(config);
        var evaluated = new ArrayList<MappingQualityEvaluator.EvaluatedDataset>();
        for (CorpusDataset dataset : manifest.datasets()) {
            var result = engine.analyze(new AnalysisRequest(new PathTabularSource(corpus.resolve(dataset.source())),
                    JsonSupport.readSchema(corpus.resolve(dataset.schema())), AnalysisOptions.defaults()));
            var labels = dataset.labels().stream().map(label -> new MappingQualityEvaluator.ExpectedColumn(
                    label.sourceColumnId(), label.expectedTargetFieldId(), label.expectedSemanticType(),
                    label.shouldAbstain())).toList();
            evaluated.add(new MappingQualityEvaluator.EvaluatedDataset(dataset.id(), result, labels));
        }
        return new MappingQualityEvaluator().evaluate(evaluated, config);
    }

    private static MappingEngine engine(EngineConfig config) {
        List<SemanticDetector> detectors = new ArrayList<>(CoreSemanticDetectors.defaults());
        detectors.addAll(PtBrDetectors.defaults());
        return MappingEngine.builder().readers(List.of(new CsvDataReader()))
                .semanticDetectors(detectors).normalizer(PtBrHeaderRules.normalizer())
                .configuration(config).build();
    }

    private static EngineConfig config(String version, Map<String, Double> weights,
            EngineConfig.LexicalStrategy lexicalStrategy) {
        var defaults = EngineConfig.defaults();
        return new EngineConfig(version, defaults.limits(), weights, defaults.minimumEvidenceValues(),
                defaults.autoMapThreshold(), defaults.reviewThreshold(), defaults.lowThreshold(),
                defaults.minimumMargin(), defaults.minimumCoverage(), false,
                defaults.candidatePruningThreshold(), defaults.candidateShortlistSize(), lexicalStrategy);
    }

    private static Map<String, Double> weights(double lexical, double semantic,
            double physical, double pattern) {
        return Map.of("lexical", lexical, "semantic", semantic, "physicalType", physical,
                "pattern", pattern, "distribution", 0.0, "history", 0.0);
    }

    record CorpusManifest(List<CorpusDataset> datasets) {}
    record CorpusDataset(String id, String source, String schema, List<Label> labels) {}
    record Label(String sourceColumnId, String expectedTargetFieldId,
                 String expectedSemanticType, boolean shouldAbstain) {}
    record CorpusReport(String formatVersion, String purpose, List<String> ablationOrder,
                        Map<String, MappingQualityEvaluator.EvaluationReport> evaluations,
                        double top1DeltaFromBaseline, double top3DeltaFromBaseline) {}
}
