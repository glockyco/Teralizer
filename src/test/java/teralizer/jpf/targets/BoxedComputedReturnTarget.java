package teralizer.jpf.targets;

/** Fixture for boxed-primitive returns whose primitive value depends on a symbolic input. */
public final class BoxedComputedReturnTarget {

    private BoxedComputedReturnTarget() {
    }

    public static void main(String[] args) {
        longWrapper(5L);
        allocatedLongWrapper(5L);
        integerCacheWrapper(6);
        integerOutsideCacheWrapper(127);
        booleanWrapper(true);
        allocatedBooleanWrapper(true);
        allocatedBooleanIdentityWrapper(true);
        concreteLongWrapper(5L);
    }

    public static void longWrapper(long value) {
        Cut.boxedLongPlusOne(value);
    }

    public static void allocatedLongWrapper(long value) {
        Cut.boxedLongPlusOneAllocated(value);
    }

    public static void integerCacheWrapper(int value) {
        Cut.boxedIntegerPlusOne(value);
    }

    public static void integerOutsideCacheWrapper(int value) {
        Cut.boxedIntegerPlusOne(value);
    }

    public static void booleanWrapper(boolean value) {
        Cut.boxedBooleanNot(value);
    }

    public static void allocatedBooleanWrapper(boolean value) {
        Cut.boxedBooleanNotAllocated(value);
    }

    public static void allocatedBooleanIdentityWrapper(boolean value) {
        Cut.boxedBooleanIdentityAllocated(value);
    }

    public static void concreteLongWrapper(long value) {
        Cut.boxedConcreteLong(value);
    }
}
