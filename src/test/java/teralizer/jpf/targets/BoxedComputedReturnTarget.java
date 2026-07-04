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
        integerObjectWrapper(6);
        booleanWrapper(true);
        allocatedBooleanWrapper(true);
        allocatedBooleanIdentityWrapper(true);
        concreteLongWrapper(5L);
    }

    public static Long longWrapper(long value) {
        return Cut.boxedLongPlusOne(value);
    }

    public static Long allocatedLongWrapper(long value) {
        return Cut.boxedLongPlusOneAllocated(value);
    }

    public static Integer integerCacheWrapper(int value) {
        return Cut.boxedIntegerPlusOne(value);
    }

    public static Object integerObjectWrapper(int value) {
        return Cut.boxedIntegerPlusOne(value);
    }

    public static Integer integerOutsideCacheWrapper(int value) {
        return Cut.boxedIntegerPlusOne(value);
    }

    public static Boolean booleanWrapper(boolean value) {
        return Cut.boxedBooleanNot(value);
    }

    public static Boolean allocatedBooleanWrapper(boolean value) {
        return Cut.boxedBooleanNotAllocated(value);
    }

    public static Boolean allocatedBooleanIdentityWrapper(boolean value) {
        return Cut.boxedBooleanIdentityAllocated(value);
    }

    public static Long concreteLongWrapper(long value) {
        return Cut.boxedConcreteLong(value);
    }
}
