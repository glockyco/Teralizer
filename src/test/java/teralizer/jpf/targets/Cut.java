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
}
