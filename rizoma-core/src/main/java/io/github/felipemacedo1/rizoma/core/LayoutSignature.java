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

/** Privacy-safe structural identity, distinct from the full source-content fingerprint. */
public record LayoutSignature(String formatVersion, String fingerprint, String format,
        String charset, String delimiter, boolean headerPresent, String normalizationVersion,
        Map<String, String> attributes, List<Column> columns) {
    public static final String FORMAT_VERSION = "1.0";
    public static final String NORMALIZATION_VERSION = "header-normalization-0.5";

    public LayoutSignature {
        require(formatVersion, "formatVersion"); require(fingerprint, "fingerprint");
        require(format, "format"); require(normalizationVersion, "normalizationVersion");
        charset = charset == null ? "" : charset; delimiter = delimiter == null ? "" : delimiter;
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        columns = List.copyOf(columns == null ? List.of() : columns);
    }

    /** Builds a signature from bounded profiles without retaining source values. */
    public static LayoutSignature create(DataReader.SourceStructure structure,
            List<ColumnProfile> profiles, HeaderNormalizer normalizer) {
        Objects.requireNonNull(structure); Objects.requireNonNull(profiles); Objects.requireNonNull(normalizer);
        Map<String, ColumnProfile> byId = new HashMap<>();
        profiles.forEach(profile -> byId.put(profile.column().id(), profile));
        var occurrences = new HashMap<String, Integer>();
        var columns = new ArrayList<Column>();
        for (DataReader.SourceColumn source : structure.columns()) {
            String normalized = normalizer.normalize(source.header()).comparable();
            int occurrence = occurrences.merge(normalized, 1, Integer::sum) - 1;
            ColumnProfile profile = byId.get(source.id());
            PhysicalType type = profile == null ? PhysicalType.EMPTY : profile.inferredType();
            long observed = profile == null ? 0 : profile.rowCount() - profile.nullCount();
            String semantic = profile == null || profile.statistics() == null
                    ? "" : profile.statistics().dominantSemanticType();
            columns.add(new Column(source.id(), source.position(), normalized, occurrence,
                    type, semantic == null ? "" : semantic, observed));
        }
        Map<String, String> orderedAttributes = new LinkedHashMap<>();
        structure.attributes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> orderedAttributes.put(entry.getKey(), entry.getValue()));
        String normalizationVersion = NORMALIZATION_VERSION + ':' + normalizer.fingerprint();
        String canonical = canonical(structure.format(), structure.charset(), structure.delimiter(),
                structure.headerPresent(), normalizationVersion, orderedAttributes, columns);
        return new LayoutSignature(FORMAT_VERSION, sha256(canonical), structure.format(),
                structure.charset(), structure.delimiter(), structure.headerPresent(),
                normalizationVersion, orderedAttributes, columns);
    }

    /** Structural key that survives safe column reordering. */
    public record Column(String sourceColumnId, int position, String normalizedHeader,
            int occurrence, PhysicalType observedType, String dominantSemanticType,
            long observedNonEmptyValues) {
        public Column {
            require(sourceColumnId, "sourceColumnId");
            normalizedHeader = normalizedHeader == null ? "" : normalizedHeader;
            dominantSemanticType = dominantSemanticType == null ? "" : dominantSemanticType;
            observedType = observedType == null ? PhysicalType.EMPTY : observedType;
            if (position < 0 || occurrence < 0 || observedNonEmptyValues < 0)
                throw new IllegalArgumentException("invalid layout column metadata");
        }
        public String structuralKey() { return normalizedHeader + '#' + occurrence; }
    }

    private static String canonical(String format, String charset, String delimiter,
            boolean header, String normalizationVersion, Map<String, String> attributes, List<Column> columns) {
        var value = new StringBuilder(FORMAT_VERSION).append('|').append(normalizationVersion)
                .append('|').append(format).append('|').append(charset).append('|')
                .append(delimiter).append('|').append(header).append('|').append(attributes);
        columns.stream().sorted(Comparator.comparingInt(Column::position)).forEach(column -> value
                .append('|').append(column.position()).append(':').append(column.normalizedHeader())
                .append('#').append(column.occurrence()).append(':').append(column.observedType())
                .append(':').append(column.dominantSemanticType()));
        return value.toString();
    }

    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
