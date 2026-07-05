package teralizer.verification.stringparse;

public class StringParseCut {
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
