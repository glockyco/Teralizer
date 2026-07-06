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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.domain.CapturedException;
import teralizer.domain.CapturedInput;
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
    private boolean pendingThrownExceptionFromApplication;
    private boolean capturedThrowFromApplication;
    private boolean capturedThrow;
    private final List<String> instrumentedParameterNames;
    private List<CapturedInput> instrumentedInputArguments;
    private boolean targetEntered;
    private CapturedInvocation invocation;
    private int concretizationEvents;
    private final Map<String, Integer> concretizedMethods = new TreeMap<>();
    private boolean concreteApplicationBranchAfterConcretization;

    public TestGeneralizationListener(Config config) {
        this.instrumentedMethodQualifiedName = config.getString("test_generalization.instrumented_method");
        this.instrumentedMethodSpec = MethodSpec.createMethodSpec(this.instrumentedMethodQualifiedName);
        this.testedMethodSpec = MethodSpec.createMethodSpec(config.getString("test_generalization.tested_method"));
        String parameterNames = config.getString("test_generalization.instrumented_parameters", "");
        this.instrumentedParameterNames = parameterNames.isEmpty()
            ? Collections.emptyList()
            : Arrays.asList(parameterNames.split(","));
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
        this.pendingThrownExceptionFromApplication = false;
        this.capturedThrowFromApplication = false;
        this.capturedThrow = false;
        this.targetEntered = false;
        this.invocation = null;
        this.concretizationEvents = 0;
        this.concretizedMethods.clear();
        this.concreteApplicationBranchAfterConcretization = false;
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
        // Pin the instrumented wrapper's stack position at first entry. Its matching exit at the
        // same depth is the capture point for every recipe; target entry remains an observation for
        // outcome classification.
        if (this.targetDepth < 0 && this.instrumentedMethodSpec.matches(enteredMethod)) {
            LOGGER.atDebug().log("Entering instrumented method: " + enteredMethod.toString());
            this.targetDepth = currentThread.getTopFrame().getDepth();
        }
        if (this.isInInstrumentedMethod && this.testedMethodSpec.matches(enteredMethod)) {
            this.targetEntered = true;
        }
    }

    @Override
    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction insn) {
        if (!this.isExtractionActive()) {
            return;
        }

        if (this.concretizationEvents > 0
            && isConditionalBranch(insn)
            && isApplicationInstruction(insn)
            && !hasSymbolicBranchOperand(currentThread, insn)) {
            this.concreteApplicationBranchAfterConcretization = true;
        }

        if (!(insn instanceof EXECUTENATIVE)) {
            return;
        }

        MethodInfo executedMethod = ((EXECUTENATIVE) insn).getExecutedMethod();
        StackFrame callerFrame = currentThread.getCallerStackFrame();
        if (callerFrame != null && containsSymbolicExpression(callerFrame.getArgumentAttrs(executedMethod))) {
            this.concretizationEvents++;
            this.concretizedMethods.merge(executedMethod.getFullName(), 1, Integer::sum);
        }
    }

    private static boolean isConditionalBranch(Instruction instruction) {
        return instruction instanceof IfInstruction || instruction instanceof SwitchInstruction;
    }

    private static boolean hasSymbolicBranchOperand(ThreadInfo currentThread, Instruction instruction) {
        StackFrame frame = currentThread.getTopFrame();
        if (frame == null) {
            return false;
        }
        int operandCount = branchOperandCount(instruction);
        Object[] operandAttrs = new Object[operandCount];
        for (int i = 0; i < operandCount; i++) {
            operandAttrs[i] = frame.getOperandAttr(i);
        }
        return containsSymbolicExpression(operandAttrs);
    }

    private static int branchOperandCount(Instruction instruction) {
        if (instruction instanceof SwitchInstruction) {
            return 1;
        }
        int byteCode = ((IfInstruction) instruction).getByteCode();
        return byteCode >= 0x9F && byteCode <= 0xA6 ? 2 : 1;
    }

    private static boolean isApplicationInstruction(Instruction instruction) {
        MethodInfo methodInfo = instruction.getMethodInfo();
        if (methodInfo == null) {
            return false;
        }
        return isApplicationClass(methodInfo.getClassInfo());
    }

    private static boolean isApplicationClass(ClassInfo classInfo) {
        if (classInfo == null) {
            return false;
        }
        String className = classInfo.getName();
        // JDK and JPF modeled-library bytecode contains peer bookkeeping branches that do not
        // represent application reachability decisions.
        return !className.startsWith("java.")
            && !className.startsWith("javax.")
            && !className.startsWith("jdk.")
            && !className.startsWith("sun.")
            && !className.startsWith("com.sun.")
            && !className.startsWith("gov.nasa.jpf.");
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
        return this.targetDepth >= 0 && this.invocation == null;
    }

    @Override
    public void exceptionThrown(VM vm, ThreadInfo currentThread, ElementInfo thrownException) {
        if (!this.isExtractionActive()) {
            return;
        }

        this.pendingThrownException = this.captureException(currentThread, thrownException);
        this.pendingThrownExceptionFromApplication = isApplicationThrow(currentThread);
    }

    private static boolean isApplicationThrow(ThreadInfo currentThread) {
        Instruction instruction = currentThread.getPC();
        return instruction instanceof ATHROW && isApplicationInstruction(instruction);
    }

    @Override
    public void methodExited(VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
        boolean captureMethodExited = this.instrumentedMethodSpec.matches(exitedMethod);
        boolean atPinnedDepth = currentThread.getTopFrame() != null
            && currentThread.getTopFrame().getDepth() == this.targetDepth;
        boolean exceptionalWrapperUnwind = this.pendingThrownException != null && this.isInInstrumentedMethod;
        if (captureMethodExited && this.isExtractionActive() && (atPinnedDepth || exceptionalWrapperUnwind)) {
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

        List<CapturedInput> concreteInputs = this.instrumentedInputArguments == null
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

                // Typed overload: JPF stores stacked slot attributes as an ObjectList, so the untyped
                // getReturnAttr returns the raw attribute and a direct (Expression) cast fails on a list
                // or a non-Expression attribute. getReturnAttr(ti, Expression.class) unwraps via
                // ObjectList.getFirst and selects the Expression attribute (null if none).
                spfOutput = returnInstruction.getReturnAttr(vm.getCurrentThread(), Expression.class);
                Expression boxedFieldOutput = boxedPrimitiveValueFieldAttr(concreteOutputType, concreteOutputValue);
                if (boxedFieldOutput != null) {
                    spfOutput = boxedFieldOutput;
                }
            }
            this.pendingThrownException = null;
            this.pendingThrownExceptionFromApplication = false;
            Expression spfOutput_ = spfOutput; // To use spfOutput in the lambda, it needs to be (effectively) final.
            LOGGER.atTrace().log(() -> "Output: " + (spfOutput_ == null ? null : spfOutput_.toString()));
        } else if (this.pendingThrownException != null) {
            capturedException = this.pendingThrownException;
            this.capturedThrowFromApplication = this.pendingThrownExceptionFromApplication;
            this.capturedThrow = true;
            output = CapturedOutput.ofThrow(capturedException);
            LOGGER.atTrace().log("Output: Exception thrown " + capturedException.getName());
            this.pendingThrownException = null;
            this.pendingThrownExceptionFromApplication = false;
        } else if (exitInstruction instanceof ATHROW) {
            throw new RuntimeException("JPF reported exceptional exit from " + this.testedMethodSpec.getSource() + " without a captured exceptionThrown notification.");
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

    public boolean getPostConcretizationDivergenceRisk() {
        return this.concretizationEvents > 0
            && (this.concreteApplicationBranchAfterConcretization
                || (this.capturedThrow && !this.capturedThrowFromApplication));
    }

    /** Native methods that received symbolic argument attrs, keyed by qualified method name. */
    public Map<String, Integer> getConcretizedMethods() {
        return new TreeMap<>(this.concretizedMethods);
    }

    private List<CapturedInput> captureConcreteArguments(ThreadInfo currentThread) {
        List<CapturedInput> concreteArguments = new ArrayList<>();
        String[] concreteTypes = currentThread.getTopFrameMethodInfo().getArgumentTypeNames();
        Object[] concreteValues = currentThread.getTopFrame().getArgumentValues(currentThread);
        if (this.instrumentedParameterNames.size() != concreteValues.length) {
            throw new RuntimeException("Wrapper parameter-name list ("
                + this.instrumentedParameterNames.size() + " names) does not match the captured argument"
                + " count (" + concreteValues.length + ") for " + this.instrumentedMethodQualifiedName + ".");
        }
        for (int i = 0; i < concreteValues.length; i++) {
            concreteArguments.add(new CapturedInput(
                this.instrumentedParameterNames.get(i),
                captureValue(concreteTypes[i], concreteValues[i])
            ));
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
        String capturedType = referenceCaptureType(javaType, elementInfo);
        switch (capturedType) {
            case "java.lang.String":
                return new StringValue(elementInfo.asString());
            case "java.lang.Byte":
                return new PrimitiveValue(capturedType, elementInfo.getByteField("value"));
            case "java.lang.Short":
                return new PrimitiveValue(capturedType, elementInfo.getShortField("value"));
            case "java.lang.Integer":
                return new PrimitiveValue(capturedType, elementInfo.getIntField("value"));
            case "java.lang.Long":
                return new PrimitiveValue(capturedType, elementInfo.getLongField("value"));
            case "java.lang.Float":
                return new PrimitiveValue(capturedType, elementInfo.getFloatField("value"));
            case "java.lang.Double":
                return new PrimitiveValue(capturedType, elementInfo.getDoubleField("value"));
            case "java.lang.Boolean":
                return new PrimitiveValue(capturedType, elementInfo.getBooleanField("value"));
            case "java.lang.Character":
                return new PrimitiveValue(capturedType, elementInfo.getCharField("value"));
            default:
                return new ReferenceValue(javaType);
        }
    }

    private static String referenceCaptureType(String declaredType, ElementInfo elementInfo) {
        if (isStringOrBoxedPrimitive(declaredType)) {
            return declaredType;
        }
        String runtimeType = elementInfo.getClassInfo().getName();
        return isStringOrBoxedPrimitive(runtimeType) ? runtimeType : declaredType;
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
        if (!(concreteValue instanceof ElementInfo)) {
            return null;
        }
        ElementInfo elementInfo = (ElementInfo) concreteValue;
        String captureType = isBoxedPrimitive(javaType) ? javaType : elementInfo.getClassInfo().getName();
        if (!isBoxedPrimitive(captureType)) {
            return null;
        }
        FieldInfo valueField = elementInfo.getClassInfo().getInstanceField("value");
        return valueField == null ? null : elementInfo.getFieldAttr(valueField, Expression.class);
    }

    private static boolean isStringOrBoxedPrimitive(String javaType) {
        return "java.lang.String".equals(javaType) || isBoxedPrimitive(javaType);
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
