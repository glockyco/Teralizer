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

    @Example
    void mapsBuildCompilerFailuresToStableCodes() {
        RuntimeException sourceLevel = new RuntimeException("Source option 5 is no longer supported. Use 7 or later.");
        RuntimeException missingDependency = new RuntimeException("package org.example.missing does not exist");
        RuntimeException missingOutput = new RuntimeException("Test compiled path 'target/test-classes' does not exist.");

        Assert.assertEquals(TaskDiagnosticCodes.GENERATED_SOURCE_LEVEL_TOO_NEW,
            TaskDiagnosticClassifier.classify(ProcessingStage.BUILD_PROJECT_GENERALIZED, sourceLevel).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.MISSING_DEPENDENCY,
            TaskDiagnosticClassifier.classify(ProcessingStage.BUILD_PROJECT_INSTRUMENTED, missingDependency).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.TEST_COMPILE_OUTPUT_MISSING,
            TaskDiagnosticClassifier.classify(ProcessingStage.BUILD_PROJECT_INSTRUMENTED, missingOutput).reasonCode());
    }

    @Example
    void mapsReportLookupFailuresToStableCodes() {
        RuntimeException missingReport = new RuntimeException(
            "Unable to identify test report path for test class: smoke.SubjectTest. No file at default path a or alternative path b.");
        RuntimeException noTestcase = new RuntimeException(
            "Failed to identify matching test case report for smoke.SubjectTest.t in TEST-smoke.SubjectTest.xml.");

        Assert.assertEquals(TaskDiagnosticCodes.MISSING_REPORT_FILE,
            TaskDiagnosticClassifier.classify(ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED, missingReport).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.FOUND_REPORT_NO_MATCHING_TESTCASE,
            TaskDiagnosticClassifier.classify(ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL, noTestcase).reasonCode());
    }

    private static String classify(Throwable failure) {
        return TaskDiagnosticClassifier.classify(ProcessingStage.EXECUTE_JPF, failure).reasonCode();
    }
}
