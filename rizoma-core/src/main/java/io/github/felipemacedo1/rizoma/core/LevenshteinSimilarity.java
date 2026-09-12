package io.github.felipemacedo1.rizoma.core;

/** Normalized Levenshtein similarity with two-row memory. */
public final class LevenshteinSimilarity implements SimilarityMetric {
    @Override public String id() { return "levenshtein.normalized"; }

    @Override public double compare(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.length() > b.length()) { String swap = a; a = b; b = swap; }
        int[] previous = new int[a.length() + 1];
        int[] current = new int[a.length() + 1];
        for (int i = 0; i <= a.length(); i++) previous[i] = i;
        for (int j = 1; j <= b.length(); j++) {
            current[0] = j;
            for (int i = 1; i <= a.length(); i++) {
                int substitution = previous[i - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[i] = Math.min(Math.min(previous[i] + 1, current[i - 1] + 1), substitution);
            }
            int[] swap = previous; previous = current; current = swap;
        }
        return 1.0 - ((double) previous[a.length()] / Math.max(a.length(), b.length()));
    }
}
