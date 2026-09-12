package io.github.felipemacedo1.rizoma.core;

import java.util.Locale;
import java.util.Objects;

/** Open, namespaced semantic type identifier such as {@code br:cpf}. */
public record SemanticType(String id) implements Comparable<SemanticType> {
    public SemanticType {
        Objects.requireNonNull(id, "id");
        id = id.strip().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("semantic type must be namespaced: " + id);
        }
    }

    @Override public int compareTo(SemanticType other) { return id.compareTo(other.id); }
}
