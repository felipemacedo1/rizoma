package io.github.rizoma.excel;

import static org.junit.jupiter.api.Assertions.*;

import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.AnalysisRequest;
import io.github.rizoma.core.EngineException;
import io.github.rizoma.core.EngineLimits;
import io.github.rizoma.core.MappingEngine;
import io.github.rizoma.core.PathTabularSource;
import io.github.rizoma.core.PhysicalType;
import io.github.rizoma.core.TargetField;
import io.github.rizoma.core.TargetSchema;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelDataReaderTest {
    static { java.util.logging.Logger.getLogger("org.apache.poi").setLevel(java.util.logging.Level.SEVERE); }
    @TempDir Path temporary;
    private final ExcelDataReader reader = new ExcelDataReader();

    @Test void xlsxStreamsGapsDatesFormattedIdentifiersAndCachedFormulas() throws Exception {
        Path path = temporary.resolve("source-with-wrong-extension.bin");
        writeWorkbook(new XSSFWorkbook(), path);
        var source = new PathTabularSource(path);
        var options = options("header", "first", "formula", "cached");

        assertTrue(reader.supports(source));
        var structure = reader.detect(source, options, EngineLimits.defaults());
        assertEquals("XLSX", structure.format());
        assertEquals("0", structure.attributes().get("sheetIndex"));
        assertEquals("2", structure.attributes().get("headerRow"));
        assertEquals(5, structure.columns().size());
        assertEquals("c2", structure.columns().get(2).id());

        try (var dataset = reader.open(source, structure, options, EngineLimits.defaults())) {
            var rows = dataset.rows();
            assertTrue(rows.hasNext());
            var row = rows.next();
            assertEquals(3, row.physicalLine());
            assertEquals(List.of("Ana", "", "007", "2024-01-02", "2"), row.values());
            assertFalse(rows.hasNext());
            assertThrows(IllegalStateException.class, dataset::rows);
        }
    }

    @Test void formulaExpressionIsExplicitAndRejectPolicyNeverEvaluates() throws Exception {
        Path path = temporary.resolve("formula.xlsx");
        writeWorkbook(new XSSFWorkbook(), path);
        var source = new PathTabularSource(path);
        var expression = options("header", "first", "formula", "expression");
        var structure = reader.detect(source, expression, EngineLimits.defaults());
        try (var dataset = reader.open(source, structure, expression, EngineLimits.defaults())) {
            assertEquals("=1+1", dataset.rows().next().values().get(4));
        }
        var rejected = assertThrows(EngineException.class,
                () -> reader.detect(source, options("header", "first", "formula", "reject"), EngineLimits.defaults()));
        assertEquals("EXCEL_FORMULA_REJECTED", rejected.code());
    }

    @Test void xlsUsesSameContractAndPreservesCachedValues() throws Exception {
        Path path = temporary.resolve("legacy.xls");
        writeWorkbook(new HSSFWorkbook(), path);
        var source = new PathTabularSource(path);
        var options = options("header", "first");
        var structure = reader.detect(source, options, EngineLimits.defaults());
        assertEquals("XLS", structure.format());
        assertTrue(structure.warnings().stream().anyMatch(value -> value.contains("bounded in-memory")));
        try (var dataset = reader.open(source, structure, options, EngineLimits.defaults())) {
            assertEquals(List.of("Ana", "", "007", "2024-01-02", "2"), dataset.rows().next().values());
        }
    }

    @Test void multipleSheetsRequireSelectionAndSheetNameTakesPrecedenceOverIndexSyntax() throws Exception {
        Path path = temporary.resolve("multiple.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Ignored").createRow(0).createCell(0).setCellValue("X");
            var selected = workbook.createSheet("0");
            selected.createRow(0).createCell(0).setCellValue("Nome");
            selected.createRow(1).createCell(0).setCellValue("Ana");
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }
        var source = new PathTabularSource(path);
        var ambiguous = assertThrows(EngineException.class,
                () -> reader.detect(source, AnalysisOptions.defaults(), EngineLimits.defaults()));
        assertEquals("EXCEL_SHEET_AMBIGUOUS", ambiguous.code());

        var selected = options("sheet", "0", "header", "first");
        var structure = reader.detect(source, selected, EngineLimits.defaults());
        assertEquals("1", structure.attributes().get("sheetIndex"));
        try (var dataset = reader.open(source, structure, selected, EngineLimits.defaults())) {
            assertEquals("Ana", dataset.rows().next().values().getFirst());
        }
        assertEquals("0", reader.detect(source, options("sheet", "Ignored", "header", "first"),
                EngineLimits.defaults()).attributes().get("sheetIndex"));
    }

    @Test void zipAndLegacyLimitsFailEarlyWithSafeErrors() throws Exception {
        Path xlsx = temporary.resolve("limited.xlsx");
        writeWorkbook(new XSSFWorkbook(), xlsx);
        var expanded = assertThrows(EngineException.class, () -> reader.detect(new PathTabularSource(xlsx),
                options("maxExpandedBytes", "10"), EngineLimits.defaults()));
        assertEquals("XLSX_EXPANDED_SIZE_LIMIT", expanded.code());
        assertFalse(expanded.getMessage().contains("Ana"));

        Path xls = temporary.resolve("limited.xls");
        writeWorkbook(new HSSFWorkbook(), xls);
        var legacy = assertThrows(EngineException.class, () -> reader.detect(new PathTabularSource(xls),
                options("maxLegacyBytes", "1"), EngineLimits.defaults()));
        assertEquals("XLS_LEGACY_SIZE_LIMIT", legacy.code());

        Path fake = temporary.resolve("fake.xlsx");
        Files.write(fake, new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
        assertThrows(EngineException.class,
                () -> reader.detect(new PathTabularSource(fake), AnalysisOptions.defaults(), EngineLimits.defaults()));
        assertThrows(EngineException.class, () -> reader.detect(new PathTabularSource(xlsx),
                options("charset", "UTF-8"), EngineLimits.defaults()));
        assertEquals("EXCEL_PREAMBLE_LIMIT", assertThrows(EngineException.class,
                () -> reader.detect(new PathTabularSource(xlsx), options("header", "first", "maxPreambleRows", "0"),
                        EngineLimits.defaults())).code());
        assertEquals("INVALID_READER_OPTION", assertThrows(EngineException.class,
                () -> reader.detect(new PathTabularSource(xlsx), options("maxPreambleRows", "-1"),
                        EngineLimits.defaults())).code());
    }

    @Test void unsafeZipPathsAndMacrosAreRejectedBeforePoiParsing() throws Exception {
        Path traversal = temporary.resolve("traversal.xlsx");
        writeZip(traversal, List.of("../escape", "[Content_Types].xml", "xl/workbook.xml"));
        var unsafe = assertThrows(EngineException.class,
                () -> reader.detect(new PathTabularSource(traversal), AnalysisOptions.defaults(), EngineLimits.defaults()));
        assertEquals("XLSX_UNSAFE_ENTRY", unsafe.code());

        Path macro = temporary.resolve("macro.xlsx");
        writeZip(macro, List.of("[Content_Types].xml", "xl/workbook.xml", "xl/vbaProject.bin"));
        var rejected = assertThrows(EngineException.class,
                () -> reader.detect(new PathTabularSource(macro), AnalysisOptions.defaults(), EngineLimits.defaults()));
        assertEquals("EXCEL_MACRO_NOT_SUPPORTED", rejected.code());
    }

    @Test void externalRelationshipsAreRejectedWithoutAnyNetworkConnection() throws Exception {
        Path path = temporary.resolve("external-relationship.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Name");
            sheet.createRow(1).createCell(0).setCellValue("Synthetic");
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }

        try (var listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(250);
            String target = "http://127.0.0.1:" + listener.getLocalPort() + "/external.xlsx";
            try (OPCPackage packageFile = OPCPackage.open(path.toFile())) {
                packageFile.getPart(PackagingURIHelper.createPartName("/xl/workbook.xml"))
                        .addExternalRelationship(target,
                                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink");
            }

            EngineException rejected = assertThrows(EngineException.class,
                    () -> reader.detect(new PathTabularSource(path), AnalysisOptions.defaults(),
                            EngineLimits.defaults()));
            assertEquals("EXCEL_EXTERNAL_RELATIONSHIP", rejected.code());
            assertFalse(rejected.getMessage().contains(target));
            assertThrows(SocketTimeoutException.class, listener::accept,
                    "the reader must reject metadata without connecting to the external target");
        }
    }

    @Test void nonLocalXlsxSpoolIsBoundedClosedAndDeleted() throws Exception {
        Path path = temporary.resolve("bytes.xlsx");
        writeWorkbook(new XSSFWorkbook(), path);
        byte[] data = Files.readAllBytes(path);
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        var source = new io.github.rizoma.core.TabularSource() {
            @Override public String id() { return "memory"; }
            @Override public String fileName() { return "memory.xlsx"; }
            @Override public long size() { return data.length; }
            @Override public InputStream openStream() {
                opened.incrementAndGet();
                return new ByteArrayInputStream(data) {
                    @Override public void close() throws IOException { super.close(); closed.incrementAndGet(); }
                };
            }
            @Override public String sha256() throws IOException {
                try {
                    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
                } catch (java.security.NoSuchAlgorithmException impossible) {
                    throw new IOException(impossible);
                }
            }
        };
        Set<String> before = temporarySpools();
        var structure = reader.detect(source, options("header", "first"), EngineLimits.defaults());
        try (var dataset = reader.open(source, structure, options("header", "first"), EngineLimits.defaults())) {
            assertTrue(dataset.rows().hasNext());
        }
        assertEquals(before, temporarySpools());
        assertEquals(opened.get(), closed.get());
    }

    @Test void engineApiAnalyzesXlsxWithoutCliAndIncludesReaderOptionsInFingerprint() throws Exception {
        Path path = temporary.resolve("api.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Customer Name");
            sheet.createRow(1).createCell(0).setCellValue("Ana");
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }
        var schema = new TargetSchema("customer", "1", null, "en",
                List.of(new TargetField("customer.name", "Name", List.of("Customer Name"),
                        PhysicalType.TEXT, Set.of(), false, true)));
        var engine = MappingEngine.builder().readers(List.of(reader)).build();
        var request = new AnalysisRequest(new PathTabularSource(path), schema,
                options("header", "first", "formula", "cached"));
        var result = engine.analyze(request);
        assertEquals("1.3", result.formatVersion());
        assertEquals("customer.name", result.candidatesByColumn().get("c0").getFirst().targetFieldId());
        assertEquals(1, result.rowsProcessed());

        var changed = engine.analyze(new AnalysisRequest(new PathTabularSource(path), schema,
                options("header", "first", "formula", "expression")));
        assertNotEquals(result.configurationFingerprint(), changed.configurationFingerprint());
    }

    @Test void emptyDuplicateAndLateIrregularRowsFollowTheDatasetContract() throws Exception {
        Path empty = temporary.resolve("empty.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Empty");
            try (var output = Files.newOutputStream(empty)) { workbook.write(output); }
        }
        var emptyStructure = reader.detect(new PathTabularSource(empty), AnalysisOptions.defaults(), EngineLimits.defaults());
        assertTrue(emptyStructure.columns().isEmpty());
        assertTrue(emptyStructure.warnings().contains("selected worksheet is empty"));

        Path irregular = temporary.resolve("irregular.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Nome");
            header.createCell(1).setCellValue("Nome");
            header.createCell(2).setCellValue("");
            for (int row = 1; row <= 20; row++) sheet.createRow(row).createCell(0).setCellValue("row-" + row);
            sheet.getRow(1).createCell(2).setCellValue("present");
            sheet.createRow(21).createCell(3).setCellValue("late-cell");
            try (var output = Files.newOutputStream(irregular)) { workbook.write(output); }
        }
        var options = options("header", "first");
        var structure = reader.detect(new PathTabularSource(irregular), options, EngineLimits.defaults());
        assertEquals("c0", structure.columns().get(0).id());
        assertEquals("c1", structure.columns().get(1).id());
        assertTrue(structure.warnings().stream().anyMatch(value -> value.contains("duplicate")));
        assertTrue(structure.warnings().stream().anyMatch(value -> value.contains("empty header")));
        try (var dataset = reader.open(new PathTabularSource(irregular), structure, options, EngineLimits.defaults())) {
            var rows = dataset.rows();
            for (int index = 0; index < 20; index++) {
                assertTrue(rows.hasNext());
                rows.next();
            }
            var error = assertThrows(EngineException.class, rows::hasNext);
            assertEquals("IRREGULAR_ROW", error.code());
            assertThrows(IllegalStateException.class, rows::hasNext);
        }
    }

    @Test void xlsxCursorProcessesTenThousandRowsWithoutMaterializingTheDataset() throws Exception {
        Path path = temporary.resolve("streaming.xlsx");
        var workbook = new SXSSFWorkbook(100);
        try {
            var sheet = workbook.createSheet("Data");
            sheet.createRow(0).createCell(0).setCellValue("Identifier");
            for (int row = 1; row <= 10_000; row++) sheet.createRow(row).createCell(0).setCellValue("id-" + row);
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        } finally {
            workbook.close();
        }
        var options = options("header", "first");
        var structure = reader.detect(new PathTabularSource(path), options, EngineLimits.defaults());
        long count = 0;
        try (var dataset = reader.open(new PathTabularSource(path), structure, options, EngineLimits.defaults())) {
            var rows = dataset.rows();
            while (rows.hasNext()) {
                var row = rows.next();
                count++;
                if (count == 10_000) assertEquals("id-10000", row.values().getFirst());
            }
        }
        assertEquals(10_000, count);
    }

    private static AnalysisOptions options(String... pairs) {
        var values = new java.util.LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) values.put(pairs[index], pairs[index + 1]);
        return new AnalysisOptions(values, 42);
    }

    private static void writeWorkbook(Workbook workbook, Path path) throws IOException {
        try (workbook) {
            if (workbook instanceof HSSFWorkbook legacy) legacy.createInformationProperties();
            var sheet = workbook.createSheet("Dados");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("Nome");
            header.createCell(1).setCellValue("Opcional");
            header.createCell(2).setCellValue("Codigo");
            header.createCell(3).setCellValue("Nascimento");
            header.createCell(4).setCellValue("Total");
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("Ana");
            CellStyle identifier = workbook.createCellStyle();
            identifier.setDataFormat(workbook.createDataFormat().getFormat("000"));
            row.createCell(2).setCellValue(7);
            row.getCell(2).setCellStyle(identifier);
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            row.createCell(3).setCellValue(LocalDate.of(2024, 1, 2));
            row.getCell(3).setCellStyle(date);
            row.createCell(4).setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(row.getCell(4));
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }
    }

    private static void writeZip(Path path, List<String> names) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String name : names) {
                output.putNextEntry(new ZipEntry(name));
                output.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static Set<String> temporarySpools() throws IOException {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("rizoma-xlsx-")).collect(java.util.stream.Collectors.toSet());
        }
    }
}
