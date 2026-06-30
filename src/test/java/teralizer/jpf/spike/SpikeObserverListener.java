package teralizer.jpf.spike;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.JVMReturnInstruction;
import gov.nasa.jpf.symbc.numeric.Expression;
import gov.nasa.jpf.util.MethodSpec;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

/**
 * Spike for the spec-extraction redesign (P2/P3/P4): an observer-only JPF listener. It records the
 * observable state of one constraint-collection run and the raw return capture, but performs no
 * model transformation and no file I/O — classification into a total, typed outcome happens after
 * {@code jpf.run()} in {@link ExtractionOutcome#classify}. This proves the production listener's
 * five fused concerns can be split into a thin observer plus a pure post-run step.
 *
 * <p><b>Target identity.</b> The extraction contract is first-invocation-only: capture the first
 * tested-method call reached from the wrapper, then terminate the search. JPF clones frozen frames
 * ({@code StackFrame} is {@code Cloneable}; {@code getModifiableTopFrame} clones) and exposes no
 * stable per-frame id, and {@code StackFrame.equals} compares slot state — so a frame object
 * reference cannot identify an invocation across the run. The frame's intrinsic stack position
 * ({@code StackFrame.getDepth()}, copied verbatim by {@code clone()}) is the stable identity: the
 * first tested entry pins the depth, and the matching exit is the tested return at that same depth,
 * which is the outermost frame under recursion (inner frames sit deeper). {@code leave()} notifies
 * {@code methodExited} before {@code popFrame()}, so the exiting frame is still {@code top} here.
 */
public final class SpikeObserverListener extends PropertyListenerAdapter {

    private final MethodSpec instrumentedSpec;
    private final MethodSpec testedSpec;

    private boolean wrapperEntered;
    private int targetDepth = -1;

    boolean targetEntered;
    boolean targetExited;
    Integer matchedDepth;
    String concreteOut;
    Expression symbolicOut;

    public SpikeObserverListener(Config config) {
        this.instrumentedSpec = MethodSpec.createMethodSpec(config.getString("test_generalization.instrumented_method"));
        this.testedSpec = MethodSpec.createMethodSpec(config.getString("test_generalization.tested_method"));
    }

    @Override
    public void methodEntered(VM vm, ThreadInfo ti, MethodInfo enteredMethod) {
        if (this.instrumentedSpec.matches(enteredMethod)) {
            this.wrapperEntered = true;
        }
        // First tested-method invocation reached from inside the wrapper: pin its stack position.
        if (this.wrapperEntered && this.targetDepth < 0 && this.testedSpec.matches(enteredMethod)) {
            this.targetEntered = true;
            this.targetDepth = ti.getTopFrame().getDepth();
        }
    }

    @Override
    public void methodExited(VM vm, ThreadInfo ti, MethodInfo exitedMethod) {
        if (this.targetEntered && !this.targetExited && this.testedSpec.matches(exitedMethod)
                && ti.getTopFrame().getDepth() == this.targetDepth) {
            this.captureReturn(ti);
            this.matchedDepth = this.targetDepth;
            this.targetExited = true;
            vm.getSearch().terminate();
        }
        if (this.instrumentedSpec.matches(exitedMethod)) {
            this.wrapperEntered = false;
        }
    }

    private void captureReturn(ThreadInfo ti) {
        Instruction exit = ti.getPC();
        if (exit instanceof JVMReturnInstruction) {
            JVMReturnInstruction ret = (JVMReturnInstruction) exit;
            this.concreteOut = String.valueOf(ret.getReturnValue(ti));
            this.symbolicOut = ret.getReturnAttr(ti, Expression.class);
        }
    }
}
