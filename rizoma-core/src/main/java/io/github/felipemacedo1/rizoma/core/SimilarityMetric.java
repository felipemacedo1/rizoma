package io.github.felipemacedo1.rizoma.core;

/** Deterministic similarity in [0,1]. */
public interface SimilarityMetric {
    String id();
    double compare(String left, String right);
}
