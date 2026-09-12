package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.Optional;

/** Explicit registry of confirmed layout templates; it is not historical evidence. */
public interface LayoutRegistry {
    /** Finds bounded candidates using safe structural/schema metadata. */
    List<LayoutTemplate> findCandidates(LayoutQuery query);
    /** Registers one template only through an explicit caller operation. */
    void register(LayoutTemplate template);
    /** Resolves an exact immutable template identity/version. */
    Optional<LayoutTemplate> get(String templateId, String templateVersion);

    /** Coarse indexed query; detailed compatibility is evaluated by the engine. */
    record LayoutQuery(String schemaFingerprint, String format, List<String> structuralHeaderKeys) {
        public LayoutQuery {
            if (schemaFingerprint == null || schemaFingerprint.isBlank())
                throw new IllegalArgumentException("schemaFingerprint must not be blank");
            if (format == null || format.isBlank()) throw new IllegalArgumentException("format must not be blank");
            structuralHeaderKeys = List.copyOf(structuralHeaderKeys == null ? List.of() : structuralHeaderKeys);
        }
        public LayoutQuery(String schemaFingerprint, String format) {
            this(schemaFingerprint, format, List.of());
        }
    }
}
