package teralizer.verification.thrownoracle;

public class ThrownOracleCut {
    public int cleanThrow(int value) {
        if (value >= 0) {
            throw new IllegalArgumentException("nonnegative");
        }
        return value;
    }

    public int concretizedThrow(String label, int count) {
        int[] source = new int[] {7, 8};
        int[] destination = new int[] {0, 0};
        System.arraycopy(source, 0, destination, 0, count);
        if (label.length() >= 0 && destination[0] == 7) {
            throw new IllegalStateException("copied");
        }
        return 0;
    }
}
