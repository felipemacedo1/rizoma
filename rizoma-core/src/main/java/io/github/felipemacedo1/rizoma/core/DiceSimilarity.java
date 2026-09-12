package io.github.felipemacedo1.rizoma.core;

import java.util.HashSet;
import java.util.Set;

/** Sørensen-Dice coefficient over whitespace-separated token sets. */
public final class DiceSimilarity implements SimilarityMetric {
    @Override public String id() { return "dice.tokens"; }

    @Override public double compare(String left, String right) {
        Set<String> a = tokens(left); Set<String> b = tokens(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersection = 0;
        for (String token : a) if (b.contains(token)) intersection++;
        return (2.0 * intersection) / (a.size() + b.size());
    }

    private static Set<String> tokens(String value) {
        var result = new HashSet<String>();
        if (value != null && !value.isBlank()) {
            for (String token : value.strip().split("\\s+")) result.add(token);
        }
        return result;
    }
}
