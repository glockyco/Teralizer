package teralizer.jpf.targets;

/** JPF target whose instrumented wrapper exits by propagating the tested method's exception. */
public final class ThrowingWrapperTarget {

    private ThrowingWrapperTarget() {
    }

    public static void main(String[] args) {
        try {
            wrapper(5);
        } catch (IllegalArgumentException expected) {
            // The wrapper still exits exceptionally; catching here only keeps JPF from reporting an
            // uncaught main-level exception after the listener captures the wrapper exit.
        }
    }

    public static int wrapper(int value) {
        return Cut.alwaysThrows(value);
    }
}
