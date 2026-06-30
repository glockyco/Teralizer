package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.*;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.domain.CapturedException;
import teralizer.domain.MethodArgument;
import teralizer.transformer.SpfToModelTransformer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestGeneralizationListener extends PropertyListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGeneralizationListener.class);

    private final String instrumentedMethodQualifiedName;
    private final MethodSpec instrumentedMethodSpec;
    private final MethodSpec testedMethodSpec;

    private final Path inputValuesPath;
    private final Path outputValuePath;
    private final Path inputSpecificationPath;
    private final Path outputSpecificationPath;

    private final double maxExecutionTime;
    private final long maxPathConditionSize;

    private long startTime;

    private int recursionDepth;
    private boolean isInInstrumentedMethod;
    private CapturedException pendingThrownException;
    private List<MethodArgument> instrumentedInputArguments;

    public TestGeneralizationListener(Config config) {
        this.instrumentedMethodQualifiedName = config.getString("test_generalization.instrumented_method");
        this.instrumentedMethodSpec = MethodSpec.createMethodSpec(this.instrumentedMethodQualifiedName);
        this.testedMethodSpec = MethodSpec.createMethodSpec(config.getString("test_generalization.tested_method"));
        this.inputValuesPath = Paths.get(config.getString("test_generalization.input_values_path"));
        this.outputValuePath = Paths.get(config.getString("test_generalization.output_value_path"));
        this.inputSpecificationPath = Paths.get(config.getString("test_generalization.input_specification_path"));
        this.outputSpecificationPath = Paths.get(config.getString("test_generalization.output_specification_path"));
        this.maxExecutionTime = config.getDouble("test_generalization.max_execution_time");
        this.maxPathConditionSize = config.getLong("test_generalization.max_path_condition_size");
    }

    @Override
    public void searchStarted(Search search) {
        this.startTime = System.currentTimeMillis();
        this.recursionDepth = -1;
        this.isInInstrumentedMethod = false;
        this.pendingThrownException = null;
    }

    @Override
    public void searchConstraintHit(Search search) {
        if (search.getDepth() >= search.getDepthLimit()) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - Failed to collect input/output specification due to depth limiting. Depth limit of " + search.getDepthLimit() + " exceeded.");
        }
    }

    @Override
    public void propertyViolated(Search search) {
        String errorDetails = search.getLastError().getDetails();
        if (errorDetails.contains("java.lang.NullPointerException") && errorDetails.contains("at java.util.concurrent.atomic")) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - Failed JPF execution due to incomplete native peers.\n\n" + errorDetails);
        }
    }

    @Override
    public void stateAdvanced(Search search) {
        this.checkExecutionTimeoutExceeded();
        this.checkPcSizeLimitExceeded(PathCondition.getPC(search.getVM()));
    }

    @Override
    public void methodEntered(VM vm, ThreadInfo currentThread, MethodInfo enteredMethod) {
        if (this.instrumentedMethodSpec.matches(enteredMethod)) {
            this.isInInstrumentedMethod = true;
            this.instrumentedInputArguments = this.captureConcreteArguments(currentThread);
        }
        if (this.testedMethodSpec.matches(enteredMethod)) {
            LOGGER.atDebug().log("Entering tested method: " + enteredMethod.toString());
            this.recursionDepth++;
        }
    }

    @Override
    public void exceptionThrown(VM vm, ThreadInfo currentThread, ElementInfo thrownException) {
        if (!this.isInInstrumentedMethod || this.recursionDepth < 0) {
            return;
        }

        this.pendingThrownException = this.captureException(currentThread, thrownException);
    }

    @Override
    public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
        if (this.instrumentedMethodSpec.matches(exitedMethod)) {
            this.isInInstrumentedMethod = false;
        }
        if (this.testedMethodSpec.matches(exitedMethod)) {
            LOGGER.atDebug().log("Exiting tested method: " + exitedMethod.toString());
            this.recursionDepth--;
            if (this.isInInstrumentedMethod && this.recursionDepth == -1) {
                this.writeSpecificationFiles(vm, currentThread);
                vm.getSearch().terminate();
            }
        }
    }

    private void writeSpecificationFiles(VM vm, ThreadInfo currentThread) {
        PathCondition pathCondition = PathCondition.getPC(vm);
        this.checkPcSizeLimitExceeded(pathCondition);

        LOGGER.atDebug().log("Returning from: " + this.testedMethodSpec.getSource());

        List<MethodArgument> concreteInputArguments = this.instrumentedInputArguments == null
            ? this.captureConcreteArguments(currentThread)
            : this.instrumentedInputArguments;

        Constraint spfInput = pathCondition == null ? null : pathCondition.header;

        LOGGER.atTrace().log(() -> "Input: " + (spfInput == null ? null : spfInput.toString()));

        MethodArgument concreteOutputArgument;

        Expression spfOutput = null;
        CapturedException capturedException = null;

        Instruction exitInstruction = vm.getCurrentThread().getPC();
        if (exitInstruction instanceof JVMReturnInstruction) {
            JVMReturnInstruction returnInstruction = (JVMReturnInstruction) exitInstruction;

            Object concreteOutputValue = returnInstruction.getReturnValue(currentThread);
            String concreteOutputType = returnInstruction.getMethodInfo().getReturnTypeName();

            Object outputValueForArgument;
            if (returnInstruction instanceof ARETURN) {
                if (concreteOutputValue == null) {
                    outputValueForArgument = null;
                } else if (concreteOutputValue instanceof ElementInfo) {
                    outputValueForArgument = renderReferenceValue(concreteOutputType, (ElementInfo) concreteOutputValue);
                } else {
                    outputValueForArgument = concreteOutputValue.toString();
                }
            } else if (returnInstruction instanceof DRETURN) {
                outputValueForArgument = (Double) concreteOutputValue;
            } else if (returnInstruction instanceof FRETURN) {
                outputValueForArgument = (Float) concreteOutputValue;
            } else if (returnInstruction instanceof IRETURN) {
                outputValueForArgument = (Integer) concreteOutputValue;
            } else if (returnInstruction instanceof LRETURN) {
                outputValueForArgument = (Long) concreteOutputValue;
            } else if (returnInstruction instanceof NATIVERETURN) {
                outputValueForArgument = concreteOutputValue;
            } else if (returnInstruction instanceof RETURN) {
                outputValueForArgument = concreteOutputValue; // void
            } else {
                throw new RuntimeException("Unexpected returnInstruction: " + returnInstruction);
            }

            concreteOutputArgument = new MethodArgument(concreteOutputType, outputValueForArgument == null ? "null" : outputValueForArgument.toString());

            // Typed overload: JPF stores stacked slot attributes as an ObjectList, so the untyped
            // getReturnAttr returns the raw attribute and a direct (Expression) cast fails on a list
            // or a non-Expression attribute. getReturnAttr(ti, Expression.class) unwraps via
            // ObjectList.getFirst and selects the Expression attribute (null if none).
            spfOutput = returnInstruction.getReturnAttr(vm.getCurrentThread(), Expression.class);
            Expression spfOutput_ = spfOutput; // To use spfOutput in the lambda, it needs to be (effectively) final.
            LOGGER.atTrace().log(() -> "Output: " + (spfOutput_ == null ? null : spfOutput_.toString()));
        } else if (exitInstruction instanceof ATHROW) {
            if (this.pendingThrownException == null) {
                throw new RuntimeException("JPF reported exceptional exit from " + this.testedMethodSpec.getSource() + " without a captured exceptionThrown notification.");
            }

            capturedException = this.pendingThrownException;
            concreteOutputArgument = new MethodArgument(capturedException.getName(), capturedException.getMessage());
            LOGGER.atTrace().log("Output: Exception thrown " + capturedException.getName());
            this.pendingThrownException = null;
        } else {
            throw new RuntimeException("Unexpected exit instruction: " + exitInstruction);
        }

        SpfToModelTransformer spfToModelTransformer = new SpfToModelTransformer();

        teralizer.domain.Expression modelInput = spfToModelTransformer.transform(spfInput);

        teralizer.domain.Expression modelOutput;
        if (capturedException == null) {
            modelOutput = spfToModelTransformer.transform(spfOutput);
        } else {
            modelOutput = spfToModelTransformer.transform(capturedException);
        }

        Invocation invocation = new Invocation(
            concreteInputArguments, concreteOutputArgument, modelInput, modelOutput);
        new SpecificationExtractor().write(
            invocation,
            this.inputValuesPath, this.outputValuePath,
            this.inputSpecificationPath, this.outputSpecificationPath);
    }

    private List<MethodArgument> captureConcreteArguments(ThreadInfo currentThread) {
        List<MethodArgument> concreteArguments = new ArrayList<>();
        String[] concreteTypes = currentThread.getTopFrameMethodInfo().getArgumentTypeNames();
        Object[] concreteValues = currentThread.getTopFrame().getArgumentValues(currentThread);
        for (int i = 0; i < concreteValues.length; i++) {
            // JPF boxes primitive arguments to host wrappers (String.valueOf is correct), but passes
            // reference arguments as ElementInfo, whose toString() is object identity. Read those by value.
            String concreteValue = concreteValues[i] instanceof ElementInfo
                ? renderReferenceValue(concreteTypes[i], (ElementInfo) concreteValues[i])
                : String.valueOf(concreteValues[i]);
            concreteArguments.add(new MethodArgument(concreteTypes[i], concreteValue));
        }
        return concreteArguments;
    }

    /**
     * Render the concrete value of a reference-typed slot (argument or return) to the string form
     * {@link teralizer.transformer.ModelToJavaTransformer} expects. JPF represents a boxed primitive
     * as an {@link ElementInfo} whose {@code toString()} is object identity (e.g.
     * {@code java.lang.Integer@1f}), so wrappers are read from their backing {@code value} field and
     * Strings via {@link ElementInfo#asString()}. Other reference types fall back to identity: such
     * parameters and returns are rejected downstream by the supported-type ceiling, so their value is
     * never rendered into a generated test.
     */
    private static String renderReferenceValue(String javaType, ElementInfo elementInfo) {
        switch (javaType) {
            case "java.lang.String":
                return elementInfo.asString();
            case "java.lang.Byte":
                return Byte.toString(elementInfo.getByteField("value"));
            case "java.lang.Short":
                return Short.toString(elementInfo.getShortField("value"));
            case "java.lang.Integer":
                return Integer.toString(elementInfo.getIntField("value"));
            case "java.lang.Long":
                return Long.toString(elementInfo.getLongField("value"));
            case "java.lang.Float":
                return Float.toString(elementInfo.getFloatField("value"));
            case "java.lang.Double":
                return Double.toString(elementInfo.getDoubleField("value"));
            case "java.lang.Boolean":
                return Boolean.toString(elementInfo.getBooleanField("value"));
            case "java.lang.Character":
                return Integer.toString((int) elementInfo.getCharField("value"));
            default:
                return elementInfo.toString();
        }
    }

    private CapturedException captureException(ThreadInfo currentThread, ElementInfo thrownException) {
        String exceptionClass = thrownException.getClassInfo().getName();
        String exceptionMessage = null;

        int messageRef = thrownException.getReferenceField("detailMessage");
        if (messageRef != MJIEnv.NULL) {
            ElementInfo messageInfo = currentThread.getElementInfo(messageRef);
            exceptionMessage = messageInfo.asString();
        }

        return new CapturedException(exceptionClass, exceptionMessage);
    }

    private void checkExecutionTimeoutExceeded() {
        double elapsedTime = (System.currentTimeMillis() - this.startTime) / 1000.0;
        if (elapsedTime > this.maxExecutionTime) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - Execution timeout exceeded: " + elapsedTime + " of " + this.maxExecutionTime + " seconds passed.");
        }
    }

    private void checkPcSizeLimitExceeded(PathCondition pathCondition) {
        int pcLength = pathCondition == null ? 0 : pathCondition.toString().length();
        if (pcLength > this.maxPathConditionSize) {
            throw new RuntimeException(this.instrumentedMethodQualifiedName + " - PC size limit exceeded: " + pcLength + " of " + this.maxPathConditionSize + " characters used.");
        }
    }
}
