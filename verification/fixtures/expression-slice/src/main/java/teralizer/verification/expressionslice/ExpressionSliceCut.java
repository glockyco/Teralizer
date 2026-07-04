package teralizer.verification.expressionslice;

public final class ExpressionSliceCut {
    private ExpressionSliceCut() {
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static int timesTwo(int x) {
        return x * 2;
    }

    public static int intCompare(int x, int y) {
        if (x < y) {
            return -1;
        }
        if (x > y) {
            return 1;
        }
        return 0;
    }

    public static java.util.List<Integer> buildList(int n) {
        java.util.List<Integer> xs = new java.util.ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            xs.add(Integer.valueOf(i));
        }
        return xs;
    }
}
