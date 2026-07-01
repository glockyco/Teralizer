package teralizer.jpf.targets;

/**
 * Methods under test for the in-process JPF specification-extraction harness
 * ({@link teralizer.jpf.JpfListenerHarness}). These are plain Java classes that JPF loads and
 * executes as the "tested method" of a scenario; they are not JUnit tests and carry no test
 * annotations, so the platform never runs them directly.
 */
public final class Cut {

    private Cut() {
    }

    /**
     * Boxed {@link Integer} in, boxed {@link Integer} out. Exercises both boxed-argument capture
     * (at the instrumented wrapper that calls this method) and boxed-return capture (at this
     * method's {@code ARETURN} exit).
     */
    public static Integer boxedIdentity(Integer value) {
        return value;
    }

    /** Boxed {@link Boolean} in, negated boxed {@link Boolean} out — the boolean rendering branch. */
    public static Boolean boxedNegate(Boolean value) {
        return !value;
    }

    /** Boxed {@link Character} in and out — the char branch, recorded as its integer code point. */
    public static Character boxedChar(Character value) {
        return value;
    }

    /** Boxed {@link Integer} in, {@link String} out — exercises String return capture via asString(). */
    public static String describe(Integer value) {
        return "n=" + value;
    }

    /**
     * Throws an exception that is caught internally, then returns normally. The output
     * specification must be the return value, not the handled exception — {@code methodExited}
     * sees an ordinary return instruction, so the listener classifies it as a normal return.
     */
    public static int catchesInternally(int value) {
        try {
            throw new IllegalStateException("handled internally");
        } catch (IllegalStateException caught) {
            return value;
        }
    }

    /**
     * Symbolic int in, symbolic int out: when the argument is made symbolic, the return value
     * carries a symbolic {@code Expression} attribute. Exercises the symbolic-return capture path —
     * the {@code getReturnAttr} read in {@code writeSpecificationFiles}.
     */
    public static int twice(int value) {
        return value + value;
    }

    /**
     * Recursive tested method. {@code triangular(3)} returns 6 via {@code 3 + triangular(2)}; the
     * inner frames return 3 / 1 / 0. Frame-identity detection must capture the <em>outermost</em>
     * frame's return (6), not the first (innermost) exit (0).
     */
    public static int triangular(int n) {
        if (n <= 0) {
            return 0;
        }
        return n + triangular(n - 1);
    }

    /**
     * Primitive {@code boolean} in and out. JPF supplies a boolean return (via {@code ireturn}) as an
     * {@link Integer} {@code 0}/{@code 1} but a boolean argument as a host {@link Boolean}, so capture
     * must coerce both forms to a typed boolean.
     */
    public static boolean negate(boolean value) {
        return !value;
    }

    /**
     * Symbolic {@link String} in: branches on equality, so the concrete path's condition carries a
     * String constraint ({@code s.equals("foo")}). Exercises String path-condition capture.
     */
    public static int equalsBranch(String s) {
        if (s.equals("foo")) {
            return 1;
        }
        return 0;
    }

    /**
     * Symbolic {@link String} in that calls {@code compareTo} — an operation SymbolicStringHandler
     * does not implement. Used to exercise the unsupported-op exclusion path.
     */
    public static int compareToBranch(String s) {
        if (s.compareTo("m") < 0) {
            return 1;
        }
        return 0;
    }
}
