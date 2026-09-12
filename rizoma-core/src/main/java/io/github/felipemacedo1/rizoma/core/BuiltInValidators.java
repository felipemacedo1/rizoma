package io.github.felipemacedo1.rizoma.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** JDK-only validators with explicit, stable error codes. */
public final class BuiltInValidators {
    private BuiltInValidators() {}
    public static List<Validator<?>> defaults() {
        return List.of(new Required(), new Regex(), new Length(), new EnumValue(),
                new NumericRange(), new DateRange());
    }
    private abstract static class AnyValidator implements Validator<Object> {
        public String version() { return "1"; }
        public Class<Object> valueType() { return Object.class; }
        ValidationResult pass() { return ValidationResult.pass(id(), version()); }
        ValidationResult fail(String code, String message) { return ValidationResult.failure(id(), version(), code, message); }
    }
    private static final class Required extends AnyValidator {
        public String id() { return "core:required"; }
        public ValidationResult validate(Object value, ValidationContext context) {
            return context.originalValue() == null || context.originalValue().isBlank()
                    ? fail("REQUIRED_VALUE_MISSING", "required source value is blank") : pass();
        }
    }
    private static final class Regex extends AnyValidator {
        public String id() { return "core:regex"; }
        public ValidationResult validate(Object value, ValidationContext context) {
            String expression = context.options().get("pattern");
            if (expression == null) return fail("INVALID_VALIDATOR_OPTION", "regex pattern is required");
            try { return value != null && Pattern.compile(expression).matcher(value.toString()).matches()
                    ? pass() : fail("REGEX_MISMATCH", "value does not match configured pattern"); }
            catch (PatternSyntaxException e) { return fail("INVALID_VALIDATOR_OPTION", "regex pattern is invalid"); }
        }
    }
    private static final class Length extends AnyValidator {
        public String id() { return "core:length"; }
        public ValidationResult validate(Object value, ValidationContext context) {
            int length = value == null ? 0 : value.toString().length();
            try {
                int min = Integer.parseInt(context.options().getOrDefault("min", "0"));
                int max = Integer.parseInt(context.options().getOrDefault("max", Integer.toString(Integer.MAX_VALUE)));
                return length >= min && length <= max ? pass() : fail("LENGTH_OUT_OF_RANGE", "value length is outside configured range");
            } catch (NumberFormatException e) { return fail("INVALID_VALIDATOR_OPTION", "length min/max must be integers"); }
        }
    }
    private static final class EnumValue extends AnyValidator {
        public String id() { return "core:enum"; }
        public ValidationResult validate(Object value, ValidationContext context) {
            Set<String> allowed = Set.copyOf(Arrays.asList(context.options().getOrDefault("values", "").split("\\|", -1)));
            return value != null && allowed.contains(value.toString()) ? pass() : fail("VALUE_NOT_IN_ENUM", "value is not in configured enum");
        }
    }
    private static final class NumericRange extends AnyValidator {
        public String id() { return "core:numeric-range"; }
        public ValidationResult validate(Object value, ValidationContext context) {
            try {
                BigDecimal number = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
                String min = context.options().get("min"), max = context.options().get("max");
                if (min != null && number.compareTo(new BigDecimal(min)) < 0 || max != null && number.compareTo(new BigDecimal(max)) > 0)
                    return fail("NUMERIC_OUT_OF_RANGE", "number is outside configured range");
                return pass();
            } catch (RuntimeException e) { return fail("INVALID_NUMERIC_VALUE", "numeric range requires a numeric value and bounds"); }
        }
    }
    private static final class DateRange extends AnyValidator {
        public String id() { return "core:date-range"; }
        public ValidationResult validate(Object value, ValidationContext context) {
            try {
                LocalDate date = value instanceof LocalDate local ? local : LocalDate.parse(value.toString());
                String min = context.options().get("min"), max = context.options().get("max");
                if (min != null && date.isBefore(LocalDate.parse(min)) || max != null && date.isAfter(LocalDate.parse(max)))
                    return fail("DATE_OUT_OF_RANGE", "date is outside configured range");
                return pass();
            } catch (RuntimeException e) { return fail("INVALID_DATE_VALUE", "date range requires ISO dates and a date value"); }
        }
    }
}
