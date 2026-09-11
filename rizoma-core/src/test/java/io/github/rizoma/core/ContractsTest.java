package io.github.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContractsTest {
    @Test void targetContractSupportsMultipleSemanticTypesAndRejectsBadIdentity() {
        var field = new TargetField("customer.document", "Documento", List.of("CPF", "CNPJ"),
                PhysicalType.TEXT, Set.of(new SemanticType("br:cpf"), new SemanticType("br:cnpj")), true);
        var schema = new TargetSchema("customer", "1", "customer", "pt-BR", List.of(field));
        assertEquals(2, field.semanticTypes().size());
        assertTrue(field.exclusive());
        assertEquals("customer", schema.id());
        assertThrows(IllegalArgumentException.class, () -> new SemanticType("cpf"));
        assertThrows(IllegalArgumentException.class, () -> new TargetSchema("x", "1", null, null, List.of(field, field)));
        assertThrows(IllegalArgumentException.class, () -> new TargetSchema(" ", "1", "", "", List.of(field)));
        assertThrows(IllegalArgumentException.class, () -> new TargetSchema("x", "1", "", "", List.of()));
    }

    @Test void invalidLimitsAndScoringFailClearly() {
        assertFalse(EngineConfig.defaults().autoMapEnabled());
        assertThrows(IllegalArgumentException.class, () -> new EngineLimits(0, 1, 1, 1, 1, 0, 1, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new EngineLimits(1, 1, 1, 1, 1, 0, 1, 1, 1, Duration.ZERO));
        Map<String, Double> weights = Map.of("lexical", Double.NaN, "semantic", 1d, "physicalType", 1d,
                "pattern", 1d, "distribution", 0d, "history", 0d);
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(), weights,
                1, .9, .7, .5, .1, .5, false));
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(),
                Map.of("lexical", 0d, "semantic", 0d, "physicalType", 0d, "pattern", 0d,
                        "distribution", 0d, "history", 0d), 1, .9, .7, .5, .1, .5, false));
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(),
                EngineConfig.defaults().weights(), 1, .6, .8, .5, .1, .5, false));
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(),
                EngineConfig.defaults().weights(), 0, .9, .7, .5, .1, .5, false));
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(),
                EngineConfig.defaults().weights(), 1, Double.POSITIVE_INFINITY, .7, .5, .1, .5, false));
        var missing = new java.util.HashMap<>(EngineConfig.defaults().weights()); missing.remove("history");
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(), missing,
                1, .9, .7, .5, .1, .5, false));
        var negative = new java.util.HashMap<>(EngineConfig.defaults().weights()); negative.put("history", -1d);
        assertThrows(IllegalArgumentException.class, () -> new EngineConfig("x", EngineLimits.defaults(), negative,
                1, .9, .7, .5, .1, .5, false));
        assertThrows(NullPointerException.class, () -> new EngineConfig(null, EngineLimits.defaults(),
                EngineConfig.defaults().weights(), 1, .9, .7, .5, .1, .5, false));
    }

    @Test void semanticReliabilityIsOperationalAndBounded() {
        assertEquals(0, SemanticDetector.reliability(0, 0, 20));
        assertEquals(.25, SemanticDetector.reliability(10, 5, 20));
        assertEquals(1, SemanticDetector.reliability(100, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new SemanticDetector.SemanticEvidence(
                new SemanticType("x:type"), -1, 0, 0, 0, 0, 0, 0, false, "bad"));
        assertThrows(IllegalArgumentException.class, () -> new SemanticDetector.SemanticEvidence(
                new SemanticType("x:type"), 1, 0, 0, 0, Double.NaN, 0, 0, false, "bad"));
    }

    @Test void coreDetectorsPreserveAmbiguousDatesAndEmailValidity() {
        var dates = CoreSemanticDetectors.date().newAccumulator();
        dates.accept(null); dates.accept(" "); dates.accept("2026-09-11"); dates.accept("01/02/2026");
        dates.accept("31/02/2026"); dates.accept("not-a-date");
        var date = dates.finish(2);
        assertEquals(1, date.ambiguous());
        assertEquals(2, date.validMatches());
        assertTrue(date.explanation().contains("ambiguity"));

        var emails = CoreSemanticDetectors.email().newAccumulator();
        emails.accept(null); emails.accept("a@b"); emails.accept("person@example.org"); emails.accept("invalid");
        var email = emails.finish(2);
        assertEquals(2, email.shapeMatches());
        assertEquals(1, email.validMatches());
        assertFalse(email.strongIdentity());
    }
}
