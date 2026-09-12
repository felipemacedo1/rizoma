package io.github.rizoma.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.rizoma.core.AnalysisOptions;
import io.github.rizoma.core.AnalysisRequest;
import io.github.rizoma.core.AnalysisResult;
import io.github.rizoma.core.CoreSemanticDetectors;
import io.github.rizoma.core.BuiltInTransformers;
import io.github.rizoma.core.BuiltInValidators;
import io.github.rizoma.core.DryRunOptions;
import io.github.rizoma.core.DryRunRequest;
import io.github.rizoma.core.DryRunResult;
import io.github.rizoma.core.EngineConfig;
import io.github.rizoma.core.EngineException;
import io.github.rizoma.core.MappingEngine;
import io.github.rizoma.core.MappingFeedback;
import io.github.rizoma.core.MappingKnowledgeBase;
import io.github.rizoma.core.MappingPlan;
import io.github.rizoma.core.MappingPlanner;
import io.github.rizoma.core.NoOpMappingKnowledgeBase;
import io.github.rizoma.core.PathTabularSource;
import io.github.rizoma.core.SemanticDetector;
import io.github.rizoma.core.Validator;
import io.github.rizoma.core.ValueTransformer;
import io.github.rizoma.csv.CsvDataReader;
import io.github.rizoma.excel.ExcelDataReader;
import io.github.rizoma.ptbr.PtBrDetectors;
import io.github.rizoma.ptbr.PtBrHeaderRules;
import io.github.rizoma.ptbr.PtBrExecutionRules;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Command-line adapter for Rizoma analysis reports. */
@Command(name = "rizoma", mixinStandardHelpOptions = true, version = "rizoma 0.4.0-SNAPSHOT",
        description = "Explainable analysis, explicit feedback and read-only dry runs.",
        subcommands = {RizomaCli.Analyze.class, RizomaCli.Explain.class,
                RizomaCli.Plan.class, RizomaCli.DryRun.class, RizomaCli.Feedback.class})
public final class RizomaCli implements Runnable {
    /** Successful execution. */
    public static final int OK = 0;
    /** Invalid command arguments or input contract. */
    public static final int INVALID_INPUT = 2;
    /** Safe analysis or I/O failure. */
    public static final int ANALYSIS_FAILURE = 3;
    /** An explain selector matched more than one source column. */
    public static final int AMBIGUOUS_COLUMN = 4;

    private final PrintWriter out;

    public RizomaCli() { this(new PrintWriter(System.out, true)); }
    private RizomaCli(PrintWriter out) { this.out = out; }

    @Override public void run() { out.println("Choose a subcommand. Use 'rizoma --help'."); }

    /** Executes the CLI without terminating the JVM, useful for embedding and tests. */
    public static int execute(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine command = new CommandLine(new RizomaCli(out));
        command.setOut(out);
        command.setErr(err);
        command.setParameterExceptionHandler((exception, commandArgs) -> {
            CommandLine failed = exception.getCommandLine();
            failed.getErr().println("Invalid arguments: " + exception.getMessage());
            failed.usage(failed.getErr());
            return INVALID_INPUT;
        });
        command.setExecutionExceptionHandler((exception, commandArgs, parseResult) -> {
            commandArgs.getErr().println("Execution failed safely: " + safeMessage(exception));
            if (exception instanceof AmbiguousColumnException) return AMBIGUOUS_COLUMN;
            if (exception instanceof EngineException engineException
                    && engineException.code().equals("INVALID_READER_OPTION")) return INVALID_INPUT;
            if (exception instanceof IllegalArgumentException || exception instanceof JsonProcessingException) return INVALID_INPUT;
            return ANALYSIS_FAILURE;
        });
        return command.execute(args);
    }

