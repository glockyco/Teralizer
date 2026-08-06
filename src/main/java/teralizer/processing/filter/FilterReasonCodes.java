package teralizer.processing.filter;

public final class FilterReasonCodes {

    public static final String ASSERTION_IN_LOOP = "ASSERTION_IN_LOOP";
    public static final String ASSERTION_IN_METHOD = "ASSERTION_IN_METHOD";

    public static final String DEPENDS_ON_EXCLUDED_ASSERTION = "EXCLUDED_ASSERTION";
    public static final String DEPENDS_ON_EXCLUDED_TEST = "EXCLUDED_TEST";
    public static final String DEPENDS_ON_MISSING_MUT = "MISSING_MUT";
    public static final String DEPENDS_ON_UNSUPPORTED_ASSERTION = "UNSUPPORTED_ASSERTION";

    public static final String UNCOMPILABLE_INSTRUMENTED_WRAPPER = "UNCOMPILABLE_INSTRUMENTED_WRAPPER";
    public static final String UNCOMPILABLE_GENERALIZED_TEST = "UNCOMPILABLE_GENERALIZED_TEST";

    public static final String EXCLUDED_PARENT_ASSERTION = "EXCLUDED_PARENT_ASSERTION";
    public static final String EXCLUDED_PARENT_TEST = "EXCLUDED_PARENT_TEST";
    public static final String MISSING_TESTED_CLASS = "MISSING_TESTED_CLASS";
    public static final String MISSING_TESTED_FILE = "MISSING_TESTED_FILE";
    public static final String MISSING_TESTED_METHOD = "MISSING_TESTED_METHOD";
    public static final String MISSING_TESTED_PARAMS = "MISSING_TESTED_PARAMS";
    public static final String MISSING_ASSERTION_PATH = "MISSING_ASSERTION_PATH";
    public static final String MISSING_TESTED_METHOD_CALL_PATH = "MISSING_TESTED_METHOD_CALL_PATH";
    // Literals match mut_resolution_observation.no_pick_reason so the analysis can
    // group filter reasons and resolver observations without a translation table.
    public static final String MUT_LIBRARY_DECLARATION = "LIBRARY_DECLARATION";
    public static final String MUT_UNRESOLVED_SOURCE_DECLARATION = "UNRESOLVED_SOURCE_DECLARATION";
    public static final String MUT_NO_VISIBLE_CALL = "NO_VISIBLE_CALL";
    public static final String MUT_UNSUPPORTED_ASSERTION_SHAPE = "UNSUPPORTED_ASSERTION_SHAPE";
    public static final String MUT_RESOLUTION_NOT_RECORDED = "RESOLUTION_NOT_RECORDED";
    public static final String NESTED_CLASSES = "NESTED_CLASSES";
    public static final String NO_ASSERTIONS = "NO_ASSERTIONS";
    public static final String NO_GENERALIZABLE_PARAMETERS = "NO_GENERALIZABLE_PARAMETERS";
    public static final String STATIC_INITIALIZERS_PRESENT = "STATIC_INITIALIZERS_PRESENT";
    public static final String TEST_NOT_PASSING = "TEST_NOT_PASSING";
    public static final String TESTED_METHOD_IN_LOOP = "TESTED_METHOD_IN_LOOP";
    public static final String UNNAMED_PACKAGE = "UNNAMED_PACKAGE";
    public static final String UNSUPPORTED_ASSERTION_ASSERT_NOT_NULL = "UNSUPPORTED_ASSERTION_ASSERT_NOT_NULL";
    public static final String UNSUPPORTED_ASSERTION_ASSERT_THAT = "UNSUPPORTED_ASSERTION_ASSERT_THAT";
    public static final String UNSUPPORTED_ASSERTION_FAIL = "UNSUPPORTED_ASSERTION_FAIL";
    public static final String UNSUPPORTED_ASSERTION_OTHER = "UNSUPPORTED_ASSERTION_OTHER";
    public static final String UNSUPPORTED_MOCKING = "UNSUPPORTED_MOCKING";
    public static final String UNSUPPORTED_PARAMETER_TYPE = "UNSUPPORTED_PARAMETER_TYPE";
    public static final String UNSUPPORTED_RETURN_TYPE = "UNSUPPORTED_RETURN_TYPE";
    public static final String UNSUPPORTED_STRING_OPERATION = "UNSUPPORTED_STRING_OPERATION";
    public static final String UNSUPPORTED_TEST_TYPE = "UNSUPPORTED_TEST_TYPE";
    public static final String UNSUPPORTED_FOREIGN_FRAMEWORK = "UNSUPPORTED_FOREIGN_FRAMEWORK";
    public static final String DISABLED_TEST = "DISABLED_TEST";

    private FilterReasonCodes() {
    }

    public static String unsupportedAssertion(String assertionName) {
        if ("assertNotNull".equals(assertionName)) {
            return UNSUPPORTED_ASSERTION_ASSERT_NOT_NULL;
        }
        if ("fail".equals(assertionName)) {
            return UNSUPPORTED_ASSERTION_FAIL;
        }
        if ("assertThat".equals(assertionName)) {
            return UNSUPPORTED_ASSERTION_ASSERT_THAT;
        }
        return UNSUPPORTED_ASSERTION_OTHER;
    }
}
