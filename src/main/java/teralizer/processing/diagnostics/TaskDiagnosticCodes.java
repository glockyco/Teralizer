package teralizer.processing.diagnostics;

public final class TaskDiagnosticCodes {

    public static final String EXECUTION_TIMEOUT = "EXECUTION_TIMEOUT";
    public static final String FOUND_REPORT_NO_MATCHING_TESTCASE = "FOUND_REPORT_NO_MATCHING_TESTCASE";
    public static final String GENERATED_SOURCE_LEVEL_TOO_NEW = "GENERATED_SOURCE_LEVEL_TOO_NEW";
    public static final String JPF_DIVERGENT_ASSERTION = "JPF_DIVERGENT_ASSERTION";
    public static final String LISTENER_BUG = "LISTENER_BUG";
    public static final String MISSING_JPF_MODEL_CLASS = "MISSING_JPF_MODEL_CLASS";
    public static final String MISSING_JPF_MODEL_METHOD = "MISSING_JPF_MODEL_METHOD";
    public static final String MISSING_DEPENDENCY = "MISSING_DEPENDENCY";
    public static final String MISSING_REPORT_FILE = "MISSING_REPORT_FILE";
    public static final String MISSING_NATIVE_PEER = "MISSING_NATIVE_PEER";
    public static final String NO_INPUT_SPEC = "NO_INPUT_SPEC";
    public static final String NO_OUTPUT_SPEC = "NO_OUTPUT_SPEC";
    public static final String OTHER_COMPILE_FAILURE = "OTHER_COMPILE_FAILURE";
    public static final String PC_SIZE_LIMIT = "PC_SIZE_LIMIT";
    public static final String SEARCH_DEPTH_LIMIT = "SEARCH_DEPTH_LIMIT";
    public static final String SUITE_TIMEOUT = "SUITE_TIMEOUT";
    public static final String UNCAUGHT_EXCEPTION_PATH = "UNCAUGHT_EXCEPTION_PATH";
    public static final String TEST_COMPILE_OUTPUT_MISSING = "TEST_COMPILE_OUTPUT_MISSING";
    public static final String UNSUPPORTED_BYTECODE = "UNSUPPORTED_BYTECODE";
    public static final String UNSUPPORTED_REPORT_LAYOUT = "UNSUPPORTED_REPORT_LAYOUT";

    private TaskDiagnosticCodes() {
    }
}
