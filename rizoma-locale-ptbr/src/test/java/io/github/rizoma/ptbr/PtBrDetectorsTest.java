package io.github.rizoma.ptbr;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.SemanticDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

import io.github.rizoma.core.MappingPlan;
import io.github.rizoma.core.PhysicalType;
import io.github.rizoma.core.SemanticType;
import io.github.rizoma.core.TargetField;
import io.github.rizoma.core.TransformationPipeline;
import io.github.rizoma.core.Validator;
import io.github.rizoma.core.ValueTransformer;
import java.util.Map;
import java.util.Set;

class PtBrDetectorsTest {

    @Test void executionRulesCanonicalizeKnownRepresentationsWithoutDiscardingLetters() {
        var target = new TargetField("document", "CPF", List.of(), PhysicalType.TEXT,
                Set.of(new SemanticType("br:cpf")), false);
        var pipeline = new TransformationPipeline(PtBrExecutionRules.transformers());
        var canonical = pipeline.execute("529.982.247-25", List.of(new MappingPlan.Step("br:cpf-canonical")),
                target, "pt-BR");
        assertEquals("52998224725", canonical.value());
        assertEquals("529.982.247-25", canonical.steps().getFirst().originalValue());
        assertEquals(ValueTransformer.Status.SUCCESS, canonical.status());

        var corrupted = pipeline.execute("CPF 529.982.247-25",
                List.of(new MappingPlan.Step("br:cpf-canonical")), target, "pt-BR");
        assertEquals(ValueTransformer.Status.FAILURE, corrupted.status());
        assertEquals("INVALID_CPF_FORMAT", corrupted.steps().getFirst().code());
    }

    @Test void cpfChecksumValidationAndPhoneCepCanonicalizationAreExplicit() {
        Validator<?> cpf = PtBrExecutionRules.validators().getFirst();
        @SuppressWarnings("unchecked") Validator<Object> typed = (Validator<Object>) cpf;
        var context = new Validator.ValidationContext(new TargetField("document", "CPF", List.of(),
                PhysicalType.TEXT, Set.of(new SemanticType("br:cpf")), false), "pt-BR", "masked", Map.of());
        assertEquals(Validator.Status.PASS, typed.validate("52998224725", context).status());
        assertEquals("INVALID_CPF_CHECKSUM", typed.validate("00000000000", context).code());

        var pipeline = new TransformationPipeline(PtBrExecutionRules.transformers());
        var text = new TargetField("text", "Text", List.of(), PhysicalType.TEXT, Set.of(), false);
        assertEquals("11999999999", pipeline.execute("(11) 99999-9999",
                List.of(new MappingPlan.Step("br:phone-canonical")), text, "pt-BR").value());
        assertEquals("01001000", pipeline.execute("01001-000",
                List.of(new MappingPlan.Step("br:cep-canonical")), text, "pt-BR").value());
    }
    @Test void cpfSeparatesShapeFromChecksumAndCanBecomeStrongEvidence() {
        SemanticDetector.Accumulator accumulator = new CpfDetector().newAccumulator();
        for (int i = 0; i < 20; i++) accumulator.accept(i % 2 == 0 ? "529.982.247-25" : "11144477735");
        var evidence = accumulator.finish(20);
        assertEquals(20, evidence.shapeMatches());
        assertEquals(20, evidence.validMatches());
        assertTrue(evidence.strongIdentity());
        assertEquals(1, evidence.reliability());

        var invalid = new CpfDetector().newAccumulator();
        invalid.accept("111.111.111-11");
        invalid.accept("ABC123");
        var invalidEvidence = invalid.finish(20);
        assertEquals(1, invalidEvidence.shapeMatches());
        assertEquals(0, invalidEvidence.validMatches());
        assertFalse(invalidEvidence.strongIdentity());
    }

    @Test void bareElevenDigitsAreAmbiguousForPhone() {
        var bare = new BrazilianPhoneDetector().newAccumulator();
        for (int i = 0; i < 20; i++) bare.accept("11999999999");
        var ambiguous = bare.finish(20);
        assertEquals(1, ambiguous.validityScore());
        assertEquals(0, ambiguous.reliability());
        assertEquals(20, ambiguous.ambiguous());

        var formatted = new BrazilianPhoneDetector().newAccumulator();
        for (int i = 0; i < 20; i++) formatted.accept("(11) 99999-9999");
        assertEquals(1, formatted.finish(20).reliability());
    }

    @Test void packExposesOnlyImplementedRulesAndDetectors() {
        assertEquals("pt-BR-0.1a", PtBrHeaderRules.version());
        assertEquals("data nascimento", PtBrHeaderRules.normalizer().normalize("dtNasc").comparable());
        assertEquals(List.of("br:cpf", "br:phone"), PtBrDetectors.defaults().stream().map(d -> d.type().id()).toList());
    }
}
