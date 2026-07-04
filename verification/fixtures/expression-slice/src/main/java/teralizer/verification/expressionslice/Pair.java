package teralizer.verification.expressionslice;

public final class Pair {
    private final int a;
    private final int b;

    public Pair(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public Pair(int a) {
        this(a, 0);
    }

    public int compareTo(Pair other) {
        if (a < other.a) {
            return -1;
        }
        if (a > other.a) {
            return 1;
        }
        return 0;
    }

    public boolean equalsPair(Pair other) {
        return a == other.a && b == other.b;
    }
}
