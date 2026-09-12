package io.github.felipemacedo1.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MappingKnowledgeBaseTest {
    @Test void confirmationsRejectionsAndCorrectionsRemainSeparateAndBounded() {
        var knowledge = new InMemoryMappingKnowledgeBase();
        knowledge.record(event("confirm", "2026-01-01T00:00:00Z",
                MappingFeedback.FeedbackType.CONFIRMED, "customer.code", "customer.code"));
        knowledge.record(event("reject", "2026-01-02T00:00:00Z",
                MappingFeedback.FeedbackType.REJECTED, "customer.email", ""));
        knowledge.record(event("correct", "2026-01-03T00:00:00Z",
                MappingFeedback.FeedbackType.CORRECTED, "customer.code", "customer.document"));

        HistoricalEvidence code = knowledge.find(query("customer.code"));
        assertEquals(1, code.confirmedCount());
        assertEquals(1, code.correctedFromCount());
        assertEquals(1, code.positiveCount());
        assertEquals(1, code.negativeCount());
        assertTrue(code.conflicting());
        assertEquals(.5, code.historicalScore());
        assertEquals(0, code.reliability());

        HistoricalEvidence document = knowledge.find(query("customer.document"));
        assertEquals(1, document.correctedToCount());
        assertEquals(1, document.historicalScore());
        assertTrue(document.reliability() > 0 && document.reliability() < 1);

        HistoricalEvidence email = knowledge.find(query("customer.email"));
        assertEquals(1, email.rejectedCount());
        assertEquals(0, email.historicalScore());
        assertTrue(Double.isFinite(email.reliability()));
    }

    @Test void snapshotIsDeterministicIdempotentAndContextIsolated() {
        MappingFeedback first = event("a", "2026-01-01T00:00:00Z",
                MappingFeedback.FeedbackType.CONFIRMED, "customer.code", "customer.code");
        MappingFeedback second = event("b", "2026-01-02T00:00:00Z",
                MappingFeedback.FeedbackType.REJECTED, "customer.email", "");
        var left = new InMemoryMappingKnowledgeBase();
        left.record(first); left.record(second); left.record(first);
        var right = new InMemoryMappingKnowledgeBase();
        right.record(second); right.record(first);
        assertEquals(2, left.snapshot().eventCount());
        assertEquals(left.snapshot().id(), right.snapshot().id());
        assertSame(left.snapshot(), left.snapshot(), "unchanged store should reuse its immutable snapshot");

        assertFalse(left.find(new KnowledgeQuery("other", "1", "schema-sha", "customer.code",
                "codigo cliente", "pt-BR", "customer")).available());
        assertFalse(left.find(new KnowledgeQuery("customer", "1", "schema-sha", "customer.code",
                "codigo cliente", "en-US", "customer")).available());
        assertFalse(left.find(new KnowledgeQuery("customer", "1", "schema-sha", "customer.code",
                "codigo cliente", "pt-BR", "logistics")).available());
    }

    @Test void duplicateConflictLimitsAndInvalidEventsFailClearly() {
        var knowledge = new InMemoryMappingKnowledgeBase(1);
        MappingFeedback first = event("same", "2026-01-01T00:00:00Z",
                MappingFeedback.FeedbackType.CONFIRMED, "customer.code", "customer.code");
        knowledge.record(first);
        MappingFeedback conflict = event("same", "2026-01-02T00:00:00Z",
                MappingFeedback.FeedbackType.CONFIRMED, "customer.code", "customer.code");
        assertThrows(IllegalArgumentException.class, () -> knowledge.record(conflict));
        assertThrows(IllegalStateException.class, () -> knowledge.record(event("other", "2026-01-02T00:00:00Z",
                MappingFeedback.FeedbackType.CONFIRMED, "customer.code", "customer.code")));
        assertThrows(UnsupportedOperationException.class, () -> NoOpMappingKnowledgeBase.INSTANCE.record(first));
        assertEquals(NoOpMappingKnowledgeBase.SNAPSHOT_ID,
                NoOpMappingKnowledgeBase.INSTANCE.snapshot().id());

        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "id", "not-time",
                MappingFeedback.FeedbackType.REJECTED, first.source(), first.scope(),
                "customer.code", "", null, "test", "engine", "config"));
        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "id", "2026-01-01T00:00:00Z",
                MappingFeedback.FeedbackType.CORRECTED, first.source(), first.scope(),
                "customer.code", "customer.code", null, "test", "engine", "config"));

        MappingFeedback normalizedTime = event("offset", "2026-01-01T01:00:00+01:00",
                MappingFeedback.FeedbackType.CONFIRMED, "customer.code", "customer.code");
        assertEquals("2026-01-01T00:00:00Z", normalizedTime.timestamp());
    }

    @Test void feedbackMetadataAndHistoricalEvidenceEnforceEverySafetyBoundary() {
        var source = new MappingFeedback.SourceMetadata("c0", 0, 7, 2, "codigo cliente");
        var scope = new MappingFeedback.Scope("customer", "1", "schema-sha", null, null);
        assertEquals("", scope.locale());
        assertEquals("", scope.domainContext());

        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("2.0", "id",
                "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.REJECTED, source, scope,
                "customer.code", "", null, "test", "engine", "config"));
        for (double score : List.of(Double.NaN, -0.1, 1.1)) {
            assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "id",
                    "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.REJECTED, source, scope,
                    "customer.code", "", score, "test", "engine", "config"));
        }
        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "id",
                "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.CONFIRMED, source, scope,
                "customer.code", "customer.name", null, "test", "engine", "config"));
        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "id",
                "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.REJECTED, source, scope,
                "customer.code", "customer.name", null, "test", "engine", "config"));
        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "id",
                "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.CORRECTED, source, scope,
                "customer.code", "", null, "test", "engine", "config"));
        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "bad\nid",
                "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.REJECTED, source, scope,
                "customer.code", null, null, "test", "engine", "config"));
        assertThrows(IllegalArgumentException.class, () -> new MappingFeedback("1.0", "x".repeat(201),
                "2026-01-01T00:00:00Z", MappingFeedback.FeedbackType.REJECTED, source, scope,
                "customer.code", null, null, "test", "engine", "config"));

        assertThrows(IllegalArgumentException.class,
                () -> new MappingFeedback.SourceMetadata("c0", -1, 7, 2, "codigo"));
        assertThrows(IllegalArgumentException.class,
                () -> new MappingFeedback.SourceMetadata("c0", 0, -1, 2, "codigo"));
        assertThrows(IllegalArgumentException.class,
                () -> new MappingFeedback.SourceMetadata("c0", 0, 7, 0, "codigo"));
        assertThrows(IllegalArgumentException.class,
                () -> new MappingFeedback.Scope("customer", "1", "schema", "x".repeat(65), ""));

        HistoricalEvidence defaults = new HistoricalEvidence("target", "source", 0, 0, 0, 0,
                null, null, 0, 0, false, null);
        assertEquals("", defaults.lastPositiveAt());
        assertEquals("", defaults.lastNegativeAt());
        assertEquals("", defaults.explanation());
        assertFalse(defaults.available());
        assertThrows(IllegalArgumentException.class, () -> evidence(null, "source", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> evidence("target", "", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> evidence("target", "source", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEvidence("target", "source",
                0, -1, 0, 0, "", "", 0, 0, false, ""));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEvidence("target", "source",
                0, 0, -1, 0, "", "", 0, 0, false, ""));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEvidence("target", "source",
                0, 0, 0, -1, "", "", 0, 0, false, ""));
        for (double invalid : List.of(Double.NaN, -0.1, 1.1)) {
            assertThrows(IllegalArgumentException.class, () -> new HistoricalEvidence("target", "source",
                    0, 0, 0, 0, "", "", invalid, 0, false, ""));
        }
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEvidence("target", "source",
                0, 0, 0, 0, "", "", 0, Double.POSITIVE_INFINITY, false, ""));

        KnowledgeQuery defaultsQuery = new KnowledgeQuery("schema", "1", "fingerprint", "target",
                "source", null, null);
        assertEquals("", defaultsQuery.locale());
        assertEquals("", defaultsQuery.domainContext());
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeQuery("", "1", "fingerprint",
                "target", "source", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryMappingKnowledgeBase(0));
    }

    private static HistoricalEvidence evidence(String target, String source, long confirmed, long rejected) {
        return new HistoricalEvidence(target, source, confirmed, rejected, 0, 0,
                "", "", 0, 0, false, "");
    }

    private static MappingFeedback event(String id, String timestamp,
            MappingFeedback.FeedbackType type, String suggested, String human) {
        return new MappingFeedback("1.0", id, timestamp, type,
                new MappingFeedback.SourceMetadata("c0", 0, 7, 2, "codigo cliente"),
                new MappingFeedback.Scope("customer", "1", "schema-sha", "pt-BR", "customer"),
                suggested, human, .5, "synthetic-test", "0.4.0-SNAPSHOT", "config-sha");
    }

    private static KnowledgeQuery query(String target) {
        return new KnowledgeQuery("customer", "1", "schema-sha", target,
                "codigo cliente", "pt-BR", "customer");
    }
}
