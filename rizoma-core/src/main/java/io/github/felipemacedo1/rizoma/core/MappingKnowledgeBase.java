package io.github.felipemacedo1.rizoma.core;

/** Explicit feedback store; analysis consumes only an immutable snapshot. */
public interface MappingKnowledgeBase {
    /** Returns an immutable, reproducibly identified view used throughout one analysis. */
    KnowledgeSnapshot snapshot();

    /** Records one explicit event. Analyze and dry-run never call this method. */
    void record(MappingFeedback feedback);

    /** Convenience lookup against the current snapshot. */
    default HistoricalEvidence find(KnowledgeQuery query) { return snapshot().find(query); }

    /** Immutable query view with O(1)-style indexed lookup for built-in implementations. */
    interface KnowledgeSnapshot {
        String id();
        String version();
        long eventCount();
        HistoricalEvidence find(KnowledgeQuery query);
    }
}
