package io.github.rizoma.core;

import java.util.Objects;

/** Complete input to one stateless analysis execution. */
public record AnalysisRequest(TabularSource source, TargetSchema targetSchema,
                              AnalysisOptions options, MappingKnowledgeBase knowledgeBase) {
    public AnalysisRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetSchema, "targetSchema");
        options = options == null ? AnalysisOptions.defaults() : options;
        knowledgeBase = knowledgeBase == null ? NoOpMappingKnowledgeBase.INSTANCE : knowledgeBase;
    }

    /** Backward-compatible request using the default empty knowledge base. */
    public AnalysisRequest(TabularSource source, TargetSchema targetSchema, AnalysisOptions options) {
        this(source, targetSchema, options, NoOpMappingKnowledgeBase.INSTANCE);
    }
}
