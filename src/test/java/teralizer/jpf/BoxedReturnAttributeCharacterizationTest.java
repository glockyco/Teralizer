package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;

/** Characterizes where SPF keeps attrs for boxed primitive returns at ARETURN. */
class BoxedReturnAttributeCharacterizationTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "BoxedComputedReturnTarget";

    @Example
    void longValueOfDoesNotExposeThePrimitiveExpression() throws IOException {
        Observation observation = observe("longWrapper", "Cut.boxedLongPlusOne");

        print("boxed Long.valueOf", observation);
        Assert.assertNull("Long.valueOf return reference attr", observation.returnAttr);
        Assert.assertNull("Long.valueOf value field attr", observation.valueFieldAttr);
    }

    @Example
    void allocatedLongKeepsExpressionOnBoxValueFieldNotReturnReference() throws IOException {
        Observation observation = observe("allocatedLongWrapper", "Cut.boxedLongPlusOneAllocated");

        print("allocated boxed Long", observation);
        Assert.assertNull("allocated Long return reference attr", observation.returnAttr);
        Assert.assertNotNull("allocated Long value field attr", observation.valueFieldAttr);
    }

    @Example
    void integerCacheAutoboxingKeepsExpressionOnBoxValueField() throws IOException {
        Observation observation = observe("integerCacheWrapper", "Cut.boxedIntegerPlusOne");

        print("boxed Integer cache", observation);
        Assert.assertNull("cache-range Integer return reference attr", observation.returnAttr);
        Assert.assertNotNull("cache-range Integer value field attr", observation.valueFieldAttr);
    }

    @Example
    void integerOutsideCacheAutoboxingKeepsExpressionOnBoxValueField() throws IOException {
        Observation observation = observe("integerOutsideCacheWrapper", "Cut.boxedIntegerPlusOne");

        print("boxed Integer outside-cache", observation);
        Assert.assertNull("outside-cache Integer return reference attr", observation.returnAttr);
        Assert.assertNotNull("outside-cache Integer value field attr", observation.valueFieldAttr);
    }

    @Example
    void booleanValueOfDoesNotExposeThePrimitiveExpression() throws IOException {
        Observation observation = observe("booleanWrapper", "Cut.boxedBooleanNot");

        print("boxed Boolean.valueOf", observation);
        Assert.assertNull("Boolean.valueOf return reference attr", observation.returnAttr);
        Assert.assertNull("Boolean.valueOf value field attr", observation.valueFieldAttr);
    }

    @Example
    void allocatedBooleanIdentityKeepsExpressionOnBoxValueField() throws IOException {
        Observation observation = observe("allocatedBooleanIdentityWrapper", "Cut.boxedBooleanIdentityAllocated");

        print("allocated boxed Boolean identity", observation);
        Assert.assertNull("allocated Boolean return reference attr", observation.returnAttr);
        Assert.assertNotNull("allocated Boolean value field attr", observation.valueFieldAttr);
    }

    @Example
    void allocatedBooleanNegationDoesNotExposeThePrimitiveExpression() throws IOException {
        Observation observation = observe("allocatedBooleanWrapper", "Cut.boxedBooleanNotAllocated");

        print("allocated boxed Boolean negation", observation);
        Assert.assertNull("allocated Boolean negation return reference attr", observation.returnAttr);
        Assert.assertNull("allocated Boolean negation value field attr", observation.valueFieldAttr);
    }

    private static Observation observe(String wrapper, String testedMethod) throws IOException {
        Path workDir = Files.createTempDirectory("boxed-return-attrs");
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            TARGET,
            TARGET + "." + wrapper + "(sym)",
            TARGET + "." + wrapper,
            PKG + testedMethod,
            false
        );
        JPF jpf = new JPF(config);
        BoxedReturnObserver observer = new BoxedReturnObserver(TARGET + "." + wrapper, PKG + testedMethod);
        jpf.addListener(observer);
        jpf.run();
        if (jpf.foundErrors()) {
            Assert.fail("JPF reported errors for " + testedMethod + ": " + jpf.getSearchErrors());
        }
        Assert.assertTrue("observer should see tested method exit", observer.observed);
        return new Observation(observer.returnAttr, observer.valueFieldAttr);
    }

    private static void print(String label, Observation observation) {
        System.out.println(label + " return ref attr=" + observation.returnAttr
            + ", value field attr=" + observation.valueFieldAttr);
    }

    private static final class BoxedReturnObserver extends PropertyListenerAdapter {
        private final MethodSpec instrumentedMethodSpec;
        private final MethodSpec testedMethodSpec;
        private boolean active;
        private boolean observed;
        private Expression returnAttr;
        private Expression valueFieldAttr;

        private BoxedReturnObserver(String instrumentedMethod, String testedMethod) {
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
                Assert.assertTrue("boxed return should exit through a return instruction",
                    instruction instanceof JVMReturnInstruction);
                JVMReturnInstruction returnInstruction = (JVMReturnInstruction) instruction;
                this.returnAttr = returnInstruction.getReturnAttr(currentThread, Expression.class);
                Object returnValue = returnInstruction.getReturnValue(currentThread);
                Assert.assertTrue("boxed return should be a JPF heap object", returnValue instanceof ElementInfo);
                ElementInfo elementInfo = (ElementInfo) returnValue;
                FieldInfo valueField = elementInfo.getClassInfo().getInstanceField("value");
                Assert.assertNotNull("boxed primitive should declare a value field", valueField);
                this.valueFieldAttr = elementInfo.getFieldAttr(valueField, Expression.class);
                this.observed = true;
                vm.getSearch().terminate();
            }
            if (this.instrumentedMethodSpec.matches(exitedMethod)) {
                this.active = false;
            }
        }

        @Override
        public void searchFinished(Search search) {
            Assert.assertTrue("JPF should terminate after observing the boxed return", this.observed);
        }
    }

    private static final class Observation {
        private final Expression returnAttr;
        private final Expression valueFieldAttr;

        private Observation(Expression returnAttr, Expression valueFieldAttr) {
            this.returnAttr = returnAttr;
            this.valueFieldAttr = valueFieldAttr;
        }
    }
}
