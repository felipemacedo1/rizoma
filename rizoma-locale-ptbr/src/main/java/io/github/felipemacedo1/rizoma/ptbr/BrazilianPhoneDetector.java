package io.github.felipemacedo1.rizoma.ptbr;

import io.github.felipemacedo1.rizoma.core.SemanticDetector;
import io.github.felipemacedo1.rizoma.core.SemanticType;

/** Conservative Brazilian phone detector; bare eleven-digit values remain ambiguous. */
public final class BrazilianPhoneDetector implements SemanticDetector {
    private static final SemanticType TYPE = new SemanticType("br:phone");
    @Override public SemanticType type() { return TYPE; }
    @Override public ValueEvidence inspect(String raw) {
        if (raw == null || raw.isBlank()) return ValueEvidence.unavailable();
        String value = raw.strip();
        String digits = value.replaceAll("[^0-9]", "");
        boolean shape = digits.matches("\\d{10,11}");
        boolean valid = shape && Integer.parseInt(digits.substring(0, 2)) >= 11;
        return new ValueEvidence(true, shape, valid, value.matches("\\d{11}"));
    }
    @Override public Accumulator newAccumulator() {
        return new Accumulator() {
            long observed, shape, valid, ambiguous;
            @Override public void accept(String raw) {
                if (raw == null || raw.isBlank()) return;
                observed++;
                String value = raw.strip();
                String digits = value.replaceAll("[^0-9]", "");
                boolean acceptedShape = digits.matches("\\d{10,11}");
                if (acceptedShape) shape++;
                if (acceptedShape && Integer.parseInt(digits.substring(0, 2)) >= 11) valid++;
                if (value.matches("\\d{11}")) ambiguous++;
            }
            @Override public SemanticEvidence finish(int minimum) {
                double shapeScore = observed == 0 ? 0 : (double) shape / observed;
                double validScore = observed == 0 ? 0 : (double) valid / observed;
                double reliability = SemanticDetector.reliability(observed, ambiguous, minimum);
                return new SemanticEvidence(TYPE, observed, shape, valid, ambiguous, shapeScore,
                        validScore, reliability,
                        validScore >= .8 && reliability >= .5 && ambiguous == 0,
                        ambiguous > 0 ? "bare eleven-digit values are ambiguous" : "Brazilian phone shape");
            }
        };
    }
}
