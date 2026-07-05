package teralizer.jpf.targets;

public final class DivergenceRiskTarget {

    private DivergenceRiskTarget() {
    }

    public static void main(String[] args) {
        try {
            straightLineMessageWrapper(5);
        } catch (IllegalArgumentException expected) {
        }
        try {
            concreteBranchWrapper(5);
        } catch (IllegalArgumentException expected) {
        }
        try {
            nativeThrowWrapper(3);
        } catch (RuntimeException expected) {
        }
        try {
            symbolicBranchWrapper(5);
        } catch (IllegalArgumentException expected) {
        }
    }

    public static int straightLineMessageWrapper(int value) {
        return straightLineMessage(value);
    }

    public static int concreteBranchWrapper(int value) {
        return concreteBranchAfterMessage(value);
    }

    public static int nativeThrowWrapper(int count) {
        return nativeThrow(count);
    }

    public static int symbolicBranchWrapper(int value) {
        return symbolicBranchAfterMessage(value);
    }

    public static int straightLineMessage(int value) {
        if (value == 5) {
            throw new IllegalArgumentException("boom " + value);
        }
        return value;
    }

    public static int concreteBranchAfterMessage(int value) {
        String message = "boom " + value;
        if (System.identityHashCode(message) != 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static int nativeThrow(int count) {
        int[] source = new int[]{7, 8};
        int[] destination = new int[]{0, 0};
        System.arraycopy(source, 0, destination, 0, count);
        return destination[0];
    }

    public static int symbolicBranchAfterMessage(int value) {
        String message = "boom " + value;
        if (value == 5) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
