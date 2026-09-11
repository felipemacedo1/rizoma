package io.github.rizoma.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A destination field and only the metadata consumed by analysis 0.1a. */
public record TargetField(
        String id,
        String displayName,
        List<String> aliases,
        PhysicalType physicalType,
        Set<SemanticType> semanticTypes,
        boolean required,
        boolean exclusive) {
    public TargetField {
        id = requireText(id, "field id");
        displayName = requireText(displayName, "display name");
        aliases = List.copyOf(aliases == null ? List.of() : aliases);
        physicalType = Objects.requireNonNull(physicalType, "physicalType");
        semanticTypes = Set.copyOf(semanticTypes == null ? Set.of() : semanticTypes);
    }

    public TargetField(String id, String displayName, List<String> aliases,
                       PhysicalType physicalType, Set<SemanticType> semanticTypes,
                       boolean required) {
        this(id, displayName, aliases, physicalType, semanticTypes, required, true);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
