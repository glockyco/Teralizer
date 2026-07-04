package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.*;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.numeric.Constraint;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.util.ObjectList;
import gov.nasa.jpf.vm.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.domain.CapturedException;
import teralizer.domain.CapturedOutput;
import teralizer.domain.NullValue;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.ReferenceValue;
import teralizer.domain.StringValue;
import teralizer.domain.Value;
import teralizer.transformer.SpfToModelTransformer;

public class TestGeneralizationListener extends PropertyListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGeneralizationListener.class);

    private final String instrumentedMethodQualifiedName;
    private final MethodSpec instrumentedMethodSpec;
    private final MethodSpec testedMethodSpec;
    private final boolean expressionRecipe;

    private final Path inputValuesPath;
    private final Path outputValuePath;
    private final Path inputSpecificationPath;
    private final Path outputSpecificationPath;

    private final double maxExecutionTime;
    private final long maxPathConditionSize;

    private long startTime;

    private int targetDepth;
    private boolean isInInstrumentedMethod;
    private CapturedException pendingThrownException;
    private List<Value> instrumentedInputArguments;
    private boolean targetEntered;
    private CapturedInvocation invocation;
    private int concretizationEvents;

    public TestGeneralizationListener(Config config) {
        this.instrumentedMethodQualifiedName = config.getString("test_generalization.instrumented_method");
        this.instrumentedMethodSpec = MethodSpec.createMethodSpec(this.instrumentedMethodQualifiedName);
        this.expressionRecipe = config.getBoolean("test_generalization.expression_recipe", false);
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
        this.targetDepth = -1;
        this.isInInstrumentedMethod = false;
        this.pendingThrownException = null;
        this.targetEntered = false;
        this.invocation = null;
        this.concretizationEvents = 0;
    }

    @Override
    public void searchConstraintHit(Search search) {
        if (search.getDepth() >= search.getDepthLimit()) {
            throw new ExtractionAborted(ExtractionAborted.Reason.SEARCH_DEPTH_LIMIT, this.instrumentedMethodQualifiedName + " - Search depth limit of " + search.getDepthLimit() + " exceeded.");
        }
    }

    @Override
    public void propertyViolated(Search search) {
        String errorDetails = search.getLastError().getDetails();
        if (errorDetails.contains("java.lang.NullPointerException") && errorDetails.contains("at java.util.concurrent.atomic")) {
            throw new ExtractionAborted(ExtractionAborted.Reason.NATIVE_MODEL_GAP, this.instrumentedMethodQualifiedName + " - Failed JPF execution due to incomplete native peers.\n\n" + errorDetails);
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
        // Pin the capture frame's stack position at its first entry reached from inside the wrapper.
        // In expression mode the wrapper is the capture frame; in invocation mode the tested call is.
        // Its matching exit (same depth) is the outermost frame under recursion and the first call
        // under a looped wrapper. The single constraint-collection path does not backtrack, so the
        // pinned depth stays valid for the rest of the run.
        if (this.expressionRecipe && this.targetDepth < 0 && this.instrumentedMethodSpec.matches(enteredMethod)) {
            LOGGER.atDebug().log("Entering instrumented method: " + enteredMethod.toString());
            this.targetDepth = currentThread.getTopFrame().getDepth();
        }
        if (this.isInInstrumentedMethod && this.testedMethodSpec.matches(enteredMethod)) {
            this.targetEntered = true;
            if (!this.expressionRecipe && this.targetDepth < 0) {
                LOGGER.atDebug().log("Entering tested method: " + enteredMethod.toString());
                this.targetDepth = currentThread.getTopFrame().getDepth();
            }
        }
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction insn) {
        if (!this.isExtractionActive() || !(insn instanceof EXECUTENATIVE)) {
            return;
        }

        MethodInfo executedMethod = ((EXECUTENATIVE) insn).getExecutedMethod();
        StackFrame callerFrame = currentThread.getCallerStackFrame();
        if (callerFrame != null && containsSymbolicExpression(callerFrame.getArgumentAttrs(executedMethod))) {
            this.concretizationEvents++;
        }
    }

    /**
     * Count symbolic values at the native-call boundary before JPF boxes the concrete arguments for
     * MJI. Native peers only preserve such attrs when they explicitly read and reattach them, so each
     * symbolic argument here marks a boundary where the downstream path condition may be weakened.
     */
    private static boolean containsSymbolicExpression(Object[] argumentAttrs) {
        if (argumentAttrs == null) {
            return false;
        }
        for (Object argumentAttr : argumentAttrs) {
            if (ObjectList.containsType(argumentAttr, Expression.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExtractionActive() {
        return (this.expressionRecipe ? this.targetDepth >= 0 : this.targetEntered) && this.invocation == null;
    }

    @Override
    public void exceptionThrown(VM vm, ThreadInfo currentThread, ElementInfo thrownException) {
        if (!this.isExtractionActive()) {
            return;
        }

        this.pendingThrownException = this.captureException(currentThread, thrownException);
    }

    @Override
    public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
        boolean captureMethodExited = this.expressionRecipe
            ? this.instrumentedMethodSpec.matches(exitedMethod)
            : this.testedMethodSpec.matches(exitedMethod);
        if (captureMethodExited && this.isExtractionActive()
                && currentThread.getTopFrame().getDepth() == this.targetDepth) {
            LOGGER.atDebug().log("Exiting capture method: " + exitedMethod.toString());
            this.invocation = this.captureInvocation(vm, currentThread);
            vm.getSearch().terminate();
        }
        if (this.instrumentedMethodSpec.matches(exitedMethod)) {
            this.isInInstrumentedMethod = false;
        }
    }

    private CapturedInvocation captureInvocation(VM vm, ThreadInfo currentThread) {
        PathCondition pathCondition = PathCondition.getPC(vm);
        this.checkPcSizeLimitExceeded(pathCondition);

        LOGGER.atDebug().log("Returning from: " + this.testedMethodSpec.getSource());

        List<Value> concreteInputs = this.instrumentedInputArguments == null
            ? this.captureConcreteArguments(currentThread)
            : this.instrumentedInputArguments;

        Constraint spfInput = pathCondition == null ? null : pathCondition.header;

        LOGGER.atTrace().log(() -> "Input: " + (spfInput == null ? null : spfInput.toString()));

        CapturedOutput output;
        Expression spfOutput = null;
        CapturedException capturedException = null;

        Instruction exitInstruction = vm.getCurrentThread().getPC();
        if (exitInstruction instanceof JVMReturnInstruction) {
            JVMReturnInstruction returnInstruction = (JVMReturnInstruction) exitInstruction;
            String concreteOutputType = returnInstruction.getMethodInfo().getReturnTypeName();

            Object concreteOutputValue = null;
            if (returnInstruction instanceof RETURN || "void".equals(concreteOutputType)) {
                output = CapturedOutput.ofVoid();
            } else {
                concreteOutputValue = returnInstruction.getReturnValue(currentThread);
                output = CapturedOutput.ofReturnValue(captureValue(concreteOutputType, concreteOutputValue));
            }

            // Typed overload: JPF stores stacked slot attributes as an ObjectList, so the untyped
            // getReturnAttr returns the raw attribute and a direct (Expression) cast fails on a list
            // or a non-Expression attribute. getReturnAttr(ti, Expression.class) unwraps via
            // ObjectList.getFirst and selects the Expression attribute (null if none).
            spfOutput = returnInstruction.getReturnAttr(vm.getCurrentThread(), Expression.class);
            Expression boxedFieldOutput = boxedPrimitiveValueFieldAttr(concreteOutputType, concreteOutputValue);
            if (boxedFieldOutput != null) {
                spfOutput = boxedFieldOutput;
            }
            Expression spfOutput_ = spfOutput; // To use spfOutput in the lambda, it needs to be (effectively) final.
            LOGGER.atTrace().log(() -> "Output: " + (spfOutput_ == null ? null : spfOutput_.toString()));
        } else if (exitInstruction instanceof ATHROW) {
            if (this.pendingThrownException == null) {
                throw new RuntimeException("JPF reported exceptional exit from " + this.testedMethodSpec.getSource() + " without a captured exceptionThrown notification.");
            }

            capturedException = this.pendingThrownException;
            output = CapturedOutput.ofThrow(capturedException);
            LOGGER.atTrace().log("Output: Exception thrown " + capturedException.getName());
            this.pendingThrownException = null;
        } else {
            throw new RuntimeException("Unexpected exit instruction: " + exitInstruction);
        }

        SpfToModelTransformer spfToModelTransformer = new SpfToModelTransformer();

        teralizer.domain.Expression numericInput = spfToModelTransformer.transform(spfInput);
        teralizer.domain.Expression stringInput = pathCondition == null
            ? null
            : spfToModelTransformer.transform(pathCondition.spc);
        teralizer.domain.Expression modelInput = conjoin(numericInput, stringInput);

        teralizer.domain.Expression modelOutput;
        if (capturedException == null) {
            modelOutput = spfToModelTransformer.transform(spfOutput);
        } else {
            modelOutput = spfToModelTransformer.transform(capturedException);
        }

        return new CapturedInvocation(concreteInputs, output, modelInput, modelOutput);
    }

    /**
     * Conjoin the numeric and String path-condition models into one input predicate. The numeric
     * header and the String path condition ({@code pathCondition.spc}) are collected independently
     * by SPF; either may be null (a MUT constrains only numbers, only Strings, or neither).
     */
    private static teralizer.domain.Expression conjoin(
        teralizer.domain.Expression numeric,
        teralizer.domain.Expression string
    ) {
        if (numeric == null) {
            return string;
        }
        if (string == null) {
            return numeric;
        }
        return new teralizer.domain.Operation(numeric, teralizer.domain.Operator.AND, string);
    }

    /** The captured invocation, or {@code null} if the tested method never returned in-state. */
    public CapturedInvocation getInvocation() {
        return this.invocation;
    }

    /** Whether the tested method was entered at least once on the explored path. */
    public boolean wasTargetEntered() {
        return this.targetEntered;
    }

    /** Number of native-call boundaries that received at least one symbolic argument attr. */
    public int getConcretizationEvents() {
        return this.concretizationEvents;
    }

    private List<Value> captureConcreteArguments(ThreadInfo currentThread) {
        List<Value> concreteArguments = new ArrayList<>();
        String[] concreteTypes = currentThread.getTopFrameMethodInfo().getArgumentTypeNames();
        Object[] concreteValues = currentThread.getTopFrame().getArgumentValues(currentThread);
        for (int i = 0; i < concreteValues.length; i++) {
            concreteArguments.add(captureValue(concreteTypes[i], concreteValues[i]));
        }
        return concreteArguments;
    }

    /**
     * Build a typed {@link Value} from a concrete slot. JPF passes a primitive as its host wrapper
     * and a reference as an {@link ElementInfo} (whose {@code toString()} is object identity), so a
     * boxed wrapper is read from its backing field, a String via {@link ElementInfo#asString()}, a
     * null reference as a {@link NullValue}, and any other reference (e.g. an instance-method
     * receiver) as an opaque {@link ReferenceValue} — never corrupted into a null.
     */
    private static Value captureValue(String javaType, Object concreteValue) {
        if (concreteValue == null) {
            return new NullValue(javaType);
        }
        if (concreteValue instanceof ElementInfo) {
            return captureReferenceValue(javaType, (ElementInfo) concreteValue);
        }
        return capturePrimitiveValue(javaType, concreteValue);
    }

    private static Value captureReferenceValue(String javaType, ElementInfo elementInfo) {
        switch (javaType) {
            case "java.lang.String":
                return new StringValue(elementInfo.asString());
            case "java.lang.Byte":
                return new PrimitiveValue(javaType, elementInfo.getByteField("value"));
            case "java.lang.Short":
                return new PrimitiveValue(javaType, elementInfo.getShortField("value"));
            case "java.lang.Integer":
                return new PrimitiveValue(javaType, elementInfo.getIntField("value"));
            case "java.lang.Long":
                return new PrimitiveValue(javaType, elementInfo.getLongField("value"));
            case "java.lang.Float":
                return new PrimitiveValue(javaType, elementInfo.getFloatField("value"));
            case "java.lang.Double":
                return new PrimitiveValue(javaType, elementInfo.getDoubleField("value"));
            case "java.lang.Boolean":
                return new PrimitiveValue(javaType, elementInfo.getBooleanField("value"));
            case "java.lang.Character":
                return new PrimitiveValue(javaType, elementInfo.getCharField("value"));
            default:
                return new ReferenceValue(javaType);
        }
    }

    /**
     * Boxed primitive returns are heap references at {@code areturn}, so the reference slot usually
     * has no symbolic expression: boxing stores the primitive into the wrapper's {@code value} field,
     * and SPF keeps the primitive attr on that field. Reading only the returned reference therefore
     * mistakes a symbolic boxed result for a concrete/null output model. If JPF returns an interned
     * box or native peer object whose field has no attr, capture degrades to the ordinary null model
     * rather than inventing an unsound expression.
     */
    private static Expression boxedPrimitiveValueFieldAttr(String javaType, Object concreteValue) {
        if (!(concreteValue instanceof ElementInfo) || !isBoxedPrimitive(javaType)) {
            return null;
        }
        ElementInfo elementInfo = (ElementInfo) concreteValue;
        FieldInfo valueField = elementInfo.getClassInfo().getInstanceField("value");
        return valueField == null ? null : elementInfo.getFieldAttr(valueField, Expression.class);
    }

    private static boolean isBoxedPrimitive(String javaType) {
        switch (javaType) {
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.lang.Boolean":
            case "java.lang.Character":
                return true;
            default:
                return false;
        }
    }

    private static Value capturePrimitiveValue(String javaType, Object concreteValue) {
        switch (javaType) {
            case "byte":
                return new PrimitiveValue(javaType, ((Number) concreteValue).byteValue());
            case "short":
                return new PrimitiveValue(javaType, ((Number) concreteValue).shortValue());
            case "int":
                return new PrimitiveValue(javaType, ((Number) concreteValue).intValue());
            case "long":
                return new PrimitiveValue(javaType, ((Number) concreteValue).longValue());
            case "float":
                return new PrimitiveValue(javaType, ((Number) concreteValue).floatValue());
            case "double":
                return new PrimitiveValue(javaType, ((Number) concreteValue).doubleValue());
            case "boolean":
                // A boolean return exits via ireturn and arrives as an Integer 0/1, while a boolean
                // argument arrives as a host Boolean from the frame slot; handle both forms.
                return new PrimitiveValue(javaType, concreteValue instanceof Boolean
                    ? (Boolean) concreteValue
                    : ((Number) concreteValue).intValue() != 0);
            case "char":
                return new PrimitiveValue(javaType, concreteValue instanceof Character
                    ? (Character) concreteValue
                    : (char) ((Number) concreteValue).intValue());
            default:
                throw new IllegalStateException(
                    "Unexpected primitive slot type " + javaType + " with value " + concreteValue);
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
            throw new ExtractionAborted(ExtractionAborted.Reason.EXECUTION_TIMEOUT, this.instrumentedMethodQualifiedName + " - Execution timeout exceeded: " + elapsedTime + " of " + this.maxExecutionTime + " seconds passed.");
        }
    }

    private void checkPcSizeLimitExceeded(PathCondition pathCondition) {
        int pcLength = pathCondition == null ? 0 : pathCondition.toString().length();
        if (pcLength > this.maxPathConditionSize) {
            throw new ExtractionAborted(ExtractionAborted.Reason.PATH_CONDITION_TOO_LARGE, this.instrumentedMethodQualifiedName + " - PC size limit exceeded: " + pcLength + " of " + this.maxPathConditionSize + " characters used.");
        }
    }
}
