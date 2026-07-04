package teralizer.jpf.targets;

/**
 * JPF target for {@link String} return capture: {@code main} seeds the wrapper with {@code 7},
 * which calls {@link Cut#describe(Integer)} (returns {@code "n=7"}). Exercises the String return
 * path (captured via {@code ElementInfo.asString()}) alongside a boxed {@link Integer} argument.
 */
public final class StringReturnTarget {

    private StringReturnTarget() {
    }

    public static void main(String[] args) {
        wrapper(7);
    }

    public static String wrapper(Integer value) {
        return Cut.describe(value);
    }
}
