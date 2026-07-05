package teralizer.jpf.targets;

public final class StringParseTarget {

    private StringParseTarget() {
    }

    public static void main(String[] args) {
        parsingSeedWrapper("42");
        failingSeedWrapper("nope");
        integerParseableSeedWrapper("42");
        integerUnparseableSeedWrapper("nope");
        doubleParseableSeedWrapper("3.5");
        doubleUnparseableSeedWrapper("nope");
    }

    public static int parsingSeedWrapper(String s) {
        return parseThenDouble(s);
    }

    public static int failingSeedWrapper(String s) {
        return parseOrDefault(s);
    }

    public static boolean integerParseableSeedWrapper(String value) {
        return isIntegerParseable(value);
    }

    public static boolean integerUnparseableSeedWrapper(String value) {
        return isIntegerParseable(value);
    }

    public static boolean doubleParseableSeedWrapper(String value) {
        return isDoubleParseable(value);
    }

    public static boolean doubleUnparseableSeedWrapper(String value) {
        return isDoubleParseable(value);
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

    public static boolean isIntegerParseable(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isDoubleParseable(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
