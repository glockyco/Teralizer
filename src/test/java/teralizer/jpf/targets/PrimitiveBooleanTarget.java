package teralizer.jpf.targets;

/**
 * JPF target for primitive {@code boolean} capture: {@code main} seeds the wrapper with {@code true},
 * which calls {@link Cut#negate(boolean)}. JPF supplies the boolean argument as a host {@link Boolean}
 * but the boolean return (via {@code ireturn}) as an {@link Integer} {@code 0}/{@code 1}, so this
 * exercises that capture coerces both forms to a typed boolean value.
 */
public final class PrimitiveBooleanTarget {

    private PrimitiveBooleanTarget() {
    }

    public static void main(String[] args) {
        wrapper(true);
    }

    public static boolean wrapper(boolean value) {
        return Cut.negate(value);
    }
}
