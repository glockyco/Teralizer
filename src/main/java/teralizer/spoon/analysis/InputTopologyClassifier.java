package teralizer.spoon.analysis;

import java.util.List;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

/** Pure input-topology telemetry for assertion actual expressions. */
final class InputTopologyClassifier {

    private InputTopologyClassifier() {
    }

    /**
     * Classifies the asserted value shape for observation only.  LITERAL/VARIABLE/FIELD_ACCESS are
     * leaf oracle values; SINGLE_CALL and CTOR_RECEIVER_CALL identify one method call, with the
     * latter marking inline receiver construction; CHAINED_CALLS_END0ARG/ENDNARG distinguish
     * chained call oracles by whether the outermost call has input arguments; CTOR_ONLY is a bare
     * constructor expression; OPERATOR_COMPOSITE covers binary/unary/conditional expressions whose
     * inputs are embedded in operators; ARRAY_INDEX and LAMBDA_OR_METHODREF reserve other expression
     * families.  NONE means no supported value topology.  This taxonomy never changes the MUT pick.
     */
    static MutResolution.ActualShape classifyShape(CtExpression<?> actual) {
        if (actual == null) {
            return MutResolution.ActualShape.NONE;
        }
        if (actual instanceof CtLiteral<?>) {
            return MutResolution.ActualShape.LITERAL;
        }
        if (actual instanceof CtVariableRead<?>) {
            return MutResolution.ActualShape.VARIABLE;
        }
        if (actual instanceof CtFieldRead<?>) {
            return MutResolution.ActualShape.FIELD_ACCESS;
        }
        if (actual instanceof CtBinaryOperator<?>
                || actual instanceof CtUnaryOperator<?>
                || actual instanceof CtConditional<?>) {
            return MutResolution.ActualShape.OPERATOR_COMPOSITE;
        }
        if (actual instanceof CtArrayRead<?>) {
            return MutResolution.ActualShape.ARRAY_INDEX;
        }
        if (actual instanceof CtLambda<?> || actual instanceof CtExecutableReferenceExpression<?, ?>) {
            return MutResolution.ActualShape.LAMBDA_OR_METHODREF;
        }
        if (actual instanceof CtConstructorCall<?>) {
            return MutResolution.ActualShape.CTOR_ONLY;
        }
        if (actual instanceof CtInvocation<?>) {
            CtInvocation<?> outermost = (CtInvocation<?>) actual;
            int invocationCount = 0;
            CtExpression<?> current = actual;
            while (current instanceof CtInvocation<?>) {
                invocationCount++;
                current = ((CtInvocation<?>) current).getTarget();
            }
            if (invocationCount == 1) {
                return current instanceof CtConstructorCall<?>
                    ? MutResolution.ActualShape.CTOR_RECEIVER_CALL
                    : MutResolution.ActualShape.SINGLE_CALL;
            }
            return outermost.getArguments().isEmpty()
                ? MutResolution.ActualShape.CHAINED_CALLS_END0ARG
                : MutResolution.ActualShape.CHAINED_CALLS_ENDNARG;
        }
        return MutResolution.ActualShape.NONE;
    }

    /**
     * Classifies where the receiver of an invocation-rooted actual came from, again as telemetry
     * only.  INLINE_CTOR marks a root receiver constructed in the assertion; LOCAL_CTOR means a local
     * variable was initialized from a constructor and left untouched; LOCAL_CTOR_MUTATED marks the
     * R2 statement-slice family where a local constructor receiver was mutated before inspection;
     * LOCAL_OTHER covers locals not rooted in a constructor; FIELD covers field receivers; and
     * PARAM_OR_STATIC covers parameters, static calls, and receiver-less invocations.
     */
    static MutResolution.ReceiverProvenance receiverProvenance(
        CtExpression<?> actual,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        if (!(actual instanceof CtInvocation<?>)) {
            return MutResolution.ReceiverProvenance.NONE;
        }
        CtExpression<?> receiver = rootReceiver((CtInvocation<?>) actual);
        if (receiver instanceof CtConstructorCall<?>) {
            return MutResolution.ReceiverProvenance.INLINE_CTOR;
        }
        if (receiver instanceof CtFieldRead<?>) {
            return MutResolution.ReceiverProvenance.FIELD;
        }
        if (receiver instanceof CtTypeAccess<?> || receiver == null) {
            return MutResolution.ReceiverProvenance.PARAM_OR_STATIC;
        }
        if (receiver instanceof CtVariableRead<?>) {
            CtVariableReference<?> ref = ((CtVariableRead<?>) receiver).getVariable();
            if (!(ref instanceof CtLocalVariableReference)) {
                return MutResolution.ReceiverProvenance.PARAM_OR_STATIC;
            }
            ReachingWrite write = nearestLocalWrite(ref, testMethod, assertion);
            if (write == null) {
                return MutResolution.ReceiverProvenance.LOCAL_OTHER;
            }
            if (write.rhs instanceof CtConstructorCall<?>) {
                return localReceiverMutatedBetween(ref, write.index, testMethod, assertion)
                    ? MutResolution.ReceiverProvenance.LOCAL_CTOR_MUTATED
                    : MutResolution.ReceiverProvenance.LOCAL_CTOR;
            }
            return MutResolution.ReceiverProvenance.LOCAL_OTHER;
        }
        if (receiver instanceof CtThisAccess<?>) {
            return MutResolution.ReceiverProvenance.PARAM_OR_STATIC;
        }
        return MutResolution.ReceiverProvenance.NONE;
    }

