package io.github.rizoma.core;

import java.util.Objects;

/** Exact, context-isolated lookup key for historical mapping evidence. */
public record KnowledgeQuery(String schemaId, String schemaVersion, String schemaFingerprint,
        String targetFieldId, String normalizedSourceName, String locale, String domainContext) {
    public KnowledgeQuery {
        schemaId = required(schemaId, "schemaId");
        schemaVersion = required(schemaVersion, "schemaVersion");
        schemaFingerprint = required(schemaFingerprint, "schemaFingerprint");
        targetFieldId = required(targetFieldId, "targetFieldId");
        normalizedSourceName = required(normalizedSourceName, "normalizedSourceName");
        locale = locale == null ? "" : locale;
        domainContext = domainContext == null ? "" : domainContext;
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
