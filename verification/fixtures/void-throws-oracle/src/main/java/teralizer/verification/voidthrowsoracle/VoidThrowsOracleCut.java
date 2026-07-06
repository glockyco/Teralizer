package teralizer.verification.voidthrowsoracle;

public class VoidThrowsOracleCut {
    public void requireNegative(int value) {
        if (value >= 0) {
            throw new IllegalArgumentException("nonnegative input");
        }
    }
}
