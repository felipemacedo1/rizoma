package io.github.felipemacedo1.rizoma.core;

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

/** Creates immutable source-bound plans only from projections explicitly confirmed by a caller. */
public final class MappingPlanner {
    /** Creates a traditional direct-column plan linked to the supplied analysis. */
    public MappingPlan create(AnalysisResult analysis, TargetSchema schema, List<Selection> selections) {
        return createProjected(analysis, schema, selections.stream()
                .map(selection -> new ProjectionSelection(selection.targetFieldId(),
                        ProjectionSource.sourceColumn(selection.sourceColumnId()),
                        selection.confirmationReason())).toList(), List.of());
    }

    /** Creates a plan with direct, constant, derived or explicitly unmapped target projections. */
    public MappingPlan createProjected(AnalysisResult analysis, TargetSchema schema,
            List<ProjectionSelection> selections, List<String> ignoredSourceColumns) {
        Objects.requireNonNull(analysis); Objects.requireNonNull(schema);
        Objects.requireNonNull(selections); Objects.requireNonNull(ignoredSourceColumns);
        validateAnalysis(analysis, schema);

        Map<String, DataReader.SourceColumn> sources = new HashMap<>();
        analysis.structure().columns().forEach(column -> sources.put(column.id(), column));
        Map<String, TargetField> targets = new HashMap<>();
        schema.fields().forEach(field -> targets.put(field.id(), field));
        Set<String> usedSources = new HashSet<>();
        Set<String> usedTargets = new HashSet<>();
        var mappings = new ArrayList<MappingPlan.FieldMapping>();
        for (ProjectionSelection selection : selections) {
            TargetField target = targets.get(selection.targetFieldId());
            if (target == null) throw new IllegalArgumentException("unknown target field: " + selection.targetFieldId());
            if (!usedTargets.add(target.id()))
                throw new IllegalArgumentException("target selected more than once: " + target.id());
            for (String sourceId : selection.projectionSource().referencedSourceColumns()) {
                if (!sources.containsKey(sourceId))
                    throw new IllegalArgumentException("unknown source column: " + sourceId);
                usedSources.add(sourceId);
            }
            String legacySource = selection.projectionSource().kind() == ProjectionSource.Kind.SOURCE_COLUMN
                    ? selection.projectionSource().sourceColumnId() : "";
            mappings.add(new MappingPlan.FieldMapping(legacySource, target.id(), selection.projectionSource(),
                    defaultTransformations(target, schema.locale()), defaultValidations(target), true,
                    selection.confirmationReason()));
        }

        Set<String> ignored = new HashSet<>();
        for (String sourceId : ignoredSourceColumns) {
            if (!sources.containsKey(sourceId))
                throw new IllegalArgumentException("unknown ignored source column: " + sourceId);
            if (!ignored.add(sourceId))
                throw new IllegalArgumentException("duplicate ignored source column: " + sourceId);
            if (usedSources.contains(sourceId))
                throw new IllegalArgumentException("source column cannot be used and ignored: " + sourceId);
        }
        mappings.sort(Comparator.comparing(MappingPlanner::mappingSortKey));
        List<String> orderedIgnored = ignored.stream().sorted().toList();
        List<String> unmapped = analysis.structure().columns().stream().map(DataReader.SourceColumn::id)
                .filter(id -> !usedSources.contains(id) && !ignored.contains(id)).toList();
        List<String> confirmed = usedSources.stream().sorted().toList();
        String canonical = canonical(analysis.sourceFingerprint(), analysis.schemaFingerprint(),
                analysis.configurationFingerprint(), mappings, orderedIgnored);
        return new MappingPlan("1.2", sha256(canonical + '|' + analysis.knowledgeSnapshotId()
                        + '|' + analysis.knowledgeVersion()), MappingEngine.ENGINE_VERSION,
                analysis.sourceId(), analysis.sourceFingerprint(), analysis.schemaId(), analysis.schemaVersion(),
                analysis.schemaFingerprint(), analysis.configurationVersion(), analysis.configurationFingerprint(),
                analysis.knowledgeSnapshotId(), analysis.knowledgeVersion(), "", "", "",
                LayoutCompatibilityReport.ExecutionRoute.FULL_ANALYSIS,
                mappings, orderedIgnored, unmapped, confirmed);
    }

    /** Replaces one direct mapping's ordered steps and returns a plan with a recalculated ID. */
    public MappingPlan configure(MappingPlan plan, String sourceColumnId,
            List<MappingPlan.Step> transformations, List<MappingPlan.Step> validations,
            String confirmationReason) {
        Objects.requireNonNull(plan); Objects.requireNonNull(sourceColumnId);
        var matches = plan.mappings().stream()
                .filter(mapping -> mapping.projectionSource().kind() == ProjectionSource.Kind.SOURCE_COLUMN
                        && mapping.sourceColumnId().equals(sourceColumnId)).toList();
        if (matches.size() != 1)
            throw new IllegalArgumentException("source column must identify exactly one direct mapping");
        return configureTarget(plan, matches.getFirst().targetFieldId(), transformations,
                validations, confirmationReason);
    }

