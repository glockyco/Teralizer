package teralizer.spoon.analysis;

import java.io.File;
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
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtVariableReference;
import teralizer.util.Configuration;

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

    public static MutResolution resolve(CtMethod<?> testMethod, CtInvocation<?> assertion) {
        Focal focal = resolveFocalType(testMethod);
        if (assertion == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }

        if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            return resolveAssertThrows(testMethod, assertion, focal);
        }

        Optional<Integer> index = TestAnalysis.getActualParameterIndex(assertion);
        if (!index.isPresent()) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }

        CtExpression<?> actual = assertion.getArguments().get(index.get());
        return resolveValueAssertion(testMethod, assertion, actual, focal);
    }

    // --- assertThrows: the executed body is the slice ---

    private static MutResolution resolveAssertThrows(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        Focal focal
    ) {
        CtElement body = getExecutedBody(assertion.getArguments().get(1)).orElse(null);
        if (body == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, focal);
        }
        List<CtInvocation<?>> invocations = body.getElements(CtInvocation.class::isInstance);
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

    // --- value assertions: producer tracing (extended in Tasks 4-8) ---

    private static MutResolution resolveValueAssertion(
        CtMethod<?> testMethod,
        CtInvocation<?> assertion,
        CtExpression<?> actual,
        Focal focal
    ) {
        List<Traced> producers = traceExpression(actual, testMethod, assertion, new HashSet<>());
        if (producers.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL, focal);
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
                first.inspectorUnwrapped,
                first.shallowInspectorPick,
                focal
            );
        }
        return rankedBase(testMethod, first.producer, first.signal, alternatives,
            first.inspectorUnwrapped, first.shallowInspectorPick, focal);
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

    /*
     * Local variables use reaching-definition semantics: the nearest prior write wins,
     * even when that write is a literal that kills an older call-produced value.
     */
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
        // If the nearest in-scope write is not visible from the top-level statement
        // scan, retain the old nested-declaration fallback but grade it as unproven.
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

    /*
     * Fields are intentionally narrower than locals: only writes to this.field (or
     * unqualified field) inside the test method count. Setup methods and collaborators
     * are out of scope, so absence of an in-method write means "no visible producer."
     */
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
        // Field state can be overwritten by several writes; only a single straight-line
        // write proves the producer, otherwise the nearest visible write is weak.
        boolean proven = writeCount == 1 && directWrite;
        return wrapTrace(
            traceExpression(rhs, testMethod, assertion, visited),
            proven,
            MutResolution.Signal.FIELD_PRODUCER
        );
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
        return target == null || "this".equals(target.toString());
    }

    private static boolean sameField(CtFieldReference<?> left, CtFieldReference<?> right) {
        return left != null && right != null && left.getSimpleName().equals(right.getSimpleName());
    }

    private static final class Focal {
        final String qualifiedName;
        final MutResolution.FocalSource source;

        Focal(String qualifiedName, MutResolution.FocalSource source) {
            this.qualifiedName = qualifiedName;
            this.source = source;
        }
    }

    private static Focal noFocal() {
        return new Focal(null, MutResolution.FocalSource.NONE);
    }

    private static Focal resolveFocalType(CtMethod<?> testMethod) {
        if (testMethod == null || testMethod.getDeclaringType() == null) {
            return noFocal();
        }
        CtType<?> testType = testMethod.getDeclaringType();
        String focalSimpleName = stripTestAffix(testType.getSimpleName());
        CtType<?> nameDerived = focalSimpleName == null
            ? null
            : findTypeBySimpleName(testMethod, focalSimpleName, packageName(testType));
        String mirroredPath = realMirrorPath(testType);
        CtType<?> pathDerived = mirroredPath == null ? null : findTypeByPath(testMethod, mirroredPath);
        if (nameDerived != null) {
            MutResolution.FocalSource source = pathsEqual(mirroredPath, sourcePath(nameDerived))
                ? MutResolution.FocalSource.PATH_AND_NAME
                : MutResolution.FocalSource.NAME_ONLY;
            return new Focal(nameDerived.getQualifiedName(), source);
        }
        if (pathDerived != null) {
            return new Focal(pathDerived.getQualifiedName(), MutResolution.FocalSource.PATH_ONLY);
        }
        return noFocal();
    }

    private static CtType<?> findTypeBySimpleName(
        CtMethod<?> testMethod,
        String simpleName,
        String preferredPackage
    ) {
        CtType<?> fallback = null;
        for (CtElement element : testMethod.getFactory().getModel().getElements(CtType.class::isInstance)) {
            CtType<?> type = (CtType<?>) element;
            if (!simpleName.equals(type.getSimpleName())) {
                continue;
            }
            if (preferredPackage.equals(packageName(type))) {
                return type;
            }
            if (fallback == null) {
                fallback = type;
            }
        }
        return fallback;
    }

    private static CtType<?> findTypeByPath(CtMethod<?> testMethod, String mirroredPath) {
        for (CtElement element : testMethod.getFactory().getModel().getElements(CtType.class::isInstance)) {
            CtType<?> type = (CtType<?>) element;
            if (pathsEqual(mirroredPath, sourcePath(type))) {
                return type;
            }
        }
        return null;
    }

    private static String realMirrorPath(CtType<?> testType) {
        SourcePosition position = testType.getPosition();
        if (position == null || !position.isValidPosition() || position.getFile() == null
                || !position.getFile().isFile()) {
            return null;
        }
        return mirrorTestPath(position.getFile().getPath());
    }

    private static String sourcePath(CtType<?> type) {
        SourcePosition position = type.getPosition();
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return null;
        }
        return position.getFile().getPath();
    }

    private static boolean pathsEqual(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizePath(left).equals(normalizePath(right));
    }

    private static String normalizePath(String path) {
        return new File(path).getAbsoluteFile().toURI().normalize().getPath();
    }

    private static String packageName(CtType<?> type) {
        if (type == null || type.getPackage() == null || type.getPackage().getQualifiedName() == null) {
            return "";
        }
        return type.getPackage().getQualifiedName();
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
        Focal focal
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
        Focal focal
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
        Focal focal
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

    /**
     * Mirror a test-source path to its production twin (Methods2Test path matching):
     * src/test/java/<pkg>/FooTest.java -> src/main/java/<pkg>/Foo.java. Returns null when the path
     * is not under src/test or the file name carries no Test/Tests/IT/ITCase/TestCase prefix/suffix.
     */
    static String mirrorTestPath(String testPath) {
        if (testPath == null || !testPath.contains("src/test/java/")) {
            return null;
        }
        int slash = testPath.lastIndexOf('/');
        String dir = testPath.substring(0, slash + 1).replace("src/test/java/", "src/main/java/");
        String file = testPath.substring(slash + 1);
        if (!file.endsWith(".java")) {
            return null;
        }
        String base = file.substring(0, file.length() - ".java".length());
        String stripped = stripTestAffix(base);
        return stripped == null ? null : dir + stripped + ".java";
    }

    /** FooTest/FooTests/FooIT/FooITCase/FooTestCase/TestFoo -> Foo; null when no affix present. */
    static String stripTestAffix(String simpleName) {
        String[] suffixes = { "TestCase", "ITCase", "Tests", "Test", "IT" };
        for (String suffix : suffixes) {
            if (simpleName.endsWith(suffix) && simpleName.length() > suffix.length()) {
                return simpleName.substring(0, simpleName.length() - suffix.length());
            }
        }
        if (simpleName.startsWith("Test") && simpleName.length() > 4) {
            return simpleName.substring(4);
        }
        return null;
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

    private static MutResolution none(MutResolution.NoPickReason reason, Focal focal) {
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
        Focal focal
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

    private static Boolean focalAgreement(CtInvocation<?> pick, Focal focal) {
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
