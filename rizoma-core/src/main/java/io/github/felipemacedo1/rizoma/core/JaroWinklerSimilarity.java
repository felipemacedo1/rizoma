package io.github.felipemacedo1.rizoma.core;

/**
 * Jaro-Winkler similarity with a four-character prefix and scaling factor
 * {@code 0.1}. Prefix boosting is applied only when Jaro is at least 0.7.
 * Complexity is dominated by the direct Jaro implementation: worst-case
 * {@code O(m*n)} time and {@code O(m+n)} memory.
 */
public final class JaroWinklerSimilarity implements SimilarityMetric {
    private static final double BOOST_THRESHOLD = 0.7;
    private final JaroSimilarity jaro = new JaroSimilarity();

    @Override public String id() { return "jaro-winkler.characters"; }

    @Override public double compare(String left, String right) {
        String a = left == null ? "" : left;
        String b = right == null ? "" : right;
        double base = jaro.compare(a, b);
        if (base < BOOST_THRESHOLD) return base;
        int prefix = 0;
        int maximum = Math.min(4, Math.min(a.length(), b.length()));
        while (prefix < maximum && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        return base + prefix * 0.1 * (1.0 - base);
    }
}
