package teralizer.jpf.targets;

/**
 * JPF targets for expression-recipe listener capture. The instrumented methods are wrapper-shaped:
 * they compute an oracle expression around helper calls, while tests choose one focal helper as the
 * tested method whose entry remains telemetry only in expression mode.
 */
public final class ExpressionWrapperTarget {

    private ExpressionWrapperTarget() {
    }

    public static void main(String[] args) {
        comparisonWrapper(3, 1);
        shortCircuitWrapper(1, -5);
    }

    public static boolean comparisonWrapper(int a, int b) {
        return helperA(a) > helperB(b);
    }

    public static boolean shortCircuitWrapper(int a, int b) {
        return a > 0 || skippedHelper(b) > 0;
    }

    public static int helperA(int value) {
        return value + 1;
    }

    public static int helperB(int value) {
        return value + 2;
    }

    public static int skippedHelper(int value) {
        return value + 1;
    }
}
