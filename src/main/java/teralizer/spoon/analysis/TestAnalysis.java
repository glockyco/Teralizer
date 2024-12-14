package teralizer.spoon.analysis;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.stream.Collectors;

public class TestAnalysis {

    private static final String JUNIT4_ASSERTION_PACKAGE = "org.junit.Assert";
    private static final String JUNIT5_ASSERTION_PACKAGE = "org.junit.jupiter.api.Assertions";

    public static CtInvocation<?> findTestedMethodCall(CtMethod<?> testMethod) {
        // @TODO: Use more sophisticated detection of tested method.

        CtInvocation<?> testedMethodCall = null;

        List<CtInvocation<?>> methodCalls = testMethod.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> methodCall : methodCalls) {
            if (methodCall.getExecutable().getSimpleName().startsWith("assert")) {
                break;
            }
            testedMethodCall = methodCall;
        }

        assert testedMethodCall != null;

        return testedMethodCall;
    }

    public static List<CtInvocation<?>> findAssertCalls(CtMethod<?> testMethod) {
        return testMethod.getElements(element -> {
            CtExecutableReference<?> executable = element.getExecutable();
            CtTypeReference<?> declaringType = executable.getDeclaringType();

            if (declaringType == null) {
                return false;
            }

            String qualifiedName = declaringType.getQualifiedName();
            return qualifiedName.equals(JUNIT4_ASSERTION_PACKAGE) || qualifiedName.equals(JUNIT5_ASSERTION_PACKAGE);
        });
    }

    public static CtInvocation<?> findAssertEqualsCall(CtMethod<?> testMethod) {
        // @TODO: Use more sophisticated detection of generalizable assertEquals calls.

        List<CtInvocation<?>> methodCalls = testMethod.getElements(new TypeFilter<>(CtInvocation.class));
        List<CtInvocation<?>> assertEqualsCalls = methodCalls.stream().filter(m -> m.getExecutable().getSimpleName().equals("assertEquals")).collect(Collectors.toList());

        assert assertEqualsCalls.size() == 1;
        return assertEqualsCalls.get(0);
    }
}
