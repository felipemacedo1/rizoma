package io.github.rizoma.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Creates an immutable plan only from mappings explicitly confirmed by a caller. */
public final class MappingPlanner {
    /** Creates a plan linked to the supplied analysis, schema and configuration fingerprints. */
    public MappingPlan create(AnalysisResult analysis, TargetSchema schema, List<Selection> selections) {
        Objects.requireNonNull(analysis); Objects.requireNonNull(schema); Objects.requireNonNull(selections);
        if (!analysis.engineVersion().equals(MappingEngine.ENGINE_VERSION))
            throw new EngineException("PLAN_ENGINE_MISMATCH", "analysis was produced by a different engine version");
        if (!analysis.schemaId().equals(schema.id()) || !analysis.schemaVersion().equals(schema.version())
                || !analysis.schemaFingerprint().equals(MappingEngine.schemaFingerprint(schema))) {
            throw new EngineException("PLAN_SCHEMA_MISMATCH", "analysis and target schema identities differ");
        }
        Map<String, DataReader.SourceColumn> sources = new HashMap<>();
        analysis.structure().columns().forEach(column -> sources.put(column.id(), column));
        Map<String, TargetField> targets = new HashMap<>();
        schema.fields().forEach(field -> targets.put(field.id(), field));
        Set<String> usedSources = new HashSet<>();
        Set<String> exclusiveTargets = new HashSet<>();
        var mappings = new ArrayList<MappingPlan.FieldMapping>();
        for (Selection selection : selections) {
            if (!usedSources.add(selection.sourceColumnId()))
                throw new IllegalArgumentException("duplicate source mapping: " + selection.sourceColumnId());
            if (!sources.containsKey(selection.sourceColumnId()))
                throw new IllegalArgumentException("unknown source column: " + selection.sourceColumnId());
            TargetField target = targets.get(selection.targetFieldId());
            if (target == null) throw new IllegalArgumentException("unknown target field: " + selection.targetFieldId());
            if (target.exclusive() && !exclusiveTargets.add(target.id()))
                throw new IllegalArgumentException("exclusive target selected more than once: " + target.id());
            mappings.add(new MappingPlan.FieldMapping(selection.sourceColumnId(), target.id(),
                    defaultTransformations(target, schema.locale()), defaultValidations(target), true,
                    selection.confirmationReason()));
        }
        mappings.sort(Comparator.comparing(MappingPlan.FieldMapping::sourceColumnId));
        List<String> unmapped = analysis.structure().columns().stream().map(DataReader.SourceColumn::id)
                .filter(id -> !usedSources.contains(id)).toList();
        List<String> confirmed = mappings.stream().map(MappingPlan.FieldMapping::sourceColumnId).toList();
        String canonical = canonical(analysis.sourceFingerprint(), analysis.schemaFingerprint(),
                analysis.configurationFingerprint(), mappings);
        return new MappingPlan("1.0", sha256(canonical), MappingEngine.ENGINE_VERSION,
                analysis.sourceId(), analysis.sourceFingerprint(), analysis.schemaId(), analysis.schemaVersion(),
                analysis.schemaFingerprint(), analysis.configurationVersion(), analysis.configurationFingerprint(),
                mappings, unmapped, confirmed);
    }

    /** Replaces one mapping's ordered steps and returns a plan with a recalculated deterministic ID. */
    public MappingPlan configure(MappingPlan plan, String sourceColumnId,
            List<MappingPlan.Step> transformations, List<MappingPlan.Step> validations,
            String confirmationReason) {
        Objects.requireNonNull(plan); Objects.requireNonNull(sourceColumnId);
        var mappings = new ArrayList<MappingPlan.FieldMapping>();
        boolean found = false;
        for (MappingPlan.FieldMapping mapping : plan.mappings()) {
            if (mapping.sourceColumnId().equals(sourceColumnId)) {
                found = true;
                mappings.add(new MappingPlan.FieldMapping(sourceColumnId, mapping.targetFieldId(),
                        transformations, validations, true, confirmationReason));
            } else mappings.add(mapping);
        }
        if (!found) throw new IllegalArgumentException("source column is not mapped: " + sourceColumnId);
        mappings.sort(Comparator.comparing(MappingPlan.FieldMapping::sourceColumnId));
        String planId = sha256(canonical(plan.sourceFingerprint(), plan.schemaFingerprint(),
                plan.configurationFingerprint(), mappings));
        return new MappingPlan(plan.formatVersion(), planId, plan.engineVersion(), plan.sourceId(),
                plan.sourceFingerprint(), plan.schemaId(), plan.schemaVersion(), plan.schemaFingerprint(),
                plan.configurationVersion(), plan.configurationFingerprint(), mappings,
                plan.unmappedSourceColumns(), plan.confirmedSourceColumns());
    }

