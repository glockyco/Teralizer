package teralizer.verification.booleaninpc;

public class BooleanInPcCut {
    public boolean computedEquality(int left, int right) {
        return left == right;
    }

    public boolean passThrough(boolean value) {
        Holder holder = new Holder(value);
        return holder.stored;
    }

    public Boolean boxedPassThrough(boolean value) {
        return Boolean.valueOf(value);
    }

    private static final class Holder {
        private final boolean stored;

        private Holder(boolean stored) {
            this.stored = stored;
        }
    }
}
