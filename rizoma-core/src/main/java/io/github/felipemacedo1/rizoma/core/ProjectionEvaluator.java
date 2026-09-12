package io.github.felipemacedo1.rizoma.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Evaluates the small, non-scriptable projection language for one row. */
public final class ProjectionEvaluator {
    /** Evaluates a projection without retaining its source values. */
    public Result evaluate(ProjectionSource source, Map<String, String> rowValues, String locale) {
        Objects.requireNonNull(source); Objects.requireNonNull(rowValues);
        return switch (source.kind()) {
            case SOURCE_COLUMN -> Result.success(rowValues.get(source.sourceColumnId()), 0);
            case CONSTANT -> Result.success(source.constantValue(), 0);
            case UNMAPPED -> Result.failure("UNMAPPED_TARGET", "target has no configured value source", 0);
            case DERIVED -> derived(source.derivedExpression(), rowValues, locale == null ? "" : locale);
        };
    }

    private Result derived(ProjectionSource.DerivedExpression expression,
            Map<String, String> rowValues, String locale) {
        var values = new ArrayList<String>();
        for (ProjectionSource.Operand operand : expression.operands()) {
            String value = operand.kind() == ProjectionSource.OperandKind.CONSTANT
                    ? operand.value() : rowValues.get(operand.value());
            values.add(value == null ? "" : value);
        }
        return switch (expression.operation()) {
            case CONCAT -> Result.success(String.join("", values), 1);
            case COALESCE -> Result.success(values.stream().filter(value -> !value.isBlank())
                    .findFirst().orElse(""), 1);
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> arithmetic(expression.operation(), values, locale);
        };
    }

    private Result arithmetic(ProjectionSource.Operation operation, List<String> values, String locale) {
        var numbers = new ArrayList<BigDecimal>();
        for (String value : values) {
            BigDecimal parsed = decimal(value, locale);
            if (parsed == null)
                return Result.failure("DERIVED_INVALID_NUMBER",
                        "derived arithmetic input is invalid for the configured locale", 1);
            numbers.add(parsed);
        }
        try {
            BigDecimal result = numbers.getFirst();
            switch (operation) {
                case ADD -> {
                    for (int index = 1; index < numbers.size(); index++) result = result.add(numbers.get(index));
                }
                case SUBTRACT -> result = result.subtract(numbers.get(1));
                case MULTIPLY -> {
                    for (int index = 1; index < numbers.size(); index++) result = result.multiply(numbers.get(index));
                }
                case DIVIDE -> {
                    if (numbers.get(1).compareTo(BigDecimal.ZERO) == 0)
                        return Result.failure("DERIVED_DIVISION_BY_ZERO", "derived division divisor is zero", 1);
                    result = result.divide(numbers.get(1), MathContext.DECIMAL128);
                }
                default -> throw new IllegalStateException("not an arithmetic operation");
            }
            return Result.success(result.stripTrailingZeros().toPlainString(), 1);
        } catch (ArithmeticException exception) {
            return Result.failure("DERIVED_ARITHMETIC_ERROR", "derived arithmetic could not be evaluated", 1);
        }
    }

    private static BigDecimal decimal(String raw, String locale) {
        String value = raw == null ? "" : raw.strip().replace(" ", "");
        if (value.isEmpty()) return null;
        if (locale.equalsIgnoreCase("pt-BR")) {
            if (!value.matches("[+-]?(?:\\d+|\\d+,\\d+|\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?)")) return null;
            value = value.replace(".", "").replace(',', '.');
        } else if (locale.equalsIgnoreCase("en-US")) {
            if (!value.matches("[+-]?(?:\\d+|\\d+\\.\\d+|\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?)")) return null;
            value = value.replace(",", "");
        } else if (value.contains(",") || !value.matches("[+-]?\\d+(?:\\.\\d+)?")) {
            return null;
        }
        try { return new BigDecimal(value); }
        catch (NumberFormatException exception) { return null; }
    }

    /** Typed projection outcome. Values remain transient and must not be logged. */
    public record Result(Status status, String value, String code, String reason, int operationsApplied) {
        public Result {
            status = Objects.requireNonNull(status); code = code == null ? "" : code;
            reason = reason == null ? "" : reason;
        }
        static Result success(String value, int operations) {
            return new Result(Status.SUCCESS, value, "", "", operations);
        }
        static Result failure(String code, String reason, int operations) {
            return new Result(Status.FAILURE, null, code, reason, operations);
        }
    }

    public enum Status { SUCCESS, FAILURE }
}