    public static void main(String[] args) {
        System.exit(execute(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true)));
    }

    private static MappingEngine engine() {
        List<SemanticDetector> detectors = new ArrayList<>(CoreSemanticDetectors.defaults());
        detectors.addAll(PtBrDetectors.defaults());
        List<ValueTransformer<?, ?>> transformers = new ArrayList<>(BuiltInTransformers.defaults());
        transformers.addAll(PtBrExecutionRules.transformers());
        List<Validator<?>> validators = new ArrayList<>(BuiltInValidators.defaults());
        validators.addAll(PtBrExecutionRules.validators());
        return MappingEngine.builder()
                .readers(List.of(new ExcelDataReader(), new CsvDataReader()))
                .semanticDetectors(detectors)
                .transformers(transformers)
                .validators(validators)
                .normalizer(PtBrHeaderRules.normalizer())
                .configuration(EngineConfig.defaults())
                .build();
    }

    @Command(name = "feedback", mixinStandardHelpOptions = true,
            description = "Record explicit human mapping feedback.",
            subcommands = {Feedback.Confirm.class, Feedback.Reject.class, Feedback.Correct.class})
    static final class Feedback implements Runnable {
        @Spec CommandSpec spec;
        @Override public void run() { spec.commandLine().usage(spec.commandLine().getOut()); }

        private abstract static class FeedbackCommand implements Callable<Integer> {
            @Spec CommandSpec spec;
            @Parameters(index = "0", description = "Analysis report JSON path") Path report;
            @Option(names = "--schema", required = true, description = "Target schema JSON path") Path schema;
            @Option(names = "--knowledge", required = true, description = "Knowledge JSON Lines path") Path knowledge;
            @Option(names = "--column-id", required = true, description = "Stable source column id such as c0") String columnId;
            @Option(names = "--feedback-id", description = "Stable event id; generated when omitted") String feedbackId;
            @Option(names = "--timestamp", description = "ISO-8601 UTC timestamp; current instant when omitted") String timestamp;
            @Option(names = "--provenance", defaultValue = "explicit-cli-feedback") String provenance;

            final MappingFeedback context(MappingFeedback.FeedbackType type,
                    String suggestedTarget, String humanTarget) throws Exception {
                requireRegularFile(report, "report");
                requireRegularFile(schema, "schema");
                if (sameFileOrPath(report, knowledge) || sameFileOrPath(schema, knowledge))
                    throw new IllegalArgumentException("--knowledge must not overwrite the report or schema");
                AnalysisResult analysis = Explain.readReport(report);
                var targetSchema = JsonSupport.readSchema(schema);
                String id = feedbackId == null ? UUID.randomUUID().toString() : feedbackId;
                String at = timestamp == null ? Instant.now().toString() : timestamp;
                return switch (type) {
                    case CONFIRMED -> MappingFeedback.confirmed(id, at, analysis, targetSchema,
                            columnId, suggestedTarget, PtBrHeaderRules.normalizer(), provenance);
                    case REJECTED -> MappingFeedback.rejected(id, at, analysis, targetSchema,
                            columnId, suggestedTarget, PtBrHeaderRules.normalizer(), provenance);
                    case CORRECTED -> MappingFeedback.corrected(id, at, analysis, targetSchema,
                            columnId, suggestedTarget, humanTarget, PtBrHeaderRules.normalizer(), provenance);
                };
            }

            final Integer persist(MappingFeedback feedback) {
                var store = new JsonLinesMappingKnowledgeBase(knowledge);
                store.record(feedback);
                var snapshot = store.snapshot();
                spec.commandLine().getOut().printf("feedbackId=%s snapshot=%s events=%d%n",
                        feedback.feedbackId(), snapshot.id(), snapshot.eventCount());
                return OK;
            }
        }

        @Command(name = "confirm", mixinStandardHelpOptions = true,
                description = "Confirm a suggested source-to-target mapping.")
        static final class Confirm extends FeedbackCommand {
            @Option(names = "--target", required = true) String target;
            @Override public Integer call() throws Exception {
                return persist(context(MappingFeedback.FeedbackType.CONFIRMED, target, target));
            }
        }

        @Command(name = "reject", mixinStandardHelpOptions = true,
                description = "Reject a suggested source-to-target mapping.")
        static final class Reject extends FeedbackCommand {
            @Option(names = "--target", required = true) String target;
            @Override public Integer call() throws Exception {
                return persist(context(MappingFeedback.FeedbackType.REJECTED, target, ""));
            }
        }

        @Command(name = "correct", mixinStandardHelpOptions = true,
                description = "Reject one target and confirm a different target in one event.")
        static final class Correct extends FeedbackCommand {
            @Option(names = "--suggested-target", required = true) String suggestedTarget;
            @Option(names = "--correct-target", required = true) String correctTarget;
            @Override public Integer call() throws Exception {
                return persist(context(MappingFeedback.FeedbackType.CORRECTED,
                        suggestedTarget, correctTarget));
            }
        }
    }

    @Command(name = "plan", mixinStandardHelpOptions = true,
            description = "Create a source-bound mapping plan from explicit mapping confirmations.")
    static final class Plan implements Callable<Integer> {
        @Parameters(index = "0", description = "Analysis report JSON path") Path report;
        @Option(names = "--schema", required = true, description = "Target schema JSON path") Path schema;
        @Option(names = "--map", required = true, description = "Confirmed sourceId=targetFieldId; repeat for each mapping")
        List<String> mappings;
        @Option(names = "--out", required = true, description = "Mapping plan JSON path") Path output;

        @Override public Integer call() throws Exception {
            requireRegularFile(report, "report");
            requireRegularFile(schema, "schema");
            if (sameFileOrPath(report, output) || sameFileOrPath(schema, output))
                throw new IllegalArgumentException("--out must not overwrite the report or schema");
            AnalysisResult analysis = Explain.readReport(report);
            var selections = mappings.stream().map(Plan::selection).toList();
            MappingPlan plan = new MappingPlanner().create(analysis, JsonSupport.readSchema(schema), selections);
            writeAtomically(output, plan);
            return OK;
        }

        private static MappingPlanner.Selection selection(String value) {
            int separator = value.indexOf('=');
            if (separator <= 0 || separator == value.length() - 1)
                throw new IllegalArgumentException("--map must use sourceId=targetFieldId");
            return new MappingPlanner.Selection(value.substring(0, separator), value.substring(separator + 1),
                    "explicit CLI confirmation");
        }
    }

    @Command(name = "dry-run", mixinStandardHelpOptions = true,
            description = "Transform and validate a source through a bound plan without a destination sink.")
    static final class DryRun implements Callable<Integer> {
        @Parameters(index = "0", description = "Input CSV, XLS or XLSX path") Path source;
        @Option(names = "--schema", required = true, description = "Target schema JSON path") Path schema;
        @Option(names = "--mapping", required = true, description = "Mapping plan JSON path") Path mapping;
        @Option(names = "--out", required = true, description = "Dry-run result JSON path") Path output;
        @Option(names = "--delimiter", description = "One of comma, semicolon, tab or pipe") String delimiter;
        @Option(names = "--charset", description = "CSV only: UTF-8, ISO-8859-1 or WINDOWS-1252") String charset;
        @Option(names = "--header", defaultValue = "detect", description = "detect, first or none") String header;
        @Option(names = "--sheet", description = "Excel only: exact worksheet name or zero-based index") String sheet;
        @Option(names = "--formula", description = "Excel only: cached, expression or reject") String formula;
        @Option(names = "--error-policy", defaultValue = "COLLECT_ERRORS",
                description = "FAIL_FAST, SKIP_ROW or COLLECT_ERRORS") DryRunOptions.ErrorPolicy errorPolicy;
        @Option(names = "--max-errors", defaultValue = "1000") long maxErrors;
        @Option(names = "--max-issue-samples", defaultValue = "100") int maxIssueSamples;
        @Option(names = "--max-issue-codes", defaultValue = "256") int maxIssueCodes;

        @Override public Integer call() throws Exception {
            requireRegularFile(source, "source"); requireRegularFile(schema, "schema");
            requireRegularFile(mapping, "mapping");
            if (sameFileOrPath(source, output) || sameFileOrPath(schema, output)
                    || sameFileOrPath(mapping, output))
                throw new IllegalArgumentException("--out must not overwrite an input");
            MappingPlan plan;
            try { plan = JsonSupport.MAPPER.readValue(mapping.toFile(), MappingPlan.class); }
            catch (JsonProcessingException exception) { throw new IllegalArgumentException("invalid mapping plan JSON", exception); }
            Map<String, String> readerOptions = readerOptions(header, charset, delimiter, sheet, formula);
            DryRunResult result = engine().dryRun(new DryRunRequest(new PathTabularSource(source),
                    JsonSupport.readSchema(schema), plan, new AnalysisOptions(readerOptions, 42L),
                    new DryRunOptions(errorPolicy, maxErrors, maxIssueSamples, maxIssueCodes)));
            writeAtomically(output, result);
            return OK;
        }
    }

    private static String safeMessage(Throwable exception) {
        if (exception instanceof EngineException engineException) {
            return engineException.code() + ": " + engineException.getMessage();
        }
        if (exception instanceof IllegalArgumentException) return exception.getMessage();
        return exception.getClass().getSimpleName() + " (details intentionally omitted)";
    }

    @Command(name = "analyze", mixinStandardHelpOptions = true,
            description = "Analyze a CSV, XLS or XLSX against a versioned target schema and write a JSON report.")
    static final class Analyze implements Callable<Integer> {
        @Parameters(index = "0", description = "Input CSV, XLS or XLSX path") Path source;
        @Option(names = "--schema", required = true, description = "Target schema JSON path") Path schema;
        @Option(names = "--out", required = true, description = "Analysis report JSON path") Path report;
        @Option(names = "--delimiter", description = "One of comma, semicolon, tab or pipe") String delimiter;
        @Option(names = "--charset", description = "CSV only: UTF-8, ISO-8859-1 or WINDOWS-1252") String charset;
        @Option(names = "--header", defaultValue = "detect", description = "detect, first or none") String header;
        @Option(names = "--sheet", description = "Excel only: exact worksheet name or zero-based index") String sheet;
        @Option(names = "--formula", description = "Excel only: cached, expression or reject") String formula;
        @Option(names = "--knowledge", description = "Optional bounded feedback JSON Lines path") Path knowledge;

        @Override public Integer call() throws Exception {
            requireRegularFile(source, "source");
            requireRegularFile(schema, "schema");
            if (sameFileOrPath(source, report) || sameFileOrPath(schema, report)
                    || knowledge != null && sameFileOrPath(knowledge, report)) {
                throw new IllegalArgumentException("--out must not overwrite an input");
            }
            Map<String, String> options = readerOptions(header, charset, delimiter, sheet, formula);
            MappingKnowledgeBase selectedKnowledge = knowledge == null
                    ? NoOpMappingKnowledgeBase.INSTANCE : new JsonLinesMappingKnowledgeBase(knowledge);
            AnalysisResult result = engine().analyze(new AnalysisRequest(
                    new PathTabularSource(source), JsonSupport.readSchema(schema),
                    new AnalysisOptions(options, 42L), selectedKnowledge));
            writeAtomically(report, result);
            return OK;
        }

    }

    @Command(name = "explain", mixinStandardHelpOptions = true,
            description = "Explain a source column from an existing report without re-analyzing data.")
    static final class Explain implements Callable<Integer> {
        @Spec CommandSpec spec;
        @Parameters(index = "0", description = "Analysis report JSON path") Path report;
        @Option(names = "--column", description = "Exact source header") String header;
        @Option(names = "--column-id", description = "Stable position identifier such as c0") String columnId;

        @Override public Integer call() throws Exception {
            requireRegularFile(report, "report");
            if ((header == null) == (columnId == null)) {
                throw new IllegalArgumentException("provide exactly one of --column or --column-id");
            }
            AnalysisResult result = readReport(report);
            String id = columnId == null ? resolveHeader(result, header) : resolveId(result, columnId);
            var column = result.structure().columns().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
            var decision = result.decisionsByColumn().get(id);
            var candidates = result.candidatesByColumn().getOrDefault(id, List.of());
            var profile = result.profiles().stream().filter(item -> item.column().id().equals(id)).findFirst().orElseThrow();
            var statistics = profile.statistics();
            PrintWriter writer = spec.commandLine().getOut();
            writer.printf("Column %s [id=%s, position=%d]%n", protectedHeader(column.header()), id, column.position());
            writer.printf("Decision: %s -> %s%n", decision.status(),
                    decision.targetFieldId() == null ? "<abstain>" : decision.targetFieldId());
            writer.printf("Score %.6f | coverage %.6f | margin %s | confidenceIndex %.6f | calibration %s%n",
                    decision.score(), decision.coverage(), decision.margin() == null ? "unavailable" : String.format(java.util.Locale.ROOT, "%.6f", decision.margin()),
                    decision.confidenceIndex(), result.calibration());
            writer.printf("Knowledge: snapshot=%s version=%s historicalPairs=%d%n",
                    result.knowledgeSnapshotId(), result.knowledgeVersion(),
                    result.historicalEvidenceByColumn().getOrDefault(id, List.of()).size());
            writer.printf("Profile: rows=%d nullRatio=%.6f cardinality=%d (%s) uniqueRatio=%.6f entropy=%s%n",
                    profile.rowCount(), statistics.nullRatio(), statistics.cardinality().value(),
                    statistics.cardinality().accuracy(), statistics.uniqueRatio(),
                    statistics.entropyBits() == null ? "unavailable"
                            : String.format(java.util.Locale.ROOT, "%.6f (%s)", statistics.entropyBits(),
                                    statistics.entropyAccuracy()));
            if (statistics.entropyBits() != null) {
                writer.println("Entropy role: auxiliary profiling evidence; mapping contribution is unavailable "
                        + "without a target distribution baseline");
            }
            if (!statistics.dominantSemanticType().isEmpty()) {
                writer.printf("Semantic: %s confidenceIndex=%.6f validRatio=%.6f invalidRatio=%.6f%n",
                        statistics.dominantSemanticType(), statistics.semanticConfidence(),
                        statistics.semanticValidRatio(), statistics.semanticInvalidRatio());
            }
            if (!statistics.anomalies().isEmpty()) {
                writer.println("Anomalies: " + statistics.anomalies().stream()
                        .map(item -> item.code() + "=" + item.count()).collect(java.util.stream.Collectors.joining("; ")));
            }
            if (!decision.blockers().isEmpty()) writer.println("Blockers: " + String.join("; ", decision.blockers()));
            for (int index = 0; index < candidates.size(); index++) {
                var candidate = candidates.get(index);
                writer.printf("%d. %s score=%.6f coverage=%.6f eligible=%s%n",
                        index + 1, candidate.targetFieldId(), candidate.score(), candidate.coverage(), candidate.eligible());
                candidate.components().forEach(component -> writer.printf(
                        "   %s: available=%s value=%s weight=%.4f reliability=%.4f contribution=%.6f evidence=%s%n",
                        component.id(), component.available(), component.value(), component.weight(), component.reliability(),
                        component.contribution(), component.available() ? component.evidence() : component.unavailableReason()));
            }
            List<AnalysisResult.PrunedCandidate> pruned = result.prunedCandidatesByColumn().getOrDefault(id, List.of());
            if (!pruned.isEmpty()) writer.println("Pruned candidates: " + pruned.size() + " (reasons retained in JSON report)");
            return OK;
        }

        private static AnalysisResult readReport(Path report) throws IOException {
            try {
                AnalysisResult result = JsonSupport.MAPPER.readValue(report.toFile(), AnalysisResult.class);
                if (!List.of("1.0", "1.1", "1.2", "1.3").contains(result.formatVersion())) {
                    throw new IllegalArgumentException("unsupported report formatVersion");
                }
                return result;
            }
            catch (JsonProcessingException e) { throw new IllegalArgumentException("invalid analysis report JSON", e); }
        }

        private static String resolveHeader(AnalysisResult result, String header) {
            List<String> matches = result.structure().columns().stream()
                    .filter(column -> column.header().equals(header)).map(item -> item.id()).toList();
            if (matches.isEmpty()) throw new IllegalArgumentException("source column header was not found");
            if (matches.size() > 1) throw new AmbiguousColumnException(
                    "header is duplicated; select one of these ids: " + String.join(", ", matches));
            return matches.getFirst();
        }

        private static String resolveId(AnalysisResult result, String id) {
            return result.structure().columns().stream().filter(column -> column.id().equals(id))
                    .map(column -> column.id()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("source column id was not found"));
        }
    }

    private static void requireRegularFile(Path path, String label) {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException(label + " must be a readable regular file");
    }

    private static boolean sameFileOrPath(Path input, Path output) throws IOException {
        Path left = input.toAbsolutePath().normalize();
        Path right = output.toAbsolutePath().normalize();
        return left.equals(right) || Files.exists(right) && Files.isSameFile(left, right);
    }

    private static Map<String, String> readerOptions(String header, String charset,
            String delimiter, String sheet, String formula) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("header", header);
        if (charset != null) options.put("charset", charset);
        if (delimiter != null) options.put("delimiter", namedDelimiter(delimiter));
        if (sheet != null) options.put("sheet", sheet);
        if (formula != null) options.put("formula", formula);
        return options;
    }

    private static String namedDelimiter(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "comma", "," -> ",";
            case "semicolon", ";" -> ";";
            case "tab", "\\t" -> "\\t";
            case "pipe", "|" -> "|";
            default -> throw new IllegalArgumentException("--delimiter must be comma, semicolon, tab or pipe");
        };
    }

    private static void writeAtomically(Path destination, Object result) throws IOException {
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".rizoma-report-", ".json");
        try {
            JsonSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), result);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String protectedHeader(String header) {
        return header.isBlank() ? "<empty>" : header;
    }

    private static final class AmbiguousColumnException extends IllegalArgumentException {
        AmbiguousColumnException(String message) { super(message); }
    }
}
