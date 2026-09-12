package io.github.felipemacedo1.rizoma.core;

import java.util.Objects;

/** Default knowledge base: stable empty snapshot and no implicit persistence. */
public final class NoOpMappingKnowledgeBase implements MappingKnowledgeBase {
    public static final String SNAPSHOT_ID = "NO_KNOWLEDGE";
    public static final NoOpMappingKnowledgeBase INSTANCE = new NoOpMappingKnowledgeBase();
    private static final KnowledgeSnapshot SNAPSHOT = new KnowledgeSnapshot() {
        @Override public String id() { return SNAPSHOT_ID; }
        @Override public String version() { return "1.0"; }
        @Override public long eventCount() { return 0; }
        @Override public HistoricalEvidence find(KnowledgeQuery query) {
            Objects.requireNonNull(query);
            return new HistoricalEvidence(query.targetFieldId(), query.normalizedSourceName(),
                    0, 0, 0, 0, "", "", 0, 0, false, "no matching historical feedback");
        }
    };

    private NoOpMappingKnowledgeBase() {}
    @Override public KnowledgeSnapshot snapshot() { return SNAPSHOT; }
    @Override public void record(MappingFeedback feedback) {
        throw new UnsupportedOperationException("NoOp knowledge base does not persist feedback");
    }
}
