package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.Value;

/**
 * Pins capture of a symbolic return value. With the argument made symbolic, the tested method's
 * return carries a symbolic {@code Expression} attribute, which {@code writeSpecificationFiles}
 * reads via {@code getReturnAttr}. The listener uses the typed overload
 * {@code getReturnAttr(ThreadInfo, Expression.class)}, so the read is robust to JPF returning an
 * {@code ObjectList} of stacked attributes (the untyped overload returns the raw slot attribute,
 * whose {@code (Expression)} cast would fail on a list or non-{@code Expression} attribute).
 */
class TestGeneralizationListenerSymbolicTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void capturesSymbolicReturnExpressionAndConcreteValue(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            workDir,
            PKG + "SymbolicReturnTarget",
            PKG + "SymbolicReturnTarget.wrapper(sym)",
            PKG + "SymbolicReturnTarget.wrapper",
            PKG + "Cut.twice"
        );

        Value output = capture.getOutput().getReturnValue();
        assertEquals("int", output.getJavaType(), "concrete return type");
        assertEquals(Integer.valueOf(10), ((PrimitiveValue) output).getValue(), "concrete return value for the seed 5");

        String outputSpecification = capture.getOutputSpecificationJson();
        assertNotNull(outputSpecification, "a symbolic return Expression must be captured");
        assertTrue(
            outputSpecification.trim().length() > 0 && !"null".equals(outputSpecification.trim()),
            "the symbolic output specification must be a non-empty Expression model, was: " + outputSpecification
        );
    }
}
