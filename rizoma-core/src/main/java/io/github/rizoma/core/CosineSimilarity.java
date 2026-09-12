package io.github.rizoma.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Cosine similarity over character n-gram frequency vectors with boundary
 * markers. It preserves repeated grams, unlike set metrics. Extraction and dot
 * product are expected linear in input length and distinct grams.
 */
public final class CosineSimilarity implements SimilarityMetric {
    private final int gramSize;

    /** Creates a cosine metric over character n-grams of the given size. */
    public CosineSimilarity(int gramSize) {
        if (gramSize < 1) throw new IllegalArgumentException("n-gram size must be positive");
        this.gramSize = gramSize;
    }

    @Override public String id() { return "cosine." + gramSize + "gram.frequencies"; }

    @Override public double compare(String left, String right) {
        Map<String, Integer> a = frequencies(left);
        Map<String, Integer> b = frequencies(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (var entry : a.entrySet()) {
            int count = entry.getValue();
            normA += (double) count * count;
            dot += (double) count * b.getOrDefault(entry.getKey(), 0);
        }
        for (int count : b.values()) normB += (double) count * count;
        if (normA == 0 || normB == 0) return 0.0;
        double result = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return Double.isFinite(result) ? Math.max(0, Math.min(1, result)) : 0.0;
    }

    private Map<String, Integer> frequencies(String value) {
        var result = new HashMap<String, Integer>();
        if (value == null || value.isEmpty()) return result;
        String padded = "^" + value + "$";
        if (padded.length() <= gramSize) {
            result.put(padded, 1);
            return result;
        }
        for (int i = 0; i <= padded.length() - gramSize; i++) {
            result.merge(padded.substring(i, i + gramSize), 1, Integer::sum);
        }
        return result;
    }
}
