package teralizer.spoon.analysis;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TestAnalysis {

    private static final String JUNIT4_ASSERTION_PACKAGE = "org.junit.Assert";
    private static final String JUNIT5_ASSERTION_PACKAGE = "org.junit.jupiter.api.Assertions";

    private static final String ASSERT_EQUALS = "assertEquals";
    private static final String ASSERT_TRUE = "assertTrue";
    private static final String ASSERT_FALSE = "assertFalse";

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

    private static final List<String> GENERALIZABLE_ASSERTS = Arrays.asList(ASSERT_EQUALS, ASSERT_TRUE, ASSERT_FALSE);

    public static Optional<CtInvocation<?>> findTestedMethodCall(CtMethod<?> method, CtInvocation<?> assertion) {
        if (assertion == null) {
            // @TODO: Assume a "no exceptions" test.
            //   Which method should we return as tested method in this case?
            return Optional.empty();
        }

        Optional<Integer> index = getActualParameterIndex(assertion);

        if (!index.isPresent()) {
            return Optional.empty();
        }

        CtExpression<?> actual = assertion.getArguments().get(index.get());

        if (actual instanceof CtInvocation<?>) {
            return Optional.of((CtInvocation<?>) actual);
        } else if (actual instanceof CtFieldRead<?>) {
            // @TODO: Add handling for field reads.
        } else if (actual instanceof CtVariableRead<?>) {
            // @TODO: Consider that the actual value might be redefined after declaration.
            CtLocalVariableReference<?> reference = (CtLocalVariableReference<?>) ((CtVariableRead<?>) actual).getVariable();
            CtLocalVariable<?> declaration = reference.getDeclaration();
            CtExpression<?> assignment = declaration.getAssignment();

            if (assignment instanceof CtInvocation<?>) {
                return Optional.of((CtInvocation<?>) assignment);
            }
        }

        return Optional.empty();
    }

    public static Optional<CtInvocation<?>> findGeneralizableAssert(CtMethod<?> method) {
        return findGeneralizableAsserts(method).stream().findFirst();
    }

    public static List<CtInvocation<?>> findGeneralizableAsserts(CtMethod<?> method) {
        return method.getElements(e -> TestAnalysis.isAssertion(e) && TestAnalysis.isGeneralizable(e));
    }

    public static List<CtInvocation<?>> findAllAsserts(CtMethod<?> method) {
        return method.getElements(TestAnalysis::isAssertion);
    }

    public static boolean isGeneralizable(CtInvocation<?> invocation) {
        return GENERALIZABLE_ASSERTS.contains(invocation.getExecutable().getSimpleName());
    }

    public static boolean isAssertion(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        CtTypeReference<?> declaringType = executable.getDeclaringType();

        if (declaringType == null) {
            return false;
        }

        String qualifiedName = declaringType.getQualifiedName();
        return qualifiedName.equals(JUNIT4_ASSERTION_PACKAGE) || qualifiedName.equals(JUNIT5_ASSERTION_PACKAGE);
    }

    public static Optional<Integer> getActualParameterIndex(CtInvocation<?> assertion) {
        switch (assertion.getExecutable().getSimpleName()) {
            case ASSERT_EQUALS:
                if (assertion.getArguments().size() == 3 && isJUnit4Assertion(assertion)) {
                    return Optional.of(2);
                } else if (assertion.getArguments().size() == 2) {
                    return Optional.of(1);
                } else {
                    throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                }
            case ASSERT_TRUE:
            case ASSERT_FALSE:
                if (assertion.getArguments().size() == 2 && isJUnit4Assertion(assertion)) {
                    return Optional.of(1);
                } else if (assertion.getArguments().size() == 1) {
                    return Optional.of(0);
                } else {
                    throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                }
            default:
                return Optional.empty();
        }
    }

    public static Optional<Integer> getExpectedParameterIndex(CtInvocation<?> assertion) {
        if (assertion.getExecutable().getSimpleName().equals(ASSERT_EQUALS)) {
            if (assertion.getArguments().size() == 3 && isJUnit4Assertion(assertion)) {
                return Optional.of(1);
            } else if (assertion.getArguments().size() == 2) {
                return Optional.of(0);
            } else {
                throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
            }
        }
        return Optional.empty();
    }

    public static boolean isJUnit4Assertion(CtInvocation<?> assertion) {
        return assertion.getExecutable().getDeclaringType().getQualifiedName().equals(JUNIT4_ASSERTION_PACKAGE);
    }

    public static boolean isJUnit5Assertion(CtInvocation<?> assertion) {
        return assertion.getExecutable().getDeclaringType().getQualifiedName().equals(JUNIT5_ASSERTION_PACKAGE);
    }
}
