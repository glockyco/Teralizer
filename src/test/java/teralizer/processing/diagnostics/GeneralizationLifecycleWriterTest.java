package teralizer.processing.diagnostics;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;

public class GeneralizationLifecycleWriterTest {

    @Example
    void completeLifecycleIsUsableWithoutFailure() {
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            true,
            true,
            true,
            true,
            true,
            "IGNORED"
        );

        Assert.assertTrue(rollup.isFinalUsable());
        Assert.assertNull(rollup.getFinalFailureStage());
        Assert.assertNull(rollup.getFinalFailureCode());
    }

    @Example
    void firstIncompleteStageWins() {
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            true,
            false,
            false,
            false,
            false,
            TaskDiagnosticCodes.EXECUTION_TIMEOUT
        );

        Assert.assertFalse(rollup.isFinalUsable());
        Assert.assertEquals(ProcessingStage.EXECUTE_TESTS_GENERALIZED.name(), rollup.getFinalFailureStage());
        Assert.assertEquals(TaskDiagnosticCodes.EXECUTION_TIMEOUT, rollup.getFinalFailureCode());
    }

    @Example
    void filterRejectionUsesFilterReasonCode() {
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            true,
            true,
            true,
            false,
            false,
            "GENERATED_TEST_FAILED"
        );

        Assert.assertFalse(rollup.isFinalUsable());
        Assert.assertEquals(ProcessingStage.FILTER_GENERALIZATIONS.name(), rollup.getFinalFailureStage());
        Assert.assertEquals("GENERATED_TEST_FAILED", rollup.getFinalFailureCode());
    }

    @Example
    void pendingNextStageHasFailureStageWithoutCauseCode() {
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            false,
            false,
            false,
            false,
            false,
            null
        );

        Assert.assertFalse(rollup.isFinalUsable());
        Assert.assertEquals(ProcessingStage.BUILD_PROJECT_GENERALIZED.name(), rollup.getFinalFailureStage());
        Assert.assertNull(rollup.getFinalFailureCode());
    }

    @Example
    void codelessEventKeepsTheRecordedCodeForTheSameStage() {
        // A rejection recorded TEST_NOT_PASSING; a later report-collection success recomputes the
        // rollup with no code of its own. Attribution must not depend on that event order.
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            true,
            true,
            true,
            false,
            false,
            null
        );

        String retained = GeneralizationLifecycleWriter.retainedFailureCode(
            ProcessingStage.FILTER_GENERALIZATIONS.name(),
            "TEST_NOT_PASSING",
            rollup
        );

        Assert.assertEquals("TEST_NOT_PASSING", retained);
    }

    @Example
    void codelessEventDropsACodeRecordedForADifferentStage() {
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            false,
            false,
            false,
            false,
            false,
            null
        );

        String retained = GeneralizationLifecycleWriter.retainedFailureCode(
            ProcessingStage.FILTER_GENERALIZATIONS.name(),
            "TEST_NOT_PASSING",
            rollup
        );

        Assert.assertNull(retained);
    }

    @Example
    void anEventWithItsOwnCodeOverwritesTheStoredOne() {
        GeneralizationLifecycleWriter.Rollup rollup = GeneralizationLifecycleWriter.deriveRollup(
            true,
            true,
            true,
            true,
            false,
            false,
            "EXCLUDED_ASSERTION"
        );

        String retained = GeneralizationLifecycleWriter.retainedFailureCode(
            ProcessingStage.FILTER_GENERALIZATIONS.name(),
            "TEST_NOT_PASSING",
            rollup
        );

        Assert.assertEquals("EXCLUDED_ASSERTION", retained);
    }
}
