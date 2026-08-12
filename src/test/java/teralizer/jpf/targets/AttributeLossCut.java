package teralizer.jpf.targets;

/**
 * Methods under test for the attribute-loss spike. Each one is a return shape that
 * {@code WideningLicense} may refuse, isolated so the harness can report the output-spec class and
 * the literal flag per shape. Plain Java, no test annotations, loaded and executed by JPF.
 */
public final class AttributeLossCut {

    private AttributeLossCut() {
    }

    /** Holder for the field round trip. */
    public static final class Holder {
        public int value;
    }

    /** Control: a bytecode literal reaches the return directly. */
    public static int literalDirect(int value) {
        return 7;
    }

    /** A comparison result reaches the return directly, as {@code ICONST}. */
    public static boolean comparisonDirect(int value) {
        return value > 0;
    }

    /** The same comparison, parked in a local first, so {@code ILOAD} reaches the return. */
    public static boolean comparisonViaLocal(int value) {
        boolean result = value > 0;
        return result;
    }

    /** Arithmetic on the symbolic input: the return should carry an expression. */
    public static int arithmetic(int value) {
        return value + 1;
    }

    /** The same arithmetic through a local, to test whether a local store drops the attribute. */
    public static int arithmeticViaLocal(int value) {
        int result = value + 1;
        return result;
    }

    /** Store into a field and read it back. */
    public static int fieldRoundTrip(int value) {
        Holder holder = new Holder();
        holder.value = value + 1;
        return holder.value;
    }

    /** Store into an array and read it back. */
    public static int arrayElement(int value) {
        int[] cell = new int[] {value + 1};
        return cell[0];
    }

    /**
     * Read an array at an index taken from the symbolic input. This is the shape the
     * {@code symarrays} instruction classes exist for: with {@code symbolic.arrays} off the index is
     * the concrete seed value, and with it on the index itself becomes a choice.
     */
    public static int arrayIndexedByInput(int value) {
        int[] cells = new int[] {10, 20, 30};
        if (value < 0 || value > 2) {
            return -1;
        }
        return cells[value];
    }

    /** Accumulate over a trip count that depends on the input. */
    public static int loopAccumulator(int value) {
        int sum = 0;
        for (int i = 0; i < value; i++) {
            sum += i;
        }
        return sum;
    }
}
