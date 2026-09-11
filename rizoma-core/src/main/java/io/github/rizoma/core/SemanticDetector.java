package io.github.rizoma.core;

/** Streaming semantic detector. Accumulators see raw values transiently; results contain aggregates only. */
public interface SemanticDetector {
    SemanticType type();
    Accumulator newAccumulator();

    interface Accumulator {
        void accept(String rawValue);
        SemanticEvidence finish(int minimumEvidenceValues);
    }

    /** Aggregate semantic evidence safe for public reports. */
    record SemanticEvidence(SemanticType type, long observed, long shapeMatches,
                            long validMatches, long ambiguous, double shapeScore,
                            double validityScore, double reliability,
                            boolean strongIdentity, String explanation) {
        public SemanticEvidence {
            if (observed < 0 || shapeMatches < 0 || validMatches < 0 || ambiguous < 0
                    || !finite01(shapeScore) || !finite01(validityScore) || !finite01(reliability)) {
                throw new IllegalArgumentException("invalid semantic evidence");
            }
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
