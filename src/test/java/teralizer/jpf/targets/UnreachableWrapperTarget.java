package teralizer.jpf.targets;

/**
 * JPF target for the unreachable-assertion case (the commons-lang {@code isAscii} dead-{@code else}
 * shape): the tested call sits in a branch the seeded concrete path never takes, so
 * {@link Cut#twice(int)} is never entered. The observer must classify this as
 * {@code TARGET_NOT_ENTERED}, not a silent "unknown reason" failure.
 */
public final class UnreachableWrapperTarget {

    private UnreachableWrapperTarget() {
    }

    public static void main(String[] args) {
        wrapper(5);
    }

    public static int wrapper(int value) {
        if (value < 128) {
            return value;             // taken for the seed (5) — the live branch
        } else {
            return Cut.twice(value);  // dead for value < 128 — tested method never entered
        }
    }
}
