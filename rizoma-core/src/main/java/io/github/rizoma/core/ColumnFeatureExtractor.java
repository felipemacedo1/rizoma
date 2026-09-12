package io.github.rizoma.core;

import java.util.Objects;

/** Minimal concrete feature extractor; no strategy interface is needed through 0.2. */
public final class ColumnFeatureExtractor {
    private final HeaderNormalizer normalizer;

    public ColumnFeatureExtractor(HeaderNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    /** Projects only evidence used by the current scorer. */
    public ColumnFeatures extract(ColumnProfile profile) {
        Objects.requireNonNull(profile, "profile");
        var header = normalizer.normalize(profile.column().header());
        return new ColumnFeatures(profile.column(), header.comparable(), header.compact(), header.tokens(),
                profile.inferredType(), profile.rowCount() - profile.nullCount(), profile.semanticEvidence());
    }
}
