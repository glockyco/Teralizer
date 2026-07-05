package teralizer.jpf.targets;

/** Fixture for boxed Long valueOf calls that immediately unbox to primitive returns. */
public final class BoxedUnboxRoundTripTarget {

    private BoxedUnboxRoundTripTarget() {
    }

    public static void main(String[] args) {
        intRoundTripWrapper(5L);
        intValueRoundTripWrapper(6L);
        longRoundTripWrapper(7L);
    }

    public static int intRoundTripWrapper(long value) {
        return intRoundTrip(value);
    }

    public static int intValueRoundTripWrapper(long value) {
        return intValueRoundTrip(value);
    }

    public static long longRoundTripWrapper(long value) {
        return longRoundTrip(value);
    }

    public static int intRoundTrip(long value) {
        Long boxed = Long.valueOf(value);
        return (int) boxed.longValue();
    }

    public static int intValueRoundTrip(long value) {
        Long boxed = Long.valueOf(value);
        return boxed.intValue();
    }

    public static long longRoundTrip(long value) {
        Long boxed = Long.valueOf(value);
        return boxed.longValue();
    }
}
