package io.github.rizoma.ptbr;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.SemanticDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class PtBrDetectorsTest {
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
