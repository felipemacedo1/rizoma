package io.github.felipemacedo1.rizoma.core;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/** Immutable human feedback event containing no source cell value. */
public record MappingFeedback(String formatVersion, String feedbackId, String timestamp,
        FeedbackType type, SourceMetadata source, Scope scope, String suggestedTargetFieldId,
        String humanTargetFieldId, Double previousScore, String provenance,
        String engineVersion, String configurationFingerprint) {
    public MappingFeedback {
        if (!"1.0".equals(formatVersion)) throw new IllegalArgumentException("unsupported feedback formatVersion");
        feedbackId = safeRequired(feedbackId, "feedbackId", 200);
        timestamp = safeRequired(timestamp, "timestamp", 64);
        try { timestamp = Instant.parse(timestamp).toString(); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("timestamp must be ISO-8601 UTC", exception); }
        type = Objects.requireNonNull(type, "type");
        source = Objects.requireNonNull(source, "source");
        scope = Objects.requireNonNull(scope, "scope");
        suggestedTargetFieldId = safeRequired(suggestedTargetFieldId, "suggestedTargetFieldId", 512);
        humanTargetFieldId = humanTargetFieldId == null ? "" : safe(humanTargetFieldId, "humanTargetFieldId", 512);
        if (previousScore != null && (!Double.isFinite(previousScore) || previousScore < 0 || previousScore > 1))
            throw new IllegalArgumentException("previousScore must be in [0,1]");
        provenance = safeRequired(provenance, "provenance", 256);
        engineVersion = safeRequired(engineVersion, "engineVersion", 128);
        configurationFingerprint = safeRequired(configurationFingerprint, "configurationFingerprint", 256);
        switch (type) {
            case CONFIRMED -> {
                if (!suggestedTargetFieldId.equals(humanTargetFieldId))
                    throw new IllegalArgumentException("confirmed feedback must keep the suggested target");
            }
            case REJECTED -> {
                if (!humanTargetFieldId.isEmpty())
                    throw new IllegalArgumentException("rejected feedback must not select a target");
            }
            case CORRECTED -> {
                if (humanTargetFieldId.isEmpty() || suggestedTargetFieldId.equals(humanTargetFieldId))
                    throw new IllegalArgumentException("corrected feedback needs a different human target");
            }
        }
    }

    /** Builds a confirmation from a prior analysis without retaining the source header text. */
    public static MappingFeedback confirmed(String feedbackId, String timestamp,
            AnalysisResult analysis, TargetSchema schema, String sourceColumnId,
            String targetFieldId, HeaderNormalizer normalizer, String provenance) {
        return fromAnalysis(feedbackId, timestamp, FeedbackType.CONFIRMED, analysis, schema,
                sourceColumnId, targetFieldId, targetFieldId, normalizer, provenance);
    }

    /** Builds a rejection from a prior analysis without retaining the source header text. */
    public static MappingFeedback rejected(String feedbackId, String timestamp,
            AnalysisResult analysis, TargetSchema schema, String sourceColumnId,
            String rejectedTargetFieldId, HeaderNormalizer normalizer, String provenance) {
        return fromAnalysis(feedbackId, timestamp, FeedbackType.REJECTED, analysis, schema,
                sourceColumnId, rejectedTargetFieldId, "", normalizer, provenance);
    }

    /** Builds a correction: negative evidence for suggested and positive evidence for selected. */
    public static MappingFeedback corrected(String feedbackId, String timestamp,
            AnalysisResult analysis, TargetSchema schema, String sourceColumnId,
            String suggestedTargetFieldId, String correctTargetFieldId,
            HeaderNormalizer normalizer, String provenance) {
        return fromAnalysis(feedbackId, timestamp, FeedbackType.CORRECTED, analysis, schema,
                sourceColumnId, suggestedTargetFieldId, correctTargetFieldId, normalizer, provenance);
    }

    private static MappingFeedback fromAnalysis(String feedbackId, String timestamp, FeedbackType type,
            AnalysisResult analysis, TargetSchema schema, String sourceColumnId,
            String suggestedTarget, String humanTarget, HeaderNormalizer normalizer, String provenance) {
        Objects.requireNonNull(analysis); Objects.requireNonNull(schema); Objects.requireNonNull(normalizer);
        if (!analysis.schemaId().equals(schema.id()) || !analysis.schemaVersion().equals(schema.version())
                || !analysis.schemaFingerprint().equals(MappingEngine.schemaFingerprint(schema)))
            throw new IllegalArgumentException("analysis and schema differ");
        requireTarget(schema, suggestedTarget);
        if (!humanTarget.isEmpty()) requireTarget(schema, humanTarget);
        DataReader.SourceColumn column = analysis.structure().columns().stream()
                .filter(item -> item.id().equals(sourceColumnId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("source column was not analyzed"));
        HeaderNormalizer.NormalizedHeader normalized = normalizer.normalize(column.header());
        if (normalized.comparable().isBlank())
            throw new IllegalArgumentException("feedback requires a non-empty normalized source name");
        Double previousScore = analysis.candidatesByColumn().getOrDefault(sourceColumnId, List.of()).stream()
                .filter(candidate -> candidate.targetFieldId().equals(suggestedTarget))
                .map(AnalysisResult.MappingCandidate::score).findFirst().orElse(null);
        return new MappingFeedback("1.0", feedbackId, timestamp, type,
                new SourceMetadata(column.id(), column.position(), column.header().length(), normalized.tokens().size(),
                        normalized.comparable()),
                new Scope(schema.id(), schema.version(), analysis.schemaFingerprint(), schema.locale(), schema.context()),
                suggestedTarget, humanTarget, previousScore, provenance,
                analysis.engineVersion(), analysis.configurationFingerprint());
    }

    private static void requireTarget(TargetSchema schema, String target) {
        if (schema.fields().stream().noneMatch(field -> field.id().equals(target)))
            throw new IllegalArgumentException("target field does not exist in schema: " + target);
    }

    private static String safeRequired(String value, String name, int maximum) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return safe(value, name, maximum);
    }

    private static String safe(String value, String name, int maximum) {
        if (value.length() > maximum || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException(name + " exceeds safe metadata limits");
        return value;
    }

    /** Safe source metadata: position and lengths, never the original header or a cell. */
    public record SourceMetadata(String sourceColumnId, int position, int originalHeaderLength,
            int normalizedTokenCount, String normalizedSourceName) {
        public SourceMetadata {
            sourceColumnId = safeRequired(sourceColumnId, "sourceColumnId", 128);
            normalizedSourceName = safeRequired(normalizedSourceName, "normalizedSourceName", 1024);
            if (position < 0 || originalHeaderLength < 0 || normalizedTokenCount <= 0)
                throw new IllegalArgumentException("invalid safe source metadata");
        }
    }

    /** Exact schema, locale and domain boundary used to isolate feedback. */
    public record Scope(String schemaId, String schemaVersion, String schemaFingerprint,
            String locale, String domainContext) {
        public Scope {
            schemaId = safeRequired(schemaId, "schemaId", 512);
            schemaVersion = safeRequired(schemaVersion, "schemaVersion", 128);
            schemaFingerprint = safeRequired(schemaFingerprint, "schemaFingerprint", 256);
            locale = locale == null ? "" : safe(locale, "locale", 64);
            domainContext = domainContext == null ? "" : safe(domainContext, "domainContext", 512);
        }
    }

    public enum FeedbackType { CONFIRMED, REJECTED, CORRECTED }
}
