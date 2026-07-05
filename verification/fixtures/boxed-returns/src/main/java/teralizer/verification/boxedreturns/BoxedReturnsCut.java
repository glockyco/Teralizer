package teralizer.verification.boxedreturns;

public class BoxedReturnsCut {
    public Integer boxedInteger(int value) {
        return Integer.valueOf(value + 1);
    }

    public Long boxedLong(long value) {
        return Long.valueOf(value + 1L);
    }

    public Boolean boxedBoolean(int value) {
        return Boolean.valueOf(value > 0);
    }
}
