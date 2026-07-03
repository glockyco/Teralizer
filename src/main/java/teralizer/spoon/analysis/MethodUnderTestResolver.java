package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtVariableReference;
import teralizer.util.Configuration;

/**
 * Confidence-ranked method-under-test resolution for one assertion.
 *
 * <p>The resolver first identifies the expression whose value is asserted. For ordinary value
 * assertions, that is the "actual" argument. For {@code assertThrows}, that is the executable
 * body. A direct invocation in the actual position wins immediately, a one-hop local variable can
 * point back to the nearest invocation that assigned it, and {@code assertThrows} uses the last
 * invocation in the executed body.
 *
 * <p>The result is total. A visible source-model method is
 * {@link MutResolution.Status#RESOLVED}; a visible call outside the source model is
 * {@link MutResolution.Status#CHARACTERIZATION_ONLY}; missing or unsupported shapes are explicit
 * {@link MutResolution.Status#NONE} results with a reason. The tier records how strong the
 * deciding evidence was, and alternatives preserve the calls that lost a ranked choice.
 */
public final class MethodUnderTestResolver {

    private MethodUnderTestResolver() {
    }

    public static MutResolution resolve(CtMethod<?> testMethod, CtInvocation<?> assertion) {
        if (assertion == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE);
        }

