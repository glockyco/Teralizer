package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFListenerException;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.PrimitiveValue;
import teralizer.transformer.UnsupportedSpfTermException;

class StringParseCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "StringParseTarget";

    @Example
    void integerParseableSeedCapturesIsIntegerPredicate() throws IOException {
        JpfListenerHarness.Capture capture = capture("integerParseableSeedWrapper", "isIntegerParseable");

        assertBooleanReturn(capture, true, "integer parseable seed returns the parseable branch");
        assertParsePredicate(capture, "isInteger", false);
    }

    @Example
    void integerUnparseableSeedCapturesNegatedIsIntegerPredicate() throws IOException {
        JpfListenerHarness.Capture capture = capture("integerUnparseableSeedWrapper", "isIntegerParseable");

        assertBooleanReturn(capture, false, "integer unparseable seed returns the unparseable branch");
        assertParsePredicate(capture, "isInteger", true);
    }

    @Example
    void doubleParseableSeedCapturesIsDoublePredicate() throws IOException {
        JpfListenerHarness.Capture capture = capture("doubleParseableSeedWrapper", "isDoubleParseable");

        assertBooleanReturn(capture, true, "double parseable seed returns the parseable branch");
        assertParsePredicate(capture, "isDouble", false);
    }

    @Example
    void doubleUnparseableSeedCapturesNegatedIsDoublePredicate() throws IOException {
        JpfListenerHarness.Capture capture = capture("doubleUnparseableSeedWrapper", "isDoubleParseable");

        assertBooleanReturn(capture, false, "double unparseable seed returns the unparseable branch");
        assertParsePredicate(capture, "isDouble", true);
    }

    @Example
    void parsedIntegerValueUseStillRaisesTypedUnsupportedTerm() throws IOException {
        try {
            capture("parsingSeedWrapper", "parseThenDouble");
            Assert.fail("parsed-value dataflow should remain refused as SpecialIntegerExpression");
        } catch (JPFListenerException expected) {
            Assert.assertTrue(expected.getCause() instanceof UnsupportedSpfTermException);
            Assert.assertTrue(
                "parsed-value dataflow should surface the special integer expression, was: " + expected.getCause().getMessage(),
                expected.getCause().getMessage().contains("SpecialIntegerExpression"));
        }
    }

    @Example
    void failingSeedCaughtNumberFormatExceptionCompletes() throws IOException {
        ReturnObservation observation = observeParseOrDefaultReturn();

        Assert.assertEquals("the failing seed should be caught by the target", -1, observation.returnValue);
    }

    private static JpfListenerHarness.Capture capture(String wrapper, String testedMethod) throws IOException {
        Path workDir = Files.createTempDirectory("string-parse-capture");
        return JpfListenerHarness.run(
            workDir,
            TARGET,
            TARGET + "." + wrapper + "(sym)",
            TARGET + "." + wrapper,
            TARGET + "." + testedMethod
        );
    }

    private static void assertBooleanReturn(
        JpfListenerHarness.Capture capture,
        boolean expected,
        String message
    ) {
        PrimitiveValue value = (PrimitiveValue) capture.getOutput().getReturnValue();
        Assert.assertEquals("boolean", value.getJavaType());
        Assert.assertEquals(message, Boolean.valueOf(expected), value.getValue());
    }

    private static void assertParsePredicate(
        JpfListenerHarness.Capture capture,
        String method,
        boolean negated
    ) {
        String spec = capture.getInputSpecificationJson();
        Assert.assertNotNull("the parseability constraint must be captured", spec);
        Assert.assertTrue(
            "parseability must capture the static helper predicate, was: " + spec,
            spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"qualifier\": \"ParsePredicates\"")
                && spec.contains("\"method\": \"" + method + "\""));
        if (negated) {
            Assert.assertTrue("unparseable branch must wrap the predicate in Not, was: " + spec,
                spec.contains("\"_type\": \"Not\""));
        } else {
            Assert.assertFalse("parseable branch must not be negated, was: " + spec,
                spec.contains("\"_type\": \"Not\""));
        }
    }

    private static ReturnObservation observeParseOrDefaultReturn() throws IOException {
        Path workDir = Files.createTempDirectory("string-parse-caught-nfe");
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            TARGET,
            TARGET + ".failingSeedWrapper(sym)",
            TARGET + ".failingSeedWrapper",
            TARGET + ".parseOrDefault"
        );
        JPF jpf = new JPF(config);
        ReturnObserver observer = new ReturnObserver(TARGET + ".failingSeedWrapper", TARGET + ".parseOrDefault");
        jpf.addListener(observer);
        jpf.run();
        if (jpf.foundErrors()) {
            Assert.fail("JPF reported errors: " + jpf.getSearchErrors());
        }
        Assert.assertTrue("JPF VM initialized", jpf.getVM().isInitialized());
        return new ReturnObservation(observer.returnValue);
    }

    private static final class ReturnObserver extends PropertyListenerAdapter {
        private final MethodSpec instrumentedMethodSpec;
        private final MethodSpec testedMethodSpec;
        private boolean active;
        private boolean observed;
        private int returnValue;

        private ReturnObserver(String instrumentedMethod, String testedMethod) {
            this.instrumentedMethodSpec = MethodSpec.createMethodSpec(instrumentedMethod);
            this.testedMethodSpec = MethodSpec.createMethodSpec(testedMethod);
        }

        @Override
        public void methodEntered(VM vm, ThreadInfo currentThread, MethodInfo enteredMethod) {
            if (this.instrumentedMethodSpec.matches(enteredMethod)) {
                this.active = true;
            }
        }

        @Override
        public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
            if (this.testedMethodSpec.matches(exitedMethod) && this.active && !this.observed) {
                Instruction instruction = currentThread.getPC();
                Assert.assertTrue("parseOrDefault should exit through a return instruction",
                    instruction instanceof JVMReturnInstruction);
                Object value = ((JVMReturnInstruction) instruction).getReturnValue(currentThread);
                Assert.assertTrue("parseOrDefault should return an int", value instanceof Integer);
                this.returnValue = ((Integer) value).intValue();
                this.observed = true;
                vm.getSearch().terminate();
            }
            if (this.instrumentedMethodSpec.matches(exitedMethod)) {
                this.active = false;
            }
        }

        @Override
        public void searchFinished(Search search) {
            Assert.assertTrue("JPF should observe the caught parse return", this.observed);
        }
    }


    private static final class ReturnObservation {
        private final int returnValue;

        private ReturnObservation(int returnValue) {
            this.returnValue = returnValue;
        }
    }
}
