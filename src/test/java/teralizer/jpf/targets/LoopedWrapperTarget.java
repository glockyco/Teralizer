package teralizer.jpf.targets;

/**
 * JPF target for the first-invocation-only contract under a looped wrapper — the reachable shape of
 * the commons-lang {@code isAscii} loop, where instrumentation routes one tested call through a
 * wrapper that the test method invokes once per iteration. The observer must capture the first
 * wrapper invocation's tested call ({@code twice(7) == 14}) and terminate the search before the
 * later iterations ({@code twice(8)}, {@code twice(9)}) run.
 */
public final class LoopedWrapperTarget {

    private LoopedWrapperTarget() {
    }

    public static void main(String[] args) {
        for (int value = 7; value <= 9; value++) {
            wrapper(value);
        }
    }

    public static int wrapper(int value) {
        return Cut.twice(value);
    }
}
