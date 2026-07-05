package teralizer.processing.diagnostics;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.jpf.ExtractionAborted;
import teralizer.jpf.ExtractionOutcome;
import teralizer.processing.ProcessingStage;
import teralizer.transformer.UnsupportedSpfTermException;

public class TaskDiagnosticClassifierTest {

    @Example
    void mapsExtractionAbortedReasonsToStableCodes() {
        Assert.assertEquals(TaskDiagnosticCodes.PC_SIZE_LIMIT,
            classify(new ExtractionAborted(ExtractionAborted.Reason.PATH_CONDITION_TOO_LARGE, "pc")));
        Assert.assertEquals(TaskDiagnosticCodes.SEARCH_DEPTH_LIMIT,
            classify(new ExtractionAborted(ExtractionAborted.Reason.SEARCH_DEPTH_LIMIT, "depth")));
        Assert.assertEquals(TaskDiagnosticCodes.MISSING_NATIVE_PEER,
            classify(new ExtractionAborted(ExtractionAborted.Reason.NATIVE_MODEL_GAP, "native")));
    }

    @Example
    void mapsExtractionOutcomeKindsToSpecificationGaps() {
        Assert.assertEquals(TaskDiagnosticCodes.NO_INPUT_SPEC,
            TaskDiagnosticClassifier.fromOutcome(ExtractionOutcome.fromState(false, false, true)).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.NO_OUTPUT_SPEC,
            TaskDiagnosticClassifier.fromOutcome(ExtractionOutcome.fromState(true, false, true)).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.UNSUPPORTED_BYTECODE,
            TaskDiagnosticClassifier.fromOutcome(ExtractionOutcome.unsupportedTerm("SpecialIntegerExpression"))
                .reasonCode());
    }

    @Example
    void mapsUnsupportedTermExceptionWrappedByJpf() {
        RuntimeException failure = new RuntimeException(new UnsupportedSpfTermException("SpecialIntegerExpression"));

        TaskDiagnosticClassifier.Diagnostic diagnostic = TaskDiagnosticClassifier.classify(ProcessingStage.EXECUTE_JPF, failure);

        Assert.assertEquals(TaskDiagnosticCodes.UNSUPPORTED_BYTECODE, diagnostic.reasonCode());
        Assert.assertTrue(diagnostic.detailJson().contains("SpecialIntegerExpression"));
    }

    private static String classify(Throwable failure) {
        return TaskDiagnosticClassifier.classify(ProcessingStage.EXECUTE_JPF, failure).reasonCode();
    }
}
