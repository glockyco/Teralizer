package teralizer.spoon.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.spoon.analysis.TestShape;
import teralizer.util.Configuration;

/**
 * Deletes {@code extends junit.framework.TestCase} from a generalized class.
 *
 * <p>The vintage engine runs any class that extends {@code TestCase}. jqwik runs any class with a
 * {@code @Property} method. A generalized class that extends {@code TestCase} matches both rules, so
 * both engines run its property. The second run fails, because the arbitrary has already recorded
 * its values and rejects every new one. Surefire writes each run to a different file, so the class
 * reports the property as passed in one file and as failed in the other. Mutation analysis then
 * refuses the project, because it requires a suite that passes.
 *
 * <p>The property still calls assertions that it inherited from {@code TestCase}. Those assertions
 * come from {@code junit.framework.Assert} and are static, so {@link #detach} rewrites
 * {@code assertEquals(1, x)} to {@code junit.framework.Assert.assertEquals(1, x)}. It also deletes
 * {@code super.setUp()} and {@code super.tearDown()} calls, because both methods are empty in
 * {@code TestCase}.
 *
 * <p>{@link #isDetachable} reads the source class first, and looks at the test method, the fixtures,
 * and the helpers they call. If that code calls anything else it inherited, the class cannot lose
 * the ancestry, and {@code InheritedTestCaseFilter} rejects the test. Sibling test methods do not
 * matter here, because cloning deletes them.
 */
public final class TestCaseDetachment {

    private TestCaseDetachment() {
    }

    /**
      * Whether a class cloned from this test method can drop its {@code TestCase} ancestry. Returns
      * true for a class that never had it, so callers need no separate framework check.
      */
    public static boolean isDetachable(CtClass<?> testClass, CtMethod<?> testMethod) {
        if (!TestShape.extendsTestCase(testClass)) {
            return true;
        }
        CtTypeReference<?> superclass = testClass.getSuperclass();
        if (superclass == null || !Configuration.JUNIT3_TEST_CASE_CLASS.equals(superclass.getQualifiedName())) {
            // An intermediate base class can give the test helper methods and fields. Cloning
            // copies test methods and fixtures only, so the class still needs that base. The base
            // also keeps the class claimable by the vintage engine.
            return false;
        }
        for (CtInvocation<?> invocation : inheritedCalls(retainedMethods(testClass, testMethod))) {
            if (!isRewritable(invocation)) {
                return false;
            }
        }
        return true;
    }

    /**
      * Rewrites a cloned class whose sibling test methods are already deleted. Call
      * {@link #isDetachable} on the source class first. The filtering stage does that.
      */
    public static void detach(CtClass<?> generalizedClass) {
        CtTypeReference<?> superclass = generalizedClass.getSuperclass();
        if (superclass == null || !Configuration.JUNIT3_TEST_CASE_CLASS.equals(superclass.getQualifiedName())) {
            return;
        }
        CtTypeReference<?> assertType = generalizedClass.getFactory().Type()
            .createReference(Configuration.JUNIT3_ASSERTION_PACKAGE);
        for (CtInvocation<?> invocation : inheritedCalls(generalizedClass.getMethods())) {
            if (isSuperFixtureCall(invocation)) {
                invocation.delete();
            } else if (isAssertionCall(invocation)) {
                invocation.setTarget(generalizedClass.getFactory().Code().createTypeAccess(assertType));
            }
        }
        removeOverrideOnFixtures(generalizedClass);
        generalizedClass.setSuperclass(null);
    }

    /**
     * Deletes {@code @Override} from {@code setUp} and {@code tearDown}. Those methods override
     * {@code TestCase}, so the annotation stops compiling once the class no longer extends it. The
     * compiler reports {@code method does not override or implement a method from a supertype}.
     * No other method needs this, because {@link #isDetachable} rejects a class that overrides
     * anything else from {@code TestCase}.
     */
    private static void removeOverrideOnFixtures(CtClass<?> generalizedClass) {
        for (CtMethod<?> method : generalizedClass.getMethods()) {
            String name = method.getSimpleName();
            if (!Configuration.JUNIT3_SET_UP_METHOD.equals(name)
                && !Configuration.JUNIT3_TEAR_DOWN_METHOD.equals(name)) {
                continue;
            }
            new ArrayList<>(method.getAnnotations()).stream()
                .filter(annotation -> Override.class.getName().equals(annotation.getAnnotationType().getQualifiedName()))
                .forEach(method::removeAnnotation);
        }
    }

    /** The methods a clone keeps: the test method, the fixtures, and the helpers they call. */
    private static List<CtMethod<?>> retainedMethods(CtClass<?> testClass, CtMethod<?> testMethod) {
        List<CtMethod<?>> retained = new ArrayList<>();
        Deque<CtMethod<?>> pending = new ArrayDeque<>();
        Set<CtMethod<?>> seen = new HashSet<>();
        if (testMethod != null) {
            pending.add(testMethod);
        }
        testClass.getMethods().stream()
            .filter(m -> TestShape.isFixture(m, testClass))
            .forEach(pending::add);

        while (!pending.isEmpty()) {
            CtMethod<?> method = pending.poll();
            if (!seen.add(method)) {
                continue;
            }
            retained.add(method);
            for (CtInvocation<?> invocation : method.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
                CtMethod<?> callee = declaredCallee(testClass, invocation);
                if (callee != null) {
                    pending.add(callee);
                }
            }
        }
        return retained;
    }

    private static CtMethod<?> declaredCallee(CtClass<?> testClass, CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null) {
            return null;
        }
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null || !declaringType.getQualifiedName().equals(testClass.getQualifiedName())) {
            return null;
        }
        return testClass.getMethods().stream()
            .filter(m -> m.getSignature().equals(executable.getSignature()))
            .findFirst()
            .orElse(null);
    }

    /** Calls to methods that {@code TestCase} or {@code Assert} declares. */
    private static List<CtInvocation<?>> inheritedCalls(Iterable<? extends CtMethod<?>> methods) {
        List<CtInvocation<?>> inherited = new ArrayList<>();
        for (CtMethod<?> method : methods) {
            for (CtInvocation<?> invocation : method.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
                CtExecutableReference<?> executable = invocation.getExecutable();
                CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
                if (declaringType == null) {
                    continue;
                }
                String qualifiedName = declaringType.getQualifiedName();
                if (Configuration.JUNIT3_TEST_CASE_CLASS.equals(qualifiedName)
                    || Configuration.JUNIT3_ASSERTION_PACKAGE.equals(qualifiedName)) {
                    inherited.add(invocation);
                }
            }
        }
        return inherited;
    }

    private static boolean isRewritable(CtInvocation<?> invocation) {
        // A constructor call needs no rewrite. Removal of the ancestry makes Object the implicit
        // superclass, and the call then names the no-argument constructor of Object.
        return invocation.getExecutable().isConstructor()
            || isAssertionCall(invocation)
            || isSuperFixtureCall(invocation);
    }

    private static boolean isAssertionCall(CtInvocation<?> invocation) {
        String name = invocation.getExecutable().getSimpleName();
        return name.startsWith("assert") || "fail".equals(name);
    }

    private static boolean isSuperFixtureCall(CtInvocation<?> invocation) {
        String name = invocation.getExecutable().getSimpleName();
        return invocation.getTarget() instanceof CtSuperAccess
            && (Configuration.JUNIT3_SET_UP_METHOD.equals(name) || Configuration.JUNIT3_TEAR_DOWN_METHOD.equals(name));
    }
}
