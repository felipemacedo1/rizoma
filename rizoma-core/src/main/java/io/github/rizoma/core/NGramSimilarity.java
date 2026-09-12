package io.github.rizoma.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Sørensen-Dice similarity over sets of character n-grams with boundary
 * markers. Extraction is linear in input length; set comparison is expected
 * linear in the number of distinct grams.
 */
public final class NGramSimilarity implements SimilarityMetric {
    private final int size;

    /** Creates a character n-gram metric. */
    public NGramSimilarity(int size) {
        if (size < 1) throw new IllegalArgumentException("n-gram size must be positive");
        this.size = size;
    }

    @Override public String id() { return "dice." + size + "gram.characters"; }

    @Override public double compare(String left, String right) {
        Set<String> a = grams(left);
        Set<String> b = grams(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersection = 0;
        for (String gram : a) if (b.contains(gram)) intersection++;
        return (2.0 * intersection) / (a.size() + b.size());
    }

    private Set<String> grams(String value) {
        var result = new HashSet<String>();
        if (value == null || value.isEmpty()) return result;
        String padded = "^" + value + "$";
        if (padded.length() <= size) {
            result.add(padded);
            return result;
        }
        for (int i = 0; i <= padded.length() - size; i++) {
            result.add(padded.substring(i, i + size));
        }
        return result;
    }
}
