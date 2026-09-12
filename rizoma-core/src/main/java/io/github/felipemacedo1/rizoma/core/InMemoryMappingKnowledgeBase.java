package io.github.felipemacedo1.rizoma.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded event store with immutable indexed snapshots for tests and embedded use. */
public final class InMemoryMappingKnowledgeBase implements MappingKnowledgeBase {
    public static final int DEFAULT_MAX_EVENTS = 100_000;
    private static final int SUPPORT_SATURATION = 8;
    private final int maxEvents;
    private final Map<String, MappingFeedback> events = new LinkedHashMap<>();
    private KnowledgeSnapshot cached;

    public InMemoryMappingKnowledgeBase() { this(DEFAULT_MAX_EVENTS); }

    public InMemoryMappingKnowledgeBase(int maxEvents) {
        if (maxEvents <= 0) throw new IllegalArgumentException("maxEvents must be positive");
        this.maxEvents = maxEvents;
    }

    @Override public synchronized void record(MappingFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        MappingFeedback existing = events.get(feedback.feedbackId());
        if (existing != null) {
            if (existing.equals(feedback)) return;
            throw new IllegalArgumentException("feedbackId already identifies a different event");
        }
        if (events.size() >= maxEvents) throw new IllegalStateException("knowledge event limit reached");
        events.put(feedback.feedbackId(), feedback);
        cached = null;
    }

    @Override public synchronized KnowledgeSnapshot snapshot() {
        if (cached == null) cached = buildSnapshot(List.copyOf(events.values()));
        return cached;
    }

    private static KnowledgeSnapshot buildSnapshot(List<MappingFeedback> events) {
        var aggregates = new HashMap<KnowledgeQuery, Aggregate>();
        for (MappingFeedback feedback : events) {
            KnowledgeQuery suggested = query(feedback, feedback.suggestedTargetFieldId());
            switch (feedback.type()) {
                case CONFIRMED -> aggregates.computeIfAbsent(suggested, ignored -> new Aggregate())
                        .positive(feedback, false);
                case REJECTED -> aggregates.computeIfAbsent(suggested, ignored -> new Aggregate())
                        .negative(feedback, false);
                case CORRECTED -> {
                    aggregates.computeIfAbsent(suggested, ignored -> new Aggregate()).negative(feedback, true);
                    KnowledgeQuery corrected = query(feedback, feedback.humanTargetFieldId());
                    aggregates.computeIfAbsent(corrected, ignored -> new Aggregate()).positive(feedback, true);
                }
            }
        }
        var evidence = new HashMap<KnowledgeQuery, HistoricalEvidence>();
        aggregates.forEach((query, aggregate) -> evidence.put(query, aggregate.evidence(query)));
        String id = fingerprint(events);
        Map<KnowledgeQuery, HistoricalEvidence> immutable = Map.copyOf(evidence);
        return new Snapshot(id, events.size(), immutable);
    }

    private static KnowledgeQuery query(MappingFeedback feedback, String target) {
        MappingFeedback.Scope scope = feedback.scope();
        return new KnowledgeQuery(scope.schemaId(), scope.schemaVersion(), scope.schemaFingerprint(),
                target, feedback.source().normalizedSourceName(), scope.locale(), scope.domainContext());
    }

    private static String fingerprint(List<MappingFeedback> events) {
        var ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparing(MappingFeedback::feedbackId));
        var canonical = new StringBuilder("mapping-knowledge:1.0:saturation=")
                .append(SUPPORT_SATURATION);
        for (MappingFeedback event : ordered) canonical.append('\n').append(event);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Aggregate {
        private long confirmed, rejected, correctedTo, correctedFrom;
        private String lastPositive = "", lastNegative = "";
        private void positive(MappingFeedback feedback, boolean correction) {
            if (correction) correctedTo++; else confirmed++;
            if (feedback.timestamp().compareTo(lastPositive) > 0) lastPositive = feedback.timestamp();
        }
        private void negative(MappingFeedback feedback, boolean correction) {
            if (correction) correctedFrom++; else rejected++;
            if (feedback.timestamp().compareTo(lastNegative) > 0) lastNegative = feedback.timestamp();
        }
        private HistoricalEvidence evidence(KnowledgeQuery query) {
            long positive = confirmed + correctedTo;
            long negative = rejected + correctedFrom;
            long total = positive + negative;
            double signedSupport = (double) (positive - negative) / (total + 2.0);
            double score = signedSupport > 0 ? 1.0 : signedSupport < 0 ? 0.0 : 0.5;
            double support = Math.min(1.0, Math.log1p(total) / Math.log1p(SUPPORT_SATURATION));
            double reliability = support * Math.abs(signedSupport);
            String explanation = "positive=" + positive + ", negative=" + negative
                    + ", smoothedSignedSupport=" + rounded(signedSupport)
                    + ", supportSaturation=" + SUPPORT_SATURATION;
            return new HistoricalEvidence(query.targetFieldId(), query.normalizedSourceName(),
                    confirmed, rejected, correctedTo, correctedFrom, lastPositive, lastNegative,
                    bounded(score), bounded(reliability), positive > 0 && negative > 0, explanation);
        }
    }

    private static double bounded(double value) { return Math.max(0, Math.min(1, value)); }
    private static double rounded(double value) { return Math.round(value * 10_000.0) / 10_000.0; }

    private record Snapshot(String id, long eventCount,
            Map<KnowledgeQuery, HistoricalEvidence> evidence) implements KnowledgeSnapshot {
        @Override public String version() { return "1.0"; }
        @Override public HistoricalEvidence find(KnowledgeQuery query) {
            Objects.requireNonNull(query);
            return evidence.getOrDefault(query, new HistoricalEvidence(query.targetFieldId(),
                    query.normalizedSourceName(), 0, 0, 0, 0, "", "", 0, 0,
                    false, "no matching historical feedback"));
        }
    }
}
