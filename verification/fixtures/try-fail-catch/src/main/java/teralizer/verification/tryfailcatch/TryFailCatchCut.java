package teralizer.verification.tryfailcatch;

public class TryFailCatchCut {
    public int rejectNonnegative(int value) {
        if (value >= 0) {
            throw new IllegalArgumentException("nonnegative input");
        }
        return value;
    }

    public int rejectWithMessage(int value) {
        if (value >= 0) {
            throw new IllegalStateException("constant message");
        }
        return value;
    }
}
