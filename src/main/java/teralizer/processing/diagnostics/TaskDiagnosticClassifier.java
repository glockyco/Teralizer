package teralizer.processing.diagnostics;

import com.google.gson.JsonObject;
import gov.nasa.jpf.JPFListenerException;
import gov.nasa.jpf.JPFNativePeerException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import teralizer.jpf.ExtractionAborted;
import teralizer.jpf.ExtractionOutcome;
import teralizer.processing.ProcessingStage;
import teralizer.transformer.UnsupportedSpfTermException;
import teralizer.util.ConsoleCommandException;

public final class TaskDiagnosticClassifier {

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger(TaskDiagnosticClassifier.class);
    private static final String NO_UNCAUGHT_EXCEPTIONS_PROPERTY =
        "gov.nasa.jpf.vm.NoUncaughtExceptionsProperty";

    // A command timeout in any of these stages is a measured limitation (the work is too slow for
    // the budget), not breakage, so the planner treats it as attrition and the run continues.
    private static final Set<ProcessingStage> COMMAND_TIMEOUT_ATTRITION_STAGES = EnumSet.of(
        ProcessingStage.EXECUTE_TESTS_ORIGINAL,
        ProcessingStage.EXECUTE_TESTS_INITIAL,
        ProcessingStage.EXECUTE_TESTS_GENERALIZED,
        ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL,
        ProcessingStage.COLLECT_JACOCO_DATA_INITIAL,
        ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED,
        ProcessingStage.COLLECT_PIT_DATA_ORIGINAL,
        ProcessingStage.COLLECT_PIT_DATA_INITIAL,
        ProcessingStage.COLLECT_PIT_DATA_GENERALIZED);

    private TaskDiagnosticClassifier() {
    }

