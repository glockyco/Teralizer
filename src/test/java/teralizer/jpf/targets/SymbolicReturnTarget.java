package teralizer.jpf.targets;

/**
 * JPF target for symbolic-return capture: {@code main} seeds the wrapper with {@code 5}, which
 * calls {@link Cut#twice(int)}. When the harness marks the wrapper argument {@code sym}, the return
 * value carries a symbolic {@code Expression}, so the listener must read it from the return slot's
 * attribute (the {@code getReturnAttr} path) while the concrete return remains {@code 10}.
 */
public final class SymbolicReturnTarget {

    private SymbolicReturnTarget() {
    }

    public static void main(String[] args) {
        wrapper(5);
    }

    public static int wrapper(int value) {
        return Cut.twice(value);
    }
}
