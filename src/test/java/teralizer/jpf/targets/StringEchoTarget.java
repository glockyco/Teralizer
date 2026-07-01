package teralizer.jpf.targets;

/**
 * JPF target for symbolic {@link String} return capture: {@code main} seeds the wrapper with
 * {@code "foo"}, made symbolic (with {@code symbolic.strings} on), and the wrapper returns the
 * value produced by the tested method (identity {@link Cut#echo(String)} or derived
 * {@link Cut#concatTail(String)}).
 */
public final class StringEchoTarget {

    private StringEchoTarget() {
    }

    public static void main(String[] args) {
        echoWrapper("foo");
        concatWrapper("foo");
        trimWrapper(" foo ");
        lowerWrapper("FOO");
        upperWrapper("foo");
        replaceWrapper("foo");
    }

    public static String echoWrapper(String value) {
        return Cut.echo(value);
    }

    public static String concatWrapper(String value) {
        return Cut.concatTail(value);
    }

    public static String trimWrapper(String value) {
        return Cut.trimmed(value);
    }

    public static String lowerWrapper(String value) {
        return Cut.lowered(value);
    }

    public static String upperWrapper(String value) {
        return Cut.uppered(value);
    }

    public static String replaceWrapper(String value) {
        return Cut.replaced(value);
    }
}