    public static Diagnostic classify(ProcessingStage stage, Throwable failure) {
        Throwable typed = unwrap(failure);
        if (typed instanceof ExtractionAborted) {
            return fromAbort((ExtractionAborted) typed);
        }
        if (typed instanceof UnsupportedSpfTermException) {
            return unsupportedTerm(typed.getMessage());
        }
        if (typed instanceof JPFNativePeerException || contains(failure, "native peer")) {
            return diagnostic(TaskDiagnosticCodes.MISSING_NATIVE_PEER, messageDetail(failure));
        }
        if (stage == ProcessingStage.ANALYZE_JPF && contains(failure, "All assertions were excluded")) {
            return diagnostic(TaskDiagnosticCodes.NO_INPUT_SPEC, messageDetail(failure));
        }
        if (stage == ProcessingStage.BUILD_PROJECT_INSTRUMENTED || stage == ProcessingStage.BUILD_PROJECT_GENERALIZED) {
            return classifyBuildFailure(stage, failure);
        }
        if (stage == ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL || stage == ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED) {
            return classifyReportFailure(failure);
        }
        if (contains(failure, "Command execution timeout exceeded")
            && COMMAND_TIMEOUT_ATTRITION_STAGES.contains(stage)) {
            // A command timeout during test execution, coverage collection, or mutation testing is
            // a measured limitation (the work is too slow for the budget), not breakage. Keep
            // SUITE_TIMEOUT for the generalized suite so existing analysis keys still match, and
            // record every other timeout as the general EXECUTION_TIMEOUT. Both are attrition, so a
            // slow project drops its downstream data instead of halting the whole run.
            return diagnostic(
                stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED
                    ? TaskDiagnosticCodes.SUITE_TIMEOUT
                    : TaskDiagnosticCodes.EXECUTION_TIMEOUT,
                messageDetail(failure));
        }
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains(ExtractionOutcome.Kind.UNSUPPORTED_TERM.name())) {
            return unsupportedTerm(afterToken(message, ExtractionOutcome.Kind.UNSUPPORTED_TERM.name()));
        }
        if (message.contains(ExtractionOutcome.Kind.TARGET_NOT_ENTERED.name())) {
            return fromOutcome(ExtractionOutcome.fromState(false, false, true));
        }
        if (message.contains(ExtractionOutcome.Kind.TARGET_NOT_EXITED.name())) {
            return fromOutcome(ExtractionOutcome.fromState(true, false, true));
        }
        if (contains(failure, "Identified") && contains(failure, "error(s) during JPF execution")) {
            return classifyJpfUncaughtFailure(failure);
        }
        Diagnostic commandFailure = classifyCommandFailure(failure);
        if (commandFailure != null) {
            return commandFailure;
        }
        // The collection tasks raise this themselves when a tool ran but wrote no report where we
        // expected one, which is a different fact from the tool failing.
        if (contains(failure, "Report file") && contains(failure, "does not exist")) {
            return diagnostic(TaskDiagnosticCodes.REPORT_ABSENT, messageDetail(failure));
        }
        return diagnostic(TaskDiagnosticCodes.LISTENER_BUG, messageDetail(failure));
    }

    /**
     * Types a failed Maven command from its captured output. A coverage or mutation command reports
     * only an exit code and the paths of its stdout and stderr files, so the discriminating text has
     * to be read back from disk. That is the same approach {@code TestExecutionTask} already takes
     * when it inspects a failed test run. Returns null when the failure is not a command failure or
     * its output is unreadable, leaving the caller's fallback in place.
     */
    private static Diagnostic classifyCommandFailure(Throwable failure) {
        ConsoleCommandException command = commandException(failure);
        if (command == null) {
            return null;
        }
        String output = readCommandOutput(command);
        if (output.isEmpty()) {
            return null;
        }
        // Ordered: a dead minion and an unusable plugin both also print a Maven build failure, so
        // the specific markers have to be tested before any general one.
        if (output.contains("MINION_DIED") || output.contains("Could not find or load main class")) {
            return diagnostic(TaskDiagnosticCodes.MINION_DIED, commandDetail(command, "minion exited abnormally"));
        }
        if (output.contains("does not have a no-args constructor") || output.contains("mutationCoverage failed: null")) {
            return diagnostic(TaskDiagnosticCodes.PLUGIN_UNUSABLE, commandDetail(command, "plugin version cannot run"));
        }
        if (output.contains("did not pass without mutation")) {
            return diagnostic(TaskDiagnosticCodes.SUITE_NOT_GREEN, commandDetail(command, "unmutated suite has failing tests"));
        }
        if (output.contains("No tests found")) {
            return diagnostic(TaskDiagnosticCodes.NO_TESTS_FOUND, commandDetail(command, "no tests visible to the tool"));
        }
        return null;
    }

    private static ConsoleCommandException commandException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConsoleCommandException) {
                return (ConsoleCommandException) current;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    private static String readCommandOutput(ConsoleCommandException command) {
        StringBuilder text = new StringBuilder();
        for (Path path : new Path[]{command.getErrorPath(), command.getOutputPath()}) {
            if (path == null || !Files.exists(path)) {
                continue;
            }
            try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                lines.forEach(line -> text.append(line).append('\n'));
            } catch (IOException | UncheckedIOException e) {
                LOGGER.atDebug().log("Could not read captured command output: " + path);
            }
        }
        return text.toString();
    }

    private static String commandDetail(ConsoleCommandException command, String summary) {
        JsonObject json = new JsonObject();
        json.addProperty("throwable_type", command.getClass().getName());
        json.addProperty("message", command.getMessage());
        json.addProperty("summary", summary);
        return json.toString();
    }

    public static Diagnostic fromOutcome(ExtractionOutcome outcome) {
        switch (outcome.getKind()) {
            case TARGET_NOT_ENTERED:
                return diagnostic(TaskDiagnosticCodes.NO_INPUT_SPEC, outcomeDetail(outcome));
            case TARGET_NOT_EXITED:
                return diagnostic(TaskDiagnosticCodes.NO_OUTPUT_SPEC, outcomeDetail(outcome));
            case UNSUPPORTED_TERM:
                return unsupportedTerm(outcome.getDetail());
            case EXTRACTED:
            default:
                return diagnostic(TaskDiagnosticCodes.LISTENER_BUG, outcomeDetail(outcome));
        }
    }

    private static Diagnostic fromAbort(ExtractionAborted aborted) {
        switch (aborted.getReason()) {
            case PATH_CONDITION_TOO_LARGE:
                return diagnostic(TaskDiagnosticCodes.PC_SIZE_LIMIT, messageDetail(aborted));
            case SEARCH_DEPTH_LIMIT:
                return diagnostic(TaskDiagnosticCodes.SEARCH_DEPTH_LIMIT, messageDetail(aborted));
            case EXECUTION_TIMEOUT:
                return diagnostic(TaskDiagnosticCodes.EXECUTION_TIMEOUT, messageDetail(aborted));
            case NATIVE_MODEL_GAP:
                return diagnostic(TaskDiagnosticCodes.MISSING_NATIVE_PEER, messageDetail(aborted));
            default:
                return diagnostic(TaskDiagnosticCodes.LISTENER_BUG, messageDetail(aborted));
        }
    }

    private static Diagnostic classifyBuildFailure(ProcessingStage stage, Throwable failure) {
        if (contains(failure, "Source option") || contains(failure, "release version") || contains(failure, "invalid target release")) {
            String code = stage == ProcessingStage.BUILD_PROJECT_GENERALIZED
                ? TaskDiagnosticCodes.GENERATED_SOURCE_LEVEL_TOO_NEW
                : TaskDiagnosticCodes.OTHER_COMPILE_FAILURE;
            return diagnostic(code, messageDetail(failure));
        }
        if (contains(failure, "does not exist") && contains(failure, "compiled path")) {
            return diagnostic(TaskDiagnosticCodes.TEST_COMPILE_OUTPUT_MISSING, messageDetail(failure));
        }
        if (contains(failure, "package ") && contains(failure, " does not exist")) {
            return diagnostic(TaskDiagnosticCodes.MISSING_DEPENDENCY, messageDetail(failure));
        }
        return diagnostic(TaskDiagnosticCodes.OTHER_COMPILE_FAILURE, messageDetail(failure));
    }

    private static Diagnostic classifyReportFailure(Throwable failure) {
        if (contains(failure, "Unable to identify test report path")) {
            return diagnostic(TaskDiagnosticCodes.MISSING_REPORT_FILE, messageDetail(failure));
        }
        if (contains(failure, "Failed to identify matching test case report")) {
            return diagnostic(TaskDiagnosticCodes.FOUND_REPORT_NO_MATCHING_TESTCASE, messageDetail(failure));
        }
        return diagnostic(TaskDiagnosticCodes.UNSUPPORTED_REPORT_LAYOUT, messageDetail(failure));
    }

    private static Diagnostic classifyJpfUncaughtFailure(Throwable failure) {
        if (containsMockingFramework(failure)) {
            return diagnostic(TaskDiagnosticCodes.UNSUPPORTED_MOCKING, messageDetail(failure));
        }
        String exceptionType = jpfUncaughtExceptionType(failure);
        if ("java.lang.UnsatisfiedLinkError".equals(exceptionType)) {
            return diagnostic(TaskDiagnosticCodes.MISSING_NATIVE_PEER, messageDetail(failure));
        }
        if ("java.lang.ClassNotFoundException".equals(exceptionType)) {
            return diagnostic(TaskDiagnosticCodes.MISSING_JPF_MODEL_CLASS, messageDetail(failure));
        }
        if ("java.lang.NoSuchMethodError".equals(exceptionType)) {
            return diagnostic(TaskDiagnosticCodes.MISSING_JPF_MODEL_METHOD, messageDetail(failure));
        }
        if ("java.lang.AssertionError".equals(exceptionType)
            || "org.junit.ComparisonFailure".equals(exceptionType)) {
            return diagnostic(TaskDiagnosticCodes.JPF_DIVERGENT_ASSERTION, messageDetail(failure));
        }
        return diagnostic(TaskDiagnosticCodes.UNCAUGHT_EXCEPTION_PATH, messageDetail(failure));
    }

    private static boolean containsMockingFramework(Throwable failure) {
        return Stream.of("org.mockito.", "org.powermock.", "org.easymock.", "org.jmock.", "mockit.")
            .anyMatch(prefix -> contains(failure, prefix));
    }

    private static String jpfUncaughtExceptionType(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String exceptionType = jpfUncaughtExceptionType(current.getMessage());
            if (!exceptionType.isEmpty()) {
                return exceptionType;
            }
        }
        return "";
    }

    private static String jpfUncaughtExceptionType(String message) {
        if (message == null) {
            return "";
        }
        int markerIndex = message.indexOf(NO_UNCAUGHT_EXCEPTIONS_PROPERTY);
        if (markerIndex < 0) {
            return "";
        }
        int lineStart = markerIndex + NO_UNCAUGHT_EXCEPTIONS_PROPERTY.length();
        while (lineStart < message.length()) {
            int lineEnd = message.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = message.length();
            }
            String line = message.substring(lineStart, lineEnd).trim();
            if (!line.isEmpty() && !"--".equals(line)) {
                return exceptionTypeFromLine(line);
            }
            lineStart = lineEnd + 1;
        }
        return "";
    }

    private static String exceptionTypeFromLine(String line) {
        int end = line.length();
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == ':' || Character.isWhitespace(value)) {
                end = i;
                break;
            }
        }
        return end == 0 ? "" : line.substring(0, end);
    }

    private static Diagnostic unsupportedTerm(String detail) {
        JsonObject json = new JsonObject();
        json.addProperty("outcome", ExtractionOutcome.Kind.UNSUPPORTED_TERM.name());
        json.addProperty("detail", detail == null ? "" : detail.trim());
        return diagnostic(TaskDiagnosticCodes.UNSUPPORTED_BYTECODE, json.toString());
    }

    private static String outcomeDetail(ExtractionOutcome outcome) {
        JsonObject json = new JsonObject();
        json.addProperty("outcome", outcome.getKind().name());
        json.addProperty("detail", outcome.getDetail());
        return json.toString();
    }

    private static String messageDetail(Throwable failure) {
        JsonObject json = new JsonObject();
        json.addProperty("throwable_type", failure.getClass().getName());
        json.addProperty("message", failure.getMessage());
        return json.toString();
    }

    private static Diagnostic diagnostic(String reasonCode, String detailJson) {
        return new Diagnostic(reasonCode, detailJson);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof JPFListenerException || current.getClass() == RuntimeException.class) {
            if (current.getCause() == null) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }

    private static boolean contains(Throwable failure, String token) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String afterToken(String message, String token) {
        int index = message.indexOf(token);
        if (index < 0) {
            return message;
        }
        String tail = message.substring(index + token.length());
        return tail.replaceFirst("^[\\s:.-]+", "");
    }

    public static final class Diagnostic {
        private final String reasonCode;
        private final String detailJson;

        private Diagnostic(String reasonCode, String detailJson) {
            this.reasonCode = reasonCode;
            this.detailJson = detailJson;
        }

        public String reasonCode() {
            return this.reasonCode;
        }

        public String detailJson() {
            return this.detailJson;
        }
    }
}
