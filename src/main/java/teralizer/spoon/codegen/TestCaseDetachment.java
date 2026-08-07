package teralizer.spoon.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
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
        if (!canConstructWithoutArguments(testClass)) {
            return false;
        }
        List<CtElement> scopes = new ArrayList<>(retainedMethods(testClass, testMethod));
        scopes.addAll(testClass.getConstructors());
        for (CtInvocation<?> invocation : inheritedCalls(scopes)) {
            if (!isRewritable(invocation)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether jqwik can construct the class after detachment. jqwik constructs a container without
     * arguments. A class that declares no constructor gets the default one. Otherwise the class needs
     * a constructor without arguments, or the String constructor that
     * {@link #ensureNoArgConstructor} can delegate to.
     */
    private static boolean canConstructWithoutArguments(CtClass<?> testClass) {
        Set<? extends CtConstructor<?>> constructors = testClass.getConstructors();
        if (constructors.isEmpty()
            || constructors.stream().anyMatch(constructor -> constructor.getParameters().isEmpty())) {
            return true;
        }
        return constructors.stream().anyMatch(constructor -> constructor.getParameters().size() == 1
            && "java.lang.String".equals(constructor.getParameters().get(0).getType().getQualifiedName()));
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
        deleteSuperConstructorCalls(generalizedClass);
        ensureNoArgConstructor(generalizedClass);
        generalizedClass.setSuperclass(null);
    }

    /**
     * Deletes calls to a {@code TestCase} constructor. JUnit 3 classes often declare
     * {@code MyTest(String name)} and call {@code super(name)} from it. Object becomes the superclass
     * after detachment, and it declares no constructor that takes a name, so the compiler reports
     * {@code constructor Object in class java.lang.Object cannot be applied to given types}. The call
     * only set the JUnit 3 test name, which nothing reads after detachment.
     */
    private static void deleteSuperConstructorCalls(CtClass<?> generalizedClass) {
        inheritedCalls(generalizedClass.getConstructors()).stream()
            .filter(invocation -> invocation.getExecutable().isConstructor())
            .forEach(CtInvocation::delete);
    }

    /**
     * Adds {@code MyTest()} when the class declares only {@code MyTest(String name)}. jqwik
     * constructs a container without arguments. The new constructor delegates with {@code this("")},
     * so the field initialization in the String constructor still runs.
     * {@code InstrumentedClassBuilder} gives the symbolic driver the same shape.
     */
    private static void ensureNoArgConstructor(CtClass<?> generalizedClass) {
        Set<? extends CtConstructor<?>> constructors = generalizedClass.getConstructors();
        if (constructors.isEmpty()
            || constructors.stream().anyMatch(constructor -> constructor.getParameters().isEmpty())) {
            return;
        }
        Factory factory = generalizedClass.getFactory();
        CtConstructor<?> noArgConstructor = factory.Constructor().create(
            generalizedClass,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            Collections.emptyList(),
            Collections.emptySet(),
            factory.Core().createBlock()
        );
        noArgConstructor.getBody().addStatement(factory.Code().createCodeSnippetStatement("this(\"\")"));
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
    private static List<CtInvocation<?>> inheritedCalls(Iterable<? extends CtElement> scopes) {
        List<CtInvocation<?>> inherited = new ArrayList<>();
        for (CtElement scope : scopes) {
            for (CtInvocation<?> invocation : scope.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
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
        // {@link #detach} deletes a call to a TestCase constructor. Keeping one would not compile:
        // Object becomes the superclass, and it declares no constructor that takes a test name.
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
