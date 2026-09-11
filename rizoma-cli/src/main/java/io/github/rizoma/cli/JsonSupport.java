package io.github.rizoma.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.rizoma.core.PhysicalType;
import io.github.rizoma.core.SemanticType;
import io.github.rizoma.core.TargetField;
import io.github.rizoma.core.TargetSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class JsonSupport {
    static final String SCHEMA_FORMAT_VERSION = "1.0";
    static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonSupport() {}

    static TargetSchema readSchema(Path path) throws IOException {
        SchemaDocument document = MAPPER.readValue(path.toFile(), SchemaDocument.class);
        if (!SCHEMA_FORMAT_VERSION.equals(document.formatVersion())) {
            throw new IllegalArgumentException("unsupported schema JSON formatVersion: " + document.formatVersion());
        }
        if (document.fields() == null) throw new IllegalArgumentException("schema fields are required");
        List<TargetField> fields = document.fields().stream().map(JsonSupport::field).toList();
        return new TargetSchema(document.schemaId(), document.schemaVersion(), document.context(),
                document.locale(), fields);
    }

    private static TargetField field(FieldDocument field) {
        Set<SemanticType> semantics = new TreeSet<>();
        if (field.semanticTypes() != null) {
            field.semanticTypes().forEach(value -> semantics.add(new SemanticType(value)));
        }
        return new TargetField(field.id(), field.displayName(), field.aliases(), field.physicalType(),
                semantics, field.required(), field.exclusive() == null || field.exclusive());
    }

    record SchemaDocument(String formatVersion, String schemaId, String schemaVersion,
                          String context, String locale, List<FieldDocument> fields) {}

    record FieldDocument(String id, String displayName, List<String> aliases,
                         PhysicalType physicalType, List<String> semanticTypes,
                         boolean required, Boolean exclusive) {}
}
