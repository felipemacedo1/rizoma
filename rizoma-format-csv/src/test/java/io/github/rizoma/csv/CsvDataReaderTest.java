package io.github.rizoma.csv;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.EngineException;
import io.github.rizoma.core.EngineLimits;
import io.github.rizoma.core.PathTabularSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvDataReaderTest {
    @TempDir Path temporary;
    private final CsvDataReader reader = new CsvDataReader();

    @Test void parsesBomQuotesEscapesDelimiterAndMultilineWithoutConsumingProfileRows() throws Exception {
        Path path = temporary.resolve("quoted.csv");
        Files.writeString(path, "\ufeffNome;Nota\n\"Ana; Maria\";\"linha 1\nlinha \"\"2\"\"\"\n", StandardCharsets.UTF_8);
        var source = new PathTabularSource(path);
        var options = new AnalysisOptions(Map.of("header", "first"), 42);
        var structure = reader.detect(source, options, EngineLimits.defaults());
        assertEquals(";", structure.delimiter());
        assertEquals(2, structure.columns().size());
        try (var dataset = reader.open(source, structure, options, EngineLimits.defaults())) {
            var rows = dataset.rows();
            assertTrue(rows.hasNext());
            var row = rows.next();
            assertEquals("Ana; Maria", row.values().get(0));
            assertEquals("linha 1\nlinha \"2\"", row.values().get(1));
            assertTrue(row.physicalLine() >= 3);
            assertFalse(rows.hasNext());
            assertThrows(IllegalStateException.class, dataset::rows);
        }
    }

    @Test void duplicateAndEmptyHeadersHaveStablePositionIdentityAndWarnings() throws Exception {
        Path path = temporary.resolve("duplicates.csv");
        Files.writeString(path, "Nome,Nome,\nAna,Bia,x\n");
        var options = new AnalysisOptions(Map.of("header", "first", "delimiter", ","), 1);
        var structure = reader.detect(new PathTabularSource(path), options, EngineLimits.defaults());
        assertEquals("c0", structure.columns().get(0).id());
        assertEquals("c1", structure.columns().get(1).id());
        assertTrue(structure.warnings().stream().anyMatch(value -> value.contains("duplicate")));
        assertTrue(structure.warnings().stream().anyMatch(value -> value.contains("empty header")));
    }

    @Test void explicitNoHeaderUsesSyntheticNamesAndEmptyFileIsRepresented() throws Exception {
        Path noHeader = temporary.resolve("no-header.csv");
        Files.writeString(noHeader, "001,Ana\n002,Bia\n");
        var options = new AnalysisOptions(Map.of("header", "none", "delimiter", ","), 1);
        var source = new PathTabularSource(noHeader);
        var structure = reader.detect(source, options, EngineLimits.defaults());
        assertEquals("column_1", structure.columns().getFirst().header());
        try (var dataset = reader.open(source, structure, options, EngineLimits.defaults())) {
            assertEquals("001", dataset.rows().next().values().getFirst());
            dataset.close();
            assertThrows(IllegalStateException.class, dataset::rows);
        }

        Path empty = temporary.resolve("empty.csv");
        Files.writeString(empty, "");
        var emptyStructure = reader.detect(new PathTabularSource(empty), AnalysisOptions.defaults(), EngineLimits.defaults());
        assertTrue(emptyStructure.columns().isEmpty());
        assertTrue(emptyStructure.warnings().contains("empty CSV source"));
    }

    @Test void irregularRowsAndLimitsFailWithSafeMessages() throws Exception {
        Path irregular = temporary.resolve("irregular.csv");
        Files.writeString(irregular, "A,B\n1\n");
        var options = new AnalysisOptions(Map.of("header", "first", "delimiter", ","), 1);
        var source = new PathTabularSource(irregular);
        var structure = reader.detect(source, options, EngineLimits.defaults());
        try (var dataset = reader.open(source, structure, options, EngineLimits.defaults())) {
            var error = assertThrows(EngineException.class, () -> dataset.rows().next());
            assertEquals("IRREGULAR_ROW", error.code());
            assertFalse(error.getMessage().contains("1"));
        }

        var tiny = new EngineLimits(5, 10, 10, 10, 10, 1, 1, 1, 1, Duration.ofSeconds(1));
        assertThrows(EngineException.class, () -> reader.detect(source, options, tiny));
        assertThrows(EngineException.class, () -> reader.detect(source,
                new AnalysisOptions(Map.of("charset", "UTF-16"), 1), EngineLimits.defaults()));
        assertThrows(EngineException.class, () -> reader.detect(source,
                new AnalysisOptions(Map.of("delimiter", ":"), 1), EngineLimits.defaults()));
    }

    @Test void ambiguousDelimiterRequiresOverride() throws Exception {
        Path path = temporary.resolve("single.csv");
        Files.writeString(path, "only-one-field\n");
        var error = assertThrows(EngineException.class,
                () -> reader.detect(new PathTabularSource(path), AnalysisOptions.defaults(), EngineLimits.defaults()));
        assertEquals("CSV_DELIMITER_AMBIGUOUS", error.code());
    }

    @Test void headerOnlyAndLegacyEncodingAreHandledExplicitly() throws Exception {
        Path headerOnly = temporary.resolve("header-only.csv");
        Files.writeString(headerOnly, "Nome;E-mail\n");
        var detected = reader.detect(new PathTabularSource(headerOnly), AnalysisOptions.defaults(), EngineLimits.defaults());
        assertTrue(detected.headerPresent());
        assertTrue(detected.warnings().stream().anyMatch(value -> value.contains("ambiguous")));
        try (var dataset = reader.open(new PathTabularSource(headerOnly), detected, AnalysisOptions.defaults(), EngineLimits.defaults())) {
            assertFalse(dataset.rows().hasNext());
        }

        Path latin = temporary.resolve("latin.csv");
        Files.write(latin, "Nome;Cidade\nAndré;São Paulo\n".getBytes(StandardCharsets.ISO_8859_1));
        var options = new AnalysisOptions(Map.of("charset", "ISO-8859-1", "delimiter", ";", "header", "first"), 1);
        var structure = reader.detect(new PathTabularSource(latin), options, EngineLimits.defaults());
        try (var dataset = reader.open(new PathTabularSource(latin), structure, options, EngineLimits.defaults())) {
            assertEquals("André", dataset.rows().next().values().getFirst());
        }
    }
}
