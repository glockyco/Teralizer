package teralizer.jpf.targets;

/**
 * JPF target for recursion-aware frame identity: {@code main} seeds {@code wrapper(3)}, which calls
 * the recursive {@link Cut#triangular(int)}. {@code triangular(3)} recurses to depth 3 (returning
 * 6) while inner frames return 3 / 1 / 0. The spec-extraction observer must capture the outermost
 * frame's return (6), proving frame identity is by stack position, not "first exit" (which is the
 * innermost, 0).
 */
public final class RecursiveSumTarget {

    private RecursiveSumTarget() {
    }

    public static void main(String[] args) {
        wrapper(3);
    }

    public static int wrapper(int n) {
        return Cut.triangular(n);
    }
}
