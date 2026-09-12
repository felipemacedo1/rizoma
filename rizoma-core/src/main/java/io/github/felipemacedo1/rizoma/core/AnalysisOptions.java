package io.github.felipemacedo1.rizoma.core;

import java.util.Map;

/** Per-request, format-neutral analysis options. Reader options are explicit string pairs. */
public record AnalysisOptions(Map<String, String> readerOptions, long sampleSeed) {
    public AnalysisOptions {
        readerOptions = Map.copyOf(readerOptions == null ? Map.of() : readerOptions);
    }
    public static AnalysisOptions defaults() { return new AnalysisOptions(Map.of(), 42L); }
}
