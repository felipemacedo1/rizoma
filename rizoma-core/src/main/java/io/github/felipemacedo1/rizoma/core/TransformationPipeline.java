package io.github.felipemacedo1.rizoma.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes the ordered transformation steps from a mapping without retaining rows. */
public final class TransformationPipeline {
    private final Map<String, ValueTransformer<?, ?>> transformers;

    public TransformationPipeline(List<ValueTransformer<?, ?>> configured) {
        var map = new LinkedHashMap<String, ValueTransformer<?, ?>>();
        for (var transformer : configured) {
            if (map.putIfAbsent(transformer.id(), transformer) != null)
                throw new IllegalArgumentException("duplicate transformer: " + transformer.id());
        }
        transformers = Map.copyOf(map);
    }

    /** Applies steps in order and stops at the first data failure. */
    public PipelineResult execute(String original, List<MappingPlan.Step> steps,
                                  TargetField target, String locale) {
        Object current = original;
        var results = new ArrayList<ValueTransformer.TransformationResult<?>>();
        for (MappingPlan.Step step : steps) {
            ValueTransformer<?, ?> transformer = transformers.get(step.id());
            if (transformer == null || !transformer.version().equals(step.version()))
                throw new EngineException("UNKNOWN_TRANSFORMER", "mapping plan references an unavailable transformer");
            if (current != null && !transformer.sourceType().isInstance(current))
                throw new EngineException("TRANSFORMER_TYPE_MISMATCH", "mapping plan has incompatible transformer types");
            var result = invoke(transformer, current,
                    new ValueTransformer.TransformationContext(target, locale, step.options()));
            results.add(result);
            if (result.status() == ValueTransformer.Status.FAILURE)
                return new PipelineResult(ValueTransformer.Status.FAILURE, null, results);
            current = result.transformedValue();
        }
        boolean warning = results.stream().anyMatch(item -> item.status() == ValueTransformer.Status.WARNING);
        return new PipelineResult(warning ? ValueTransformer.Status.WARNING : ValueTransformer.Status.SUCCESS,
                current, results);
    }

    @SuppressWarnings("unchecked")
    private static <S, T> ValueTransformer.TransformationResult<T> invoke(
            ValueTransformer<S, T> transformer, Object value,
            ValueTransformer.TransformationContext context) {
        return transformer.transform((S) value, context);
    }

    /** Transient value and detailed step results; callers must not log original values. */
    public record PipelineResult(ValueTransformer.Status status, Object value,
                                 List<ValueTransformer.TransformationResult<?>> steps) {
        public PipelineResult { steps = List.copyOf(steps); }
    }
}
