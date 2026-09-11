package io.github.rizoma.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RizomaCliTest {
    static { java.util.logging.Logger.getLogger("org.apache.poi").setLevel(java.util.logging.Level.SEVERE); }
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

    @Test void explainRemainsCompatibleWithAnalysisReportVersionOneZero() throws Exception {
        Path currentReport = temporary.resolve("report-1.1.json");
        assertEquals(0, run("analyze", example("clientes.csv").toString(), "--schema",
                example("customer.schema.json").toString(), "--out", currentReport.toString()));

        ObjectNode legacy = (ObjectNode) JsonSupport.MAPPER.readTree(currentReport.toFile());
        legacy.put("formatVersion", "1.0");
        ((ObjectNode) legacy.get("structure")).remove("attributes");
        Path legacyReport = temporary.resolve("report-1.0.json");
        JsonSupport.MAPPER.writeValue(legacyReport.toFile(), legacy);

        var output = new StringWriter();
        var error = new StringWriter();
        int exit = RizomaCli.execute(new String[]{"explain", legacyReport.toString(), "--column-id", "c1"},
                new PrintWriter(output, true), new PrintWriter(error, true));

        assertEquals(RizomaCli.OK, exit, error.toString());
        assertTrue(output.toString().contains("customer.document"));
        AnalysisResult parsed = JsonSupport.MAPPER.readValue(legacyReport.toFile(), AnalysisResult.class);
        assertEquals(Map.of(), parsed.structure().attributes());
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

    @Test void analyzeSupportsXlsxThroughTheSameCliAndEnginePipeline() throws Exception {
        Path source = temporary.resolve("clientes.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Clientes");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Nome Completo");
            header.createCell(1).setCellValue("Documento");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Ana Maria");
            row.createCell(1).setCellValue("529.982.247-25");
            try (var output = Files.newOutputStream(source)) { workbook.write(output); }
        }
        Path report = temporary.resolve("xlsx-report.json");
        assertEquals(0, run("analyze", source.toString(), "--schema", example("customer.schema.json").toString(),
                "--out", report.toString(), "--header", "first", "--sheet", "Clientes"));
        AnalysisResult result = JsonSupport.MAPPER.readValue(report.toFile(), AnalysisResult.class);
        assertEquals("XLSX", result.structure().format());
        assertEquals("0", result.structure().attributes().get("sheetIndex"));
        assertEquals(1, result.rowsProcessed());
        assertTop(result, "c0", "customer.name");
        assertTop(result, "c1", "customer.document");
        assertFalse(Files.readString(report).contains("529.982.247-25"));
    }

    @Test void csvXlsAndXlsxProduceTheSameSevenTopMappings() throws Exception {
        Path csvReport = temporary.resolve("csv.json");
        assertEquals(0, run("analyze", example("clientes.csv").toString(), "--schema",
                example("customer.schema.json").toString(), "--out", csvReport.toString()));
        AnalysisResult csv = JsonSupport.MAPPER.readValue(csvReport.toFile(), AnalysisResult.class);

        for (Workbook workbook : List.of(new HSSFWorkbook(), new XSSFWorkbook())) {
            String extension = workbook instanceof HSSFWorkbook ? ".xls" : ".xlsx";
            Path source = temporary.resolve("parity" + extension);
            writeParityWorkbook(workbook, source);
            Path report = temporary.resolve("parity" + extension + ".json");
            assertEquals(0, run("analyze", source.toString(), "--schema",
                    example("customer.schema.json").toString(), "--out", report.toString(), "--header", "first"));
            AnalysisResult excel = JsonSupport.MAPPER.readValue(report.toFile(), AnalysisResult.class);
            assertEquals(csv.candidatesByColumn().keySet(), excel.candidatesByColumn().keySet());
            for (String column : csv.candidatesByColumn().keySet()) {
                assertEquals(csv.candidatesByColumn().get(column).getFirst().targetFieldId(),
                        excel.candidatesByColumn().get(column).getFirst().targetFieldId());
            }
        }
    }

    @Test void formatSpecificOptionsAreRejectedAsInvalidInput() throws Exception {
        Path report = temporary.resolve("invalid-option.json");
        assertEquals(RizomaCli.INVALID_INPUT, run("analyze", example("clientes.csv").toString(),
                "--schema", example("customer.schema.json").toString(), "--out", report.toString(),
                "--sheet", "0"));
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

    private static void writeParityWorkbook(Workbook workbook, Path path) throws Exception {
        try (workbook) {
            if (workbook instanceof HSSFWorkbook legacy) legacy.createInformationProperties();
            var sheet = workbook.createSheet("Clientes");
            String[] headers = {"Nome Completo", "CPF Cliente", "E-mail", "Celular", "Nascimento",
                    "Endereço Residencial", "CEP"};
            String[] values = {"Ana Maria", "529.982.247-25", "ana@example.test", "(11) 99999-9999",
                    "1990-05-20", "Rua Um, 10", "01001-000"};
            var header = sheet.createRow(0);
            var row = sheet.createRow(1);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
                row.createCell(index).setCellValue(values[index]);
            }
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }
    }
}
