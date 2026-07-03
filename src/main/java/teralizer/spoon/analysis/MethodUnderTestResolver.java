package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
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

    // --- value assertions: today's one-hop logic (extended in Tasks 4-8) ---

    private static MutResolution resolveValueAssertion(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        CtExpression<?> actual
    ) {
        if (actual instanceof CtInvocation<?>) {
            return graded(
                testMethod,
                (CtInvocation<?>) actual,
                MutResolution.Signal.DIRECT_ACTUAL_CALL,
                MutResolution.Tier.T1_PROVEN,
                new ArrayList<MutResolution.Candidate>(),
                false,
                false
            );
        }
        if (actual instanceof CtVariableRead<?>) {
            CtVariableReference<?> ref = ((CtVariableRead<?>) actual).getVariable();
            if (!(ref instanceof CtLocalVariableReference)) {
                return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
            }
            CtInvocation<?> producer = nearestWriteProducer(testMethod, assertion, ref);
            if (producer != null) {
                return graded(
                    testMethod,
                    producer,
                    MutResolution.Signal.LOCAL_VARIABLE_PRODUCER,
                    MutResolution.Tier.T1_PROVEN,
                    new ArrayList<MutResolution.Candidate>(),
                    false,
                    false
                );
            }
        }
        return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
    }

    /**
     * Reaching definition on the top-level statement list: nearest write to {@code ref} before the
     * assertion whose RHS is an invocation; falls back to the declaration initializer.
     */
    private static CtInvocation<?> nearestWriteProducer(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        CtVariableReference<?> ref
    ) {
        List<CtStatement> statements = testMethod.getBody().getStatements();
        int assertionIndex = 0;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i) == assertion) {
                assertionIndex = i;
                break;
            }
        }
        for (int i = assertionIndex - 1; i >= 0; i--) {
            CtStatement statement = statements.get(i);
            if (statement instanceof CtLocalVariable<?>) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) statement;
                if (localVar.getReference().equals(ref)) {
                    CtExpression<?> assignment = localVar.getAssignment();
                    if (assignment instanceof CtInvocation<?>) {
                        return (CtInvocation<?>) assignment;
                    }
                    return null;
                }
            } else if (statement instanceof CtAssignment<?, ?>) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
                CtExpression<?> assigned = assignment.getAssigned();
                if (assigned instanceof CtVariableWrite<?>
                        && ((CtVariableWrite<?>) assigned).getVariable().equals(ref)) {
                    CtExpression<?> value = assignment.getAssignment();
                    if (value instanceof CtInvocation<?>) {
                        return (CtInvocation<?>) value;
                    }
                    return null;
                }
            }
        }
        // Declaration not on the top-level path (nested block): fall back to the initializer.
        CtLocalVariable<?> declaration = ((CtLocalVariableReference<?>) ref).getDeclaration();
        if (declaration != null && declaration.getAssignment() instanceof CtInvocation<?>) {
            return (CtInvocation<?>) declaration.getAssignment();
        }
        return null;
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
