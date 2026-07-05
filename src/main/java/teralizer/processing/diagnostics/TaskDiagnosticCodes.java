package teralizer.processing.diagnostics;

public final class TaskDiagnosticCodes {

    public static final String EXECUTION_TIMEOUT = "EXECUTION_TIMEOUT";
    public static final String LISTENER_BUG = "LISTENER_BUG";
    public static final String MISSING_JPF_MODEL_CLASS = "MISSING_JPF_MODEL_CLASS";
    public static final String MISSING_JPF_MODEL_METHOD = "MISSING_JPF_MODEL_METHOD";
    public static final String MISSING_NATIVE_PEER = "MISSING_NATIVE_PEER";
    public static final String NO_INPUT_SPEC = "NO_INPUT_SPEC";
    public static final String NO_OUTPUT_SPEC = "NO_OUTPUT_SPEC";
    public static final String PC_SIZE_LIMIT = "PC_SIZE_LIMIT";
    public static final String SEARCH_DEPTH_LIMIT = "SEARCH_DEPTH_LIMIT";
    public static final String UNCAUGHT_EXCEPTION_PATH = "UNCAUGHT_EXCEPTION_PATH";
    public static final String UNSUPPORTED_BYTECODE = "UNSUPPORTED_BYTECODE";

    private TaskDiagnosticCodes() {
    }
}
