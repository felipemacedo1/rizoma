package io.github.rizoma.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.MappingFeedback;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonLinesMappingKnowledgeBaseTest {
    @TempDir Path temporary;

    @Test void appendRoundTripIsDeterministicIdempotentAndContainsNoCellValue() throws Exception {
        Path file = temporary.resolve("knowledge.jsonl");
        var store = new JsonLinesMappingKnowledgeBase(file);
        MappingFeedback feedback = event("one");
        store.record(feedback);
        store.record(feedback);
        assertEquals(1, Files.readAllLines(file).size());
        assertEquals(1, store.snapshot().eventCount());
        assertEquals(store.snapshot().id(), new JsonLinesMappingKnowledgeBase(file).snapshot().id());
        String persisted = Files.readString(file);
        assertFalse(persisted.contains("529.982.247-25"));
        assertFalse(persisted.contains("Synthetic Person"));
        assertTrue(persisted.contains("codigo cliente"));
    }

    @Test void corruptionAndLimitsAreRejectedWithoutEchoingContent() throws Exception {
        Path corrupt = temporary.resolve("corrupt.jsonl");
        Files.writeString(corrupt, "{this-is-not-json}\n");
        var error = assertThrows(IllegalArgumentException.class,
                () -> new JsonLinesMappingKnowledgeBase(corrupt).snapshot());
        assertTrue(error.getMessage().contains("corrupt at line 1"));
        assertFalse(error.getMessage().contains("this-is-not-json"));

        Path truncated = temporary.resolve("truncated.jsonl");
        Files.writeString(truncated, JsonSupport.MAPPER.writeValueAsString(event("partial")));
        var truncatedError = assertThrows(IllegalArgumentException.class,
                () -> new JsonLinesMappingKnowledgeBase(truncated).snapshot());
        assertTrue(truncatedError.getMessage().contains("truncated final JSON line"));

        Path bounded = temporary.resolve("bounded.jsonl");
        var store = new JsonLinesMappingKnowledgeBase(bounded, 1_000, 1, 1_000);
        store.record(event("one"));
        assertThrows(IllegalStateException.class, () -> store.record(event("two")));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonLinesMappingKnowledgeBase(temporary.resolve("x"), 10, 1, 11));
    }

    @Test void symlinkIsRejectedWhenSupported() throws Exception {
        Path actual = temporary.resolve("actual.jsonl");
        Files.writeString(actual, "");
        Path link = temporary.resolve("link.jsonl");
        try { Files.createSymbolicLink(link, actual); }
        catch (UnsupportedOperationException exception) { return; }
        assertThrows(IllegalArgumentException.class,
                () -> new JsonLinesMappingKnowledgeBase(link).snapshot());
    }

    private static MappingFeedback event(String id) {
        return new MappingFeedback("1.0", id, "2026-01-01T00:00:00Z",
                MappingFeedback.FeedbackType.CONFIRMED,
                new MappingFeedback.SourceMetadata("c0", 0, 7, 2, "codigo cliente"),
                new MappingFeedback.Scope("customer", "1", "schema-sha", "pt-BR", "customer"),
                "customer.code", "customer.code", .5, "synthetic-test",
                "0.4.0-SNAPSHOT", "config-sha");
    }
}
