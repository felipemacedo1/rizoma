package io.github.felipemacedo1.rizoma.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable reusable recipe confirmed for one structural layout and target schema. */
public record LayoutTemplate(String formatVersion, String templateId, String templateVersion,
        String name, LayoutSignature expectedLayout, String targetSchemaId,
        String targetSchemaVersion, String targetSchemaFingerprint,
        String configurationVersion, String configurationFingerprint,
        String knowledgeSnapshotId, String knowledgeVersion, String originPlanId,
        List<MappingPlan.FieldMapping> bindings, List<String> ignoredSourceColumns,
        String createdAt, String provenance, String engineVersion) {
    public static final String FORMAT_VERSION = "1.0";

    public LayoutTemplate {
        require(formatVersion, "formatVersion"); require(templateId, "templateId");
        require(templateVersion, "templateVersion"); require(name, "name");
        expectedLayout = Objects.requireNonNull(expectedLayout, "expectedLayout");
        require(targetSchemaId, "targetSchemaId"); require(targetSchemaVersion, "targetSchemaVersion");
        require(targetSchemaFingerprint, "targetSchemaFingerprint");
        require(configurationVersion, "configurationVersion");
        require(configurationFingerprint, "configurationFingerprint");
        require(knowledgeSnapshotId, "knowledgeSnapshotId"); require(knowledgeVersion, "knowledgeVersion");
        require(originPlanId, "originPlanId"); require(createdAt, "createdAt");
        require(provenance, "provenance"); require(engineVersion, "engineVersion");
        bindings = List.copyOf(bindings == null ? List.of() : bindings);
        ignoredSourceColumns = List.copyOf(ignoredSourceColumns == null ? List.of() : ignoredSourceColumns);
        Set<String> layoutSources = expectedLayout.columns().stream()
                .map(LayoutSignature.Column::sourceColumnId).collect(java.util.stream.Collectors.toSet());
        Set<String> usedSources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (MappingPlan.FieldMapping binding : bindings) {
            if (!binding.confirmed()) throw new IllegalArgumentException("template binding must be confirmed");
            if (!targets.add(binding.targetFieldId()))
                throw new IllegalArgumentException("template target binding is duplicated");
            for (String source : binding.projectionSource().referencedSourceColumns()) {
                if (!layoutSources.contains(source))
                    throw new IllegalArgumentException("template binding references an unknown source column");
                usedSources.add(source);
            }
        }
        Set<String> ignored = new HashSet<>();
        for (String source : ignoredSourceColumns) {
            if (!layoutSources.contains(source))
                throw new IllegalArgumentException("template ignores an unknown source column");
            if (!ignored.add(source)) throw new IllegalArgumentException("template ignored column is duplicated");
            if (usedSources.contains(source))
                throw new IllegalArgumentException("template source column cannot be used and ignored");
        }
        Set<String> classified = new HashSet<>(usedSources); classified.addAll(ignored);
        if (!classified.equals(layoutSources))
            throw new IllegalArgumentException("every template source column must be bound or explicitly ignored");
    }

    /** Explicitly creates a template from a source-bound plan and its original analysis. */
    public static LayoutTemplate create(String name, String templateVersion,
            AnalysisResult analysis, MappingPlan plan, String createdAt, String provenance,
            HeaderNormalizer normalizer) {
        Objects.requireNonNull(analysis); Objects.requireNonNull(plan); Objects.requireNonNull(normalizer);
        if (!plan.sourceFingerprint().equals(analysis.sourceFingerprint())
                || !plan.sourceId().equals(analysis.sourceId())
                || !plan.schemaFingerprint().equals(analysis.schemaFingerprint())
                || !plan.configurationFingerprint().equals(analysis.configurationFingerprint())
                || !plan.engineVersion().equals(analysis.engineVersion())
                || !plan.formatVersion().equals("1.2"))
            throw new IllegalArgumentException("plan and analysis identities differ");
        if (!plan.unmappedSourceColumns().isEmpty())
            throw new IllegalArgumentException("every source column must be bound or explicitly ignored");
        LayoutSignature layout = LayoutSignature.create(analysis.structure(), analysis.profiles(), normalizer);
        var orderedBindings = new ArrayList<>(plan.mappings());
        orderedBindings.sort(Comparator.comparing(MappingPlan.FieldMapping::targetFieldId));
        var ignored = new ArrayList<>(plan.ignoredSourceColumns());
        ignored.sort(String::compareTo);
        String canonical = canonical(name, templateVersion, layout, analysis, plan,
                orderedBindings, ignored, createdAt, provenance);
        return new LayoutTemplate(FORMAT_VERSION, LayoutSignature.sha256(canonical), templateVersion,
                name, layout, analysis.schemaId(), analysis.schemaVersion(), analysis.schemaFingerprint(),
                analysis.configurationVersion(), analysis.configurationFingerprint(),
                analysis.knowledgeSnapshotId(), analysis.knowledgeVersion(), plan.planId(),
                orderedBindings, ignored, createdAt, provenance, analysis.engineVersion());
    }

    private static String canonical(String name, String version, LayoutSignature layout,
            AnalysisResult analysis, MappingPlan plan, List<MappingPlan.FieldMapping> bindings,
            List<String> ignored, String createdAt, String provenance) {
        var value = new StringBuilder(FORMAT_VERSION).append('|').append(name).append('|')
                .append(version).append('|').append(layout.fingerprint()).append('|')
                .append(analysis.schemaFingerprint()).append('|')
                .append(analysis.configurationFingerprint()).append('|')
                .append(analysis.knowledgeSnapshotId()).append('|').append(plan.planId())
                .append('|').append(createdAt).append('|').append(provenance).append('|').append(ignored);
        for (MappingPlan.FieldMapping binding : bindings) {
            value.append('|').append(binding.targetFieldId()).append("<-").append(binding.projectionSource())
                    .append('|').append(binding.confirmationReason());
            for (MappingPlan.Step step : binding.transformations()) value.append("|T:")
                    .append(step.id()).append('@').append(step.version()).append(new TreeMap<>(step.options()));
            for (MappingPlan.Step step : binding.validations()) value.append("|V:")
                    .append(step.id()).append('@').append(step.version()).append(new TreeMap<>(step.options()));
        }
        return value.toString();
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 16_384
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException(name + " is blank or exceeds safe limits");
    }
}
