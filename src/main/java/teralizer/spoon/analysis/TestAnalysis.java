package teralizer.spoon.analysis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import teralizer.util.Configuration;

public class TestAnalysis {
    private static final int NO_INDEX = -1;
    // junit.framework.Assert and org.junit.Assert share the message-first argument order.
    private static final List<AssertionFramework> MESSAGE_FIRST_FRAMEWORKS =
        Arrays.asList(AssertionFramework.JUNIT3, AssertionFramework.JUNIT4);
    private static final Map<AssertionIndexKey, AssertionIndexes> ASSERT_EQUALS_INDEXES =
        createAssertEqualsIndexTable();
    private static final Map<AssertionIndexKey, AssertionIndexes> CONDITION_INDEXES =
        createConditionIndexTable();
    private static final String ASSERT_THAT = "assertThat";
    private static final String HAMCREST_MATCHER_ASSERT = "org.hamcrest.MatcherAssert";
    private static final String HAMCREST_PACKAGE = "org.hamcrest.";

    // JUnit 4:
    //
    // - assertEquals(double expected, double actual)
    // - assertEquals(double expected, double actual, double delta)
    // - assertEquals(long expected, long actual)
    // - assertEquals(Object[] expecteds, Object[] actuals)
    // - assertEquals(Object expected, Object actual)
    // - assertEquals(String message, double expected, double actual)
    // - assertEquals(String message, double expected, double actual, double delta)
    // - assertEquals(String message, long expected, long actual)
    // - assertEquals(String message, Object[] expecteds, Object[] actuals)
    // - assertEquals(String message, Object expected, Object actual)
    //
    // - assertTrue(boolean condition)
    // - assertTrue(String message, boolean condition)
    //
    // - assertFalse(boolean condition)
    // - assertFalse(String message, boolean condition)

    // JUnit 5:
    //
    // - assertEquals(byte expected, byte actual)
    // - assertEquals(byte expected, byte actual, String message)
    // - assertEquals(byte expected, byte actual, Supplier<String> messageSupplier)
    // - assertEquals(char expected, char actual)
    // - assertEquals(char expected, char actual, String message)
    // - assertEquals(char expected, char actual, Supplier<String> messageSupplier)
    // - assertEquals(double expected, double actual)
    // - assertEquals(double expected, double actual, double delta)
    // - assertEquals(double expected, double actual, double delta, String message)
    // - assertEquals(double expected, double actual, double delta, Supplier<String> messageSupplier)
    // - assertEquals(double expected, double actual, String message)
    // - assertEquals(double expected, double actual, Supplier<String> messageSupplier)
    // - assertEquals(float expected, float actual)
    // - assertEquals(float expected, float actual, float delta)
    // - assertEquals(float expected, float actual, float delta, String message)
    // - assertEquals(float expected, float actual, float delta, Supplier<String> messageSupplier)
    // - assertEquals(float expected, float actual, String message)
    // - assertEquals(float expected, float actual, Supplier<String> messageSupplier)
    // - assertEquals(int expected, int actual)
    // - assertEquals(int expected, int actual, String message)
    // - assertEquals(int expected, int actual, Supplier<String> messageSupplier)
    // - assertEquals(long expected, long actual)
    // - assertEquals(long expected, long actual, String message)
    // - assertEquals(long expected, long actual, Supplier<String> messageSupplier)
    // - assertEquals(Object expected, Object actual)
    // - assertEquals(Object expected, Object actual, String message)
    // - assertEquals(Object expected, Object actual, Supplier<String> messageSupplier)
    // - assertEquals(short expected, short actual)
    // - assertEquals(short expected, short actual, String message)
    // - assertEquals(short expected, short actual, Supplier<String> messageSupplier)
    //
    // - assertTrue(boolean condition)
    // - assertTrue(boolean condition, String message)
    // - assertTrue(boolean condition, Supplier<String> messageSupplier)
    // - assertTrue(BooleanSupplier booleanSupplier)
    // - assertTrue(BooleanSupplier booleanSupplier, String message)
    // - assertTrue(BooleanSupplier booleanSupplier, Supplier<String> messageSupplier)
    //
    // - assertFalse(boolean condition)
    // - assertFalse(boolean condition, String message)
    // - assertFalse(boolean condition, Supplier<String> messageSupplier)
    // - assertFalse(BooleanSupplier booleanSupplier)
    // - assertFalse(BooleanSupplier booleanSupplier, String message)
    // - assertFalse(BooleanSupplier booleanSupplier, Supplier<String> messageSupplier)
    //
    // - assertThrows(Class<T> expectedType, Executable executable)
    // - assertThrows(Class<T> expectedType, Executable executable, String message)
    // - assertThrows(Class<T> expectedType, Executable executable, Supplier<String> messageSupplier)



