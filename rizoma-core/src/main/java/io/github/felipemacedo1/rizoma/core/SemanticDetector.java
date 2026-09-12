package io.github.felipemacedo1.rizoma.core;

/** Streaming semantic detector. Accumulators see raw values transiently; results contain aggregates only. */
public interface SemanticDetector {
    SemanticType type();
    Accumulator newAccumulator();

    /** Optional per-value classification used only to retain bounded protected anomaly locations. */
    default ValueEvidence inspect(String rawValue) { return ValueEvidence.unavailable(); }

    interface Accumulator {
        void accept(String rawValue);
        SemanticEvidence finish(int minimumEvidenceValues);
    }

    /** Per-value result; it must never contain or retain the source value. */
    record ValueEvidence(boolean available, boolean shapeMatch, boolean valid, boolean ambiguous) {
        public static ValueEvidence unavailable() { return new ValueEvidence(false, false, false, false); }
    }

    /** Aggregate semantic evidence safe for public reports. */
    record SemanticEvidence(SemanticType type, long observed, long shapeMatches,
                            long validMatches, long ambiguous, double shapeScore,
                            double validityScore, double reliability,
                            boolean strongIdentity, String explanation,
                            double semanticConfidence) {
        public SemanticEvidence {
            if (observed < 0 || shapeMatches < 0 || validMatches < 0 || ambiguous < 0
                    || !finite01(shapeScore) || !finite01(validityScore) || !finite01(reliability)
                    || !finite01(semanticConfidence)) {
                throw new IllegalArgumentException("invalid semantic evidence");
            }
        }

        /** Backward-compatible constructor used by detector implementations from 0.1. */
        public SemanticEvidence(SemanticType type, long observed, long shapeMatches,
                                long validMatches, long ambiguous, double shapeScore,
                                double validityScore, double reliability,
                                boolean strongIdentity, String explanation) {
            this(type, observed, shapeMatches, validMatches, ambiguous, shapeScore,
                    validityScore, reliability, strongIdentity, explanation,
                    validityScore * reliability);
        }
        private static boolean finite01(double v) { return Double.isFinite(v) && v >= 0 && v <= 1; }
    }

    static double reliability(long observed, long ambiguous, int minimumEvidenceValues) {
        if (observed == 0) return 0;
        double quantity = Math.min(1.0, (double) observed / minimumEvidenceValues);
        double unambiguous = 1.0 - Math.min(1.0, (double) ambiguous / observed);
        return quantity * unambiguous;
    }
}
