package io.github.rizoma.ptbr;

import io.github.rizoma.core.TargetField;
import io.github.rizoma.core.Validator;
import io.github.rizoma.core.ValueTransformer;
import java.util.List;

/** Brazilian canonicalizers and validators used explicitly by mapping plans. */
public final class PtBrExecutionRules {
    private PtBrExecutionRules() {}
    public static List<ValueTransformer<?, ?>> transformers() {
        return List.of(new Digits("br:cpf-canonical", 11), new Phone(), new Digits("br:cep-canonical", 8));
    }
    public static List<Validator<?>> validators() { return List.of(new CpfChecksum()); }

    private static final class Digits implements ValueTransformer<String, String> {
        private final String id; private final int length;
        private Digits(String id, int length) { this.id = id; this.length = length; }
        public String id() { return id; } public String version() { return "1"; }
        public Class<String> sourceType() { return String.class; }
        public Class<String> targetType() { return String.class; }
        public TransformationResult<String> transform(String raw, TransformationContext context) {
            String original = raw == null ? "" : raw;
            String allowed = id.equals("br:cep-canonical") ? "[0-9\\-\\s]*" : "[0-9.\\-\\s]*";
            if (!original.matches(allowed)) return TransformationResult.failure(original, id(), version(),
                    id.equals("br:cep-canonical") ? "INVALID_CEP_FORMAT" : "INVALID_CPF_FORMAT",
                    "value contains characters outside the accepted representation", false);
            String digits = original.replaceAll("[^0-9]", "");
            if (digits.length() != length) return TransformationResult.failure(original, id(), version(),
                    id.equals("br:cep-canonical") ? "INVALID_CEP_FORMAT" : "INVALID_CPF_FORMAT",
                    "value does not have the required canonical digit count", false);
            return TransformationResult.success(original, digits, id(), version());
        }
    }

    private static final class Phone implements ValueTransformer<String, String> {
        public String id() { return "br:phone-canonical"; } public String version() { return "1"; }
        public Class<String> sourceType() { return String.class; }
        public Class<String> targetType() { return String.class; }
        public TransformationResult<String> transform(String raw, TransformationContext context) {
            String original = raw == null ? "" : raw;
            if (!original.matches("[0-9+()\\-\\s]*")) return TransformationResult.failure(original,
                    id(), version(), "INVALID_PHONE_FORMAT",
                    "value contains characters outside the accepted phone representation", false);
            String digits = original.replaceAll("[^0-9]", "");
            if (digits.length() == 12 || digits.length() == 13) {
                if (!digits.startsWith("55")) return TransformationResult.failure(original, id(), version(),
                        "INVALID_PHONE_FORMAT", "international phone must use country code 55", false);
                digits = digits.substring(2);
            }
            if (digits.length() < 10 || digits.length() > 11) return TransformationResult.failure(original,
                    id(), version(), "INVALID_PHONE_FORMAT", "Brazilian phone must have 10 or 11 national digits", false);
            return TransformationResult.success(original, digits, id(), version());
        }
    }

    private static final class CpfChecksum implements Validator<Object> {
        public String id() { return "br:cpf-checksum"; } public String version() { return "1"; }
        public Class<Object> valueType() { return Object.class; }
        public ValidationResult validate(Object value, ValidationContext context) {
            String digits = value == null ? "" : value.toString();
            return CpfDetector.checksumValid(digits)
                    ? ValidationResult.pass(id(), version())
                    : ValidationResult.failure(id(), version(), "INVALID_CPF_CHECKSUM", "CPF modulus-11 checksum is invalid");
        }
    }
}