    private static String canonical(String sourceFingerprint, String schemaFingerprint,
            String configurationFingerprint, List<MappingPlan.FieldMapping> mappings) {
        var value = new StringBuilder().append(sourceFingerprint).append('|')
                .append(schemaFingerprint).append('|').append(configurationFingerprint);
        for (MappingPlan.FieldMapping mapping : mappings) {
            value.append('|').append(mapping.sourceColumnId()).append("->").append(mapping.targetFieldId())
                    .append('|').append(mapping.confirmed()).append('|').append(mapping.confirmationReason());
            appendSteps(value, mapping.transformations());
            appendSteps(value, mapping.validations());
        }
        return value.toString();
    }

    private static void appendSteps(StringBuilder value, List<MappingPlan.Step> steps) {
        value.append('[');
        for (MappingPlan.Step step : steps) {
            value.append(step.id()).append('@').append(step.version()).append(':')
                    .append(new java.util.TreeMap<>(step.options())).append(';');
        }
        value.append(']');
    }

    private static List<MappingPlan.Step> defaultTransformations(TargetField target, String locale) {
        var steps = new ArrayList<MappingPlan.Step>();
        Set<String> semantics = target.semanticTypes().stream().map(SemanticType::id).collect(java.util.stream.Collectors.toSet());
        if (semantics.contains("br:cpf")) steps.add(new MappingPlan.Step("br:cpf-canonical"));
        else if (semantics.contains("br:phone")) steps.add(new MappingPlan.Step("br:phone-canonical"));
        else if (semantics.contains("br:cep")) steps.add(new MappingPlan.Step("br:cep-canonical"));
        else switch (target.physicalType()) {
            case INTEGER -> steps.add(new MappingPlan.Step("core:long"));
            case DECIMAL -> steps.add(new MappingPlan.Step("core:big-decimal", "1", locale.isBlank() ? Map.of() : Map.of("locale", locale)));
            case DATE -> steps.add(new MappingPlan.Step("core:local-date", "1", dateOptions(locale)));
            case BOOLEAN -> steps.add(new MappingPlan.Step("core:boolean", "1", locale.isBlank() ? Map.of() : Map.of("locale", locale)));
            default -> { }
        }
        return List.copyOf(steps);
    }

    private static Map<String, String> dateOptions(String locale) {
        if (locale.equalsIgnoreCase("pt-BR")) return Map.of("formats", "uuuu-MM-dd|dd/MM/uuuu");
        if (locale.equalsIgnoreCase("en-US")) return Map.of("formats", "uuuu-MM-dd|MM/dd/uuuu");
        return Map.of();
    }

    private static List<MappingPlan.Step> defaultValidations(TargetField target) {
        var steps = new ArrayList<MappingPlan.Step>();
        if (target.required()) steps.add(new MappingPlan.Step("core:required"));
        if (target.semanticTypes().stream().anyMatch(type -> type.id().equals("br:cpf")))
            steps.add(new MappingPlan.Step("br:cpf-checksum"));
        if (target.semanticTypes().stream().anyMatch(type -> type.id().equals("core:email")))
            steps.add(new MappingPlan.Step("core:regex", "1",
                    Map.of("pattern", "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")));
        return List.copyOf(steps);
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** One explicit mapping confirmation. */
    public record Selection(String sourceColumnId, String targetFieldId, String confirmationReason) {
        public Selection {
            Objects.requireNonNull(sourceColumnId); Objects.requireNonNull(targetFieldId);
            confirmationReason = confirmationReason == null ? "explicit caller confirmation" : confirmationReason;
        }
    }
}
