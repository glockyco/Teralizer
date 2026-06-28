package teralizer.spoon.analysis;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import teralizer.util.SpfSymbolicConfig;

public class SpfSymbolicConfigSelectorTest {

    @Example
    void selectsRawBitsForDoubleToRawLongBits() {
        CtMethod<?> method = methodFromSource(
            "class Subject {"
                + "  public static boolean almostEqual(double x, double y, int maxUlps) {"
                + "    long xi = Double.doubleToRawLongBits(x);"
                + "    long yi = Double.doubleToRawLongBits(y);"
                + "    return Math.abs(xi - yi) <= maxUlps;"
                + "  }"
                + "}",
            "almostEqual");

        SpfSymbolicConfig config = SpfSymbolicConfigSelector.select(method);

        Assert.assertEquals("z3bitvector", config.getDp());
        Assert.assertTrue(config.isFp());
        Assert.assertEquals(64, config.getBvLength());
    }

    @Example
    void selectsRawBitsForLongBitsToDouble() {
        CtMethod<?> method = methodFromSource(
            "class Subject {"
                + "  public static double fromBits(long bits) {"
                + "    return Double.longBitsToDouble(bits);"
                + "  }"
                + "}",
            "fromBits");

        Assert.assertEquals("z3bitvector", SpfSymbolicConfigSelector.select(method).getDp());
    }

    @Example
    void selectsDefaultForPlainArithmetic() {
        CtMethod<?> method = methodFromSource(
            "class Subject {"
                + "  public static int add(int a, int b) { return a + b; }"
                + "}",
            "add");

        SpfSymbolicConfig config = SpfSymbolicConfigSelector.select(method);

        Assert.assertEquals("z3", config.getDp());
        Assert.assertFalse(config.isFp());
        Assert.assertEquals(32, config.getBvLength());
    }

    @Example
    void selectsDefaultForOrdinaryDoubleComparison() {
        CtMethod<?> method = methodFromSource(
            "class Subject {"
                + "  public static boolean within(double x, double y, double eps) {"
                + "    return Math.abs(y - x) <= eps;"
                + "  }"
                + "}",
            "within");

        Assert.assertEquals("z3", SpfSymbolicConfigSelector.select(method).getDp());
    }

    private static CtMethod<?> methodFromSource(String source, String methodName) {
        CtClass<?> subject = Launcher.parseClass(source);
        return subject.getMethodsByName(methodName).get(0);
    }
}
