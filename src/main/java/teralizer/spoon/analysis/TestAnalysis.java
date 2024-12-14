package teralizer.spoon.analysis;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.stream.Collectors;

public class TestAnalysis {

    public static CtInvocation<?> findTestedMethodCall(CtMethod<?> testMethodDeclaration) {
        // @TODO: Use more sophisticated detection of tested method.

        CtInvocation<?> testedMethodCall = null;

        List<CtInvocation<?>> methodCalls = testMethodDeclaration.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> methodCall : methodCalls) {
            if (methodCall.getExecutable().getSimpleName().startsWith("assert")) {
                break;
            }
            testedMethodCall = methodCall;
        }

        assert testedMethodCall != null;

        return testedMethodCall;
    }

    public static List<CtInvocation<?>> findAssertCalls(CtMethod<?> testMethodDeclaration) {
        List<CtInvocation<?>> methodCalls = testMethodDeclaration.getElements(new TypeFilter<>(CtInvocation.class));
        return methodCalls.stream().filter(m -> m.getExecutable().getSimpleName().startsWith("assert")).collect(Collectors.toList());
    }

    public static CtInvocation<?> findAssertEqualsCall(CtMethod<?> testMethodDeclaration) {
        // @TODO: Use more sophisticated detection of generalizable assertEquals calls.

        List<CtInvocation<?>> methodCalls = testMethodDeclaration.getElements(new TypeFilter<>(CtInvocation.class));
        List<CtInvocation<?>> assertEqualsCalls = methodCalls.stream().filter(m -> m.getExecutable().getSimpleName().equals("assertEquals")).collect(Collectors.toList());

        assert assertEqualsCalls.size() == 1;
        return assertEqualsCalls.get(0);
    }
}
