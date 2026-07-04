package teralizer.jpf.targets;

/**
 * JPF target for the caught-internally scenario: {@code main} seeds the wrapper with {@code 5},
 * which calls {@link Cut#catchesInternally(int)}. That method throws and catches an exception
 * internally, then returns the value, so the listener must record the return value rather than the
 * handled exception.
 */
public final class CatchesInternallyTarget {

    private CatchesInternallyTarget() {
    }

    public static void main(String[] args) {
        wrapper(5);
    }

    public static int wrapper(int value) {
        return Cut.catchesInternally(value);
    }
}
