package io.github.rizoma.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.AnalysisRequest;
import io.github.rizoma.core.AnalysisResult;
import io.github.rizoma.core.CoreSemanticDetectors;
import io.github.rizoma.core.InMemoryMappingKnowledgeBase;
import io.github.rizoma.core.MappingEngine;
import io.github.rizoma.core.MappingFeedback;
import io.github.rizoma.core.MappingKnowledgeBase;
import io.github.rizoma.core.NoOpMappingKnowledgeBase;
import io.github.rizoma.core.PathTabularSource;
import io.github.rizoma.core.SemanticDetector;
import io.github.rizoma.core.TargetSchema;
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

class FeedbackEvaluationTest {
    @Test void measuresFeedbackBenefitsRisksConflictsAndSuppressionOnHeldOutFixtures() throws Exception {
        Path root = Path.of("..", "corpus").toAbsolutePath().normalize();
        Path feedbackRoot = root.resolve("feedback");
        TargetSchema customer = JsonSupport.readSchema(root.resolve("clientes.schema.json"));
        TargetSchema supplier = JsonSupport.readSchema(root.resolve("fornecedores.schema.json"));
        TargetSchema contact = JsonSupport.readSchema(feedbackRoot.resolve("contact.schema.json"));
        TargetSchema misleading = JsonSupport.readSchema(feedbackRoot.resolve("misleading.schema.json"));
        var knowledge = new InMemoryMappingKnowledgeBase();

        AnalysisResult devCode = analyze(feedbackRoot.resolve("development/code.csv"), customer,
                NoOpMappingKnowledgeBase.INSTANCE);
        knowledge.record(MappingFeedback.confirmed("code-confirm", at(0), devCode, customer,
                "c0", "customer.code", PtBrHeaderRules.normalizer(), "feedback-development-corpus"));

        AnalysisResult devContact = analyze(feedbackRoot.resolve("development/contact.csv"), contact,
                NoOpMappingKnowledgeBase.INSTANCE);
        for (int index = 0; index < 8; index++) {
            knowledge.record(MappingFeedback.rejected("email-reject-" + index, at(index + 1),
                    devContact, contact, "c0", "customer.email", PtBrHeaderRules.normalizer(),
                    "feedback-development-corpus"));
        }

        AnalysisResult devSupplier = analyze(feedbackRoot.resolve("development/supplier.csv"), supplier,
                NoOpMappingKnowledgeBase.INSTANCE);
        for (int index = 0; index < 12; index++) {
            knowledge.record(MappingFeedback.corrected("supplier-correct-" + index, at(index + 10),
                    devSupplier, supplier, "c0", "supplier.code", "supplier.document",
                    PtBrHeaderRules.normalizer(), "feedback-development-corpus"));
        }

        AnalysisResult devDocument = analyze(feedbackRoot.resolve("development/document.csv"), customer,
                NoOpMappingKnowledgeBase.INSTANCE);
        for (int index = 0; index < 20; index++) {
            knowledge.record(MappingFeedback.confirmed("wrong-document-" + index, at(index + 22),
                    devDocument, customer, "c0", "customer.code", PtBrHeaderRules.normalizer(),
                    "feedback-development-corpus"));
        }

        AnalysisResult devMisleading = analyze(feedbackRoot.resolve("development/misleading.csv"), misleading,
                NoOpMappingKnowledgeBase.INSTANCE);
        for (int index = 0; index < 12; index++) {
            knowledge.record(MappingFeedback.corrected("misleading-" + index, at(index + 42),
                    devMisleading, misleading, "c0", "customer.code", "customer.name",
                    PtBrHeaderRules.normalizer(), "feedback-development-corpus"));
        }

        List<Case> cases = List.of(
                new Case("code-confirmation", feedbackRoot.resolve("evaluation/code.csv"), customer, "customer.code"),
                new Case("supplier-correction", feedbackRoot.resolve("evaluation/supplier.csv"), supplier, "supplier.document"),
                new Case("semantic-conflict", feedbackRoot.resolve("evaluation/document.csv"), customer, "customer.document"),
                new Case("known-risk", feedbackRoot.resolve("evaluation/misleading.csv"), misleading, "customer.code"));
        int top1Before = 0, top1After = 0, top3Before = 0, top3After = 0;
        int abstentionsBefore = 0, abstentionsAfter = 0;
        int improved = 0, worsened = 0, unchanged = 0, historicalConflicts = 0;
        var details = new ArrayList<CaseResult>();
        for (Case item : cases) {
            AnalysisResult before = analyze(item.source(), item.schema(), NoOpMappingKnowledgeBase.INSTANCE);
            AnalysisResult after = analyze(item.source(), item.schema(), knowledge);
            int beforeRank = rank(before, item.expectedTarget());
            int afterRank = rank(after, item.expectedTarget());
            if (beforeRank == 1) top1Before++;
            if (afterRank == 1) top1After++;
            if (beforeRank > 0 && beforeRank <= 3) top3Before++;
            if (afterRank > 0 && afterRank <= 3) top3After++;
            if (before.decisionsByColumn().get("c0").targetFieldId() == null) abstentionsBefore++;
            if (after.decisionsByColumn().get("c0").targetFieldId() == null) abstentionsAfter++;
            if (afterRank > 0 && (beforeRank == 0 || afterRank < beforeRank)) improved++;
            else if (beforeRank > 0 && (afterRank == 0 || afterRank > beforeRank)) worsened++;
            else unchanged++;
            long conflicts = after.candidatesByColumn().get("c0").stream()
                    .filter(candidate -> candidate.components().stream()
                            .anyMatch(component -> component.id().equals("history") && component.available()))
                    .filter(candidate -> !candidate.contradictions().isEmpty()).count();
            historicalConflicts += (int) conflicts;
            details.add(new CaseResult(item.id(), beforeRank, afterRank,
                    before.decisionsByColumn().get("c0").status().name(),
                    after.decisionsByColumn().get("c0").status().name(), conflicts));
        }

        AnalysisResult contactBefore = analyze(feedbackRoot.resolve("evaluation/contact.csv"), contact,
                NoOpMappingKnowledgeBase.INSTANCE);
        AnalysisResult contactAfter = analyze(feedbackRoot.resolve("evaluation/contact.csv"), contact, knowledge);
        double rejectedBefore = score(contactBefore, "customer.email");
        double rejectedAfter = score(contactAfter, "customer.email");
        boolean rejectedSuppressed = rejectedAfter < rejectedBefore;

        var report = new FeedbackReport("1.0", "synthetic-feedback-evaluation-not-calibration",
                cases.size(), top1Before, top1After, top1After - top1Before,
                top3Before, top3After, top3After - top3Before, improved, worsened,
                unchanged, abstentionsBefore, abstentionsAfter,
                abstentionsAfter - abstentionsBefore, historicalConflicts, rejectedSuppressed,
                rejectedBefore, rejectedAfter, details);
        Path output = Path.of(System.getProperty("basedir", ".")).resolve("target/feedback-evaluation.json");
        Files.createDirectories(output.getParent());
        JsonSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertEquals(4, report.cases());
        assertTrue(report.improved() >= 1, "correction should improve at least one held-out case");
        assertTrue(report.worsened() >= 1, "the deliberately misleading history must remain visible");
        assertTrue(report.historicalConflicts() >= 1, "wrong history must conflict with strong CPF evidence");
        assertTrue(report.rejectedCandidateSuppressed());
        assertEquals("customer.document", analyze(feedbackRoot.resolve("evaluation/document.csv"),
                customer, knowledge).candidatesByColumn().get("c0").getFirst().targetFieldId());
        assertTrue(Files.size(output) > 0);
    }

