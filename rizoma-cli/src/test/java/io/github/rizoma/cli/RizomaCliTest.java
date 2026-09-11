package io.github.rizoma.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.AnalysisRequest;
import io.github.rizoma.core.AnalysisResult;
import io.github.rizoma.core.CoreSemanticDetectors;
import io.github.rizoma.core.EngineConfig;
import io.github.rizoma.core.MappingEngine;
import io.github.rizoma.core.PathTabularSource;
import io.github.rizoma.core.SemanticDetector;
import io.github.rizoma.csv.CsvDataReader;
import io.github.rizoma.ptbr.PtBrDetectors;
import io.github.rizoma.ptbr.PtBrHeaderRules;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RizomaCliTest {
    @TempDir Path temporary;

    @Test void mainFixtureProducesSevenTopOneMappingsAndExplainUsesSameReport() throws Exception {
        Path source = example("clientes.csv");
        Path schemaPath = example("customer.schema.json");
        Path reportPath = temporary.resolve("report.json");
        var output = new StringWriter(); var error = new StringWriter();
        int exit = RizomaCli.execute(new String[]{"analyze", source.toString(), "--schema", schemaPath.toString(),
                "--out", reportPath.toString()}, new PrintWriter(output, true), new PrintWriter(error, true));
        assertEquals(RizomaCli.OK, exit, error.toString());
        AnalysisResult cli = JsonSupport.MAPPER.readValue(reportPath.toFile(), AnalysisResult.class);
        assertEquals(20, cli.rowsProcessed());
        assertTop(cli, "c0", "customer.name");
        assertTop(cli, "c1", "customer.document");
        assertTop(cli, "c2", "customer.email");
        assertTop(cli, "c3", "customer.phone");
        assertTop(cli, "c4", "customer.birthDate");
        assertTop(cli, "c5", "customer.address");
        assertTop(cli, "c6", "customer.postalCode");
        assertTrue(cli.decisionsByColumn().values().stream().noneMatch(
                decision -> decision.status() == AnalysisResult.DecisionStatus.AUTO_MAP));
        assertFalse(Files.readString(reportPath).contains("cliente01@example.test"));

        var explainOut = new StringWriter();
        int explainExit = RizomaCli.execute(new String[]{"explain", reportPath.toString(), "--column", "CPF Cliente"},
                new PrintWriter(explainOut, true), new PrintWriter(new StringWriter(), true));
        assertEquals(RizomaCli.OK, explainExit);
        assertTrue(explainOut.toString().contains("customer.document"));
        assertTrue(explainOut.toString().contains("lexical:"));
        assertFalse(explainOut.toString().contains("529.982.247-25"));

        AnalysisResult library = libraryAnalyze(source, schemaPath);
        assertEquals(library.candidatesByColumn(), cli.candidatesByColumn());
        assertEquals(library.decisionsByColumn(), cli.decisionsByColumn());
    }

    @Test void documentContentOutranksIncompatibleCandidateAndBareDigitsDoNotAutoMap() throws Exception {
        Path source = temporary.resolve("document.csv");
        var csv = new StringBuilder("Documento\n");
        for (int i = 0; i < 18; i++) csv.append(i % 2 == 0 ? "529.982.247-25\n" : "11144477735\n");
        csv.append("SEM CPF\nABC123\n");
        Files.writeString(source, csv);
        Path schema = temporary.resolve("schema.json");
        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"x","schemaVersion":"1","fields":[
                  {"id":"customer.document","displayName":"CPF","aliases":[],"physicalType":"TEXT","semanticTypes":["br:cpf"],"required":false},
                  {"id":"customer.phone","displayName":"Telefone","aliases":[],"physicalType":"TEXT","semanticTypes":["br:phone"],"required":false}
                ]}
                """);
        Path report = temporary.resolve("document-report.json");
        assertEquals(0, run("analyze", source.toString(), "--schema", schema.toString(), "--out", report.toString(),
                "--delimiter", "comma", "--header", "first"));
        AnalysisResult result = JsonSupport.MAPPER.readValue(report.toFile(), AnalysisResult.class);
        assertTop(result, "c0", "customer.document");
        assertNotEquals(AnalysisResult.DecisionStatus.AUTO_MAP, result.decisionsByColumn().get("c0").status());

        Files.writeString(source, "Documento\n11999999999\n11888888888\n11777777777\n");
        assertEquals(0, run("analyze", source.toString(), "--schema", schema.toString(), "--out", report.toString(),
                "--delimiter", "comma", "--header", "first"));
        result = JsonSupport.MAPPER.readValue(report.toFile(), AnalysisResult.class);
        assertNotEquals(AnalysisResult.DecisionStatus.AUTO_MAP, result.decisionsByColumn().get("c0").status());
        assertEquals(0, result.profiles().getFirst().semanticEvidence().get("br:phone").reliability());
    }

    @Test void duplicateHeaderRequiresPositionAndUnknownSchemaPropertiesFail() throws Exception {
        Path source = temporary.resolve("duplicate.csv");
        Files.writeString(source, "Nome,Nome\nAna,Bia\n");
        Path schema = temporary.resolve("schema.json");
        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"x","schemaVersion":"1","fields":[
                  {"id":"name","displayName":"Nome","physicalType":"TEXT","semanticTypes":[],"required":false}
                ]}
                """);
        Path report = temporary.resolve("duplicate-report.json");
        assertEquals(0, run("analyze", source.toString(), "--schema", schema.toString(), "--out", report.toString(),
                "--delimiter", "comma", "--header", "first"));
        var out = new StringWriter(); var err = new StringWriter();
        int ambiguous = RizomaCli.execute(new String[]{"explain", report.toString(), "--column", "Nome"},
                new PrintWriter(out, true), new PrintWriter(err, true));
        assertEquals(RizomaCli.AMBIGUOUS_COLUMN, ambiguous);
        assertTrue(err.toString().contains("c0, c1"));
        assertEquals(0, run("explain", report.toString(), "--column-id", "c1"));

        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"x","schemaVersion":"1","unknown":true,"fields":[]}
                """);
        assertEquals(RizomaCli.INVALID_INPUT, run("analyze", source.toString(), "--schema", schema.toString(), "--out", report.toString()));
    }

    @Test void invalidArgumentsAreExplainedWithDocumentedExitCode() {
        var out = new StringWriter(); var err = new StringWriter();
        int exit = RizomaCli.execute(new String[]{"analyze"}, new PrintWriter(out, true), new PrintWriter(err, true));
        assertEquals(RizomaCli.INVALID_INPUT, exit);
        assertTrue(err.toString().contains("Invalid arguments"));
    }

    @Test void outputCannotOverwriteSource() throws Exception {
        Path source = temporary.resolve("protected.csv");
        Files.writeString(source, "Name\nAna\n");
        String original = Files.readString(source);
        int exit = run("analyze", source.toString(), "--schema", example("customer.schema.json").toString(),
                "--out", source.toString(), "--delimiter", "comma", "--header", "first");
        assertEquals(RizomaCli.INVALID_INPUT, exit);
        assertEquals(original, Files.readString(source));
    }

    private static AnalysisResult libraryAnalyze(Path source, Path schemaPath) throws Exception {
        List<SemanticDetector> detectors = new ArrayList<>(CoreSemanticDetectors.defaults());
        detectors.addAll(PtBrDetectors.defaults());
        var engine = MappingEngine.builder().readers(List.of(new CsvDataReader())).semanticDetectors(detectors)
                .normalizer(PtBrHeaderRules.normalizer()).configuration(EngineConfig.defaults()).build();
        return engine.analyze(new AnalysisRequest(new PathTabularSource(source), JsonSupport.readSchema(schemaPath),
                new AnalysisOptions(Map.of("charset", "UTF-8", "header", "detect"), 42)));
    }

    private static void assertTop(AnalysisResult result, String source, String target) {
        assertEquals(target, result.candidatesByColumn().get(source).getFirst().targetFieldId());
    }

    private static Path example(String name) { return Path.of("..", "examples", name).toAbsolutePath().normalize(); }

    private static int run(String... args) {
        return RizomaCli.execute(args, new PrintWriter(new StringWriter(), true), new PrintWriter(new StringWriter(), true));
    }
}