    public static Optional<CtInvocation<?>> findGeneralizableAssert(CtMethod<?> method) {
        return findGeneralizableAsserts(method).stream().findFirst();
    }

    public static List<CtInvocation<?>> findGeneralizableAsserts(CtMethod<?> method) {
        return method.getElements(e -> TestAnalysis.isAssertion(e) && TestAnalysis.isGeneralizable(e));
    }

    public static List<CtInvocation<?>> findAllAsserts(CtMethod<?> method) {
        return method.getElements(TestAnalysis::isAssertion);
    }

    public static boolean isGeneralizable(String assertionName) {
        return Configuration.GENERALIZABLE_ASSERTS.contains(assertionName);
    }

    public static boolean isGeneralizable(CtInvocation<?> assertionInvocation) {
        return normalizedAssertion(assertionInvocation).isPresent();
    }

    public static Optional<NormalizedAssertion> normalizedAssertion(CtInvocation<?> assertion) {
        if (assertion == null || !isAssertion(assertion)) {
            return Optional.empty();
        }
        List<CtExpression<?>> arguments = assertion.getArguments();
        AssertionFramework framework = assertionFrameworkOrNull(assertion);

        switch (assertion.getExecutable().getSimpleName()) {
            case Configuration.ASSERT_EQUALS: {
                if (framework == null) {
                    return Optional.empty();
                }
                AssertionIndexes indexes = assertEqualsIndexes(assertion, framework, arguments.size());
                return Optional.of(new NormalizedAssertion(
                    AssertionKind.EQUALITY,
                    arguments.get(indexes.actualIndex().get()),
                    arguments.get(indexes.expectedIndex().get()),
                    assertion,
                    indexes.expectedIndex().get()));
            }
            case Configuration.ASSERT_TRUE: {
                if (framework == null) {
                    return Optional.empty();
                }
                AssertionIndexes indexes = conditionIndexes(assertion, framework, arguments.size());
                return Optional.of(new NormalizedAssertion(
                    AssertionKind.BOOLEAN_TRUE,
                    arguments.get(indexes.actualIndex().get()),
                    null,
                    null,
                    NO_INDEX));
            }
            case Configuration.ASSERT_FALSE: {
                if (framework == null) {
                    return Optional.empty();
                }
                AssertionIndexes indexes = conditionIndexes(assertion, framework, arguments.size());
                return Optional.of(new NormalizedAssertion(
                    AssertionKind.BOOLEAN_FALSE,
                    arguments.get(indexes.actualIndex().get()),
                    null,
                    null,
                    NO_INDEX));
            }
            case Configuration.ASSERT_THROWS:
                if (framework != AssertionFramework.JUNIT5 || arguments.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new NormalizedAssertion(
                    AssertionKind.THROWS,
                    null,
                    arguments.get(0),
                    assertion,
                    0,
                    exceptionTypeNameFromClassLiteral(arguments.get(0)).orElse(null)));
            case "fail":
                return normalizedFail(assertion, framework);
            case ASSERT_THAT:
                return normalizedAssertThat(assertion);
            default:
                return Optional.empty();
        }
    }

    public static boolean isAssertion(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        CtTypeReference<?> declaringType = executable.getDeclaringType();

        if (declaringType == null) {
            return false;
        }

        String qualifiedName = declaringType.getQualifiedName();
        return qualifiedName.equals(Configuration.JUNIT4_ASSERTION_PACKAGE)
            || qualifiedName.equals(Configuration.JUNIT5_ASSERTION_PACKAGE)
            || qualifiedName.equals(Configuration.JUNIT3_ASSERTION_PACKAGE)
            || qualifiedName.equals(Configuration.JUNIT3_TEST_CASE_CLASS)
            || qualifiedName.equals(HAMCREST_MATCHER_ASSERT);
    }

