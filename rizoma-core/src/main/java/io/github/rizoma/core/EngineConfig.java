package io.github.rizoma.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable scoring, confidence and safety configuration. */
public record EngineConfig(String version, EngineLimits limits, Map<String, Double> weights,
                           int minimumEvidenceValues, double autoMapThreshold,
                           double reviewThreshold, double lowThreshold,
                           double minimumMargin, double minimumCoverage,
                           boolean autoMapEnabled, int candidatePruningThreshold,
                           int candidateShortlistSize, LexicalStrategy lexicalStrategy) {
    private static final List<String> COMPONENTS = List.of(
            "lexical", "semantic", "physicalType", "pattern", "distribution", "history");

    public EngineConfig {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(lexicalStrategy, "lexicalStrategy");
        weights = Map.copyOf(Objects.requireNonNull(weights, "weights"));
        if (minimumEvidenceValues <= 0) throw new IllegalArgumentException("minimumEvidenceValues must be positive");
        if (!finite01(autoMapThreshold) || !finite01(reviewThreshold) || !finite01(lowThreshold)
                || !finite01(minimumMargin) || !finite01(minimumCoverage)
                || autoMapThreshold < reviewThreshold || reviewThreshold < lowThreshold) {
            throw new IllegalArgumentException("invalid confidence thresholds");
        }
        if (candidatePruningThreshold <= 0 || candidateShortlistSize <= 0) {
            throw new IllegalArgumentException("candidate pruning limits must be positive");
        }
        double sum = 0;
        for (String component : COMPONENTS) {
            Double weight = weights.get(component);
            if (weight == null || !Double.isFinite(weight) || weight < 0) {
                throw new IllegalArgumentException("invalid weight for " + component);
            }
            sum += weight;
        }
        if (sum <= 0 || !Double.isFinite(sum)) throw new IllegalArgumentException("effective weights must be positive");
    }

    /** Backward-compatible constructor using the 0.2 candidate-generation defaults. */
    public EngineConfig(String version, EngineLimits limits, Map<String, Double> weights,
                        int minimumEvidenceValues, double autoMapThreshold,
                        double reviewThreshold, double lowThreshold,
                        double minimumMargin, double minimumCoverage,
                        boolean autoMapEnabled) {
        this(version, limits, weights, minimumEvidenceValues, autoMapThreshold,
                reviewThreshold, lowThreshold, minimumMargin, minimumCoverage,
                autoMapEnabled, 128, 32, LexicalStrategy.ENHANCED_0_2);
    }

    public static EngineConfig defaults() {
        return new EngineConfig("0.3-default", EngineLimits.defaults(), Map.of(
                "lexical", .30, "semantic", .30, "physicalType", .10,
                "pattern", .15, "distribution", .05, "history", .10),
                20, .90, .70, .50, .15, .60, false, 128, 32,
                LexicalStrategy.ENHANCED_0_2);
    }

    public double weight(String component) { return weights.get(component); }
    private static boolean finite01(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }

    /** Versioned lexical composition used for reproducible ablation. */
    public enum LexicalStrategy { BASELINE_0_1, ENHANCED_0_2 }
}