        if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            return resolveAssertThrows(testMethod, assertion);
        }

        Optional<Integer> index = TestAnalysis.getActualParameterIndex(assertion);
        if (!index.isPresent()) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE);
        }

        CtExpression<?> actual = assertion.getArguments().get(index.get());
        return resolveValueAssertion(testMethod, assertion, actual);
    }

    // --- assertThrows: the executed body is the slice ---

    private static MutResolution resolveAssertThrows(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        CtElement body = getExecutedBody(assertion.getArguments().get(1)).orElse(null);
        if (body == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE);
        }
        List<CtInvocation<?>> invocations = body.getElements(CtInvocation.class::isInstance);
        if (invocations.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
        }
        CtInvocation<?> pick = invocations.get(invocations.size() - 1);
        if (invocations.size() == 1) {
            return graded(
                testMethod,
                pick,
                MutResolution.Signal.ASSERT_THROWS_LAMBDA,
                MutResolution.Tier.T1_PROVEN,
                alternativesExcluding(invocations, pick),
                false,
                false
            );
        }
        // Multiple calls: last-call position decided => guess-grade base.
        return rankedBase(
            testMethod,
            pick,
            MutResolution.Signal.ASSERT_THROWS_LAMBDA,
            alternativesExcluding(invocations, pick)
        );
    }

    // --- value assertions: producer tracing (extended in Tasks 4-8) ---

    private static MutResolution resolveValueAssertion(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        CtExpression<?> actual
    ) {
        List<Traced> producers = traceExpression(actual, testMethod, assertion, new HashSet<>());
        if (producers.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
        }
        Traced first = producers.get(0);
        List<MutResolution.Candidate> alternatives = alternativesAfterFirst(producers);
        if (producers.size() == 1) {
            return graded(
                testMethod,
                first.producer,
                first.signal,
                first.proven ? MutResolution.Tier.T1_PROVEN : MutResolution.Tier.T3_SINGLE_WEAK,
                alternatives,
                false,
                false
            );
        }
        return rankedBase(testMethod, first.producer, first.signal, alternatives);
    }

    /** A traced producer: the call plus whether the trace was a straight-line reaching definition. */
    private static final class Traced {
        final CtInvocation<?> producer;
        final boolean proven;
        final MutResolution.Signal signal;

        Traced(CtInvocation<?> producer, boolean proven, MutResolution.Signal signal) {
            this.producer = producer;
            this.proven = proven;
            this.signal = signal;
        }
    }

    private static List<Traced> traceExpression(
        CtExpression<?> expression,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        if (expression instanceof CtInvocation<?>) {
            return single(new Traced(
                (CtInvocation<?>) expression,
                true,
                MutResolution.Signal.DIRECT_ACTUAL_CALL
            ));
        }
        if (expression instanceof CtFieldRead<?>) {
            CtFieldRead<?> fieldRead = (CtFieldRead<?>) expression;
            if (!isThisOrUnqualified(fieldRead.getTarget())) {
                return new ArrayList<>();
            }
            CtFieldReference<?> ref = fieldRead.getVariable();
            if (visited.contains(ref)) {
                return new ArrayList<>();
            }
            Set<CtVariableReference<?>> nextVisited = new HashSet<>(visited);
            nextVisited.add(ref);
            return traceField(ref, testMethod, assertion, nextVisited);
        }
        if (expression instanceof CtVariableRead<?>) {
            CtVariableReference<?> ref = ((CtVariableRead<?>) expression).getVariable();
            if (!(ref instanceof CtLocalVariableReference)) {
                return new ArrayList<>();
            }
            if (visited.contains(ref)) {
                return new ArrayList<>();
            }
            Set<CtVariableReference<?>> nextVisited = new HashSet<>(visited);
            nextVisited.add(ref);
            return traceLocalVariable(ref, testMethod, assertion, nextVisited);
        }
        return new ArrayList<>();
    }

    private static List<Traced> traceLocalVariable(
        CtVariableReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        CtExpression<?> rhs = null;
        boolean writeProven = false;
        int bestIndex = -1;
        for (CtStatement statement : body) {
            for (CtElement localElement : statement.getElements(CtLocalVariable.class::isInstance)) {
                CtLocalVariable<?> local = (CtLocalVariable<?>) localElement;
                if (local.getReference().equals(ref)) {
                    int index = topLevelIndex(local, body);
                    if (index >= 0 && index < assertionIndex && index >= bestIndex) {
                        bestIndex = index;
                        rhs = local.getAssignment();
                        writeProven = isDirectBodyStatement(local, body);
                    }
                }
            }
            for (CtElement assignmentElement : statement.getElements(CtAssignment.class::isInstance)) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) assignmentElement;
                CtExpression<?> assigned = assignment.getAssigned();
                if (assigned instanceof CtVariableWrite<?>
                        && ((CtVariableWrite<?>) assigned).getVariable().equals(ref)) {
                    int index = topLevelIndex(assignment, body);
                    if (index >= 0 && index < assertionIndex && index >= bestIndex) {
                        bestIndex = index;
                        rhs = assignment.getAssignment();
                        writeProven = isDirectBodyStatement(assignment, body);
                    }
                }
            }
        }
        if (rhs == null && ref instanceof CtLocalVariableReference) {
            CtLocalVariable<?> declaration = ((CtLocalVariableReference<?>) ref).getDeclaration();
            if (declaration != null) {
                rhs = declaration.getAssignment();
                writeProven = false;
            }
        }
        return wrapTrace(
            traceExpression(rhs, testMethod, assertion, visited),
            writeProven,
            MutResolution.Signal.LOCAL_VARIABLE_PRODUCER
        );
    }

    private static List<Traced> traceField(
        CtFieldReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        CtExpression<?> rhs = null;
        boolean directWrite = false;
        int writeCount = 0;
        int bestIndex = -1;
        for (CtStatement statement : body) {
            for (CtElement assignmentElement : statement.getElements(CtAssignment.class::isInstance)) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) assignmentElement;
                CtExpression<?> assigned = assignment.getAssigned();
                if (assigned instanceof CtFieldWrite<?>) {
                    CtFieldWrite<?> fieldWrite = (CtFieldWrite<?>) assigned;
                    int index = topLevelIndex(assignment, body);
                    if (sameField(fieldWrite.getVariable(), ref)
                            && isThisOrUnqualified(fieldWrite.getTarget())
                            && index >= 0
                            && index < assertionIndex) {
                        writeCount++;
                        if (index >= bestIndex) {
                            bestIndex = index;
                            rhs = assignment.getAssignment();
                            directWrite = isDirectBodyStatement(assignment, body);
                        }
                    }
                }
            }
        }
        boolean proven = writeCount == 1 && directWrite;
        return wrapTrace(
            traceExpression(rhs, testMethod, assertion, visited),
            proven,
            MutResolution.Signal.FIELD_PRODUCER
        );
    }

    private static List<Traced> wrapTrace(
        List<Traced> inner,
        boolean wrapperProven,
        MutResolution.Signal wrapperSignal
    ) {
        List<Traced> wrapped = new ArrayList<>();
        for (Traced traced : inner) {
            wrapped.add(new Traced(
                traced.producer,
                wrapperProven && traced.proven,
                strongerSignal(wrapperSignal, traced.signal)
            ));
        }
        return wrapped;
    }

    private static List<Traced> single(Traced traced) {
        List<Traced> traces = new ArrayList<>();
        traces.add(traced);
        return traces;
    }

    private static MutResolution.Signal strongerSignal(
        MutResolution.Signal outer,
        MutResolution.Signal inner
    ) {
        return signalStrength(outer) >= signalStrength(inner) ? outer : inner;
    }

    private static int signalStrength(MutResolution.Signal signal) {
        switch (signal) {
            case INSPECTOR_UNWRAP:
                return 5;
            case FIELD_PRODUCER:
                return 4;
            case LOCAL_VARIABLE_PRODUCER:
                return 3;
            case SUBEXPRESSION_PRODUCER:
                return 2;
            case DIRECT_ACTUAL_CALL:
                return 1;
            default:
                return 0;
        }
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

    private static boolean isDirectBodyStatement(CtElement element, List<CtStatement> body) {
        for (CtStatement statement : body) {
            if (statement == element) {
                return true;
            }
        }
        return false;
    }

    private static boolean isThisOrUnqualified(CtExpression<?> target) {
        return target == null || "this".equals(target.toString());
    }

    private static boolean sameField(CtFieldReference<?> left, CtFieldReference<?> right) {
        return left != null && right != null && left.getSimpleName().equals(right.getSimpleName());
    }

    // --- grading ---

    /** Grade a pick whose mechanism-tier is already known (T1 proofs, weak single producers). */
    private static MutResolution graded(
        CtMethod<?> testMethod,
        CtInvocation<?> pick,
        MutResolution.Signal signal,
        MutResolution.Tier mechanismTier,
        List<MutResolution.Candidate> alternatives,
        boolean inspectorUnwrapped,
        boolean shallow
    ) {
        EnumSet<MutResolution.Corroborator> corroborators = corroboratorsFor(testMethod, pick);
        MutResolution.Tier tier = promote(mechanismTier, corroborators.size());
        return build(
            statusFor(pick),
            tier,
            signal,
            corroborators,
            noPickReasonFor(pick),
            pick,
            alternatives,
            alternatives.size() + 1,
            inspectorUnwrapped,
            shallow
        );
    }

    /** Grade a pick that position/ranking decided: base T4, promotable to T3/T2 by indicators. */
    private static MutResolution rankedBase(
        CtMethod<?> testMethod,
        CtInvocation<?> pick,
        MutResolution.Signal signal,
        List<MutResolution.Candidate> alternatives
    ) {
        EnumSet<MutResolution.Corroborator> corroborators = corroboratorsFor(testMethod, pick);
        MutResolution.Tier tier = promote(MutResolution.Tier.T4_GUESS, corroborators.size());
        return build(
            statusFor(pick),
            tier,
            signal,
            corroborators,
            noPickReasonFor(pick),
            pick,
            alternatives,
            alternatives.size() + 1,
            false,
            false
        );
    }

    /**
     * Identity-indicator promotion: T1 never moves; a weak single (T3) with an indicator becomes
     * T2; a ranked pick (T4) with exactly one indicator becomes T3, with two or more becomes T2.
     */
    private static MutResolution.Tier promote(MutResolution.Tier base, int indicatorCount) {
        if (base == MutResolution.Tier.T1_PROVEN || indicatorCount == 0) {
            return base;
        }
        if (base == MutResolution.Tier.T3_SINGLE_WEAK) {
            return MutResolution.Tier.T2_CORROBORATED;
        }
        if (base == MutResolution.Tier.T4_GUESS) {
            return indicatorCount >= 2
                ? MutResolution.Tier.T2_CORROBORATED
                : MutResolution.Tier.T3_SINGLE_WEAK;
        }
        return base;
    }

    /** Name matching is the first identity indicator; focal-class matching is added later. */
    private static EnumSet<MutResolution.Corroborator> corroboratorsFor(
        CtMethod<?> testMethod,
        CtInvocation<?> pick
    ) {
        EnumSet<MutResolution.Corroborator> set = EnumSet.noneOf(MutResolution.Corroborator.class);
        if (nameMatches(testMethod.getSimpleName(), pick.getExecutable().getSimpleName())) {
            set.add(MutResolution.Corroborator.NAME_MATCH);
        }
        return set;
    }

    /**
     * Methods2Test-style name strip: testGcd ~ gcd. Conservative: candidate name must be >= 3 chars
     * (rejects get/is/of coincidences) and contained in the normalized test name.
     */
    static boolean nameMatches(String testMethodName, String candidateName) {
        if (candidateName == null || candidateName.length() < 3) {
            return false;
        }
        String normalizedTest = testMethodName.toLowerCase();
        if (normalizedTest.startsWith("test")) {
            normalizedTest = normalizedTest.substring(4);
        }
        return normalizedTest.contains(candidateName.toLowerCase());
    }

    private static MutResolution.Status statusFor(CtInvocation<?> pick) {
        if (pick == null) {
            return MutResolution.Status.NONE;
        }
        return pick.getExecutable().getDeclaration() instanceof CtMethod<?>
            ? MutResolution.Status.RESOLVED
            : MutResolution.Status.CHARACTERIZATION_ONLY;
    }

    private static MutResolution.NoPickReason noPickReasonFor(CtInvocation<?> pick) {
        if (pick == null || pick.getExecutable().getDeclaration() instanceof CtMethod<?>) {
            return null;
        }
        // Declaration unresolved: JDK/classpath types have no source declaration in the model.
        if (pick.getExecutable().getDeclaringType() != null) {
            String qualifiedName = pick.getExecutable().getDeclaringType().getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("java.")) {
                return MutResolution.NoPickReason.LIBRARY_DECLARATION;
            }
            if (pick.getExecutable().getDeclaringType().getTypeDeclaration() == null) {
                return MutResolution.NoPickReason.LIBRARY_DECLARATION;
            }
        }
        return MutResolution.NoPickReason.UNRESOLVED_SOURCE_DECLARATION;
    }

    private static MutResolution none(MutResolution.NoPickReason reason) {
        return build(
            MutResolution.Status.NONE,
            MutResolution.Tier.T5_NONE,
            MutResolution.Signal.NONE,
            EnumSet.noneOf(MutResolution.Corroborator.class),
            reason,
            null,
            new ArrayList<MutResolution.Candidate>(),
            0,
            false,
            false
        );
    }

    private static MutResolution build(
        MutResolution.Status status,
        MutResolution.Tier tier,
        MutResolution.Signal signal,
        EnumSet<MutResolution.Corroborator> corroborators,
        MutResolution.NoPickReason reason,
        CtInvocation<?> pick,
        List<MutResolution.Candidate> alternatives,
        int candidateCount,
        boolean inspectorUnwrapped,
        boolean shallow
    ) {
        // Focal, shape, and provenance classification are populated by later resolver stages.
        return new MutResolution(
            status,
            tier,
            signal,
            corroborators,
            reason,
            pick,
            alternatives,
            candidateCount,
            inspectorUnwrapped,
            shallow,
            null,
            MutResolution.FocalSource.NONE,
            null,
            null,
            null
        );
    }

    private static List<MutResolution.Candidate> alternativesAfterFirst(List<Traced> traces) {
        List<MutResolution.Candidate> alternatives = new ArrayList<>();
        for (int i = 1; i < traces.size(); i++) {
            alternatives.add(toCandidate(traces.get(i).producer));
        }
        return alternatives;
    }

    private static List<MutResolution.Candidate> alternativesExcluding(
        List<CtInvocation<?>> pool,
        CtInvocation<?> pick
    ) {
        List<MutResolution.Candidate> alternatives = new ArrayList<>();
        for (CtInvocation<?> invocation : pool) {
            if (invocation != pick) {
                alternatives.add(toCandidate(invocation));
            }
        }
        return alternatives;
    }

    static MutResolution.Candidate toCandidate(CtInvocation<?> invocation) {
        String declaringType = invocation.getExecutable().getDeclaringType() == null
            ? null
            : invocation.getExecutable().getDeclaringType().getQualifiedName();
        return new MutResolution.Candidate(
            invocation.getExecutable().getSimpleName(),
            declaringType,
            invocation.toString()
        );
    }

    // Handles executable bodies passed to assertThrows, including lambdas and executable objects.
    private static Optional<CtElement> getExecutedBody(CtElement element) {
        CtElement body = null;
        if (element instanceof CtExecutable) {
            CtExecutable<?> executable = (CtExecutable<?>) element;
            if (executable instanceof CtLambda) {
                if (executable.getBody() == null) {
                    body = ((CtLambda<?>) executable).getExpression();
                } else {
                    body = executable.getBody();
                }
            } else {
                body = executable.getBody();
            }
        } else if (element instanceof CtNewClass) {
            // Check the execute method
            CtNewClass<?> newClass = (CtNewClass<?>) element;
            body = ((CtMethod<?>) newClass.getElements(CtMethod.class::isInstance).stream()
                .filter(m -> ((CtMethod<?>) m).getSimpleName().equals("execute"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "Could not find execute method of anonymous class"
                ))).getBody();
        } else if (element instanceof CtVariableRead) {
            // Check declaration of variable
            CtLocalVariableReference<?> reference =
                (CtLocalVariableReference<?>) ((CtVariableRead<?>) element).getVariable();
            CtLocalVariable<?> declaration = reference.getDeclaration();
            CtExpression<?> assignment = declaration.getAssignment();
            return getExecutedBody(assignment);
        } else if (element instanceof CtExecutableReferenceExpression) {
            // If element is a method reference like this::someMethod,
            // we cannot distinguish whether someMethod is the method
            // that is tested or contains the method that is tested.
            //
            // Thus, we return an empty body to remove the test
            // via the missing values filter.
            return Optional.empty();
        }
        return Optional.ofNullable(body);
    }
}