    public static Optional<Integer> getActualParameterIndex(CtInvocation<?> assertion) {
        String assertionName = assertion.getExecutable().getSimpleName();
        if (ASSERT_THAT.equals(assertionName)) {
            return Optional.empty();
        }
        AssertionFramework framework = assertionFrameworkOrNull(assertion);
        if (framework == null) {
            return Optional.empty();
        }
        int argumentCount = assertion.getArguments().size();

        switch (assertionName) {
            case Configuration.ASSERT_EQUALS:
                return assertEqualsIndexes(assertion, framework, argumentCount).actualIndex();
            case Configuration.ASSERT_TRUE:
            case Configuration.ASSERT_FALSE:
                return conditionIndexes(assertion, framework, argumentCount).actualIndex();
            default:
                return Optional.empty();
        }
    }

    public static Optional<Integer> getExpectedParameterIndex(CtInvocation<?> assertion) {
        String assertionName = assertion.getExecutable().getSimpleName();
        if (ASSERT_THAT.equals(assertionName)) {
            return Optional.empty();
        }
        AssertionFramework framework = assertionFrameworkOrNull(assertion);
        if (framework == null) {
            return Optional.empty();
        }
        int argumentCount = assertion.getArguments().size();

        if (assertionName.equals(Configuration.ASSERT_EQUALS)) {
            return assertEqualsIndexes(assertion, framework, argumentCount).expectedIndex();
        } else if (assertionName.equals(Configuration.ASSERT_THROWS)) {
            if (framework == AssertionFramework.JUNIT5) {
                return Optional.of(0);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static AssertionIndexes assertEqualsIndexes(
        CtInvocation<?> assertion,
        AssertionFramework framework,
        int argumentCount
    ) {
        AssertionIndexes indexes = ASSERT_EQUALS_INDEXES.get(
            new AssertionIndexKey(framework, argumentCount, isDoubleDelta(assertion)));
        if (indexes == null) {
            throw unexpectedParameterCount(assertion);
        }
        return indexes;
    }

    private static AssertionIndexes conditionIndexes(
        CtInvocation<?> assertion,
        AssertionFramework framework,
        int argumentCount
    ) {
        AssertionIndexes indexes = CONDITION_INDEXES.get(
            new AssertionIndexKey(framework, argumentCount, false));
        if (indexes == null) {
            throw unexpectedParameterCount(assertion);
        }
        return indexes;
    }

    private static Optional<NormalizedAssertion> normalizedAssertThat(CtInvocation<?> assertion) {
        List<CtExpression<?>> arguments = assertion.getArguments();
        int actualIndex;
        int matcherIndex;
        if (arguments.size() == 2) {
            actualIndex = 0;
            matcherIndex = 1;
        } else if (arguments.size() == 3) {
            actualIndex = 1;
            matcherIndex = 2;
        } else {
            return Optional.empty();
        }

        CtExpression<?> matcherExpression = arguments.get(matcherIndex);
        if (!(matcherExpression instanceof CtInvocation<?>)) {
            return Optional.empty();
        }
        Optional<ExpectedExpressionSite> expected = hamcrestEqualityExpected((CtInvocation<?>) matcherExpression);
        if (!expected.isPresent()) {
            return Optional.empty();
        }
        ExpectedExpressionSite site = expected.get();
        return Optional.of(new NormalizedAssertion(
            AssertionKind.EQUALITY,
            arguments.get(actualIndex),
            site.expression,
            site.owner,
            site.argumentIndex));
    }

    private static Optional<NormalizedAssertion> normalizedFail(
        CtInvocation<?> assertion,
        AssertionFramework framework
    ) {
        if (framework == null || !assertion.getArguments().isEmpty()) {
            return Optional.empty();
        }
        AssertionSemanticsClassifier.Result semantics = AssertionSemanticsClassifier.classify(assertion);
        if (!AssertionSemanticCodes.FAIL_CONTEXT_TRY_BLOCK_EXPECTING_EXCEPTION.equals(semantics.failContext())) {
            return Optional.empty();
        }
        CtTry tryBlock = assertion.getParent(CtTry.class);
        if (tryBlock == null || tryBlock.getCatchers().size() != 1) {
            return Optional.empty();
        }
        CtCatch catcher = tryBlock.getCatchers().get(0);
        if (catcher.getParameter() == null
            || catcher.getParameter().getType() == null
            || catcher.getParameter().getMultiTypes().size() != 1) {
            return Optional.empty();
        }
        if (!tryBlock.getBody().getStatements().contains(assertion)) {
            return Optional.empty();
        }
        if (!catchBodyIsEmptyOrCaughtMessageAssertions(catcher)) {
            return Optional.empty();
        }
        return Optional.of(new NormalizedAssertion(
            AssertionKind.THROWS,
            null,
            null,
            null,
            NO_INDEX,
            catcher.getParameter().getType().getQualifiedName()));
    }

    private static boolean catchBodyIsEmptyOrCaughtMessageAssertions(CtCatch catcher) {
        if (catcher.getBody() == null) {
            return true;
        }
        for (CtStatement statement : catcher.getBody().getStatements()) {
            if (!(statement instanceof CtInvocation<?>)) {
                return false;
            }
            if (!isCaughtMessageAssertion((CtInvocation<?>) statement, catcher.getParameter().getSimpleName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCaughtMessageAssertion(CtInvocation<?> assertion, String catchParameterName) {
        if (!Configuration.ASSERT_EQUALS.equals(assertion.getExecutable().getSimpleName())
            || !isAssertion(assertion)
            || assertion.getArguments().size() != 2) {
            return false;
        }
        CtExpression<?> actual = assertion.getArguments().get(1);
        if (!(actual instanceof CtInvocation<?>)) {
            return false;
        }
        CtInvocation<?> messageCall = (CtInvocation<?>) actual;
        if (!"getMessage".equals(messageCall.getExecutable().getSimpleName())
            || !messageCall.getArguments().isEmpty()
            || !(messageCall.getTarget() instanceof CtVariableRead<?>)) {
            return false;
        }
        CtVariableRead<?> target = (CtVariableRead<?>) messageCall.getTarget();
        return catchParameterName.equals(target.getVariable().getSimpleName());
    }

    private static Optional<String> exceptionTypeNameFromClassLiteral(CtExpression<?> expression) {
        if (expression instanceof CtFieldRead<?>) {
            CtFieldRead<?> fieldRead = (CtFieldRead<?>) expression;
            if ("class".equals(fieldRead.getVariable().getSimpleName())
                && fieldRead.getTarget() != null
                && fieldRead.getTarget().getType() != null) {
                return Optional.of(fieldRead.getTarget().getType().getQualifiedName());
            }
        }
        String source = expression.toString();
        if (source.endsWith(".class")) {
            return Optional.of(expression.getFactory().Type()
                .createReference(source.substring(0, source.length() - ".class".length()))
                .getQualifiedName());
        }
        return Optional.empty();
    }

    private static Optional<ExpectedExpressionSite> hamcrestEqualityExpected(CtInvocation<?> matcher) {
        if (!isHamcrestMatcherFactory(matcher)) {
            return Optional.empty();
        }
        List<CtExpression<?>> arguments = matcher.getArguments();
        if (arguments.size() != 1) {
            return Optional.empty();
        }

        String name = matcher.getExecutable().getSimpleName();
        CtExpression<?> onlyArgument = arguments.get(0);
        if ("equalTo".equals(name)) {
            return Optional.of(new ExpectedExpressionSite(onlyArgument, matcher, 0));
        }
        if (!"is".equals(name)) {
            return Optional.empty();
        }
        if (onlyArgument instanceof CtInvocation<?>) {
            CtInvocation<?> nested = (CtInvocation<?>) onlyArgument;
            if (isHamcrestMatcherFactory(nested)) {
                return "equalTo".equals(nested.getExecutable().getSimpleName())
                    ? hamcrestEqualityExpected(nested)
                    : Optional.empty();
            }
        }
        return Optional.of(new ExpectedExpressionSite(onlyArgument, matcher, 0));
    }

    private static boolean isHamcrestMatcherFactory(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null) {
            return false;
        }
        String name = executable.getSimpleName();
        if (!"is".equals(name) && !"equalTo".equals(name)) {
            return false;
        }
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        return declaringType != null && declaringType.getQualifiedName().startsWith(HAMCREST_PACKAGE);
    }

    private static AssertionFramework assertionFrameworkOrNull(CtInvocation<?> assertion) {
        if (isJUnit3Assertion(assertion)) {
            return AssertionFramework.JUNIT3;
        }
        if (isJUnit4Assertion(assertion)) {
            return AssertionFramework.JUNIT4;
        }
        if (isJUnit5Assertion(assertion)) {
            return AssertionFramework.JUNIT5;
        }
        return null;
    }

    private static boolean isDoubleDelta(CtInvocation<?> assertion) {
        return assertion.getExecutable().getParameters().stream()
            .allMatch(parameter -> parameter.getSimpleName().equals("double"));
    }

    private static RuntimeException unexpectedParameterCount(CtInvocation<?> assertion) {
        return new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
    }

    private static Map<AssertionIndexKey, AssertionIndexes> createAssertEqualsIndexTable() {
        Map<AssertionIndexKey, AssertionIndexes> table = new HashMap<>();
        for (AssertionFramework framework : MESSAGE_FIRST_FRAMEWORKS) {
            putAssertEquals(table, framework, 2, false, 0, 1);
            putAssertEquals(table, framework, 2, true, 0, 1);
            putAssertEquals(table, framework, 3, false, 1, 2);
            putAssertEquals(table, framework, 3, true, 0, 1);
            putAssertEquals(table, framework, 4, false, 1, 2);
            putAssertEquals(table, framework, 4, true, 1, 2);
        }
        putAssertEquals(table, AssertionFramework.JUNIT5, 2, false, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT5, 2, true, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT5, 3, false, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT5, 3, true, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT5, 4, false, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT5, 4, true, 0, 1);
        return table;
    }

    private static Map<AssertionIndexKey, AssertionIndexes> createConditionIndexTable() {
        Map<AssertionIndexKey, AssertionIndexes> table = new HashMap<>();
        for (AssertionFramework framework : MESSAGE_FIRST_FRAMEWORKS) {
            putCondition(table, framework, 1, 0);
            putCondition(table, framework, 2, 1);
        }
        putCondition(table, AssertionFramework.JUNIT5, 1, 0);
        putCondition(table, AssertionFramework.JUNIT5, 2, 0);
        return table;
    }

    private static void putAssertEquals(
        Map<AssertionIndexKey, AssertionIndexes> table,
        AssertionFramework framework,
        int argumentCount,
        boolean doubleDelta,
        int expectedIndex,
        int actualIndex
    ) {
        table.put(
            new AssertionIndexKey(framework, argumentCount, doubleDelta),
            new AssertionIndexes(expectedIndex, actualIndex));
    }

    private static void putCondition(
        Map<AssertionIndexKey, AssertionIndexes> table,
        AssertionFramework framework,
        int argumentCount,
        int actualIndex
    ) {
        table.put(
            new AssertionIndexKey(framework, argumentCount, false),
            new AssertionIndexes(NO_INDEX, actualIndex));
    }

    public enum AssertionKind {
        EQUALITY,
        BOOLEAN_TRUE,
        BOOLEAN_FALSE,
        THROWS
    }

    public static final class NormalizedAssertion {
        private final AssertionKind kind;
        private final CtExpression<?> actualExpression;
        private final CtExpression<?> expectedExpression;
        private final CtInvocation<?> expectedArgumentOwner;
        private final int expectedArgumentIndex;

        private final String expectedExceptionTypeName;

        private NormalizedAssertion(
            AssertionKind kind,
            CtExpression<?> actualExpression,
            CtExpression<?> expectedExpression,
            CtInvocation<?> expectedArgumentOwner,
            int expectedArgumentIndex
        ) {
            this(kind, actualExpression, expectedExpression, expectedArgumentOwner, expectedArgumentIndex, null);
        }

        private NormalizedAssertion(
            AssertionKind kind,
            CtExpression<?> actualExpression,
            CtExpression<?> expectedExpression,
            CtInvocation<?> expectedArgumentOwner,
            int expectedArgumentIndex,
            String expectedExceptionTypeName
        ) {
            this.kind = kind;
            this.actualExpression = actualExpression;
            this.expectedExpression = expectedExpression;
            this.expectedArgumentOwner = expectedArgumentOwner;
            this.expectedArgumentIndex = expectedArgumentIndex;
            this.expectedExceptionTypeName = expectedExceptionTypeName;
        }

        public AssertionKind getKind() {
            return this.kind;
        }

        public CtExpression<?> getActualExpression() {
            return this.actualExpression;
        }

        public CtExpression<?> getExpectedExpression() {
            return this.expectedExpression;
        }

        public String getExpectedExceptionTypeName() {
            return this.expectedExceptionTypeName;
        }

        public boolean hasReplaceableExpectedExpression() {
            return this.expectedArgumentOwner != null && this.expectedArgumentIndex >= 0;
        }

        public void replaceExpectedExpression(Factory factory, String expression) {
            if (!hasReplaceableExpectedExpression()) {
                return;
            }
            this.expectedArgumentOwner.getArguments()
                .set(this.expectedArgumentIndex, factory.Code().createCodeSnippetExpression(expression));
        }
    }

    private static final class ExpectedExpressionSite {
        private final CtExpression<?> expression;
        private final CtInvocation<?> owner;
        private final int argumentIndex;

        private ExpectedExpressionSite(CtExpression<?> expression, CtInvocation<?> owner, int argumentIndex) {
            this.expression = expression;
            this.owner = owner;
            this.argumentIndex = argumentIndex;
        }
    }

    private enum AssertionFramework {
        JUNIT3,
        JUNIT4,
        JUNIT5
    }

    private static final class AssertionIndexKey {
        private final AssertionFramework framework;
        private final int argumentCount;
        private final boolean doubleDelta;

        private AssertionIndexKey(AssertionFramework framework, int argumentCount, boolean doubleDelta) {
            this.framework = framework;
            this.argumentCount = argumentCount;
            this.doubleDelta = doubleDelta;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AssertionIndexKey)) {
                return false;
            }
            AssertionIndexKey other = (AssertionIndexKey) obj;
            return this.framework == other.framework
                && this.argumentCount == other.argumentCount
                && this.doubleDelta == other.doubleDelta;
        }

        @Override
        public int hashCode() {
            int result = this.framework.hashCode();
            result = 31 * result + this.argumentCount;
            result = 31 * result + (this.doubleDelta ? 1 : 0);
            return result;
        }
    }

    private static final class AssertionIndexes {
        private final int expectedIndex;
        private final int actualIndex;

        private AssertionIndexes(int expectedIndex, int actualIndex) {
            this.expectedIndex = expectedIndex;
            this.actualIndex = actualIndex;
        }

        private Optional<Integer> expectedIndex() {
            return this.expectedIndex == NO_INDEX ? Optional.empty() : Optional.of(this.expectedIndex);
        }

        private Optional<Integer> actualIndex() {
            return this.actualIndex == NO_INDEX ? Optional.empty() : Optional.of(this.actualIndex);
        }
    }

    /**
     * An assertion declared by any JUnit generation this pipeline analyzes. Call sites that act
     * on "an assertion we understand" use this instead of enumerating frameworks themselves.
     */
    public static boolean isSupportedFrameworkAssertion(CtInvocation<?> assertion) {
        return isJUnit3Assertion(assertion)
            || isJUnit4Assertion(assertion)
            || isJUnit5Assertion(assertion);
    }

    public static boolean isJUnit3Assertion(CtInvocation<?> assertion) {
        String declaringType = declaringTypeName(assertion);
        return Configuration.JUNIT3_ASSERTION_PACKAGE.equals(declaringType)
            || Configuration.JUNIT3_TEST_CASE_CLASS.equals(declaringType);
    }

    public static boolean isJUnit4Assertion(CtInvocation<?> assertion) {
        return Configuration.JUNIT4_ASSERTION_PACKAGE.equals(declaringTypeName(assertion));
    }

    public static boolean isJUnit5Assertion(CtInvocation<?> assertion) {
        return Configuration.JUNIT5_ASSERTION_PACKAGE.equals(declaringTypeName(assertion));
    }

    private static String declaringTypeName(CtInvocation<?> assertion) {
        if (assertion == null || assertion.getExecutable() == null || assertion.getExecutable().getDeclaringType() == null) {
            return null;
        }
        return assertion.getExecutable().getDeclaringType().getQualifiedName();
    }

    public static boolean isContainedInLoop(CtElement element) {
        assert element != null;

        CtElement parent = element.getParent();
        while (!(parent instanceof CtMethod)) {
            if (parent instanceof CtLoop) {
                return true;
            }
            parent = parent.getParent();
        }

        return false;
    }

    public static boolean containsAssertion(CtElement element) {
        return !element.getElements(TestAnalysis::isAssertion).isEmpty();
    }
}
