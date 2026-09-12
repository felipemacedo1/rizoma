package io.github.felipemacedo1.rizoma.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.felipemacedo1.rizoma.api.Rizoma;
import io.github.felipemacedo1.rizoma.api.RizomaException;
import io.github.felipemacedo1.rizoma.core.AnalysisOptions;
import io.github.felipemacedo1.rizoma.core.AnalysisRequest;
import io.github.felipemacedo1.rizoma.core.AnalysisResult;
import io.github.felipemacedo1.rizoma.core.DryRunOptions;
import io.github.felipemacedo1.rizoma.core.DryRunRequest;
import io.github.felipemacedo1.rizoma.core.DryRunResult;
import io.github.felipemacedo1.rizoma.core.EngineException;
import io.github.felipemacedo1.rizoma.core.MappingFeedback;
import io.github.felipemacedo1.rizoma.core.MappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.MappingPlan;
import io.github.felipemacedo1.rizoma.core.MappingPlanner;
import io.github.felipemacedo1.rizoma.core.NoOpMappingKnowledgeBase;
import io.github.felipemacedo1.rizoma.core.InMemoryLayoutRegistry;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionRequest;
import io.github.felipemacedo1.rizoma.core.LayoutRecognitionResult;
import io.github.felipemacedo1.rizoma.core.LayoutTemplate;
import io.github.felipemacedo1.rizoma.core.ProjectionSource;
import io.github.felipemacedo1.rizoma.core.PathTabularSource;
import io.github.felipemacedo1.rizoma.ptbr.PtBrHeaderRules;
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
@Command(name = "rizoma", mixinStandardHelpOptions = true, version = "rizoma 0.6.0-SNAPSHOT",
        description = "Explainable analysis, adaptive layouts and read-only dry runs.",
        subcommands = {RizomaCli.Analyze.class, RizomaCli.Explain.class,
                RizomaCli.Plan.class, RizomaCli.DryRun.class, RizomaCli.Feedback.class,
                RizomaCli.Template.class, RizomaCli.Recognize.class, RizomaCli.ExplainPlan.class})
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
            if (exception instanceof RizomaException rizomaException
                    && rizomaException.code().equals("INVALID_READER_OPTION")) return INVALID_INPUT;
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

    private static Rizoma engine() { return Rizoma.create(); }

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

    @Command(name = "template", mixinStandardHelpOptions = true,
            description = "Manage explicit reusable layout templates.",
            subcommands = Template.Create.class)
    static final class Template implements Runnable {
        @Spec CommandSpec spec;
        @Override public void run() { spec.commandLine().usage(spec.commandLine().getOut()); }

        @Command(name = "create", mixinStandardHelpOptions = true,
                description = "Create a reusable template from a confirmed source-bound plan.")
        static final class Create implements Callable<Integer> {
            @Parameters(index = "0", description = "Analysis report JSON path") Path report;
            @Option(names = "--mapping", required = true, description = "Confirmed MappingPlan JSON path") Path mapping;
            @Option(names = "--name", required = true, description = "Human-readable template name") String name;
            @Option(names = "--template-version", defaultValue = "1") String templateVersion;
            @Option(names = "--timestamp", description = "ISO-8601 creation timestamp") String timestamp;
            @Option(names = "--provenance", defaultValue = "explicit-cli-template") String provenance;
            @Option(names = "--out", required = true, description = "LayoutTemplate JSON path") Path output;

            @Override public Integer call() throws Exception {
                requireRegularFile(report, "report"); requireRegularFile(mapping, "mapping");
                if (sameFileOrPath(report, output) || sameFileOrPath(mapping, output))
                    throw new IllegalArgumentException("--out must not overwrite an input");
                AnalysisResult analysis = Explain.readReport(report);
                MappingPlan plan = readMappingPlan(mapping);
                LayoutTemplate template = LayoutTemplate.create(name, templateVersion, analysis, plan,
                        timestamp == null ? Instant.now().toString() : timestamp,
                        provenance, PtBrHeaderRules.normalizer());
                writeAtomically(output, template);
                return OK;
            }
        }
    }

    @Command(name = "recognize", mixinStandardHelpOptions = true,
            description = "Recognize a current source against an explicit layout template.")
    static final class Recognize implements Callable<Integer> {
        @Parameters(index = "0", description = "Input CSV, XLS or XLSX path") Path source;
        @Option(names = "--schema", required = true) Path schema;
        @Option(names = "--template", required = true) Path template;
        @Option(names = "--out", required = true, description = "Layout recognition JSON path") Path output;
        @Option(names = "--plan-out", description = "Write the new current-source plan when reuse is safe") Path planOutput;
        @Option(names = "--guard-rows", defaultValue = "64") int guardRows;
        @Option(names = "--delimiter") String delimiter;
        @Option(names = "--charset") String charset;
        @Option(names = "--header", defaultValue = "detect") String header;
        @Option(names = "--sheet") String sheet;
        @Option(names = "--formula") String formula;

        @Override public Integer call() throws Exception {
            requireRegularFile(source, "source"); requireRegularFile(schema, "schema");
            requireRegularFile(template, "template");
            if (sameFileOrPath(source, output) || sameFileOrPath(schema, output)
                    || sameFileOrPath(template, output))
                throw new IllegalArgumentException("--out must not overwrite an input");
            if (planOutput != null && (sameFileOrPath(source, planOutput)
                    || sameFileOrPath(schema, planOutput) || sameFileOrPath(template, planOutput)
                    || sameFileOrPath(output, planOutput)))
                throw new IllegalArgumentException("--plan-out must not overwrite an input or report");
            LayoutTemplate loaded;
            try { loaded = JsonSupport.MAPPER.readValue(template.toFile(), LayoutTemplate.class); }
            catch (JsonProcessingException exception) { throw new IllegalArgumentException("invalid layout template JSON", exception); }
            var registry = new InMemoryLayoutRegistry(); registry.register(loaded);
            Map<String, String> readerOptions = readerOptions(header, charset, delimiter, sheet, formula);
            LayoutRecognitionResult result = engine().recognizeLayout(new LayoutRecognitionRequest(
                    new PathTabularSource(source), JsonSupport.readSchema(schema),
                    new AnalysisOptions(readerOptions, 42L), registry, guardRows));
            writeAtomically(output, result);
            if (planOutput != null && result.mappingPlan() != null) writeAtomically(planOutput, result.mappingPlan());
            return OK;
        }
    }

    @Command(name = "explain-plan", mixinStandardHelpOptions = true,
            description = "Explain a target projection or why a source column is unused.")
    static final class ExplainPlan implements Callable<Integer> {
        @Spec CommandSpec spec;
        @Parameters(index = "0", description = "MappingPlan JSON path") Path mapping;
        @Option(names = "--target") String target;
        @Option(names = "--source-column-id") String sourceColumnId;

        @Override public Integer call() throws Exception {
            requireRegularFile(mapping, "mapping");
            if ((target == null) == (sourceColumnId == null))
                throw new IllegalArgumentException("provide exactly one of --target or --source-column-id");
            MappingPlan plan = readMappingPlan(mapping);
            PrintWriter writer = spec.commandLine().getOut();
            writer.printf("route=%s template=%s layoutFingerprint=%s%n",
                    plan.executionRoute(), plan.layoutTemplateId().isBlank() ? "none" : plan.layoutTemplateId(),
                    plan.layoutFingerprint().isBlank() ? "none" : plan.layoutFingerprint());
            if (target != null) {
                MappingPlan.FieldMapping field = plan.mappings().stream()
                        .filter(item -> item.targetFieldId().equals(target)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("target is not present in mapping plan"));
                ProjectionSource source = field.projectionSource();
                writer.printf("target=%s source=%s%n", target, source.kind());
                switch (source.kind()) {
                    case SOURCE_COLUMN -> writer.println("sourceColumnId=" + source.sourceColumnId());
                    case CONSTANT -> writer.println("constant=<redacted:length=" + source.constantValue().length() + ">");
                    case DERIVED -> {
                        writer.println("operation=" + source.derivedExpression().operation());
                        writer.println("inputs=" + source.derivedExpression().operands().stream().map(operand ->
                                operand.kind() == ProjectionSource.OperandKind.SOURCE_COLUMN
                                        ? operand.value() : "<constant:length=" + operand.value().length() + ">").toList());
                    }
                    case UNMAPPED -> writer.println("decision=UNMAPPED_TARGET");
                }
                writer.println("transformations=" + field.transformations().stream().map(MappingPlan.Step::id).toList());
                writer.println("validators=" + field.validations().stream().map(MappingPlan.Step::id).toList());
            } else if (plan.ignoredSourceColumns().contains(sourceColumnId)) {
                writer.printf("sourceColumnId=%s decision=IGNORED_BY_PLAN%n", sourceColumnId);
            } else if (plan.unmappedSourceColumns().contains(sourceColumnId)) {
                writer.printf("sourceColumnId=%s decision=NO_MATCH_FOUND%n", sourceColumnId);
            } else {
                List<String> targets = plan.mappings().stream()
                        .filter(item -> item.projectionSource().referencedSourceColumns().contains(sourceColumnId))
                        .map(MappingPlan.FieldMapping::targetFieldId).toList();
                if (targets.isEmpty()) throw new IllegalArgumentException("source column is not present in mapping plan");
                writer.printf("sourceColumnId=%s decision=USED targets=%s%n", sourceColumnId, targets);
            }
            return OK;
        }
    }

    @Command(name = "plan", mixinStandardHelpOptions = true,
            description = "Create a source-bound mapping plan from explicit mapping confirmations.")
    static final class Plan implements Callable<Integer> {
        @Parameters(index = "0", description = "Analysis report JSON path") Path report;
        @Option(names = "--schema", required = true, description = "Target schema JSON path") Path schema;
        @Option(names = "--map", description = "Confirmed sourceId=targetFieldId; repeat for direct mappings")
        List<String> mappings;
        @Option(names = "--ignore", description = "Source column ID explicitly ignored by the plan")
        List<String> ignored;
        @Option(names = "--constant", description = "targetFieldId=value; repeat for constant projections")
        List<String> constants;
        @Option(names = "--derive", description = "target=OP:source:cN,constant:value; repeat for derived projections")
        List<String> derived;
        @Option(names = "--unmapped-target", description = "Target field explicitly left without a value source")
        List<String> unmappedTargets;
        @Option(names = "--out", required = true, description = "Mapping plan JSON path") Path output;

        @Override public Integer call() throws Exception {
            requireRegularFile(report, "report");
            requireRegularFile(schema, "schema");
            if (sameFileOrPath(report, output) || sameFileOrPath(schema, output))
                throw new IllegalArgumentException("--out must not overwrite the report or schema");
            AnalysisResult analysis = Explain.readReport(report);
            var selections = new ArrayList<MappingPlanner.ProjectionSelection>();
            safeList(mappings).stream().map(Plan::selection).forEach(selections::add);
            safeList(constants).stream().map(Plan::constantSelection).forEach(selections::add);
            safeList(derived).stream().map(Plan::derivedSelection).forEach(selections::add);
            safeList(unmappedTargets).stream().map(target -> new MappingPlanner.ProjectionSelection(
                    target, ProjectionSource.unmapped(), "explicit CLI unmapped target")).forEach(selections::add);
            if (selections.isEmpty()) throw new IllegalArgumentException("plan requires at least one target projection");
            MappingPlan plan = new MappingPlanner().createProjected(analysis,
                    JsonSupport.readSchema(schema), selections, safeList(ignored));
            writeAtomically(output, plan);
            return OK;
        }

        private static MappingPlanner.ProjectionSelection selection(String value) {
            int separator = value.indexOf('=');
            if (separator <= 0 || separator == value.length() - 1)
                throw new IllegalArgumentException("--map must use sourceId=targetFieldId");
            return new MappingPlanner.ProjectionSelection(value.substring(separator + 1),
                    ProjectionSource.sourceColumn(value.substring(0, separator)), "explicit CLI confirmation");
        }

        private static MappingPlanner.ProjectionSelection constantSelection(String value) {
            int separator = value.indexOf('=');
            if (separator <= 0) throw new IllegalArgumentException("--constant must use targetFieldId=value");
            return new MappingPlanner.ProjectionSelection(value.substring(0, separator),
                    ProjectionSource.constant(value.substring(separator + 1)), "explicit CLI constant");
        }

        private static MappingPlanner.ProjectionSelection derivedSelection(String value) {
            int equals = value.indexOf('=');
            int colon = value.indexOf(':', equals + 1);
            if (equals <= 0 || colon <= equals + 1 || colon == value.length() - 1)
                throw new IllegalArgumentException("--derive must use target=OP:source:cN,constant:value");
            ProjectionSource.Operation operation;
            try { operation = ProjectionSource.Operation.valueOf(value.substring(equals + 1, colon).toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unsupported derived operation", exception); }
            List<ProjectionSource.Operand> operands = java.util.Arrays.stream(value.substring(colon + 1).split(",", -1))
                    .map(Plan::operand).toList();
            return new MappingPlanner.ProjectionSelection(value.substring(0, equals),
                    ProjectionSource.derived(new ProjectionSource.DerivedExpression(operation, operands)),
                    "explicit CLI derived projection");
        }

        private static ProjectionSource.Operand operand(String value) {
            if (value.startsWith("source:")) return ProjectionSource.Operand.sourceColumn(value.substring(7));
            if (value.startsWith("constant:")) return ProjectionSource.Operand.constant(value.substring(9));
            throw new IllegalArgumentException("derived operand must start with source: or constant:");
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
            MappingPlan plan = readMappingPlan(mapping);
            Map<String, String> readerOptions = readerOptions(header, charset, delimiter, sheet, formula);
            DryRunResult result = engine().dryRun(new DryRunRequest(new PathTabularSource(source),
                    JsonSupport.readSchema(schema), plan, new AnalysisOptions(readerOptions, 42L),
                    new DryRunOptions(errorPolicy, maxErrors, maxIssueSamples, maxIssueCodes)));
            writeAtomically(output, result);
            return OK;
        }
    }

    private static String safeMessage(Throwable exception) {
        if (exception instanceof RizomaException rizomaException) {
            return rizomaException.code() + ": " + rizomaException.getMessage();
        }
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

    private static MappingPlan readMappingPlan(Path path) {
        try { return JsonSupport.MAPPER.readValue(path.toFile(), MappingPlan.class); }
        catch (IOException exception) { throw new IllegalArgumentException("invalid mapping plan JSON", exception); }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
