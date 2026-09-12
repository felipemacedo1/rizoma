package io.github.felipemacedo1.rizoma.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Declarative, bounded origin of one target value in a mapping plan. */
public record ProjectionSource(Kind kind, String sourceColumnId, String constantValue,
        DerivedExpression derivedExpression) {
    public ProjectionSource {
        kind = Objects.requireNonNull(kind, "kind");
        sourceColumnId = sourceColumnId == null ? "" : safe(sourceColumnId, "sourceColumnId", 128);
        constantValue = constantValue == null ? "" : safe(constantValue, "constantValue", 16_384);
        switch (kind) {
            case SOURCE_COLUMN -> {
                if (sourceColumnId.isBlank() || derivedExpression != null || !constantValue.isEmpty())
                    throw new IllegalArgumentException("SOURCE_COLUMN requires only sourceColumnId");
            }
            case CONSTANT -> {
                if (!sourceColumnId.isEmpty() || derivedExpression != null)
                    throw new IllegalArgumentException("CONSTANT requires only constantValue");
            }
            case DERIVED -> {
                if (!sourceColumnId.isEmpty() || !constantValue.isEmpty() || derivedExpression == null)
                    throw new IllegalArgumentException("DERIVED requires only derivedExpression");
            }
            case UNMAPPED -> {
                if (!sourceColumnId.isEmpty() || !constantValue.isEmpty() || derivedExpression != null)
                    throw new IllegalArgumentException("UNMAPPED does not accept a value source");
            }
        }
    }

    /** Creates a direct source-column projection. */
    public static ProjectionSource sourceColumn(String sourceColumnId) {
        return new ProjectionSource(Kind.SOURCE_COLUMN, sourceColumnId, "", null);
    }

    /** Creates an explicit constant projection. */
    public static ProjectionSource constant(String value) {
        return new ProjectionSource(Kind.CONSTANT, "", value, null);
    }

    /** Creates a deterministic derived projection. */
    public static ProjectionSource derived(DerivedExpression expression) {
        return new ProjectionSource(Kind.DERIVED, "", "", expression);
    }

    /** Creates an explicit target without a value source. */
    public static ProjectionSource unmapped() {
        return new ProjectionSource(Kind.UNMAPPED, "", "", null);
    }

    /** All source-column IDs needed to evaluate this projection. */
    public Set<String> referencedSourceColumns() {
        return switch (kind) {
            case SOURCE_COLUMN -> Set.of(sourceColumnId);
            case DERIVED -> derivedExpression.referencedSourceColumns();
            case CONSTANT, UNMAPPED -> Set.of();
        };
    }

    /** Limited deterministic expression whose operands are source columns or constants. */
    public record DerivedExpression(Operation operation, List<Operand> operands) {
        public DerivedExpression {
            operation = Objects.requireNonNull(operation, "operation");
            operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
            int minimum = operation == Operation.COALESCE || operation == Operation.CONCAT
                    || operation == Operation.ADD || operation == Operation.MULTIPLY ? 1 : 2;
            int maximum = operation == Operation.SUBTRACT || operation == Operation.DIVIDE ? 2 : 32;
            if (operands.size() < minimum || operands.size() > maximum)
                throw new IllegalArgumentException("invalid operand count for " + operation);
        }

        /** Source columns referenced by the expression, in deterministic encounter order. */
        public Set<String> referencedSourceColumns() {
            var result = new LinkedHashSet<String>();
            operands.stream().filter(item -> item.kind() == OperandKind.SOURCE_COLUMN)
                    .forEach(item -> result.add(item.value()));
            return Collections.unmodifiableSet(result);
        }
    }

    /** One expression operand; constants are explicit template/plan configuration. */
    public record Operand(OperandKind kind, String value) {
        public Operand {
            kind = Objects.requireNonNull(kind, "kind");
            value = safe(Objects.requireNonNull(value, "value"), "operand value", 16_384);
            if (kind == OperandKind.SOURCE_COLUMN && value.isBlank())
                throw new IllegalArgumentException("source operand must not be blank");
        }
        public static Operand sourceColumn(String id) { return new Operand(OperandKind.SOURCE_COLUMN, id); }
        public static Operand constant(String value) { return new Operand(OperandKind.CONSTANT, value); }
    }

    public enum Kind { SOURCE_COLUMN, CONSTANT, DERIVED, UNMAPPED }
    public enum OperandKind { SOURCE_COLUMN, CONSTANT }
    public enum Operation { CONCAT, COALESCE, ADD, SUBTRACT, MULTIPLY, DIVIDE }

    private static String safe(String value, String name, int maximum) {
        if (value.length() > maximum || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException(name + " exceeds safe limits");
        return value;
    }
}
