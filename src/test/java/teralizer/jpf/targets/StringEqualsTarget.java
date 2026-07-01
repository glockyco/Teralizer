package teralizer.jpf.targets;

/**
 * JPF target for String path-condition capture: {@code main} seeds the wrapper with {@code "foo"},
 * which calls {@link Cut#equalsBranch(String)}. With the wrapper's {@link String} parameter made
 * symbolic (and {@code symbolic.strings} on), the concrete path takes the {@code s.equals("foo")}
 * branch, so the path condition carries a symbolic String equality constraint.
 */
public final class StringEqualsTarget {

    private StringEqualsTarget() {
    }

    public static void main(String[] args) {
        wrapper("foo");
    }

    public static int wrapper(String value) {
        return Cut.equalsBranch(value);
    }
}