    /** Replaces execution steps for one target projection. */
    public MappingPlan configureTarget(MappingPlan plan, String targetFieldId,
            List<MappingPlan.Step> transformations, List<MappingPlan.Step> validations,
            String confirmationReason) {
        Objects.requireNonNull(plan); Objects.requireNonNull(targetFieldId);
        var mappings = new ArrayList<MappingPlan.FieldMapping>();
        boolean found = false;
        for (MappingPlan.FieldMapping mapping : plan.mappings()) {
            if (mapping.targetFieldId().equals(targetFieldId)) {
                found = true;
                mappings.add(new MappingPlan.FieldMapping(mapping.sourceColumnId(), targetFieldId,
                        mapping.projectionSource(), transformations, validations, true, confirmationReason));
            } else mappings.add(mapping);
        }
        if (!found) throw new IllegalArgumentException("target field is not projected: " + targetFieldId);
        mappings.sort(Comparator.comparing(MappingPlanner::mappingSortKey));
        String planId = sha256(canonical(plan.sourceFingerprint(), plan.schemaFingerprint(),
                plan.configurationFingerprint(), mappings, plan.ignoredSourceColumns())
                + '|' + plan.knowledgeSnapshotId() + '|' + plan.knowledgeVersion()
                + '|' + plan.layoutTemplateId() + '|' + plan.layoutFingerprint());
        return new MappingPlan(plan.formatVersion(), planId, plan.engineVersion(), plan.sourceId(),
                plan.sourceFingerprint(), plan.schemaId(), plan.schemaVersion(), plan.schemaFingerprint(),
                plan.configurationVersion(), plan.configurationFingerprint(), plan.knowledgeSnapshotId(),
                plan.knowledgeVersion(), plan.layoutTemplateId(), plan.layoutTemplateVersion(),
                plan.layoutFingerprint(), plan.executionRoute(), mappings, plan.ignoredSourceColumns(),
                plan.unmappedSourceColumns(), plan.confirmedSourceColumns());
    }

    static MappingPlan instantiate(LayoutTemplate template, LayoutSignature current,
            LayoutCompatibilityReport.ExecutionRoute route, String sourceId, String sourceFingerprint,
            Map<String, String> sourceRebindings, List<String> currentUnmapped) {
        var mappings = template.bindings().stream()
                .map(mapping -> rebind(mapping, sourceRebindings)).toList();
        List<String> ignored = template.ignoredSourceColumns().stream()
                .map(id -> sourceRebindings.getOrDefault(id, id)).sorted().toList();
        List<String> confirmed = mappings.stream().flatMap(mapping ->
                mapping.projectionSource().referencedSourceColumns().stream()).distinct().sorted().toList();
        String canonical = canonical(sourceFingerprint, template.targetSchemaFingerprint(),
                template.configurationFingerprint(), mappings, ignored);
        String planId = sha256(canonical + '|' + template.knowledgeSnapshotId() + '|'
                + template.knowledgeVersion() + '|' + template.templateId() + '|'
                + current.fingerprint() + '|' + route);
        return new MappingPlan("1.2", planId, MappingEngine.ENGINE_VERSION, sourceId, sourceFingerprint,
                template.targetSchemaId(), template.targetSchemaVersion(), template.targetSchemaFingerprint(),
                template.configurationVersion(), template.configurationFingerprint(),
                template.knowledgeSnapshotId(), template.knowledgeVersion(), template.templateId(),
                template.templateVersion(), current.fingerprint(), route, mappings, ignored,
                currentUnmapped.stream().sorted().toList(), confirmed);
    }

    private static MappingPlan.FieldMapping rebind(MappingPlan.FieldMapping mapping,
            Map<String, String> rebindings) {
        ProjectionSource rebound = rebind(mapping.projectionSource(), rebindings);
        String legacy = rebound.kind() == ProjectionSource.Kind.SOURCE_COLUMN
                ? rebound.sourceColumnId() : "";
        return new MappingPlan.FieldMapping(legacy, mapping.targetFieldId(), rebound,
                mapping.transformations(), mapping.validations(), true,
                mapping.confirmationReason() + "; instantiated from confirmed layout template");
    }

