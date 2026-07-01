package teralizer.jpf.targets;

/**
 * JPF target for symbolic {@link String} {@code isEmpty()} capture: the wrapper's {@link String}
 * parameter is made symbolic (with {@code symbolic.strings} on) and the tested method branches on
 * {@link Cut#isEmptyBranch(String)}. The empty seed takes the {@code isEmpty}-true branch; the
 * non-empty seed takes the false branch.
 */
public final class StringIsEmptyTarget {

    private StringIsEmptyTarget() {
    }

    public static void main(String[] args) {
        emptyWrapper("");
        nonEmptyWrapper("x");
    }

    public static int emptyWrapper(String value) {
        return Cut.isEmptyBranch(value);
    }

    public static int nonEmptyWrapper(String value) {
        return Cut.isEmptyBranch(value);
    }
}
