package io.github.felipemacedo1.rizoma.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Jaccard similarity over sets of whitespace-separated tokens.
 * Extraction and comparison cost {@code O(|left| + |right|)} expected time and
 * linear memory in the number of distinct tokens.
 */
public final class JaccardSimilarity implements SimilarityMetric {
    @Override public String id() { return "jaccard.tokens"; }

    @Override public double compare(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersection = 0;
        for (String token : a) if (b.contains(token)) intersection++;
        return (double) intersection / (a.size() + b.size() - intersection);
    }

    private static Set<String> tokens(String value) {
        var tokens = new HashSet<String>();
        if (value != null && !value.isBlank()) {
            for (String token : value.strip().split("\\s+")) tokens.add(token);
        }
        return tokens;
    }
}
