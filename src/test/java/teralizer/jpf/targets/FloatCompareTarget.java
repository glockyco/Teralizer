package teralizer.jpf.targets;

/**
 * JPF target for constraint collection symbolic {@code float} comparison capture. The wrappers seed both
 * concrete directions of {@link #floatExceeds(float, float)} while the harness marks the wrapper
 * arguments symbolic.
 */
public final class FloatCompareTarget {

    private FloatCompareTarget() {
    }

    public static void main(String[] args) {
        trueSeedWrapper(2.0f, 1.0f);
        falseSeedWrapper(1.0f, 2.0f);
    }

    public static boolean trueSeedWrapper(float a, float b) {
        return floatExceeds(a, b);
    }

    public static boolean falseSeedWrapper(float a, float b) {
        return floatExceeds(a, b);
    }

    public static boolean floatExceeds(float a, float b) {
        return a > b;
    }
}
