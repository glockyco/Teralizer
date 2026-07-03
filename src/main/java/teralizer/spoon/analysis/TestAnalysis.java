package teralizer.spoon.analysis;

import java.util.List;
import java.util.Optional;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import teralizer.util.Configuration;

public class TestAnalysis {

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

    public static Optional<CtInvocation<?>> findTestedMethodCall(CtMethod<?> method, CtInvocation<?> assertion) {
        MutResolution resolution = MethodUnderTestResolver.resolve(method, assertion);
        return Optional.ofNullable(resolution.getPick());
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
        boolean isJunit4 = isJUnit4Assertion(assertion);
        boolean isJunit5 = isJUnit5Assertion(assertion);

        if (!isJunit4 && !isJunit5) {
            throw new RuntimeException("Not a JUnit 4 or 5 assertion:\n" + assertion);
        }

        int argumentCount = assertion.getArguments().size();

        switch (assertion.getExecutable().getSimpleName()) {
            case Configuration.ASSERT_EQUALS:
                if (isJunit4) {
                    if (argumentCount == 4) {
                        // The parameters are: message, expected, actual, delta
                        return Optional.of(2);
                    } else if (argumentCount == 3) {
                        if (assertion.getExecutable().getParameters().stream().allMatch(p -> p.getSimpleName().equals("double"))) {
                            // The parameters are: expected, actual, delta
                            return Optional.of(1);
                        } else {
                            // The parameters are: message, expected, actual
                            return Optional.of(2);
                        }
                    } else if (argumentCount == 2) {
                        // The parameters are: expected, actual
                        return Optional.of(1);
                    } else {
                        throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                    }
                } else { // if (isJunit5) {
                    if (argumentCount == 2 || argumentCount == 3 || argumentCount == 4) {
                        // The parameters are: expected, actual(, delta)(, message | messageSupplier)
                        return Optional.of(1);
                    } else {
                        throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                    }
                }
            case Configuration.ASSERT_TRUE:
            case Configuration.ASSERT_FALSE:
                if (isJunit4) {
                    if (argumentCount == 2) {
                        // The parameters are: message, condition
                        return Optional.of(1);
                    } else if (argumentCount == 1) {
                        // The parameters are: condition
                        return Optional.of(0);
                    } else {
                        throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                    }
                } else { // if (isJunit5) {
                    if (argumentCount == 1 || argumentCount == 2) {
                        // The parameters are: condition | booleanSupplier(, message | message Supplier)
                        return Optional.of(0);
                    } else {
                        throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                    }
                }
            default:
                return Optional.empty();
        }
    }

    public static Optional<Integer> getExpectedParameterIndex(CtInvocation<?> assertion) {
        boolean isJunit4 = isJUnit4Assertion(assertion);
        boolean isJunit5 = isJUnit5Assertion(assertion);

        if (!isJunit4 && !isJunit5) {
            throw new RuntimeException("Not a JUnit 4 or 5 assertion:\n" + assertion);
        }

        int argumentCount = assertion.getArguments().size();

        if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_EQUALS)) {
            if (isJunit4) {
                if (argumentCount == 4) {
                    // The parameters are: message, expected, actual, delta
                    return Optional.of(1);
                } else if (argumentCount == 3) {
                    if (assertion.getExecutable().getParameters().stream().allMatch(p -> p.getSimpleName().equals("double"))) {
                        // The parameters are: expected, actual, delta
                        return Optional.of(0);
                    } else {
                        // The parameters are: message, expected, actual
                        return Optional.of(1);
                    }
                } else if (argumentCount == 2) {
                    // The parameters are: expected, actual
                    return Optional.of(0);
                } else {
                    throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                }
            } else { // if (isJunit5) {
                if (argumentCount == 2 || argumentCount == 3 || argumentCount == 4) {
                    // The parameters are: expected, actual(, delta)(, message | messageSupplier)
                    return Optional.of(0);
                } else {
                    throw new RuntimeException("Unexpected number of assertion parameters for assertion:\n" + assertion);
                }
            }
        } else if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            if (isJunit4) {
                throw new RuntimeException("Unexpected JUnit 4 assertion:\n" + assertion);
            } else { // if (isJunit5) {
                // The parameters are: expectedType, executable(, message | messageSupplier)
                return Optional.of(0);
            }
        }
        return Optional.empty();
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
