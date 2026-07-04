package teralizer.spike.r1viability;

public final class R1WrapperCut {
    private R1WrapperCut() {
    }

    private static int timesTwo(int x) {
        return x * 2;
    }

    private static int intCompare(int x, int y) {
        if (x < y) {
            return -1;
        }
        if (x > y) {
            return 1;
        }
        return 0;
    }

    private static java.util.List<Integer> buildList(int n) {
        java.util.List<Integer> xs = new java.util.ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            xs.add(Integer.valueOf(i));
        }
        return xs;
    }

    public static int direct(int a, int b) {
        return a + b;
    }

    public static int chainProjectInspector(int a) {
        return Box.of(a).value();
    }

    public static int chainLibrarySize(int n) {
        return buildList(n).size();
    }

    public static boolean operatorCompositeCalls(int a, int b) {
        return intCompare(a, b) > 0;
    }

    public static boolean compareToComparison(int a) {
        return new Pair(a).compareTo(new Pair(5)) < 0;
    }

    public static boolean ctorOnlyEquality(int a, int b) {
        return new Pair(a, b).equalsPair(new Pair(a, 5));
    }

    public static long castWrappedCall(int a) {
        return (long) timesTwo(a);
    }

    public static int arithmeticComposite(int a, int b) {
        return timesTwo(a) + timesTwo(b);
    }

    public static int chainTwoHops(int a) {
        return Box.of(a).twice().value();
    }
}
