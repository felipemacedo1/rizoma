package io.github.rizoma.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Versioned target schema used to suggest mappings; it performs no import validation in 0.1a. */
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

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
