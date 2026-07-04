package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.Model;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.Value;
import teralizer.transformer.VariableNameCollector;

class TestGeneralizationListenerExpressionModeTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "ExpressionWrapperTarget";

    @Test
    void expressionModeCapturesCompositeExpressionAtWrapperExit(@TempDir Path workDir) {
        TestGeneralizationListener listener = runExpressionMode(workDir, "comparisonWrapper", "helperA");

        assertTrue(listener.wasTargetEntered(), "the focal helper is entered on the concrete path");
        Invocation invocation = listener.getInvocation();
        assertNotNull(invocation, "expression mode captures at wrapper exit");

        Value output = invocation.getOutput().getReturnValue();
        assertEquals("boolean", output.getJavaType(), "the captured output is the wrapper expression result");
        assertEquals(Boolean.TRUE, ((PrimitiveValue) output).getValue(), "3+1 > 1+2 for the seed");

        Set<String> variables = collectVariables(invocation.getModelInput());
        assertEquals(2, variables.size(),
            "the wrapper-exit path condition must cover both helper inputs, not only the first helper");
    }

    @Test
    void expressionModeExtractsWhenFocalHelperIsShortCircuitedPast(@TempDir Path workDir) {
        TestGeneralizationListener listener = runExpressionMode(workDir, "shortCircuitWrapper", "skippedHelper");

        assertFalse(listener.wasTargetEntered(), "the focal helper is skipped by Java short-circuiting");
        Invocation invocation = listener.getInvocation();
        assertNotNull(invocation, "expression mode still captures at wrapper exit");

        Value output = invocation.getOutput().getReturnValue();
        assertEquals("boolean", output.getJavaType(), "the captured output is the wrapper expression result");
        assertEquals(Boolean.TRUE, ((PrimitiveValue) output).getValue(), "the left side makes the expression true");
        assertEquals(ExtractionOutcome.Kind.EXTRACTED,
            ExtractionOutcome.fromState(listener.wasTargetEntered(), invocation != null, true).getKind(),
            "target-not-entered is an observation, not a failure, for expression recipes");
    }

    private static TestGeneralizationListener runExpressionMode(
        Path workDir,
        String instrumentedMethodName,
        String testedMethodName
    ) {
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            TARGET,
            TARGET + "." + instrumentedMethodName + "(sym#sym)",
            TARGET + "." + instrumentedMethodName,
            TARGET + "." + testedMethodName,
            false
        );
        config.setProperty("test_generalization.expression_recipe", "true");
        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);
        jpf.run();

        if (jpf.foundErrors()) {
            throw new AssertionError("JPF reported errors: " + jpf.getSearchErrors());
        }
        assertTrue(jpf.getVM().isInitialized(), "JPF VM initialized");
        return listener;
    }

    private static Set<String> collectVariables(Model model) {
        assertNotNull(model, "the wrapper-exit input model must not be null");
        Set<String> names = new LinkedHashSet<>();
        model.accept(new VariableNameCollector(names));
        return names;
    }
}
