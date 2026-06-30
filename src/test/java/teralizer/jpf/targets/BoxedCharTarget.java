package teralizer.jpf.targets;

/**
 * JPF target for boxed {@link Character} capture: {@code main} seeds the wrapper with {@code 'A'},
 * which calls {@link Cut#boxedChar(Character)}. Both the argument and return are recorded as the
 * integer code point ({@code 65}), the form {@code ModelToJavaTransformer} renders as {@code (char) 65}.
 */
public final class BoxedCharTarget {

    private BoxedCharTarget() {
    }

    public static void main(String[] args) {
        wrapper('A');
    }

    public static void wrapper(Character value) {
        Cut.boxedChar(value);
    }
}