    private static ProjectionSource rebind(ProjectionSource source, Map<String, String> rebindings) {
        return switch (source.kind()) {
            case SOURCE_COLUMN -> ProjectionSource.sourceColumn(requiredRebinding(
                    source.sourceColumnId(), rebindings));
            case CONSTANT, UNMAPPED -> source;
            case DERIVED -> ProjectionSource.derived(new ProjectionSource.DerivedExpression(
                    source.derivedExpression().operation(), source.derivedExpression().operands().stream()
                    .map(operand -> operand.kind() == ProjectionSource.OperandKind.SOURCE_COLUMN
                            ? ProjectionSource.Operand.sourceColumn(requiredRebinding(operand.value(), rebindings))
                            : operand).toList()));
        };
    }

    private static String requiredRebinding(String oldId, Map<String, String> rebindings) {
        String current = rebindings.get(oldId);
        if (current == null) throw new EngineException("TEMPLATE_BINDING_UNRESOLVED",
                "layout template source binding could not be resolved safely");
        return current;
    }

    private static void validateAnalysis(AnalysisResult analysis, TargetSchema schema) {
        if (!analysis.engineVersion().equals(MappingEngine.ENGINE_VERSION))
            throw new EngineException("PLAN_ENGINE_MISMATCH", "analysis was produced by a different engine version");
        if (!analysis.schemaId().equals(schema.id()) || !analysis.schemaVersion().equals(schema.version())
                || !analysis.schemaFingerprint().equals(MappingEngine.schemaFingerprint(schema)))
            throw new EngineException("PLAN_SCHEMA_MISMATCH", "analysis and target schema identities differ");
    }

    private static String canonical(String sourceFingerprint, String schemaFingerprint,
            String configurationFingerprint, List<MappingPlan.FieldMapping> mappings,
            List<String> ignoredSourceColumns) {
        var value = new StringBuilder().append(sourceFingerprint).append('|')
                .append(schemaFingerprint).append('|').append(configurationFingerprint)
                .append("|ignored=").append(ignoredSourceColumns);
        for (MappingPlan.FieldMapping mapping : mappings) {
            value.append('|').append(mapping.targetFieldId()).append("<-")
                    .append(mapping.projectionSource()).append('|').append(mapping.confirmed())
                    .append('|').append(mapping.confirmationReason());
            appendSteps(value, mapping.transformations()); appendSteps(value, mapping.validations());
        }
        return value.toString();
    }

    private static void appendSteps(StringBuilder value, List<MappingPlan.Step> steps) {
        value.append('[');
        for (MappingPlan.Step step : steps) value.append(step.id()).append('@').append(step.version())
                .append(':').append(new java.util.TreeMap<>(step.options())).append(';');
        value.append(']');
    }

    private static String mappingSortKey(MappingPlan.FieldMapping mapping) {
        return mapping.sourceColumnId().isEmpty()
                ? "~" + mapping.targetFieldId() : mapping.sourceColumnId() + '|' + mapping.targetFieldId();
    }

    private static List<MappingPlan.Step> defaultTransformations(TargetField target, String locale) {
        var steps = new ArrayList<MappingPlan.Step>();
        Set<String> semantics = target.semanticTypes().stream().map(SemanticType::id)
                .collect(java.util.stream.Collectors.toSet());
        if (semantics.contains("br:cpf")) steps.add(new MappingPlan.Step("br:cpf-canonical"));
        else if (semantics.contains("br:phone")) steps.add(new MappingPlan.Step("br:phone-canonical"));
        else if (semantics.contains("br:cep")) steps.add(new MappingPlan.Step("br:cep-canonical"));
        else switch (target.physicalType()) {
            case INTEGER -> steps.add(new MappingPlan.Step("core:long"));
            case DECIMAL -> steps.add(new MappingPlan.Step("core:big-decimal", "1",
                    locale.isBlank() ? Map.of() : Map.of("locale", locale)));
            case DATE -> steps.add(new MappingPlan.Step("core:local-date", "1", dateOptions(locale)));
            case BOOLEAN -> steps.add(new MappingPlan.Step("core:boolean", "1",
                    locale.isBlank() ? Map.of() : Map.of("locale", locale)));
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
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** One explicit traditional direct-column mapping confirmation. */
    public record Selection(String sourceColumnId, String targetFieldId, String confirmationReason) {
        public Selection {
            Objects.requireNonNull(sourceColumnId); Objects.requireNonNull(targetFieldId);
            confirmationReason = confirmationReason == null
                    ? "explicit caller confirmation" : confirmationReason;
        }
    }

    /** One explicit target projection confirmation. */
    public record ProjectionSelection(String targetFieldId, ProjectionSource projectionSource,
            String confirmationReason) {
        public ProjectionSelection {
            Objects.requireNonNull(targetFieldId); Objects.requireNonNull(projectionSource);
            confirmationReason = confirmationReason == null
                    ? "explicit caller confirmation" : confirmationReason;
        }
    }
}
