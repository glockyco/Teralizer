package teralizer.spoon.analysis;

public final class AssertionSemanticCodes {

    public static final String ARGUMENT_SHAPE_LITERAL = "LITERAL";
    public static final String ARGUMENT_SHAPE_METHOD_CALL = "METHOD_CALL";
    public static final String ARGUMENT_SHAPE_NONE = "NONE";
    public static final String ARGUMENT_SHAPE_OTHER = "OTHER";
    public static final String ARGUMENT_SHAPE_VARIABLE_OR_FIELD = "VARIABLE_OR_FIELD";

    public static final String ARRAY_EQUALITY = "ARRAY_EQUALITY";
    public static final String ASSERTJ_MATCHER = "ASSERTJ_MATCHER";
    public static final String BOOLEAN_FALSE = "BOOLEAN_FALSE";
    public static final String BOOLEAN_TRUE = "BOOLEAN_TRUE";
    public static final String EQUALITY = "EQUALITY";
    public static final String FAIL_SENTINEL = "FAIL_SENTINEL";
    public static final String HAMCREST_MATCHER = "HAMCREST_MATCHER";
    public static final String INEQUALITY = "INEQUALITY";
    public static final String NULLNESS_NOT_NULL = "NULLNESS_NOT_NULL";
    public static final String NULLNESS_NULL = "NULLNESS_NULL";
    public static final String SAMENESS = "SAMENESS";
    public static final String UNKNOWN = "UNKNOWN";

    public static final String FAIL_CONTEXT_CATCH_BLOCK_SHOULD_NOT_REACH = "CATCH_BLOCK_SHOULD_NOT_REACH";
    public static final String FAIL_CONTEXT_GUARD_BRANCH = "GUARD_BRANCH";
    public static final String FAIL_CONTEXT_TRY_BLOCK_EXPECTING_EXCEPTION = "TRY_BLOCK_EXPECTING_EXCEPTION";
    public static final String FAIL_CONTEXT_UNKNOWN = "UNKNOWN";

    public static final String MATCHER_FAMILY_ASSERTJ = "ASSERTJ";
    public static final String MATCHER_FAMILY_HAMCREST = "HAMCREST";

    private AssertionSemanticCodes() {
    }
}
