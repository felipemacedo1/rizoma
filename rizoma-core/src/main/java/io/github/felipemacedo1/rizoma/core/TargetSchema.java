package io.github.felipemacedo1.rizoma.core;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Versioned immutable target schema used by analysis, planning and dry run. */
public record TargetSchema(String id, String version, String context, String locale,
                           List<TargetField> fields) {
    public TargetSchema {
        id = requireText(id, "schema id");
        version = requireText(version, "schema version");
        context = context == null ? "" : context;
        locale = locale == null ? "" : locale;
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (fields.isEmpty()) throw new IllegalArgumentException("schema needs at least one field");
        var ids = new HashSet<String>();
        for (var field : fields) {
            if (!ids.add(field.id())) throw new IllegalArgumentException("duplicate field id: " + field.id());
        }
    }

    /** Starts an ergonomic immutable schema definition. */
    public static Builder builder(String id) { return new Builder(id); }

    /** Mutable construction helper; the resulting {@link TargetSchema} is immutable. */
    public static final class Builder {
        private final String id;
        private String version = "1";
        private String context = "";
        private String locale = "";
        private final List<TargetField> fields = new ArrayList<>();

        private Builder(String id) { this.id = requireText(id, "schema id"); }
        /** Sets the schema version; defaults to {@code 1}. */
        public Builder version(String value) { version = requireText(value, "schema version"); return this; }
        /** Sets an optional domain context used to isolate historical evidence. */
        public Builder context(String value) { context = value == null ? "" : value; return this; }
        /** Sets the locale used by semantic and value rules. */
        public Builder locale(String value) { locale = value == null ? "" : value; return this; }
        /** Adds one target field in declaration order. */
        public Builder field(TargetField value) { fields.add(Objects.requireNonNull(value)); return this; }
        /** Builds and validates the immutable schema. */
        public TargetSchema build() { return new TargetSchema(id, version, context, locale, fields); }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
