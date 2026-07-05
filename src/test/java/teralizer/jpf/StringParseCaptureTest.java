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
    void parsingSeedProducesTypedUnsupportedTermOrExtractedSpec() throws IOException {
        ParseCaptureOutcome outcome = captureParseThenDouble();

        if (outcome.unsupportedDetail != null) {
            Assert.assertTrue(
                "ISINTEGER should be refused as a typed unsupported term, was: " + outcome.unsupportedDetail,
                outcome.unsupportedDetail.contains("isinteger")
            );
            return;
        }

        PrimitiveValue value = (PrimitiveValue) outcome.capture.getOutput().getReturnValue();
        Assert.assertEquals("int", value.getJavaType());
        Assert.assertEquals("the parsing seed returns the doubled concrete value", 84, value.getValue());
        Assert.assertNotNull("the parse constraint must be captured if ingestion admits it",
            outcome.capture.getInputSpecificationJson());
    }

    @Example
    void failingSeedCaughtNumberFormatExceptionCompletes() throws IOException {
        ReturnObservation observation = observeParseOrDefaultReturn();

        Assert.assertEquals("the failing seed should be caught by the target", -1, observation.returnValue);
    }

    private static ParseCaptureOutcome captureParseThenDouble() throws IOException {
        Path workDir = Files.createTempDirectory("string-parse-capture");
        try {
            JpfListenerHarness.Capture capture = JpfListenerHarness.run(
                workDir,
                TARGET,
                TARGET + ".parsingSeedWrapper(sym)",
                TARGET + ".parsingSeedWrapper",
                TARGET + ".parseThenDouble"
            );
            return ParseCaptureOutcome.extracted(capture);
        } catch (JPFListenerException e) {
            if (e.getCause() instanceof UnsupportedSpfTermException) {
                return ParseCaptureOutcome.unsupported(e.getCause().getMessage());
            }
            throw e;
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

    private static final class ParseCaptureOutcome {
        private final JpfListenerHarness.Capture capture;
        private final String unsupportedDetail;

        private ParseCaptureOutcome(JpfListenerHarness.Capture capture, String unsupportedDetail) {
            this.capture = capture;
            this.unsupportedDetail = unsupportedDetail;
        }

        private static ParseCaptureOutcome extracted(JpfListenerHarness.Capture capture) {
            return new ParseCaptureOutcome(capture, null);
        }

        private static ParseCaptureOutcome unsupported(String detail) {
            return new ParseCaptureOutcome(null, detail);
        }
    }

    private static final class ReturnObservation {
        private final int returnValue;

        private ReturnObservation(int returnValue) {
            this.returnValue = returnValue;
        }
    }
}
