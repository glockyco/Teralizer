package teralizer.jpf.targets;

public final class StringParseTarget {

    private StringParseTarget() {
    }

    public static void main(String[] args) {
        parsingSeedWrapper("42");
        failingSeedWrapper("nope");
    }

    public static int parsingSeedWrapper(String s) {
        return parseThenDouble(s);
    }

    public static int failingSeedWrapper(String s) {
        return parseOrDefault(s);
    }

    public static int parseThenDouble(String s) {
        return Integer.parseInt(s) * 2;
    }

    public static int parseOrDefault(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
