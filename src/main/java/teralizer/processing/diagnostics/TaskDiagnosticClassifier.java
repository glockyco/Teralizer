package teralizer.processing.diagnostics;

import com.google.gson.JsonObject;
import gov.nasa.jpf.JPFListenerException;
import gov.nasa.jpf.JPFNativePeerException;
import teralizer.jpf.ExtractionAborted;
import teralizer.jpf.ExtractionOutcome;
import teralizer.processing.ProcessingStage;
import teralizer.transformer.UnsupportedSpfTermException;

public final class TaskDiagnosticClassifier {

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
            return diagnostic(TaskDiagnosticCodes.UNCAUGHT_EXCEPTION_PATH, messageDetail(failure));
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
