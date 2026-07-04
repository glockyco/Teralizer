package teralizer.jpf.targets;

/**
 * JPF target for boxed {@link Boolean} capture: {@code main} seeds the wrapper with {@code true},
 * which calls {@link Cut#boxedNegate(Boolean)}. The argument ({@code true}) and the return
 * ({@code false}) are captured independently, exercising the boolean rendering branch.
 */
public final class BoxedBooleanTarget {

    private BoxedBooleanTarget() {
    }

    public static void main(String[] args) {
        wrapper(true);
    }

    public static Boolean wrapper(Boolean value) {
        return Cut.boxedNegate(value);
    }
}
