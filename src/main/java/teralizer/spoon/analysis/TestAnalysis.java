package teralizer.spoon.analysis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import teralizer.util.Configuration;

public class TestAnalysis {
    private static final int NO_INDEX = -1;
    private static final Map<AssertionIndexKey, AssertionIndexes> ASSERT_EQUALS_INDEXES =
        createAssertEqualsIndexTable();
    private static final Map<AssertionIndexKey, AssertionIndexes> CONDITION_INDEXES =
        createConditionIndexTable();


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
        return isGeneralizable(assertionInvocation.getExecutable().getSimpleName());
    }

    public static boolean isAssertion(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        CtTypeReference<?> declaringType = executable.getDeclaringType();

        if (declaringType == null) {
            return false;
        }

        String qualifiedName = declaringType.getQualifiedName();
        return qualifiedName.equals(Configuration.JUNIT4_ASSERTION_PACKAGE) || qualifiedName.equals(Configuration.JUNIT5_ASSERTION_PACKAGE);
    }

    public static Optional<Integer> getActualParameterIndex(CtInvocation<?> assertion) {
        AssertionFramework framework = assertionFramework(assertion);
        int argumentCount = assertion.getArguments().size();

        switch (assertion.getExecutable().getSimpleName()) {
            case Configuration.ASSERT_EQUALS:
                return assertEqualsIndexes(assertion, framework, argumentCount).actualIndex();
            case Configuration.ASSERT_TRUE:
            case Configuration.ASSERT_FALSE:
                AssertionIndexes conditionIndexes = CONDITION_INDEXES.get(
                    new AssertionIndexKey(framework, argumentCount, false));
                if (conditionIndexes == null) {
                    throw unexpectedParameterCount(assertion);
                }
                return conditionIndexes.actualIndex();
            default:
                return Optional.empty();
        }
    }

    public static Optional<Integer> getExpectedParameterIndex(CtInvocation<?> assertion) {
        AssertionFramework framework = assertionFramework(assertion);
        int argumentCount = assertion.getArguments().size();

        if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_EQUALS)) {
            return assertEqualsIndexes(assertion, framework, argumentCount).expectedIndex();
        } else if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            if (framework == AssertionFramework.JUNIT4) {
                throw new RuntimeException("Unexpected JUnit 4 assertion:\n" + assertion);
            } else {
                return Optional.of(0);
            }
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

    private static AssertionFramework assertionFramework(CtInvocation<?> assertion) {
        if (isJUnit4Assertion(assertion)) {
            return AssertionFramework.JUNIT4;
        }
        if (isJUnit5Assertion(assertion)) {
            return AssertionFramework.JUNIT5;
        }
        throw new RuntimeException("Not a JUnit 4 or 5 assertion:\n" + assertion);
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
        putAssertEquals(table, AssertionFramework.JUNIT4, 2, false, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT4, 2, true, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT4, 3, false, 1, 2);
        putAssertEquals(table, AssertionFramework.JUNIT4, 3, true, 0, 1);
        putAssertEquals(table, AssertionFramework.JUNIT4, 4, false, 1, 2);
        putAssertEquals(table, AssertionFramework.JUNIT4, 4, true, 1, 2);
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
        putCondition(table, AssertionFramework.JUNIT4, 1, 0);
        putCondition(table, AssertionFramework.JUNIT4, 2, 1);
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

    private enum AssertionFramework {
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

    public static boolean isJUnit4Assertion(CtInvocation<?> assertion) {
        return assertion.getExecutable().getDeclaringType().getQualifiedName().equals(Configuration.JUNIT4_ASSERTION_PACKAGE);
    }

    public static boolean isJUnit5Assertion(CtInvocation<?> assertion) {
        return assertion.getExecutable().getDeclaringType().getQualifiedName().equals(Configuration.JUNIT5_ASSERTION_PACKAGE);
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
