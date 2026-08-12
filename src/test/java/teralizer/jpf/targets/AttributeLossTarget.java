package teralizer.jpf.targets;

/**
 * JPF driver for the attribute-loss spike. Each wrapper is an instrumented method carrying the
 * generalized parameter, and it returns the value produced by one {@link AttributeLossCut} shape.
 */
public final class AttributeLossTarget {

    private AttributeLossTarget() {
    }

    public static void main(String[] args) {
        literalDirectWrapper(3);
        comparisonDirectWrapper(3);
        comparisonViaLocalWrapper(3);
        arithmeticWrapper(3);
        arithmeticViaLocalWrapper(3);
        fieldRoundTripWrapper(3);
        arrayElementWrapper(3);
        loopAccumulatorWrapper(3);
        arrayIndexedByInputWrapper(1);
    }

    public static int literalDirectWrapper(int value) {
        return AttributeLossCut.literalDirect(value);
    }

    public static boolean comparisonDirectWrapper(int value) {
        return AttributeLossCut.comparisonDirect(value);
    }

    public static boolean comparisonViaLocalWrapper(int value) {
        return AttributeLossCut.comparisonViaLocal(value);
    }

    public static int arithmeticWrapper(int value) {
        return AttributeLossCut.arithmetic(value);
    }

    public static int arithmeticViaLocalWrapper(int value) {
        return AttributeLossCut.arithmeticViaLocal(value);
    }

    public static int fieldRoundTripWrapper(int value) {
        return AttributeLossCut.fieldRoundTrip(value);
    }

    public static int arrayElementWrapper(int value) {
        return AttributeLossCut.arrayElement(value);
    }

    public static int loopAccumulatorWrapper(int value) {
        return AttributeLossCut.loopAccumulator(value);
    }

    public static int arrayIndexedByInputWrapper(int value) {
        return AttributeLossCut.arrayIndexedByInput(value);
    }
}
