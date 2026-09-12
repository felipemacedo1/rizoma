package io.github.felipemacedo1.rizoma.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.AnalysisRequest;
import io.github.felipemacedo1.rizoma.core.AnalysisResult;
import io.github.felipemacedo1.rizoma.core.ColumnProfile;
import io.github.felipemacedo1.rizoma.core.CoreSemanticDetectors;
import io.github.felipemacedo1.rizoma.core.EngineConfig;
import io.github.felipemacedo1.rizoma.core.MappingEngine;
import io.github.felipemacedo1.rizoma.core.MappingPlan;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionResult;
import io.github.felipemacedo1.rizoma.core.LayoutCompatibilityReport;
import io.github.felipemacedo1.rizoma.core.DryRunResult;
import io.github.felipemacedo1.rizoma.core.NoOpMappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.PathTabularSource;
import io.github.felipemacedo1.rizoma.core.ProjectionSource;
import io.github.felipemacedo1.rizoma.core.SemanticDetector;
import io.github.felipemacedo1.rizoma.csv.CsvDataReader;
import io.github.felipemacedo1.rizoma.ptbr.PtBrDetectors;
import io.github.felipemacedo1.rizoma.ptbr.PtBrHeaderRules;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
        assertTrue(explainOut.toString().contains("Entropy role: auxiliary profiling evidence"));
        assertFalse(explainOut.toString().contains("529.982.247-25"));

        AnalysisResult library = libraryAnalyze(source, schemaPath);
        assertEquals(library.candidatesByColumn(), cli.candidatesByColumn());
        assertEquals(library.decisionsByColumn(), cli.decisionsByColumn());
    }

    @Test void explainRemainsCompatibleWithAnalysisReportVersionsOneZeroThroughOneTwo() throws Exception {
        Path currentReport = temporary.resolve("report-1.3.json");
        assertEquals(0, run("analyze", example("clientes.csv").toString(), "--schema",
                example("customer.schema.json").toString(), "--out", currentReport.toString()));

        for (String version : List.of("1.0", "1.1", "1.2")) {
            ObjectNode legacy = (ObjectNode) JsonSupport.MAPPER.readTree(currentReport.toFile());
            legacy.put("formatVersion", version);
            legacy.remove("knowledgeSnapshotId");
            legacy.remove("knowledgeVersion");
            legacy.remove("historicalEvidenceByColumn");
            if (!version.equals("1.2")) {
                legacy.remove("prunedCandidatesByColumn");
                legacy.withArray("profiles").forEach(node -> {
                    ((ObjectNode) node).remove("statistics");
                    node.withObject("semanticEvidence").properties().forEach(entry ->
                            ((ObjectNode) entry.getValue()).remove("semanticConfidence"));
                });
            }
            if (version.equals("1.0")) ((ObjectNode) legacy.get("structure")).remove("attributes");
            Path legacyReport = temporary.resolve("report-" + version + ".json");
            JsonSupport.MAPPER.writeValue(legacyReport.toFile(), legacy);

            var output = new StringWriter();
            var error = new StringWriter();
            int exit = RizomaCli.execute(new String[]{"explain", legacyReport.toString(), "--column-id", "c1"},
                    new PrintWriter(output, true), new PrintWriter(error, true));

            assertEquals(RizomaCli.OK, exit, error.toString());
            assertTrue(output.toString().contains("customer.document"));
            AnalysisResult parsed = JsonSupport.MAPPER.readValue(legacyReport.toFile(), AnalysisResult.class);
            if (version.equals("1.0")) assertEquals(Map.of(), parsed.structure().attributes());
            assertEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID, parsed.knowledgeSnapshotId());
            assertTrue(parsed.historicalEvidenceByColumn().isEmpty());
            if (!version.equals("1.2")) {
                assertEquals(Map.of(), parsed.prunedCandidatesByColumn());
                assertEquals(ColumnProfile.MeasureAccuracy.UNAVAILABLE,
                        parsed.profiles().getFirst().statistics().cardinality().accuracy());
            }
        }
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

        Path knowledge = temporary.resolve("knowledge.jsonl");
        Files.writeString(knowledge, "");
        assertEquals(RizomaCli.INVALID_INPUT, run("analyze", source.toString(), "--schema",
                example("customer.schema.json").toString(), "--knowledge", knowledge.toString(),
                "--out", knowledge.toString(), "--delimiter", "comma", "--header", "first"));
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

    @Test void analyzePlanAndDryRunFormAReadOnlyCsvWorkflow() throws Exception {
        Path source = temporary.resolve("dry-run.csv");
        Files.writeString(source, "Documento,Quantidade\n529.982.247-25,2\n000.000.000-00,not-a-number\n");
        Path schema = temporary.resolve("dry-run.schema.json");
        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"dry-run","schemaVersion":"1","locale":"pt-BR","fields":[
                  {"id":"customer.document","displayName":"CPF","aliases":["Documento"],"physicalType":"TEXT","semanticTypes":["br:cpf"],"required":true},
                  {"id":"order.quantity","displayName":"Quantidade","aliases":[],"physicalType":"INTEGER","semanticTypes":[],"required":true}
                ]}
                """);
        Path analysis = temporary.resolve("analysis.json");
        Path plan = temporary.resolve("mapping.json");
        Path result = temporary.resolve("dry-run.json");

        assertEquals(0, run("analyze", source.toString(), "--schema", schema.toString(), "--out", analysis.toString()));
        assertEquals(0, run("plan", analysis.toString(), "--schema", schema.toString(), "--out", plan.toString(),
                "--map", "c0=customer.document", "--map", "c1=order.quantity"));
        MappingPlan currentPlan = JsonSupport.MAPPER.readValue(plan.toFile(), MappingPlan.class);
        assertEquals("1.2", currentPlan.formatVersion());
        assertEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID, currentPlan.knowledgeSnapshotId());
        for (String version : List.of("1.0", "1.1")) {
            ObjectNode legacyPlanJson = (ObjectNode) JsonSupport.MAPPER.readTree(plan.toFile());
            legacyPlanJson.put("formatVersion", version);
            legacyPlanJson.remove("layoutTemplateId");
            legacyPlanJson.remove("layoutTemplateVersion");
            legacyPlanJson.remove("layoutFingerprint");
            legacyPlanJson.remove("executionRoute");
            legacyPlanJson.remove("ignoredSourceColumns");
            legacyPlanJson.withArray("mappings").forEach(node -> ((ObjectNode) node).remove("projectionSource"));
            if (version.equals("1.0")) {
                legacyPlanJson.remove("knowledgeSnapshotId");
                legacyPlanJson.remove("knowledgeVersion");
            }
            Path legacyPlan = temporary.resolve("mapping-" + version + ".json");
            JsonSupport.MAPPER.writeValue(legacyPlan.toFile(), legacyPlanJson);
            MappingPlan parsedLegacyPlan = JsonSupport.MAPPER.readValue(legacyPlan.toFile(), MappingPlan.class);
            assertEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID, parsedLegacyPlan.knowledgeSnapshotId());
            assertEquals(ProjectionSource.Kind.SOURCE_COLUMN,
                    parsedLegacyPlan.mappings().getFirst().projectionSource().kind());
        }
        assertEquals(0, run("dry-run", source.toString(), "--schema", schema.toString(), "--mapping", plan.toString(),
                "--out", result.toString(), "--max-issue-samples", "2"));

        DryRunResult report = JsonSupport.MAPPER.readValue(result.toFile(), DryRunResult.class);
        assertEquals("1.0", report.formatVersion());
        assertEquals(2, report.rowsProcessed());
        assertEquals(1, report.rowsValid());
        assertEquals(1, report.rowsInvalid());
        assertEquals(2, report.totalErrorCount());
        assertTrue(report.errorCodes().containsKey("INVALID_CPF_CHECKSUM"));
        assertTrue(report.errorCodes().containsKey("INVALID_LONG"));
        assertFalse(Files.readString(result).contains("529.982.247-25"));
        assertFalse(Files.readString(result).contains("not-a-number"));
        assertEquals("529.982.247-25", Files.readAllLines(source).get(1).split(",")[0]);
    }

    @Test void dryRunUsesTheExistingExcelReaderWithTheSameBoundPlan() throws Exception {
        Path source = temporary.resolve("dry-run.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Items");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Date");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Synthetic Item");
            var date = row.createCell(1);
            date.setCellValue(LocalDate.of(2026, 9, 11));
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            date.setCellStyle(style);
            try (var output = Files.newOutputStream(source)) { workbook.write(output); }
        }
        Path schema = temporary.resolve("item.schema.json");
        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"items","schemaVersion":"1","locale":"en-US","fields":[
                  {"id":"item.name","displayName":"Name","physicalType":"TEXT","semanticTypes":[],"required":true},
                  {"id":"item.date","displayName":"Date","physicalType":"DATE","semanticTypes":["core:date"],"required":true}
                ]}
                """);
        Path analysis = temporary.resolve("excel-analysis.json");
        Path plan = temporary.resolve("excel-plan.json");
        Path result = temporary.resolve("excel-dry-run.json");
        assertEquals(0, run("analyze", source.toString(), "--schema", schema.toString(), "--out", analysis.toString(),
                "--sheet", "Items", "--header", "first"));
        assertEquals(0, run("plan", analysis.toString(), "--schema", schema.toString(), "--out", plan.toString(),
                "--map", "c0=item.name", "--map", "c1=item.date"));
        assertEquals(0, run("dry-run", source.toString(), "--schema", schema.toString(), "--mapping", plan.toString(),
                "--out", result.toString(), "--sheet", "Items", "--header", "first"));
        DryRunResult dryRun = JsonSupport.MAPPER.readValue(result.toFile(), DryRunResult.class);
        assertEquals(1, dryRun.rowsValid());
        assertEquals(1, dryRun.transformationsApplied());
    }

    @Test void cliRecordsFeedbackExplicitlyAndReusesItWithoutPersistingCells() throws Exception {
        Path firstSource = temporary.resolve("first.csv");
        Files.writeString(firstSource, "Cod Cli\nA-001\n");
        Path secondSource = temporary.resolve("second.csv");
        Files.writeString(secondSource, "Cod. Cliente\nA-002\n");
        Path schema = temporary.resolve("feedback.schema.json");
        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"customer","schemaVersion":"1","context":"customer","locale":"pt-BR","fields":[
                  {"id":"customer.code","displayName":"Customer identifier","physicalType":"TEXT","semanticTypes":[],"required":false},
                  {"id":"customer.name","displayName":"Name","physicalType":"TEXT","semanticTypes":[],"required":false}
                ]}
                """);
        Path firstReport = temporary.resolve("first.json");
        Path knowledge = temporary.resolve("knowledge.jsonl");
        Path secondReport = temporary.resolve("second.json");
        assertEquals(0, run("analyze", firstSource.toString(), "--schema", schema.toString(),
                "--out", firstReport.toString(), "--header", "first", "--delimiter", "comma"));
        assertEquals(0, run("feedback", "confirm", firstReport.toString(), "--schema", schema.toString(),
                "--knowledge", knowledge.toString(), "--column-id", "c0", "--target", "customer.code",
                "--feedback-id", "synthetic-confirmation", "--timestamp", "2026-01-01T00:00:00Z"));
        assertEquals(0, run("analyze", secondSource.toString(), "--schema", schema.toString(),
                "--out", secondReport.toString(), "--header", "first", "--delimiter", "comma",
                "--knowledge", knowledge.toString()));

        AnalysisResult result = JsonSupport.MAPPER.readValue(secondReport.toFile(), AnalysisResult.class);
        assertEquals("1.3", result.formatVersion());
        assertEquals(1, result.historicalEvidenceByColumn().get("c0").getFirst().confirmedCount());
        var historical = result.candidatesByColumn().get("c0").stream()
                .filter(candidate -> candidate.targetFieldId().equals("customer.code"))
                .findFirst().orElseThrow().components().stream()
                .filter(component -> component.id().equals("history")).findFirst().orElseThrow();
        assertTrue(historical.available());
        assertTrue(historical.contribution() > 0);
        assertFalse(Files.readString(knowledge).contains("A-001"));
        assertFalse(Files.readString(secondReport).contains("A-002"));

        var explain = new StringWriter();
        assertEquals(0, RizomaCli.execute(new String[]{"explain", secondReport.toString(), "--column-id", "c0"},
                new PrintWriter(explain, true), new PrintWriter(new StringWriter(), true)));
        assertTrue(explain.toString().contains("Knowledge: snapshot="));
        assertTrue(explain.toString().contains("confirmed=1"));

        Path otherKnowledge = temporary.resolve("other-knowledge.jsonl");
        assertEquals(0, run("feedback", "reject", firstReport.toString(), "--schema", schema.toString(),
                "--knowledge", otherKnowledge.toString(), "--column-id", "c0", "--target", "customer.name",
                "--feedback-id", "synthetic-rejection", "--timestamp", "2026-01-02T00:00:00Z"));
        assertEquals(0, run("feedback", "correct", firstReport.toString(), "--schema", schema.toString(),
                "--knowledge", otherKnowledge.toString(), "--column-id", "c0",
                "--suggested-target", "customer.name", "--correct-target", "customer.code",
                "--feedback-id", "synthetic-correction", "--timestamp", "2026-01-03T00:00:00Z"));
        var otherSnapshot = new JsonLinesMappingKnowledgeBase(otherKnowledge).snapshot();
        assertEquals(2, otherSnapshot.eventCount());
    }

    @Test void cliCreatesTemplateRecognizesFastReuseAndExplainsProjectionAndIgnoredColumns() throws Exception {
        Path first = temporary.resolve("orders-day-1.csv");
        Path second = temporary.resolve("orders-day-2.csv");
        String header = "Produto;Quantidade;Valor Unitário;Comentário;Cor da Linha\n";
        Files.writeString(first, header + "Produto A;10;25,00;interno;azul\n");
        Files.writeString(second, header + "Produto B;2;7,50;novo;verde\n");
        Path schema = temporary.resolve("orders.schema.json");
        Files.writeString(schema, """
                {"formatVersion":"1.0","schemaId":"orders","schemaVersion":"1","context":"orders","locale":"pt-BR","fields":[
                  {"id":"product.name","displayName":"Produto","physicalType":"TEXT","required":true},
                  {"id":"order.quantity","displayName":"Quantidade","physicalType":"INTEGER","required":true},
                  {"id":"order.unitPrice","displayName":"Valor Unitário","physicalType":"DECIMAL","required":true},
                  {"id":"order.total","displayName":"Valor Total","physicalType":"DECIMAL","required":true},
                  {"id":"order.status","displayName":"Status","physicalType":"TEXT","required":true}
                ]}
                """);
        Path analysis = temporary.resolve("analysis.json");
        Path mapping = temporary.resolve("mapping.json");
        Path template = temporary.resolve("template.json");
        Path recognition = temporary.resolve("recognition.json");
        Path currentPlan = temporary.resolve("current-plan.json");
        Path dryRun = temporary.resolve("dry-run.json");
        assertEquals(0, run("analyze", first.toString(), "--schema", schema.toString(),
                "--out", analysis.toString(), "--header", "first", "--delimiter", "semicolon"));
        assertEquals(0, run("plan", analysis.toString(), "--schema", schema.toString(),
                "--out", mapping.toString(), "--map", "c0=product.name", "--map", "c1=order.quantity",
                "--map", "c2=order.unitPrice", "--constant", "order.status=ACTIVE",
                "--derive", "order.total=MULTIPLY:source:c1,source:c2", "--ignore", "c3", "--ignore", "c4"));
        assertEquals(0, run("template", "create", analysis.toString(), "--mapping", mapping.toString(),
                "--name", "orders-import", "--timestamp", "2026-09-12T00:00:00Z", "--out", template.toString()));
        assertEquals(0, run("recognize", second.toString(), "--schema", schema.toString(),
                "--template", template.toString(), "--out", recognition.toString(), "--plan-out", currentPlan.toString(),
                "--header", "first", "--delimiter", "semicolon"));

        LayoutRecognitionResult result = JsonSupport.MAPPER.readValue(recognition.toFile(), LayoutRecognitionResult.class);
        assertEquals(LayoutCompatibilityReport.ExecutionRoute.FAST_REUSE, result.route());
        MappingPlan rebound = JsonSupport.MAPPER.readValue(currentPlan.toFile(), MappingPlan.class);
        assertEquals(new PathTabularSource(second).sha256(), rebound.sourceFingerprint());
        assertEquals(List.of("c3", "c4"), rebound.ignoredSourceColumns());

        var targetExplain = new StringWriter();
        assertEquals(0, RizomaCli.execute(new String[]{"explain-plan", currentPlan.toString(),
                "--target", "order.total"}, new PrintWriter(targetExplain, true),
                new PrintWriter(new StringWriter(), true)));
        assertTrue(targetExplain.toString().contains("source=DERIVED"));
        assertTrue(targetExplain.toString().contains("operation=MULTIPLY"));
        var ignoredExplain = new StringWriter();
        assertEquals(0, RizomaCli.execute(new String[]{"explain-plan", currentPlan.toString(),
                "--source-column-id", "c3"}, new PrintWriter(ignoredExplain, true),
                new PrintWriter(new StringWriter(), true)));
        assertTrue(ignoredExplain.toString().contains("IGNORED_BY_PLAN"));

        assertEquals(0, run("dry-run", second.toString(), "--schema", schema.toString(),
                "--mapping", currentPlan.toString(), "--out", dryRun.toString(),
                "--header", "first", "--delimiter", "semicolon"));
        assertEquals(1, JsonSupport.MAPPER.readValue(dryRun.toFile(), DryRunResult.class).rowsValid());
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
