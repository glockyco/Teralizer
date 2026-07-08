package teralizer.processing.diagnostics;

import com.google.gson.JsonObject;
import gov.nasa.jpf.JPFListenerException;
import gov.nasa.jpf.JPFNativePeerException;
import java.util.EnumSet;
import java.util.Set;
import teralizer.jpf.ExtractionAborted;
import teralizer.jpf.ExtractionOutcome;
import teralizer.processing.ProcessingStage;
import teralizer.transformer.UnsupportedSpfTermException;

public final class TaskDiagnosticClassifier {
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
        return diagnostic(TaskDiagnosticCodes.LISTENER_BUG, messageDetail(failure));
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
