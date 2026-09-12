package io.github.felipemacedo1.rizoma.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small set of locale-explicit, JDK-only transformations. */
public final class BuiltInTransformers {
    private BuiltInTransformers() {}
    public static List<ValueTransformer<?, ?>> defaults() {
        return List.of(new NormalizeString(), new IntegerTransformer(), new LongTransformer(),
                new DecimalTransformer(), new DateTransformer(), new BooleanTransformer());
    }

    private abstract static class StringTransformer<T> implements ValueTransformer<String, T> {
        @Override public String version() { return "1"; }
        @Override public Class<String> sourceType() { return String.class; }
        protected TransformationResult<T> failure(String raw, String code, String message) {
            return TransformationResult.failure(raw, id(), version(), code, message, code.startsWith("AMBIGUOUS"));
        }
    }

    private static final class NormalizeString extends StringTransformer<String> {
        public String id() { return "core:string-normalize"; }
        public Class<String> targetType() { return String.class; }
        public TransformationResult<String> transform(String raw, TransformationContext context) {
            String value = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
            String mode = context.options().getOrDefault("case", "preserve");
            value = switch (mode) {
                case "lower" -> value.toLowerCase(Locale.ROOT);
                case "upper" -> value.toUpperCase(Locale.ROOT);
                case "preserve" -> value;
                default -> null;
            };
            if (value == null) return failure(raw, "INVALID_TRANSFORMER_OPTION", "case must be preserve, lower or upper");
            return value.equals(raw) ? TransformationResult.success(raw, value, id(), version())
                    : TransformationResult.warning(raw, value, id(), version(), "STRING_NORMALIZED",
                            "whitespace or case normalization changed the representation", true, false);
        }
    }

    private static final class IntegerTransformer extends StringTransformer<Integer> {
        public String id() { return "core:integer"; }
        public Class<Integer> targetType() { return Integer.class; }
        public TransformationResult<Integer> transform(String raw, TransformationContext context) {
            String value = cleanInteger(raw);
            if (leadingZeros(value) && !Boolean.parseBoolean(context.options().getOrDefault("allowLeadingZeros", "false")))
                return failure(raw, "IDENTIFIER_LIKE_NUMBER", "leading-zero value requires explicit conversion permission");
            try { return TransformationResult.success(raw, new BigInteger(value).intValueExact(), id(), version()); }
            catch (RuntimeException e) { return failure(raw, "INVALID_INTEGER", "value is not an in-range integer"); }
        }
    }

    private static final class LongTransformer extends StringTransformer<Long> {
        public String id() { return "core:long"; }
        public Class<Long> targetType() { return Long.class; }
        public TransformationResult<Long> transform(String raw, TransformationContext context) {
            String value = cleanInteger(raw);
            if (leadingZeros(value) && !Boolean.parseBoolean(context.options().getOrDefault("allowLeadingZeros", "false")))
                return failure(raw, "IDENTIFIER_LIKE_NUMBER", "leading-zero value requires explicit conversion permission");
            try { return TransformationResult.success(raw, new BigInteger(value).longValueExact(), id(), version()); }
            catch (RuntimeException e) { return failure(raw, "INVALID_LONG", "value is not an in-range long"); }
        }
    }

