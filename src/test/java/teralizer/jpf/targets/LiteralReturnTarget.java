package teralizer.jpf.targets;

/** Driver wrappers for literal-return telemetry scenarios. */
public final class LiteralReturnTarget {

    private LiteralReturnTarget() {
    }

    public static void main(String[] args) {
        wrapper(5);
        literalIntWrapper(5);
        fieldBooleanWrapper(5);
        divideWrapper(5);
    }

    public static boolean wrapper(int value) {
        return Cut.literalBoolean(value);
    }

    public static int literalIntWrapper(int value) {
        return Cut.literalInt(value);
    }

    public static boolean fieldBooleanWrapper(int value) {
        return Cut.fieldBoolean(value);
    }

    public static int divideWrapper(int value) {
        return Cut.divide(value);
    }
}
