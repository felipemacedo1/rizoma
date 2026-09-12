package io.github.felipemacedo1.rizoma.ptbr;

import io.github.felipemacedo1.rizoma.core.SemanticDetector;
import io.github.felipemacedo1.rizoma.core.SemanticType;

/** CPF detector that separates apparent shape from checksum validity. */
public final class CpfDetector implements SemanticDetector {
    private static final SemanticType TYPE = new SemanticType("br:cpf");
    @Override public SemanticType type() { return TYPE; }
    @Override public ValueEvidence inspect(String raw) {
        if (raw == null || raw.isBlank()) return ValueEvidence.unavailable();
        String value = raw.strip();
        boolean shape = value.matches("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
        String digits = value.replaceAll("[.-]", "");
        return new ValueEvidence(true, shape, digits.matches("\\d{11}") && checksumValid(digits), false);
    }
    @Override public Accumulator newAccumulator() {
        return new Accumulator() {
            long observed, shape, valid;
            @Override public void accept(String raw) {
                if (raw == null || raw.isBlank()) return;
                observed++;
                String value = raw.strip();
                if (value.matches("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")) shape++;
                String digits = value.replaceAll("[.-]", "");
                if (digits.matches("\\d{11}") && checksumValid(digits)) valid++;
            }
            @Override public SemanticEvidence finish(int minimum) {
                double shapeScore = observed == 0 ? 0 : (double) shape / observed;
                double validScore = observed == 0 ? 0 : (double) valid / observed;
                double reliability = SemanticDetector.reliability(observed, 0, minimum);
                return new SemanticEvidence(TYPE, observed, shape, valid, 0, shapeScore, validScore,
                        reliability, validScore >= .8 && reliability >= .5,
                        "CPF shape and modulus-11 checksum evaluated separately");
            }
        };
    }

    static boolean checksumValid(String digits) {
        if (digits.chars().distinct().count() == 1) return false;
        int first = digit(digits, 9, 10);
        int second = digit(digits, 10, 11);
        return first == digits.charAt(9) - '0' && second == digits.charAt(10) - '0';
    }

    private static int digit(String digits, int length, int initialWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) sum += (digits.charAt(i) - '0') * (initialWeight - i);
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
