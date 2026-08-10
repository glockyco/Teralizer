package teralizer.verification.literalreturns;

public class LiteralReturnsCut {
    public boolean computedBoolean(int value) {
        if (value > 0) {
            return true;
        }
        return false;
    }

    public int literalInt(int value) {
        if (value > 0) {
            return 42;
        }
        return 7;
    }

    private boolean stored;

    public boolean fieldBoolean(int value) {
        stored = value > 0;
        return stored;
    }

    public int arithmetic(int value) {
        return value / 20;
    }
}
