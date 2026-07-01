package teralizer.jpf.targets;

/**
 * Like {@link StringEqualsTarget} but {@code main} seeds {@code "bar"}, so the concrete path takes
 * the {@code s.equals("foo")} false branch (returns 0). The captured constraint must be the
 * negation {@code value != "foo"}, which the seed {@code "bar"} satisfies.
 */
public final class StringEqualsFalseTarget {

    private StringEqualsFalseTarget() {
    }

    public static void main(String[] args) {
        wrapper("bar");
    }

    public static int wrapper(String value) {
        return Cut.equalsBranch(value);
    }
}
