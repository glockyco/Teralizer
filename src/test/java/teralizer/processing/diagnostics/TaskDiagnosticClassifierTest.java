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
    void mapsJpfUncaughtUnsatisfiedLinkErrorToMissingNativePeer() {
        String message = jpfUncaughtMessage(
            "java.lang.UnsatisfiedLinkError: cannot find native java.lang.reflect.Method.getGenericParameterTypes"
                + "\n\tat java.lang.reflect.Method.getGenericParameterTypes(...)");

        Assert.assertEquals(TaskDiagnosticCodes.MISSING_NATIVE_PEER, classify(new RuntimeException(message)));
    }

    @Example
    void mapsMockingFrameworkFailuresToUnsupportedMocking() {
        String message = jpfUncaughtMessage(
            "java.lang.UnsupportedOperationException\n"
                + "\tat java.lang.Class.getProtectionDomain(Class.java:341)\n"
                + "\tat org.mockito.cglib.core.ReflectUtils.<clinit>(ReflectUtils.java:41)");

        Assert.assertEquals(TaskDiagnosticCodes.UNSUPPORTED_MOCKING, classify(new RuntimeException(message)));
    }

    @Example
    void keepsOtherJpfUncaughtExceptionTypesOnUncaughtExceptionPath() {
        String message = jpfUncaughtMessage("org.jsoup.helper.ValidationException: String must not be empty");

        Assert.assertEquals(TaskDiagnosticCodes.UNCAUGHT_EXCEPTION_PATH, classify(new RuntimeException(message)));
    }

    @Example
    void mapsJpfUncaughtAssertionFailuresToDivergentAssertion() {
        String assertionMessage = jpfUncaughtMessage("java.lang.AssertionError: expected:<3> but was:<4>");
        String comparisonMessage = jpfUncaughtMessage("org.junit.ComparisonFailure: expected:<left> but was:<right>");

        Assert.assertEquals(TaskDiagnosticCodes.JPF_DIVERGENT_ASSERTION, classify(new RuntimeException(assertionMessage)));
        Assert.assertEquals(TaskDiagnosticCodes.JPF_DIVERGENT_ASSERTION, classify(new RuntimeException(comparisonMessage)));
    }

    @Example
    void mapsJpfModelExceptionTypesToModelGapCodes() {
        String classMessage = jpfUncaughtMessage(
            "java.lang.ClassNotFoundException: class not found: javax.xml.bind.DatatypeConverter");
        String methodMessage = jpfUncaughtMessage(
            "java.lang.NoSuchMethodError: javax.xml.bind.DatatypeConverter.parseBase64Binary(Ljava/lang/String;)[B");

        Assert.assertEquals(TaskDiagnosticCodes.MISSING_JPF_MODEL_CLASS, classify(new RuntimeException(classMessage)));
        Assert.assertEquals(TaskDiagnosticCodes.MISSING_JPF_MODEL_METHOD, classify(new RuntimeException(methodMessage)));
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

    @Example
    void mapsGeneralizedSuiteTimeoutToDistinctStableCode() {
        RuntimeException timeout = new RuntimeException("Command execution timeout exceeded.");

        Assert.assertEquals(TaskDiagnosticCodes.SUITE_TIMEOUT,
            TaskDiagnosticClassifier.classify(ProcessingStage.EXECUTE_TESTS_GENERALIZED, timeout).reasonCode());
    }

    @Example
    void mapsMutationAndCoverageCollectionTimeoutsToExecutionTimeout() {
        RuntimeException timeout = new RuntimeException("Command execution timeout exceeded.");

        Assert.assertEquals(TaskDiagnosticCodes.EXECUTION_TIMEOUT,
            TaskDiagnosticClassifier.classify(ProcessingStage.COLLECT_PIT_DATA_INITIAL, timeout).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.EXECUTION_TIMEOUT,
            TaskDiagnosticClassifier.classify(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED, timeout).reasonCode());
        Assert.assertEquals(TaskDiagnosticCodes.EXECUTION_TIMEOUT,
            TaskDiagnosticClassifier.classify(ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED, timeout).reasonCode());
    }

    @Example
    void keepsPitCollectionNonTimeoutFailuresOnNonTimeoutPath() {
        RuntimeException failure = new RuntimeException("boom");

        String reasonCode = TaskDiagnosticClassifier.classify(ProcessingStage.COLLECT_PIT_DATA_INITIAL, failure).reasonCode();

        Assert.assertNotEquals(TaskDiagnosticCodes.EXECUTION_TIMEOUT, reasonCode);
        Assert.assertNotEquals(TaskDiagnosticCodes.SUITE_TIMEOUT, reasonCode);
    }

    private static String jpfUncaughtMessage(String exceptionBlock) {
        return "Identified 1 error(s) during JPF execution.\n\n--\n\n"
            + "gov.nasa.jpf.vm.NoUncaughtExceptionsProperty\n\n"
            + exceptionBlock;
    }

    private static String classify(Throwable failure) {
        return TaskDiagnosticClassifier.classify(ProcessingStage.EXECUTE_JPF, failure).reasonCode();
    }
}
