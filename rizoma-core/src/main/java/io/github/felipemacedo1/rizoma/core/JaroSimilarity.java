package io.github.felipemacedo1.rizoma.core;

/**
 * Jaro similarity over UTF-16 code units. This direct implementation searches
 * a bounded window for every character: worst-case {@code O(m*n)} time and
 * {@code O(m+n)} memory.
 */
public final class JaroSimilarity implements SimilarityMetric {
    @Override public String id() { return "jaro.characters"; }

    @Override public double compare(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int window = Math.max(0, Math.max(a.length(), b.length()) / 2 - 1);
        boolean[] matchedA = new boolean[a.length()];
        boolean[] matchedB = new boolean[b.length()];
        int matches = 0;
        for (int i = 0; i < a.length(); i++) {
            int start = Math.max(0, i - window);
            int end = Math.min(i + window + 1, b.length());
            for (int j = start; j < end; j++) {
                if (!matchedB[j] && a.charAt(i) == b.charAt(j)) {
                    matchedA[i] = true;
                    matchedB[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) return 0.0;

        int transposed = 0;
        int j = 0;
        for (int i = 0; i < a.length(); i++) {
            if (!matchedA[i]) continue;
            while (!matchedB[j]) j++;
            if (a.charAt(i) != b.charAt(j)) transposed++;
            j++;
        }
        double m = matches;
        double transpositions = transposed / 2.0;
        return (m / a.length() + m / b.length() + (m - transpositions) / m) / 3.0;
    }
}
