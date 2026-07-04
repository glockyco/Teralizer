package teralizer.jpf.targets;

/**
 * JPF target driver for the boxed-wrapper capture scenario. {@code main} invokes the instrumented
 * wrapper {@link #wrapper(Integer)} with a concrete seed; the wrapper calls the tested method
 * {@link Cut#boxedIdentity(Integer)}.
 *
 * <p>This mirrors the production driver/instrumented-method structure: the instrumented method is a
 * wrapper carrying the generalized parameters (here a boxed {@link Integer}), so the listener
 * captures the boxed argument at wrapper entry and the boxed return at {@code boxedIdentity} exit.
 * The wrapper is {@code static} so the captured argument list is exactly the declared parameters
 * (no implicit {@code this}).
 */
public final class BoxedIdentityTarget {

    private BoxedIdentityTarget() {
    }

    public static void main(String[] args) {
        wrapper(7);
    }

    public static Integer wrapper(Integer value) {
        return Cut.boxedIdentity(value);
    }
}
