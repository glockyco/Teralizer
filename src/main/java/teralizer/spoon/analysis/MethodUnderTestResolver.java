package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.util.Configuration;
import teralizer.util.TypeCapability;

/**
 * Confidence-ranked method-under-test resolution for one assertion.
 *
 * <p>This resolver separates three questions that are easy to conflate in assertion analysis:
 * which expression supplies the observed oracle value, which production class the test appears to
 * be about, and how strong the evidence is for the picked method. The returned
 * {@link MutResolution} therefore records both the call selected for downstream generation and the
 * provenance that made that call believable.
 *
 * <p>For value assertions, resolution starts at the assertion's "actual" argument and traces the
 * data dependency back to the call that produced the asserted value. The trace follows
 * straight-line local and field writes, descends through expression operands, and unwraps
 * zero-argument inspectors such as {@code isEmpty()} when their receiver has a visible producer.
 * A trace through a straight-line reaching definition is a proof; a producer found only through a
 * nested or heuristic write is kept but graded weaker. If an inspector's receiver producer is not
 * visible, the inspector remains the pick and is flagged as shallow instead of being hidden inside
 * a generic "proven" result.
 *
 * <p>For {@code assertThrows}, the executable body is the slice: a single call is a proven
 * producer, while multiple calls are ranked deterministically with the last call as the current
 * positional winner. In both assertion families, weak picks can be corroborated by independent
 * identity indicators: a method-name match with the test name and membership in the resolved focal
 * class. The focal class is inferred from the test class name and, when real source positions are
 * available, the mirrored {@code src/test/java -> src/main/java} path.
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

    /**
     * Resolves the method under test for one assertion. Never returns null and never abstains
     * silently: every outcome is a {@link MutResolution} whose status, tier, and deciding signal
     * explain what was picked and how strong the evidence is. The pick itself is computed by
     * {@link #resolveInternal}; this wrapper only enriches the result with the input-topology
     * telemetry ({@code actualShape}, {@code receiverProvenance}) used to size future recipe work.
     */
    public static MutResolution resolve(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        FocalTypeResolver focalTypeResolver
    ) {
        MutResolution resolution = resolveInternal(testMethod, assertion, focalTypeResolver);
        CtExpression<?> actual = actualExpression(testMethod, assertion);
        return resolution.withTopology(
            InputTopologyClassifier.classifyShape(actual),
            InputTopologyClassifier.receiverProvenance(actual, testMethod, assertion)
        );
    }

    /**
     * Computes the MUT pick/tier exactly as before topology enrichment.  Keeping this body separate
     * makes the topology pass visibly pure telemetry: it can describe the assertion's input shape,
     * but it cannot influence status, tier, pick, alternatives, or no-pick reason.
     */
    private static MutResolution resolveInternal(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        FocalTypeResolver focalTypeResolver
    ) {
        FocalTypeResolver.Focal focal = focalTypeResolver.resolveFocalType(testMethod);
        if (assertion == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }

        if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            if (!TestAnalysis.isJUnit5Assertion(assertion) || assertion.getArguments().size() < 2) {
                return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
            }
            return resolveAssertThrows(testMethod, assertion, focal);
        }

        Optional<TestAnalysis.NormalizedAssertion> view = TestAnalysis.normalizedAssertion(assertion);
        if (view.isPresent()
            && view.get().getKind() == TestAnalysis.AssertionKind.THROWS
            && "fail".equals(assertion.getExecutable().getSimpleName())) {
            return resolveTryFailCatch(testMethod, assertion, focal);
        }
        if (!view.isPresent() || view.get().getActualExpression() == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }

        return resolveValueAssertion(testMethod, assertion, view.get().getActualExpression(), focal);
    }

    /**
     * Extracts the assertion's actual expression for topology classification.  Unsupported
     * assertions and assertThrows return null because their "actual" is not a value expression in
     * the sense used by the R1/R2 topology taxonomy.
     */
    private static CtExpression<?> actualExpression(CtMethod<?> testMethod, CtInvocation<?> assertion) {
        if (assertion == null
                || assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            return null;
        }
        Optional<TestAnalysis.NormalizedAssertion> view = TestAnalysis.normalizedAssertion(assertion);
        return view.map(TestAnalysis.NormalizedAssertion::getActualExpression).orElse(null);
    }


    // --- assertThrows: the executed body is the slice ---

    /**
     * Resolves {@code assertThrows(E.class, executable)}: the executable body is the slice, since
     * the throwing call is by definition inside it. A single invocation is cardinality-forced
     * evidence (T1) — no other call could have thrown. With multiple invocations the last one wins
     * positionally (constructor calls typically precede the throwing call), which is a guess-grade
     * base that identity corroborators may promote; the losers are recorded as alternatives.
     */
    private static MutResolution resolveAssertThrows(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        FocalTypeResolver.Focal focal
    ) {
        CtElement body = getExecutedBody(assertion.getArguments().get(1)).orElse(null);
        if (body == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }
        List<CtInvocation<?>> invocations = body.getElements(new TypeFilter<>(CtInvocation.class));
        if (invocations.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL, focal);
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
                false,
                focal
            );
        }
        // Multiple calls: last-call position decided => guess-grade base.
        return rankedBase(
            testMethod,
            pick,
            MutResolution.Signal.ASSERT_THROWS_LAMBDA,
            alternativesExcluding(invocations, pick),
            false,
            false,
            focal
        );
    }

    private static MutResolution resolveTryFailCatch(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        FocalTypeResolver.Focal focal
    ) {
        CtTry tryBlock = assertion.getParent(CtTry.class);
        if (tryBlock == null || tryBlock.getBody() == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }
        List<CtInvocation<?>> invocations = invocationsBeforeFail(tryBlock, assertion);
        if (invocations.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL, focal);
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
                false,
                focal
            );
        }
        return rankedBase(
            testMethod,
            pick,
            MutResolution.Signal.ASSERT_THROWS_LAMBDA,
            alternativesExcluding(invocations, pick),
            false,
            false,
            focal
        );
    }

    private static List<CtInvocation<?>> invocationsBeforeFail(CtTry tryBlock, CtInvocation<?> assertion) {
        List<CtInvocation<?>> invocations = new ArrayList<>();
        for (CtStatement statement : tryBlock.getBody().getStatements()) {
            if (statement == assertion || !statement.getElements(candidate -> candidate == assertion).isEmpty()) {
                return invocations;
            }
            invocations.addAll(statement.getElements(new TypeFilter<>(CtInvocation.class)));
        }
        return invocations;
    }

    // --- value assertions: producer tracing ---

    /**
     * Resolves an ordinary value assertion by tracing the asserted "actual" expression back to its
     * producing call(s). Four outcomes, in order: (1) dataflow found several producers (a composite
     * expression) — rank them and keep the losers as alternatives; (2) dataflow found exactly one —
     * a straight-line trace is a proof (T1), a heuristic trace is weak (T3), and a shallow
     * inspector pick first gets one chance to be replaced by the pre-assertion production pool
     * (the mutator-then-inspect shape); (3) dataflow REFUTED a producer (killed definition) — the
     * result is NONE, and slice elimination must not resurrect the stale call; (4) dataflow was
     * silent — fall back to the production pool, where cardinality one is again a proof.
     */
    private static MutResolution resolveValueAssertion(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        CtExpression<?> actual,
        FocalTypeResolver.Focal focal
    ) {
        List<Traced> producers = traceExpression(actual, testMethod, assertion, new HashSet<>());
        if (producers.size() >= 2) {
            List<Traced> ranked = rankTraces(producers, testMethod, focal);
            Traced winner = ranked.get(0);
            MutResolution.Signal signal = allSameSignal(ranked, MutResolution.Signal.SUBEXPRESSION_PRODUCER)
                ? MutResolution.Signal.SUBEXPRESSION_PRODUCER
                : MutResolution.Signal.RANKED_GUESS;
            return rankedBase(
                testMethod,
                winner.producer,
                signal,
                alternativesAfterFirst(ranked),
                winner.inspectorUnwrapped,
                winner.shallowInspectorPick,
                focal
            );
        }
        if (producers.size() == 1) {
            Traced first = producers.get(0);
            if (first.shallowInspectorPick) {
                List<CtInvocation<?>> pool = productionCallsBefore(testMethod, assertion);
                pool.remove(first.producer);
                if (!pool.isEmpty()) {
                    return resolveFromProductionPool(testMethod, pool, focal);
                }
            }
            return graded(
                testMethod,
                first.producer,
                first.signal,
                first.proven ? MutResolution.Tier.T1_PROVEN : MutResolution.Tier.T3_SINGLE_WEAK,
                new ArrayList<MutResolution.Candidate>(),
                first.inspectorUnwrapped,
                first.shallowInspectorPick,
                focal
            );
        }
        /*
         * Cardinality-1 elimination is a proof only when dataflow was silent: no producer was found,
         * but exactly one production call exists in the pre-assertion slice.  If the trace reached a
         * variable/field write whose right-hand side had no producer, dataflow has refuted an older
         * producer by showing a killed definition (for example x = 5).  In that case slice
         * elimination must not resurrect the stale call merely because it is unique in the prefix.
         */
        if (hasKilledDefinition(actual, testMethod, assertion, new HashSet<>())) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL, focal);
        }
        return resolveFromProductionPool(testMethod, productionCallsBefore(testMethod, assertion), focal);
    }

    /** A traced producer: the call plus whether the trace was a straight-line reaching definition. */
    private static final class Traced {
        final CtInvocation<?> producer;
        final boolean proven;
        final MutResolution.Signal signal;
        final boolean inspectorUnwrapped;
        final boolean shallowInspectorPick;

        Traced(CtInvocation<?> producer, boolean proven, MutResolution.Signal signal) {
            this(producer, proven, signal, false, false);
        }

        Traced(
            CtInvocation<?> producer,
            boolean proven,
            MutResolution.Signal signal,
            boolean inspectorUnwrapped,
            boolean shallowInspectorPick
        ) {
            this.producer = producer;
            this.proven = proven;
            this.signal = signal;
            this.inspectorUnwrapped = inspectorUnwrapped;
            this.shallowInspectorPick = shallowInspectorPick;
        }
    }

    /** The nearest write found by the same straight-line walk used for producer tracing. */
    private static final class ReachingWrite {
        final CtExpression<?> rhs;
        final boolean proven;
        final int index;

        ReachingWrite(CtExpression<?> rhs, boolean proven, int index) {
            this.rhs = rhs;
            this.proven = proven;
            this.index = index;
        }
    }

    /**
     * Applies the pre-assertion slice fallback after dataflow has either stayed silent or produced a
     * shallow inspector whose real receiver-producer was unreachable.  A single remaining
     * production call is cardinality-forced evidence: there is exactly one non-oracle, non-library,
     * non-test-helper method call in the slice that could have affected the later assertion, so it is
     * graded as T1.  Larger pools are guesses: the same pool is ordered by the ranking comparator,
     * alternatives preserve the losing candidates, and identity corroborators may promote the base
     * T4 grade through rankedBase.
     */
    private static MutResolution resolveFromProductionPool(
        CtMethod<?> testMethod,
        List<CtInvocation<?>> pool,
        FocalTypeResolver.Focal focal
    ) {
        if (pool.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL, focal);
        }
        List<CtInvocation<?>> ranked = rankedProductionCalls(pool, testMethod, focal);
        CtInvocation<?> pick = ranked.get(0);
        if (ranked.size() == 1) {
            return graded(
                testMethod,
                pick,
                MutResolution.Signal.UNIQUE_PRODUCER_ELIMINATION,
                MutResolution.Tier.T1_PROVEN,
                new ArrayList<MutResolution.Candidate>(),
                false,
                false,
                focal
            );
        }
        return rankedBase(
            testMethod,
            pick,
            MutResolution.Signal.RANKED_GUESS,
            alternativesExcluding(ranked, pick),
            false,
            false,
            focal
        );
    }

    /**
     * Ranks traced producers with the same comparator used for slice candidates, preserving each
     * trace's original signal/flags on the winning producer.  This is only a ranking step; when all
     * candidates came from one composite expression, the caller keeps SUBEXPRESSION_PRODUCER as the
     * deciding signal because dataflow found the candidate set and ranking only broke the tie.
     */
    private static List<Traced> rankTraces(List<Traced> traces, CtMethod<?> testMethod, FocalTypeResolver.Focal focal) {
        List<Traced> ranked = new ArrayList<>(traces);
        final Comparator<CtInvocation<?>> comparator = rankingComparator(testMethod, focal);
        ranked.sort(new Comparator<Traced>() {
            @Override
            public int compare(Traced left, Traced right) {
                return comparator.compare(left.producer, right.producer);
            }
        });
        return ranked;
    }

    /**
     * Orders production calls deterministically.  The comparator is lexicographic, not weighted:
     * (1) type-eligible candidates first because an ineligible pick would immediately die at the
     * generator's parameter/return filters; (2) focal-class members first because the resolved CUT is
     * the best static scope signal but must never veto dataflow; (3) method-name matches first because
     * Methods2Test-style names are independent weak evidence; (4) later top-level statements first
     * because proximity to the assertion is LCBA's one useful hint; (5) source position in ascending
     * syntactic order as a stable tie-breaker.
     */
    private static List<CtInvocation<?>> rankedProductionCalls(
        List<CtInvocation<?>> pool,
        CtMethod<?> testMethod,
        FocalTypeResolver.Focal focal
    ) {
        List<CtInvocation<?>> ranked = new ArrayList<>(pool);
        ranked.sort(rankingComparator(testMethod, focal));
        return ranked;
    }

    /**
     * Builds the ranking comparator shared by traced multi-producer choices and slice-elimination
     * pools.  Every comparison level is a descending preference except the final source-position
     * tie-breaker, which keeps syntactic order stable and deterministic.
     */
    private static Comparator<CtInvocation<?>> rankingComparator(
        final CtMethod<?> testMethod,
        final FocalTypeResolver.Focal focal
    ) {
        return new Comparator<CtInvocation<?>>() {
            @Override
            public int compare(CtInvocation<?> left, CtInvocation<?> right) {
                int byType = Boolean.compare(typeEligible(right), typeEligible(left));
                if (byType != 0) {
                    return byType;
                }
                int byFocal = Boolean.compare(isFocalMember(right, focal), isFocalMember(left, focal));
                if (byFocal != 0) {
                    return byFocal;
                }
                int byName = Boolean.compare(
                    nameMatches(testMethod.getSimpleName(), right.getExecutable().getSimpleName()),
                    nameMatches(testMethod.getSimpleName(), left.getExecutable().getSimpleName())
                );
                if (byName != 0) {
                    return byName;
                }
                List<CtStatement> body = testMethod.getBody().getStatements();
                int byPosition = Integer.compare(topLevelIndex(right, body), topLevelIndex(left, body));
                if (byPosition != 0) {
                    return byPosition;
                }
                return Integer.compare(sourceOrder(left), sourceOrder(right));
            }
        };
    }

    /**
     * Returns every pre-assertion call that could plausibly be the MUT producer.  The pool contains
     * CtInvocations in earlier top-level statements only: assertion arguments are oracle code and
     * must not vote for themselves.  Assertion libraries are excluded by name/package because they
     * encode test checks, not production behavior.  Calls declared on the test class or its
     * superclasses are excluded as test helpers.  java/javax calls are excluded because the
     * generalizer never targets platform-library methods as the MUT.
     */
    private static List<CtInvocation<?>> productionCallsBefore(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        List<CtInvocation<?>> pool = new ArrayList<>();
        if (testMethod == null || testMethod.getBody() == null || assertion == null) {
            return pool;
        }
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        if (assertionIndex < 0) {
            return pool;
        }
        for (CtStatement statement : body) {
            for (CtInvocation<?> invocation : statement.getElements(new TypeFilter<>(CtInvocation.class))) {
                int index = topLevelIndex(invocation, body);
                if (index >= 0 && index < assertionIndex && isProductionCall(invocation, testMethod)) {
                    pool.add(invocation);
                }
            }
        }
        return pool;
    }

    /** Returns true when all traces carry the given signal; used to preserve composite dataflow. */
    private static boolean allSameSignal(List<Traced> traces, MutResolution.Signal signal) {
        for (Traced trace : traces) {
            if (trace.signal != signal) {
                return false;
            }
        }
        return !traces.isEmpty();
    }

    /**
     * A production call is a source-slice invocation that is not part of assertion/oracle machinery,
     * not a test-owned helper, and not a platform-library call.  The exclusions keep elimination from
     * picking code the downstream generator cannot or should not generalize.  Explicit constructor
     * invocations (super()/this() are CtInvocations whose executable is a CONSTRUCTOR) are excluded
     * outright: a constructor is never the method under test, and downstream consumers cast the
     * pick's declaration to CtMethod.
     */
    private static boolean isProductionCall(CtInvocation<?> invocation, CtMethod<?> testMethod) {
        return !invocation.getExecutable().isConstructor()
            && !isAssertionLibraryCall(invocation)
            && !isTestOwnHelper(invocation, testMethod)
            && !isJavaOrJavaxCall(invocation);
    }

    /** Assertion frameworks and mocking verification methods describe checks, not production work. */
    private static boolean isAssertionLibraryCall(CtInvocation<?> invocation) {
        String name = invocation.getExecutable().getSimpleName();
        if (name.startsWith("assert") || name.startsWith("fail") || name.startsWith("verify")) {
            return true;
        }
        String declaring = declaringTypeName(invocation);
        return startsWithAny(declaring, "org.junit", "org.hamcrest", "org.assertj", "org.mockito", "org.testng");
    }

    /** Test-class and superclass methods are helpers around the test, never the production MUT. */
    private static boolean isTestOwnHelper(CtInvocation<?> invocation, CtMethod<?> testMethod) {
        String declaring = declaringTypeName(invocation);
        if (declaring == null || testMethod == null || testMethod.getDeclaringType() == null) {
            return false;
        }
        CtType<?> current = testMethod.getDeclaringType();
        while (current != null) {
            if (declaring.equals(current.getQualifiedName())) {
                return true;
            }
            CtTypeReference<?> superclass = current.getSuperclass();
            current = superclass == null ? null : superclass.getTypeDeclaration();
        }
        return false;
    }

    /** Platform-library calls are characterization-only at best and cannot be the production MUT. */
    private static boolean isJavaOrJavaxCall(CtInvocation<?> invocation) {
        return startsWithAny(declaringTypeName(invocation), "java.", "javax.");
    }

    /** Source-model methods with at least one generatable input and a supported return rank first. */
    private static boolean typeEligible(CtInvocation<?> invocation) {
        if (!(invocation.getExecutable().getDeclaration() instanceof CtMethod<?>)) {
            return false;
        }
        CtMethod<?> method = (CtMethod<?>) invocation.getExecutable().getDeclaration();
        boolean hasGeneratedInput = false;
        for (CtParameter<?> parameter : method.getParameters()) {
            if (TypeCapability.supportsGeneratedInput(typeName(parameter.getType()))) {
                hasGeneratedInput = true;
                break;
            }
        }
        return hasGeneratedInput && TypeCapability.supportsReturnValue(typeName(method.getType()));
    }

    /** Converts a Spoon type reference into the qualified name expected by TypeCapability. */
    private static String typeName(CtTypeReference<?> type) {
        return type == null ? null : type.getQualifiedName();
    }

    /** Focal membership is a ranking preference and corroborator, never a hard gate. */
    private static boolean isFocalMember(CtInvocation<?> invocation, FocalTypeResolver.Focal focal) {
        return Boolean.TRUE.equals(focalAgreement(invocation, focal));
    }

    /** Null-safe prefix check for declaring-type package filters. */
    private static boolean startsWithAny(String value, String... prefixes) {
        if (value == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Source-position order is the deterministic final tie-breaker when semantic signals tie. */
    private static int sourceOrder(CtInvocation<?> invocation) {
        SourcePosition position = invocation.getPosition();
        if (position == null || !position.isValidPosition()) {
            return Integer.MAX_VALUE;
        }
        return position.getSourceStart();
    }

    /**
     * Detects the "refuting" case for slice elimination.  The same reaching-definition walk used
     * for producers is followed to the nearest write; if that write's RHS has no visible producer,
     * an older producer has been killed and the correct result is NONE rather than resurrecting a
     * unique earlier call from the slice.
     */
    private static boolean hasKilledDefinition(
        CtExpression<?> expression,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        if (expression instanceof CtBinaryOperator<?>) {
            CtBinaryOperator<?> binary = (CtBinaryOperator<?>) expression;
            return hasKilledDefinition(binary.getLeftHandOperand(), testMethod, assertion, visited)
                || hasKilledDefinition(binary.getRightHandOperand(), testMethod, assertion, visited);
        }
        if (expression instanceof CtUnaryOperator<?>) {
            return hasKilledDefinition(
                ((CtUnaryOperator<?>) expression).getOperand(),
                testMethod,
                assertion,
                visited
            );
        }
        if (expression instanceof CtVariableRead<?>) {
            CtVariableReference<?> ref = ((CtVariableRead<?>) expression).getVariable();
            if (!(ref instanceof CtLocalVariableReference) || visited.contains(ref)) {
                return false;
            }
            Set<CtVariableReference<?>> nextVisited = new HashSet<>(visited);
            nextVisited.add(ref);
            ReachingWrite write = nearestLocalWrite(ref, testMethod, assertion);
            return write != null && rhsRefutesProducer(write.rhs, testMethod, assertion, nextVisited);
        }
        if (expression instanceof CtFieldRead<?>) {
            CtFieldRead<?> fieldRead = (CtFieldRead<?>) expression;
            if (!isThisOrUnqualified(fieldRead.getTarget()) || visited.contains(fieldRead.getVariable())) {
                return false;
            }
            Set<CtVariableReference<?>> nextVisited = new HashSet<>(visited);
            nextVisited.add(fieldRead.getVariable());
            ReachingWrite write = nearestFieldWrite(fieldRead.getVariable(), testMethod, assertion);
            return write != null && rhsRefutesProducer(write.rhs, testMethod, assertion, nextVisited);
        }
        return false;
    }

    /**
     * A reached RHS refutes older producers when tracing it yields no producer.  Nested variable or
     * field reads are checked recursively so copies of a killed value preserve the guard.
     */
    private static boolean rhsRefutesProducer(
        CtExpression<?> rhs,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        if (rhs == null) {
            return false;
        }
        if (!traceExpression(rhs, testMethod, assertion, visited).isEmpty()) {
            return false;
        }
        if (hasKilledDefinition(rhs, testMethod, assertion, visited)) {
            return true;
        }
        return true;
    }

    /*
     * Producer tracing walks from the asserted value back to the call that produced it.
     * It only treats straight-line writes in the method body as proven; writes hidden in
     * control-flow blocks are useful candidates, but they are weaker because the branch
     * may not have executed on every path reaching the assertion.
     */
    private static List<Traced> traceExpression(
        CtExpression<?> expression,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        if (expression instanceof CtInvocation<?>) {
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            if (isInspector(invocation)) {
                return traceInspector(invocation, testMethod, assertion, visited);
            }
            return single(new Traced(
                invocation,
                true,
                MutResolution.Signal.DIRECT_ACTUAL_CALL
            ));
        }
        // Operators do not produce values themselves; their operands might.  A single
        // operand producer is still a proof for this expression, while a composite
        // with multiple producers becomes a ranked choice until the full ranker lands.
        if (expression instanceof CtBinaryOperator<?>) {
            CtBinaryOperator<?> binary = (CtBinaryOperator<?>) expression;
            List<Traced> traces = new ArrayList<>();
            traces.addAll(traceExpression(binary.getLeftHandOperand(), testMethod, assertion, visited));
            traces.addAll(traceExpression(binary.getRightHandOperand(), testMethod, assertion, visited));
            return wrapTrace(
                traces,
                traces.size() == 1,
                MutResolution.Signal.SUBEXPRESSION_PRODUCER
            );
        }
        if (expression instanceof CtUnaryOperator<?>) {
            CtUnaryOperator<?> unary = (CtUnaryOperator<?>) expression;
            return wrapTrace(
                traceExpression(unary.getOperand(), testMethod, assertion, visited),
                true,
                MutResolution.Signal.SUBEXPRESSION_PRODUCER
            );
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

    private static List<Traced> traceInspector(
        CtInvocation<?> inspector,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        CtExpression<?> receiver = inspector.getTarget();
        if (receiver instanceof CtInvocation<?>) {
            return markInspectorUnwrapped(traceExpression(receiver, testMethod, assertion, visited));
        }
        if (receiver instanceof CtVariableRead<?> || receiver instanceof CtFieldRead<?>) {
            List<Traced> receiverProducers = traceExpression(receiver, testMethod, assertion, visited);
            if (!receiverProducers.isEmpty()) {
                return markInspectorUnwrapped(receiverProducers);
            }
            return single(new Traced(
                inspector,
                true,
                MutResolution.Signal.DIRECT_ACTUAL_CALL,
                false,
                true
            ));
        }
        return single(new Traced(inspector, true, MutResolution.Signal.DIRECT_ACTUAL_CALL));
    }

    private static List<Traced> markInspectorUnwrapped(List<Traced> inner) {
        List<Traced> unwrapped = new ArrayList<>();
        for (Traced traced : inner) {
            unwrapped.add(new Traced(
                traced.producer,
                traced.proven,
                MutResolution.Signal.INSPECTOR_UNWRAP,
                true,
                traced.shallowInspectorPick
            ));
        }
        return unwrapped;
    }

    /**
     * Ghafari-style inspector (ICST'15): zero-argument, non-void, and either a conventional
     * accessor name or declared on a JDK type. Conservative by design.
     */
    static boolean isInspector(CtInvocation<?> invocation) {
        if (!invocation.getArguments().isEmpty()) {
            return false;
        }
        if (invocation.getType() == null || "void".equals(invocation.getType().getSimpleName())) {
            return false;
        }
        String name = invocation.getExecutable().getSimpleName();
        if (name.startsWith("get") || name.startsWith("is") || name.startsWith("has")) {
            return true;
        }
        switch (name) {
            case "size": case "length": case "isEmpty": case "toString": case "hashCode":
            case "name": case "ordinal": case "value": case "count":
                return true;
            default:
                break;
        }
        String declaring = invocation.getExecutable().getDeclaringType() == null
            ? "" : invocation.getExecutable().getDeclaringType().getQualifiedName();
        return declaring.startsWith("java.") || declaring.startsWith("javax.");
    }

    /**
     * Local variables use reaching-definition semantics: the nearest prior write wins, even when
     * that write is a literal that kills an older call-produced value.  The helper returns the same
     * nearest RHS used by the elimination guard so producer tracing and refutation agree.
     */
    private static List<Traced> traceLocalVariable(
        CtVariableReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        ReachingWrite write = nearestLocalWrite(ref, testMethod, assertion);
        return wrapTrace(
            traceExpression(write == null ? null : write.rhs, testMethod, assertion, visited),
            write != null && write.proven,
            MutResolution.Signal.LOCAL_VARIABLE_PRODUCER
        );
    }

    /**
     * Finds the nearest local declaration or assignment before the assertion.  Direct body writes are
     * proven; nested writes/declarations are retained for recall but marked weak because a branch may
     * not have executed.  If the top-level scan cannot see a nested declaration, the declaration
     * fallback preserves the pre-existing weak behavior.
     */
    private static ReachingWrite nearestLocalWrite(
        CtVariableReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        CtExpression<?> rhs = null;
        boolean writeProven = false;
        int bestIndex = -1;
        for (CtStatement statement : body) {
            for (CtLocalVariable<?> local : statement.getElements(new TypeFilter<>(CtLocalVariable.class))) {
                if (local.getReference().equals(ref)) {
                    int index = topLevelIndex(local, body);
                    if (index >= 0 && index < assertionIndex && index >= bestIndex) {
                        bestIndex = index;
                        rhs = local.getAssignment();
                        writeProven = isDirectBodyStatement(local, body);
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
                bestIndex = topLevelIndex(declaration, body);
            }
        }
        return rhs == null ? null : new ReachingWrite(rhs, writeProven, bestIndex);
    }

    /**
     * Fields are intentionally narrower than locals: only writes to this.field (or unqualified
     * field) inside the test method count.  Setup methods and collaborators are out of scope, so
     * absence of an in-method write means "no visible producer."
     */
    private static List<Traced> traceField(
        CtFieldReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Set<CtVariableReference<?>> visited
    ) {
        ReachingWrite write = nearestFieldWrite(ref, testMethod, assertion);
        return wrapTrace(
            traceExpression(write == null ? null : write.rhs, testMethod, assertion, visited),
            write != null && write.proven,
            MutResolution.Signal.FIELD_PRODUCER
        );
    }

    /**
     * Finds the nearest qualifying field assignment before the assertion.  Field state can be
     * overwritten by several writes; only a single straight-line write proves the producer, while the
     * nearest visible write remains a weak candidate.
     */
    private static ReachingWrite nearestFieldWrite(
        CtFieldReference<?> ref,
        CtMethod<?> testMethod,
        CtInvocation<?> assertion
    ) {
        List<CtStatement> body = testMethod.getBody().getStatements();
        int assertionIndex = topLevelIndex(assertion, body);
        CtExpression<?> rhs = null;
        boolean directWrite = false;
        int writeCount = 0;
        int bestIndex = -1;
        for (CtStatement statement : body) {
            for (CtAssignment<?, ?> assignment : statement.getElements(new TypeFilter<>(CtAssignment.class))) {
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
        return rhs == null ? null : new ReachingWrite(rhs, writeCount == 1 && directWrite, bestIndex);
    }

    // Apply the outer mechanism while preserving stronger inner provenance such as
    // field-over-subexpression and local-over-subexpression.
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
                strongerSignal(wrapperSignal, traced.signal),
                traced.inspectorUnwrapped,
                traced.shallowInspectorPick
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
        return target == null || target instanceof CtThisAccess;
    }

    private static boolean sameField(CtFieldReference<?> left, CtFieldReference<?> right) {
        return left != null
            && right != null
            && left.getSimpleName().equals(right.getSimpleName())
            && Objects.equals(fieldDeclaringTypeName(left), fieldDeclaringTypeName(right));
    }

    private static String fieldDeclaringTypeName(CtFieldReference<?> field) {
        CtTypeReference<?> declaringType = field.getDeclaringType();
        return declaringType == null ? null : declaringType.getQualifiedName();
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
        boolean shallow,
        FocalTypeResolver.Focal focal
    ) {
        EnumSet<MutResolution.Corroborator> corroborators = corroboratorsFor(testMethod, pick, focal);
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
            shallow,
            focal
        );
    }

    /** Grade a pick that position/ranking decided: base T4, promotable to T3/T2 by indicators. */
    private static MutResolution rankedBase(
        CtMethod<?> testMethod,
        CtInvocation<?> pick,
        MutResolution.Signal signal,
        List<MutResolution.Candidate> alternatives,
        boolean inspectorUnwrapped,
        boolean shallow,
        FocalTypeResolver.Focal focal
    ) {
        EnumSet<MutResolution.Corroborator> corroborators = corroboratorsFor(testMethod, pick, focal);
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
            inspectorUnwrapped,
            shallow,
            focal
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

    /** Name and focal-class matching are independent identity indicators. */
    private static EnumSet<MutResolution.Corroborator> corroboratorsFor(
        CtMethod<?> testMethod,
        CtInvocation<?> pick,
        FocalTypeResolver.Focal focal
    ) {
        EnumSet<MutResolution.Corroborator> set = EnumSet.noneOf(MutResolution.Corroborator.class);
        if (nameMatches(testMethod.getSimpleName(), pick.getExecutable().getSimpleName())) {
            set.add(MutResolution.Corroborator.NAME_MATCH);
        }
        if (Boolean.TRUE.equals(focalAgreement(pick, focal))) {
            set.add(MutResolution.Corroborator.FOCAL_CLASS_MEMBER);
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

    private static MutResolution none(MutResolution.NoPickReason reason, FocalTypeResolver.Focal focal) {
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
            false,
            focal
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
        boolean shallow,
        FocalTypeResolver.Focal focal
    ) {
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
            focal.qualifiedName,
            focal.source,
            focalAgreement(pick, focal),
            null,
            null
        );
    }

    private static Boolean focalAgreement(CtInvocation<?> pick, FocalTypeResolver.Focal focal) {
        if (pick == null || focal == null || focal.qualifiedName == null) {
            return null;
        }
        String declaringType = declaringTypeName(pick);
        return declaringType == null ? Boolean.FALSE : Boolean.valueOf(focal.qualifiedName.equals(declaringType));
    }

    private static String declaringTypeName(CtInvocation<?> pick) {
        return pick.getExecutable().getDeclaringType() == null
            ? null
            : pick.getExecutable().getDeclaringType().getQualifiedName();
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
            body = newClass.getElements(new TypeFilter<>(CtMethod.class)).stream()
                .filter(m -> m.getSimpleName().equals("execute"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "Could not find execute method of anonymous class"
                )).getBody();
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