    private static final class DecimalTransformer extends StringTransformer<BigDecimal> {
        public String id() { return "core:big-decimal"; }
        public Class<BigDecimal> targetType() { return BigDecimal.class; }
        public TransformationResult<BigDecimal> transform(String raw, TransformationContext context) {
            String value = raw == null ? "" : raw.strip().replace(" ", "");
            String locale = context.options().getOrDefault("locale", context.schemaLocale());
            boolean comma = value.contains(","), dot = value.contains(".");
            if ((comma || dot) && locale.isBlank())
                return failure(raw, "AMBIGUOUS_DECIMAL", "decimal separators require an explicit locale");
            if (locale.equalsIgnoreCase("pt-BR")) {
                if (!value.matches("[+-]?(?:\\d+|\\d+,\\d+|\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?)"))
                    return failure(raw, "INVALID_DECIMAL", "value is not a valid decimal for locale pt-BR");
                value = value.replace(".", "").replace(',', '.');
            } else if (locale.equalsIgnoreCase("en-US")) {
                if (!value.matches("[+-]?(?:\\d+|\\d+\\.\\d+|\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?)"))
                    return failure(raw, "INVALID_DECIMAL", "value is not a valid decimal for locale en-US");
                value = value.replace(",", "");
            }
            else if (!locale.isBlank()) return failure(raw, "UNSUPPORTED_LOCALE", "decimal locale must be pt-BR or en-US");
            try { return TransformationResult.success(raw, new BigDecimal(value), id(), version()); }
            catch (NumberFormatException e) { return failure(raw, "INVALID_DECIMAL", "value is not a valid decimal for the configured locale"); }
        }
    }

    private static final class DateTransformer extends StringTransformer<LocalDate> {
        public String id() { return "core:local-date"; }
        public Class<LocalDate> targetType() { return LocalDate.class; }
        public TransformationResult<LocalDate> transform(String raw, TransformationContext context) {
            String value = raw == null ? "" : raw.strip();
            String configured = context.options().getOrDefault("formats", "");
            if (configured.isBlank()) {
                try { return TransformationResult.success(raw, LocalDate.parse(value), id(), version()); }
                catch (DateTimeParseException ignored) {
                    if (value.matches("\\d{2}/\\d{2}/\\d{4}"))
                        return failure(raw, "AMBIGUOUS_DATE", "slash date requires explicit ordered formats");
                    return failure(raw, "INVALID_DATE", "date does not match ISO yyyy-MM-dd");
                }
            }
            var matches = new ArrayList<LocalDate>();
            for (String pattern : configured.split("\\|")) {
                try {
                    var formatter = DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
                    LocalDate parsed = LocalDate.parse(value, formatter);
                    if (!matches.contains(parsed)) matches.add(parsed);
                } catch (IllegalArgumentException | DateTimeParseException ignored) { }
            }
            if (matches.isEmpty()) return failure(raw, "INVALID_DATE", "date matches none of the configured formats");
            if (matches.size() > 1) return failure(raw, "AMBIGUOUS_DATE", "date has multiple valid interpretations");
            return TransformationResult.success(raw, matches.getFirst(), id(), version());
        }
    }

    private static final class BooleanTransformer extends StringTransformer<Boolean> {
        public String id() { return "core:boolean"; }
        public Class<Boolean> targetType() { return Boolean.class; }
        public TransformationResult<Boolean> transform(String raw, TransformationContext context) {
            String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
            if (value.equals("true")) return TransformationResult.success(raw, true, id(), version());
            if (value.equals("false")) return TransformationResult.success(raw, false, id(), version());
            String locale = context.options().getOrDefault("locale", context.schemaLocale());
            if (locale.equalsIgnoreCase("pt-BR") && value.equals("sim"))
                return TransformationResult.success(raw, true, id(), version());
            if (locale.equalsIgnoreCase("pt-BR") && (value.equals("não") || value.equals("nao")))
                return TransformationResult.success(raw, false, id(), version());
            if (locale.equalsIgnoreCase("en-US") && value.equals("yes")) return TransformationResult.success(raw, true, id(), version());
            if (locale.equalsIgnoreCase("en-US") && value.equals("no")) return TransformationResult.success(raw, false, id(), version());
            return failure(raw, "INVALID_BOOLEAN", "value is not a configured boolean literal");
        }
    }

    private static String cleanInteger(String raw) { return raw == null ? "" : raw.strip(); }
    private static boolean leadingZeros(String value) {
        String unsigned = value.startsWith("+") || value.startsWith("-") ? value.substring(1) : value;
        return unsigned.length() > 1 && unsigned.startsWith("0");
    }
}
