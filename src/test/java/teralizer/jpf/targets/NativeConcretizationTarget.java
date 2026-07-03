package teralizer.jpf.targets;

/** Fixture whose MUT crosses an MJI native peer with a symbolic primitive argument. */
public final class NativeConcretizationTarget {

    private NativeConcretizationTarget() {
    }

    public static void main(String[] args) {
        wrapper(1);
    }

    public static int wrapper(int count) {
        return nativeArrayCopy(count);
    }

    public static int nativeArrayCopy(int count) {
        int[] source = new int[]{7, 8};
        int[] destination = new int[]{0, 0};
        System.arraycopy(source, 0, destination, 0, count);
        return destination[0];
    }
}