    /** Walks an invocation chain to its first non-invocation receiver expression. */
    static CtExpression<?> rootReceiver(CtInvocation<?> invocation) {
        CtExpression<?> current = invocation.getTarget();
        while (current instanceof CtInvocation<?>) {
            current = ((CtInvocation<?>) current).getTarget();
        }
        return current;
    }

    /**
     * Detects mutation of a local constructor receiver between its definition and the assertion by
     * looking for intervening method invocations targeted at that same local.  This is the topology
     * marker for statement-slice recipes; it does not validate or alter the selected MUT.
     */
    static boolean localReceiverMutatedBetween(
        CtVariableReference<?> ref,
        int definitionIndex,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        for (CtStatement statement : body) {
            for (CtInvocation<?> invocation : statement.getElements(new TypeFilter<>(CtInvocation.class))) {
                int index = topLevelIndex(invocation, body);
                if (index > definitionIndex && index < assertionIndex && targetsLocal(invocation, ref)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when an invocation is called directly on the given local variable receiver. */
    static boolean targetsLocal(CtInvocation<?> invocation, CtVariableReference<?> ref) {
        CtExpression<?> target = invocation.getTarget();
        return target instanceof CtVariableRead<?>
            && ((CtVariableRead<?>) target).getVariable().equals(ref);
    }

    private static ReachingWrite nearestLocalWrite(
        CtVariableReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        CtExpression<?> rhs = null;
        int bestIndex = -1;
        for (CtStatement statement : body) {
            for (CtLocalVariable<?> local : statement.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (local.getReference().equals(ref)) {
                    int index = topLevelIndex(local, body);
                    if (index >= 0 && index < assertionIndex && index >= bestIndex) {
                        bestIndex = index;
                        rhs = local.getAssignment();
                    }
                }
            }
            for (CtAssignment<?, ?> assignment : statement.getElements(new TypeFilter<>(CtAssignment.class))) {
                CtExpression<?> assigned = assignment.getAssigned();
                if (assigned instanceof CtVariableWrite<?>
                        && ((CtVariableWrite<?>) assigned).getVariable().equals(ref)) {
                    int index = topLevelIndex(assignment, body);
                    if (index >= 0 && index < assertionIndex && index >= bestIndex) {
                        bestIndex = index;
                        rhs = assignment.getAssignment();
                    }
                }
            }
        }
        if (rhs == null && ref instanceof CtLocalVariableReference) {
            CtLocalVariable<?> declaration = ((CtLocalVariableReference<?>) ref).getDeclaration();
            if (declaration != null) {
                rhs = declaration.getAssignment();
                bestIndex = topLevelIndex(declaration, body);
            }
        }
        return rhs == null ? null : new ReachingWrite(rhs, bestIndex);
    }

    private static int topLevelIndex(CtElement element, List<CtStatement> body) {
        CtElement current = element;
        while (current != null) {
            for (int i = 0; i < body.size(); i++) {
                if (body.get(i) == current) {
                    return i;
                }
            }
            current = current.getParent();
        }
        return -1;
    }

    private static final class ReachingWrite {
        final CtExpression<?> rhs;
        final int index;

        ReachingWrite(CtExpression<?> rhs, int index) {
            this.rhs = rhs;
            this.index = index;
        }
    }
}
