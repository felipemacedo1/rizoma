package io.github.rizoma.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SimilarityAndNormalizationTest {
    @Test void diceHasKnownValuesAndDegenerateCases() {
        var metric = new DiceSimilarity();
        assertEquals("dice.tokens", metric.id());
        assertEquals(0.5, metric.compare("nome cliente", "nome completo"), 1e-12);
        assertEquals(1, metric.compare("", ""));
        assertEquals(0, metric.compare(null, "nome"));
        assertEquals(1, metric.compare("nome nome", "nome"));
    }

    @Test void normalizedLevenshteinHasKnownValuesAndNeverReturnsNonFinite() {
        var metric = new LevenshteinSimilarity();
        assertEquals("levenshtein.normalized", metric.id());
        assertEquals(4.0 / 7.0, metric.compare("kitten", "sitting"), 1e-12);
        assertEquals(1, metric.compare(null, ""));
        assertEquals(0, metric.compare("abc", ""));
        assertEquals(metric.compare("short", "a long value"), metric.compare("a long value", "short"), 1e-12);
        Random random = new Random(7);
        for (int attempt = 0; attempt < 2_000; attempt++) {
            String left = randomString(random), right = randomString(random);
            double value = metric.compare(left, right);
            assertTrue(Double.isFinite(value));
            assertTrue(value >= 0 && value <= 1);
            assertEquals(value, metric.compare(right, left), 1e-12);
        }
    }

    @Test void jaccardHasKnownTokenSetValues() {
        var metric = new JaccardSimilarity();
        assertEquals("jaccard.tokens", metric.id());
        assertEquals(1.0 / 3.0, metric.compare("data nasc", "data nascimento"), 1e-12);
        assertEquals(1, metric.compare("", ""));
        assertEquals(0, metric.compare("data", null));
        assertEquals(1, metric.compare("data data", "data"));
    }

    @Test void jaroAndJaroWinklerMatchPublishedExamples() {
        var jaro = new JaroSimilarity();
        var winkler = new JaroWinklerSimilarity();
        assertEquals(0.9444444444444445, jaro.compare("MARTHA", "MARHTA"), 1e-12);
        assertEquals(0.9611111111111111, winkler.compare("MARTHA", "MARHTA"), 1e-12);
        assertEquals(1, jaro.compare(null, ""));
        assertEquals(0, winkler.compare("a", ""));
    }

    @Test void nGramAndCosineUseCharacterTrigramRepresentations() {
        var ngram = new NGramSimilarity(3);
        var cosine = new CosineSimilarity(3);
        assertEquals(1, ngram.compare("nome", "nome"));
        assertEquals(0, ngram.compare("abc", "xyz"));
        assertEquals(1, cosine.compare("banana", "banana"), 1e-12);
        assertEquals(0, cosine.compare("abc", "xyz"), 1e-12);
        assertEquals(1, cosine.compare("", null), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new NGramSimilarity(0));
        assertThrows(IllegalArgumentException.class, () -> new CosineSimilarity(0));
    }

    @Test void allSimilarityMetricsAreSymmetricFiniteAndBounded() {
        List<SimilarityMetric> metrics = List.of(new DiceSimilarity(), new LevenshteinSimilarity(),
                new JaccardSimilarity(), new JaroSimilarity(), new JaroWinklerSimilarity(),
                new NGramSimilarity(3), new CosineSimilarity(3));
        Random random = new Random(17);
        for (int attempt = 0; attempt < 2_000; attempt++) {
            String left = randomString(random), right = randomString(random);
            for (SimilarityMetric metric : metrics) {
                double value = metric.compare(left, right);
                assertTrue(Double.isFinite(value), metric.id());
                assertTrue(value >= 0 && value <= 1, metric.id());
                assertEquals(value, metric.compare(right, left), 1e-12, metric.id());
            }
        }
    }

    @Test void headerNormalizationIsNonDestructiveIdempotentAndHandlesVariants() {
        var normalizer = new HeaderNormalizer(Map.of("dt", List.of("data"), "nasc", List.of("nascimento")));
        for (String value : List.of("Data Nasc.", "DATA_NASCIMENTO", "dtNascimento", "data-nascimento")) {
            var first = normalizer.normalize(value);
            var second = normalizer.normalize(first.comparable());
            assertEquals("data nascimento", first.comparable());
            assertEquals(first.comparable(), second.comparable());
            assertEquals(value, first.original());
        }
        assertEquals("codigo 2 fa", normalizer.normalize("Código2FA").comparable());
        assertEquals("", normalizer.normalize(null).comparable());
    }

    private static String randomString(Random random) {
        int length = random.nextInt(24);
        var value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append((char) ('a' + random.nextInt(6)));
        return value.toString();
    }
}
