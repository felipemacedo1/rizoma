package io.github.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionPipelineTest {
    private static final TargetField TEXT = new TargetField(
            "target.text", "Text", List.of(), PhysicalType.TEXT, Set.of(), false);

    @Test void transformationsAreDeterministicRetainOriginalAndReportLoss() {
        var pipeline = new TransformationPipeline(BuiltInTransformers.defaults());
        var steps = List.of(new MappingPlan.Step("core:string-normalize", "1", Map.of("case", "lower")));

        var first = pipeline.execute("  SYNTHETIC   VALUE ", steps, TEXT, "pt-BR");
        var second = pipeline.execute("  SYNTHETIC   VALUE ", steps, TEXT, "pt-BR");

        assertEquals(first, second);
        assertEquals("synthetic value", first.value());
        assertEquals(ValueTransformer.Status.WARNING, first.status());
        var evidence = first.steps().getFirst();
        assertEquals("  SYNTHETIC   VALUE ", evidence.originalValue());
        assertTrue(evidence.lossy());
        assertEquals("STRING_NORMALIZED", evidence.code());
    }

    @Test void numericConversionRequiresTargetIntentAndProtectsLeadingZeros() {
        var pipeline = new TransformationPipeline(BuiltInTransformers.defaults());
        var numeric = new TargetField("quantity", "Quantity", List.of(), PhysicalType.INTEGER, Set.of(), false);

        var refused = pipeline.execute("00123", List.of(new MappingPlan.Step("core:long")), numeric, "");
        assertEquals(ValueTransformer.Status.FAILURE, refused.status());
        assertEquals("IDENTIFIER_LIKE_NUMBER", refused.steps().getFirst().code());
        assertEquals("00123", refused.steps().getFirst().originalValue());

        var explicit = pipeline.execute("00123", List.of(new MappingPlan.Step(
                "core:long", "1", Map.of("allowLeadingZeros", "true"))), numeric, "");
        assertEquals(123L, explicit.value());
        assertEquals(ValueTransformer.Status.SUCCESS, explicit.status());
    }

    @Test void decimalLocaleAndDateAmbiguityAreExplicit() {
        var pipeline = new TransformationPipeline(BuiltInTransformers.defaults());
        var decimal = new TargetField("amount", "Amount", List.of(), PhysicalType.DECIMAL, Set.of(), false);
        var date = new TargetField("date", "Date", List.of(), PhysicalType.DATE, Set.of(), false);

        assertEquals(new BigDecimal("1234.56"), pipeline.execute("1.234,56",
                List.of(new MappingPlan.Step("core:big-decimal", "1", Map.of("locale", "pt-BR"))),
                decimal, "").value());
        assertEquals(new BigDecimal("1234.56"), pipeline.execute("1,234.56",
                List.of(new MappingPlan.Step("core:big-decimal", "1", Map.of("locale", "en-US"))),
                decimal, "").value());
        assertEquals("AMBIGUOUS_DECIMAL", pipeline.execute("1,234",
                List.of(new MappingPlan.Step("core:big-decimal")), decimal, "")
                .steps().getFirst().code());
        assertEquals("INVALID_DECIMAL", pipeline.execute("12.34",
                List.of(new MappingPlan.Step("core:big-decimal", "1", Map.of("locale", "pt-BR"))),
                decimal, "").steps().getFirst().code());

        var ambiguous = pipeline.execute("01/02/2026", List.of(new MappingPlan.Step(
                "core:local-date", "1", Map.of("formats", "dd/MM/uuuu|MM/dd/uuuu"))), date, "");
        assertEquals(ValueTransformer.Status.FAILURE, ambiguous.status());
        assertEquals("AMBIGUOUS_DATE", ambiguous.steps().getFirst().code());
        assertTrue(ambiguous.steps().getFirst().ambiguous());
        assertEquals(LocalDate.of(2026, 2, 1), pipeline.execute("01/02/2026",
                List.of(new MappingPlan.Step("core:local-date", "1", Map.of("formats", "dd/MM/uuuu"))),
                date, "pt-BR").value());
    }

    @Test void booleansUseExplicitLocaleAndValidatorsReturnStableDataErrors() {
        var pipeline = new TransformationPipeline(BuiltInTransformers.defaults());
        var bool = new TargetField("active", "Active", List.of(), PhysicalType.BOOLEAN, Set.of(), false);
        assertEquals(true, pipeline.execute("sim", List.of(new MappingPlan.Step("core:boolean")), bool, "pt-BR").value());
        assertEquals(false, pipeline.execute("no", List.of(new MappingPlan.Step("core:boolean")), bool, "en-US").value());
        assertEquals("INVALID_BOOLEAN", pipeline.execute("sim", List.of(new MappingPlan.Step("core:boolean")), bool, "en-US")
                .steps().getFirst().code());

        assertEquals(Validator.Status.FAILURE, validate("core:required", null, Map.of()).status());
        assertEquals(Validator.Status.FAILURE, validate("core:regex", "abc", Map.of("pattern", "\\d+" )).status());
        assertEquals(Validator.Status.FAILURE, validate("core:length", "abcd", Map.of("max", "3")).status());
        assertEquals(Validator.Status.FAILURE, validate("core:enum", "other", Map.of("values", "new|done")).status());
        assertEquals(Validator.Status.FAILURE, validate("core:numeric-range", new BigDecimal("11"),
                Map.of("max", "10")).status());
        assertEquals(Validator.Status.FAILURE, validate("core:date-range", LocalDate.of(2026, 1, 1),
                Map.of("min", "2026-02-01")).status());
    }

    @Test void transformerSuccessAndConfigurationFailureBranchesAreStable() {
        var pipeline = new TransformationPipeline(BuiltInTransformers.defaults());
        var integer = new TargetField("integer", "Integer", List.of(), PhysicalType.INTEGER, Set.of(), false);
        var decimal = new TargetField("decimal", "Decimal", List.of(), PhysicalType.DECIMAL, Set.of(), false);
        var date = new TargetField("date", "Date", List.of(), PhysicalType.DATE, Set.of(), false);
        var bool = new TargetField("bool", "Bool", List.of(), PhysicalType.BOOLEAN, Set.of(), false);

        assertEquals("same", pipeline.execute("same", List.of(new MappingPlan.Step("core:string-normalize")), TEXT, "").value());
        assertEquals("SAME", pipeline.execute("same", List.of(new MappingPlan.Step(
                "core:string-normalize", "1", Map.of("case", "upper"))), TEXT, "").value());
        assertEquals("INVALID_TRANSFORMER_OPTION", pipeline.execute("same", List.of(new MappingPlan.Step(
                "core:string-normalize", "1", Map.of("case", "title"))), TEXT, "").steps().getFirst().code());
        assertEquals(12, pipeline.execute("12", List.of(new MappingPlan.Step("core:integer")), integer, "").value());
        assertEquals("INVALID_INTEGER", pipeline.execute("999999999999", List.of(new MappingPlan.Step("core:integer")), integer, "")
                .steps().getFirst().code());
        assertEquals(-12L, pipeline.execute("-12", List.of(new MappingPlan.Step("core:long")), integer, "").value());
        assertEquals("INVALID_LONG", pipeline.execute("999999999999999999999999", List.of(new MappingPlan.Step("core:long")), integer, "")
                .steps().getFirst().code());
        assertEquals(new BigDecimal("12"), pipeline.execute("12", List.of(new MappingPlan.Step("core:big-decimal")), decimal, "").value());
        assertEquals("UNSUPPORTED_LOCALE", pipeline.execute("1.2", List.of(new MappingPlan.Step("core:big-decimal")), decimal, "fr-FR")
                .steps().getFirst().code());
        assertEquals("INVALID_DECIMAL", pipeline.execute("one", List.of(new MappingPlan.Step("core:big-decimal")), decimal, "")
                .steps().getFirst().code());
        assertEquals(LocalDate.of(2026, 1, 2), pipeline.execute("2026-01-02",
                List.of(new MappingPlan.Step("core:local-date")), date, "").value());
        assertEquals("INVALID_DATE", pipeline.execute("not-date", List.of(new MappingPlan.Step("core:local-date")), date, "")
                .steps().getFirst().code());
        assertEquals("INVALID_DATE", pipeline.execute("not-date", List.of(new MappingPlan.Step(
                "core:local-date", "1", Map.of("formats", "["))), date, "")
                .steps().getFirst().code());
        assertEquals(true, pipeline.execute("true", List.of(new MappingPlan.Step("core:boolean")), bool, "").value());
        assertEquals(false, pipeline.execute("false", List.of(new MappingPlan.Step("core:boolean")), bool, "").value());
        assertEquals(false, pipeline.execute("não", List.of(new MappingPlan.Step("core:boolean")), bool, "pt-BR").value());
        assertEquals(true, pipeline.execute("yes", List.of(new MappingPlan.Step("core:boolean")), bool, "en-US").value());
    }

    @Test void validatorPassAndInvalidOptionBranchesAreExplicit() {
        assertEquals(Validator.Status.PASS, validate("core:required", "x", Map.of()).status());
        assertEquals(Validator.Status.PASS, validate("core:regex", "123", Map.of("pattern", "\\d+")).status());
        assertEquals("INVALID_VALIDATOR_OPTION", validate("core:regex", "123", Map.of()).code());
        assertEquals("INVALID_VALIDATOR_OPTION", validate("core:regex", "123", Map.of("pattern", "[")).code());
        assertEquals(Validator.Status.PASS, validate("core:length", "abc", Map.of("min", "2", "max", "4")).status());
        assertEquals("INVALID_VALIDATOR_OPTION", validate("core:length", "abc", Map.of("min", "x")).code());
        assertEquals(Validator.Status.PASS, validate("core:enum", "done", Map.of("values", "new|done")).status());
        assertEquals(Validator.Status.PASS, validate("core:numeric-range", new BigDecimal("5"),
                Map.of("min", "1", "max", "10")).status());
        assertEquals("INVALID_NUMERIC_VALUE", validate("core:numeric-range", "not-number", Map.of()).code());
        assertEquals(Validator.Status.PASS, validate("core:date-range", LocalDate.of(2026, 2, 2),
                Map.of("min", "2026-01-01", "max", "2026-03-01")).status());
        assertEquals("INVALID_DATE_VALUE", validate("core:date-range", "not-date", Map.of()).code());
    }

    @Test void pipelineRejectsUnknownVersionsAndIncompatibleStepTypes() {
        var pipeline = new TransformationPipeline(BuiltInTransformers.defaults());
        assertEquals("UNKNOWN_TRANSFORMER", assertThrows(EngineException.class, () -> pipeline.execute("x",
                List.of(new MappingPlan.Step("missing")), TEXT, "")).code());
        assertEquals("UNKNOWN_TRANSFORMER", assertThrows(EngineException.class, () -> pipeline.execute("x",
                List.of(new MappingPlan.Step("core:long", "2", Map.of())), TEXT, "")).code());
        assertEquals("TRANSFORMER_TYPE_MISMATCH", assertThrows(EngineException.class, () -> pipeline.execute("1",
                List.of(new MappingPlan.Step("core:long"), new MappingPlan.Step("core:string-normalize")), TEXT, "")).code());
        assertThrows(IllegalArgumentException.class, () -> new TransformationPipeline(List.of(
                BuiltInTransformers.defaults().getFirst(), BuiltInTransformers.defaults().getFirst())));
    }

    @SuppressWarnings("unchecked")
    private static Validator.ValidationResult validate(String id, Object value, Map<String, String> options) {
        Validator<Object> validator = (Validator<Object>) BuiltInValidators.defaults().stream()
                .filter(item -> item.id().equals(id)).findFirst().orElseThrow();
        return validator.validate(value, new Validator.ValidationContext(TEXT, "", value == null ? null : value.toString(), options));
    }
}
