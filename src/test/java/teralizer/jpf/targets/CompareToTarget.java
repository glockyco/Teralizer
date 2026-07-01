package teralizer.jpf.targets;

/**
 * Target exercising an unsupported symbolic String operation ({@code compareTo}) so the pipeline's
 * handling of an operation absent from {@code SymbolicStringHandler} can be pinned.
 */
public final class CompareToTarget {

    private CompareToTarget() {
    }

    public static void main(String[] args) {
        wrapper("a");
    }

    public static int wrapper(String value) {
        return Cut.compareToBranch(value);
    }
}
