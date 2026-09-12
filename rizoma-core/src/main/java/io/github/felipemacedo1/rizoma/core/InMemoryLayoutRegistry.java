package io.github.felipemacedo1.rizoma.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded in-memory registry indexed by schema fingerprint and source format. */
public final class InMemoryLayoutRegistry implements LayoutRegistry {
    public static final int DEFAULT_MAX_TEMPLATES = 10_000;
    private final int maxTemplates;
    private final Map<String, LayoutTemplate> templates = new LinkedHashMap<>();
    private final Map<String, List<LayoutTemplate>> schemaIndex = new HashMap<>();
    private final Map<String, List<LayoutTemplate>> structureIndex = new HashMap<>();

    public InMemoryLayoutRegistry() { this(DEFAULT_MAX_TEMPLATES); }
    public InMemoryLayoutRegistry(int maxTemplates) {
        if (maxTemplates <= 0) throw new IllegalArgumentException("maxTemplates must be positive");
        this.maxTemplates = maxTemplates;
    }

    @Override public synchronized void register(LayoutTemplate template) {
        Objects.requireNonNull(template);
        String identity = identity(template.templateId(), template.templateVersion());
        LayoutTemplate existing = templates.get(identity);
        if (existing != null) {
            if (existing.equals(template)) return;
            throw new IllegalArgumentException("template identity already has different content");
        }
        if (templates.size() >= maxTemplates) throw new IllegalStateException("layout template limit reached");
        templates.put(identity, template);
        addToIndex(schemaIndex, key(template.targetSchemaFingerprint(), template.expectedLayout().format()), template);
        addToIndex(structureIndex, structureKey(template.expectedLayout().format(),
                template.expectedLayout().columns().stream().map(LayoutSignature.Column::structuralKey).toList()), template);
    }

    @Override public synchronized List<LayoutTemplate> findCandidates(LayoutQuery query) {
        var candidates = new LinkedHashMap<String, LayoutTemplate>();
        schemaIndex.getOrDefault(key(query.schemaFingerprint(), query.format()), List.of())
                .forEach(template -> candidates.put(identity(template.templateId(), template.templateVersion()), template));
        if (!query.structuralHeaderKeys().isEmpty()) {
            structureIndex.getOrDefault(structureKey(query.format(), query.structuralHeaderKeys()), List.of())
                    .forEach(template -> candidates.put(identity(template.templateId(), template.templateVersion()), template));
        }
        return List.copyOf(candidates.values());
    }

    @Override public synchronized Optional<LayoutTemplate> get(String templateId, String templateVersion) {
        return Optional.ofNullable(templates.get(identity(templateId, templateVersion)));
    }

    private static String key(String schema, String format) { return schema + '|' + format; }
    private static String identity(String id, String version) { return id + '@' + version; }
    private static String structureKey(String format, List<String> headers) {
        return format + '|' + headers.stream().sorted().toList();
    }
    private static void addToIndex(Map<String, List<LayoutTemplate>> index,
            String key, LayoutTemplate template) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(template);
        index.get(key).sort(Comparator.comparing(LayoutTemplate::templateId)
                .thenComparing(LayoutTemplate::templateVersion));
    }
}
