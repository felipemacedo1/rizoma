package io.github.felipemacedo1.rizoma.core;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable destination-field metadata consumed by analysis and execution planning. */
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

    /** Starts an ergonomic immutable field definition with physical type TEXT. */
    public static Builder builder(String id) { return new Builder(id); }

    /** Mutable construction helper; the resulting {@link TargetField} is immutable. */
    public static final class Builder {
        private final String id;
        private String displayName;
        private PhysicalType physicalType = PhysicalType.TEXT;
        private final List<String> aliases = new ArrayList<>();
        private final Set<SemanticType> semanticTypes = new LinkedHashSet<>();
        private boolean required;
        private boolean exclusive = true;

        private Builder(String id) {
            this.id = requireText(id, "field id");
            int separator = id.lastIndexOf('.');
            this.displayName = separator >= 0 ? id.substring(separator + 1) : id;
        }
        /** Sets the human-readable field name. */
        public Builder name(String value) { displayName = requireText(value, "display name"); return this; }
        /** Replaces the expected physical type; TEXT is the default. */
        public Builder type(PhysicalType value) { physicalType = Objects.requireNonNull(value); return this; }
        /** Adds accepted source-name aliases. */
        public Builder aliases(String... values) {
            for (String value : values) aliases.add(requireText(value, "alias"));
            return this;
        }
        /** Adds an accepted semantic type identifier such as {@code br:cpf}. */
        public Builder semanticType(String id) { semanticTypes.add(new SemanticType(id)); return this; }
        /** Marks the field as required. */
        public Builder required() { required = true; return this; }
        /** Controls whether collisions on this target require review. */
        public Builder exclusive(boolean value) { exclusive = value; return this; }
        /** Builds and validates the immutable field. */
        public TargetField build() {
            return new TargetField(id, displayName, aliases, physicalType, semanticTypes, required, exclusive);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
