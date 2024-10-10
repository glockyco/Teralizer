package teralizer.util;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JUnit5ExecutionLogger implements TestExecutionListener {

    public static final String TOOL_NAME = "Teralizer";
    private static final Logger LOGGER = Logger.getLogger(JUnit5ExecutionLogger.class.getName());

    public static long totalTestCount = 0;
    public static long executedTestCount = 0;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        totalTestCount = testPlan.countTestIdentifiers(TestIdentifier::isTest);
        LOGGER.log(Level.INFO, "Identified number of tests: " + totalTestCount + ".");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        totalTestCount = 0;
        executedTestCount = 0;
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            executedTestCount++;
            LOGGER.log(Level.INFO, "[" + TOOL_NAME + "] Executing test (" + executedTestCount + " of " + totalTestCount + "): " + getDescription(testIdentifier));
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            LOGGER.log(Level.INFO, "[" + TOOL_NAME + "] Finished test (" + executedTestCount + " of " + totalTestCount + "): " + getDescription(testIdentifier));
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (testIdentifier.isTest()) {
            executedTestCount++;
            LOGGER.log(Level.INFO, "[" + TOOL_NAME + "] Skipped test (" + executedTestCount + " of " + totalTestCount + "): " + getDescription(testIdentifier));
        }
    }

    private static String getDescription(TestIdentifier testIdentifier) {
        return testIdentifier.getSource().map(Object::toString).orElse(testIdentifier.getDisplayName());
    }
}
