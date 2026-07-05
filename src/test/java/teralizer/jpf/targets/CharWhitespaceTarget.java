package teralizer.jpf.targets;

/** JPF target for symbolic {@code char} predicates in constraint collection. */
public final class CharWhitespaceTarget {

    private CharWhitespaceTarget() {
    }

    public static void main(String[] args) {
        whitespaceReturnWrapper('\n');
        nonWhitespaceReturnWrapper('A');
        branchWrapper('\u001c');
        nonAsciiWrapper('\u00a0');
    }

    public static boolean whitespaceReturnWrapper(char c) {
        return isWhitespace(c);
    }

    public static boolean nonWhitespaceReturnWrapper(char c) {
        return isWhitespace(c);
    }

    public static int branchWrapper(char c) {
        return whitespaceBranch(c);
    }

    public static boolean nonAsciiWrapper(char c) {
        return isWhitespace(c);
    }

    public static boolean isWhitespace(char c) {
        return Character.isWhitespace(c);
    }

    public static int whitespaceBranch(char c) {
        if (Character.isWhitespace(c)) {
            return 1;
        }
        return 0;
    }
}
