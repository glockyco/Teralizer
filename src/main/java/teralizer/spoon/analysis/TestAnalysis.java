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

    private static final List<String> GENERALIZABLE_ASSERTS = Arrays.asList(ASSERT_EQUALS, ASSERT_TRUE, ASSERT_FALSE);

    public static Optional<CtInvocation<?>> findTestedMethodCall(CtMethod<?> method, CtInvocation<?> assertion) {
        CtExpression<?> actual;

        if (assertion == null) {
            // @TODO: Assume a "no exceptions" test.
            //   Which method should we return as tested method in this case?
            return Optional.empty();
        }

        switch (assertion.getExecutable().getSimpleName()) {
            case ASSERT_EQUALS:
                // @TODO: Distinguish JUnit 4 vs. JUnit 5 (=> message parameters!).
                actual = assertion.getArguments().get(1);
                break;
            case ASSERT_TRUE:
            case ASSERT_FALSE:
                // @TODO: Distinguish JUnit 4 vs. JUnit 5 (=> message parameters!).
                actual = assertion.getArguments().get(0);
                break;
            default:
                return Optional.empty();
        }

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
}