    private static AnalysisResult analyze(Path source, TargetSchema schema,
            MappingKnowledgeBase knowledge) {
        List<SemanticDetector> detectors = new ArrayList<>(CoreSemanticDetectors.defaults());
        detectors.addAll(PtBrDetectors.defaults());
        MappingEngine engine = MappingEngine.builder().readers(List.of(new CsvDataReader()))
                .semanticDetectors(detectors).normalizer(PtBrHeaderRules.normalizer()).build();
        return engine.analyze(new AnalysisRequest(new PathTabularSource(source), schema,
                new AnalysisOptions(Map.of("header", "first", "delimiter", ","), 42L), knowledge));
    }

    private static int rank(AnalysisResult result, String target) {
        List<AnalysisResult.MappingCandidate> candidates = result.candidatesByColumn().get("c0");
        for (int index = 0; index < candidates.size(); index++)
            if (candidates.get(index).targetFieldId().equals(target)) return index + 1;
        return 0;
    }

    private static double score(AnalysisResult result, String target) {
        return result.candidatesByColumn().get("c0").stream()
                .filter(candidate -> candidate.targetFieldId().equals(target))
                .mapToDouble(AnalysisResult.MappingCandidate::score).findFirst().orElseThrow();
    }

    private static String at(int second) {
        return "2026-01-01T00:" + String.format("%02d:%02d", second / 60, second % 60) + "Z";
    }

    private record Case(String id, Path source, TargetSchema schema, String expectedTarget) {}
    private record CaseResult(String id, int rankBefore, int rankAfter,
            String decisionBefore, String decisionAfter, long historicalConflicts) {}
    private record FeedbackReport(String formatVersion, String purpose, int cases,
            int top1Before, int top1After, int top1Delta, int top3Before, int top3After,
            int top3Delta, int improved, int worsened, int unchanged,
            int abstentionsBefore, int abstentionsAfter, int abstentionDelta, int historicalConflicts,
            boolean rejectedCandidateSuppressed, double rejectedScoreBefore,
            double rejectedScoreAfter, List<CaseResult> caseResults) {}
}
