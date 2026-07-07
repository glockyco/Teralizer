package teralizer.jpf.targets;

public final class StringLengthTarget {

    private StringLengthTarget() {
    }

    public static void main(String[] args) {
        emptyWrapper("");
        nonEmptyWrapper("Peter");
    }

    public static int emptyWrapper(String value) {
        return Cut.lengthZeroBranch(value);
    }

    public static int nonEmptyWrapper(String value) {
        return Cut.lengthZeroBranch(value);
    }
}
