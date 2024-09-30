package teralizer.util;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JUnit5ExecutionLogger implements TestExecutionListener {

    public static final String TOOL_NAME = "Teralizer";
    private static final Logger LOGGER = Logger.getLogger(JUnit5ExecutionLogger.class.getName());

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            LOGGER.log(Level.INFO, "[" + TOOL_NAME + "] Executing test: " + getDescription(testIdentifier));
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            LOGGER.log(Level.INFO, "[" + TOOL_NAME + "] Finished test: " + getDescription(testIdentifier));
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (testIdentifier.isTest()) {
            LOGGER.log(Level.INFO, "[" + TOOL_NAME + "] Skipped test: " + getDescription(testIdentifier));
        }
    }

    private static String getDescription(TestIdentifier testIdentifier) {
        return testIdentifier.getSource().map(Object::toString).orElse(testIdentifier.getDisplayName());
    }
}
