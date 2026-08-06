package teralizer.spoon.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import teralizer.util.Configuration;

/**
 * How a test declares itself: which framework marks it, whether it is disabled, how it names
 * its fixture, and which lifecycle hook a fixture becomes once the test is generalized.
 *
 * <p>This is the sole owner of that concept. Every decision about test, fixture, or lifecycle
 * shape resolves here, so call sites must not enumerate frameworks or annotations themselves.
 *
 * <p><b>Classification is by qualified name, never simple name.</b> A simple {@code @Test} does
 * not identify JUnit: {@code org.testng.annotations.Test} shares that simple name.
 */
public final class TestShape {

    /** The framework a test method's declaration belongs to. */
    public enum Framework {
        JUNIT3,
        JUNIT4,
        JUNIT5,
        JQWIK,
        TESTNG
    }

    /** Where a lifecycle hook runs relative to the generalized property. */
    public enum LifecyclePhase {
        BEFORE_PROPERTY("net.jqwik.api.lifecycle.BeforeProperty", false),
        AFTER_PROPERTY("net.jqwik.api.lifecycle.AfterProperty", false),
        BEFORE_CONTAINER("net.jqwik.api.lifecycle.BeforeContainer", true),
        AFTER_CONTAINER("net.jqwik.api.lifecycle.AfterContainer", true);

        private final String jqwikAnnotation;
        private final boolean requiresStatic;

        LifecyclePhase(String jqwikAnnotation, boolean requiresStatic) {
            this.jqwikAnnotation = jqwikAnnotation;
            this.requiresStatic = requiresStatic;
        }

        /** The jqwik annotation a fixture in this phase carries once generalized. */
        public String jqwikAnnotation() {
            return this.jqwikAnnotation;
        }

        /** Container-level hooks must be static; property-level hooks must not be forced static. */
        public boolean requiresStatic() {
            return this.requiresStatic;
        }

        /** Whether this phase runs before the test body, and so belongs in the symbolic driver. */
        public boolean isBefore() {
            return this == BEFORE_PROPERTY || this == BEFORE_CONTAINER;
        }
    }

    private static final Map<String, Framework> TEST_ANNOTATIONS = createTestAnnotations();
    private static final Map<String, LifecyclePhase> LIFECYCLE_ANNOTATIONS = createLifecycleAnnotations();
    private static final Set<String> DISABLED_ANNOTATIONS = Collections.unmodifiableSet(
        new LinkedHashSet<>(Arrays.asList(
            "org.junit.Ignore",
            "org.junit.jupiter.api.Disabled")));

    /** JUnit 3 declares its fixture by overriding these, not by annotation. */
    private static final Map<String, LifecyclePhase> JUNIT3_FIXTURES = createJUnit3Fixtures();

    private TestShape() {
    }

    // ----- Test declarations ----- //

    /**
     * The framework marking this method as a test, or empty when nothing does. JUnit 3 is
     * recognized structurally, so the declaring class is required.
     */
    public static Framework frameworkOf(CtMethod<?> method, CtClass<?> declaringClass) {
        for (CtAnnotation<?> annotation : method.getAnnotations()) {
            Framework framework = TEST_ANNOTATIONS.get(qualifiedAnnotationName(annotation));
            if (framework != null) {
                return framework;
            }
        }
        return isJUnit3TestMethod(method, declaringClass) ? Framework.JUNIT3 : null;
    }

    /**
     * A JUnit 3 test method, by the shape JUnit 3's own runner requires: {@code public void},
     * no parameters, the conventional {@code test} name prefix, and a
     * {@code junit.framework.TestCase} ancestor.
     */
    public static boolean isJUnit3TestMethod(CtMethod<?> method, CtClass<?> declaringClass) {
        if (method == null
            || !method.getSimpleName().startsWith(Configuration.JUNIT3_METHOD_PREFIX)
            || !method.getParameters().isEmpty()
            || !method.isPublic()
            || !isVoid(method)) {
            return false;
        }
        return extendsTestCase(declaringClass);
    }

    /**
     * Whether the type has {@code junit.framework.TestCase} among its ancestors. Walked through
     * superclass <em>references</em> rather than declarations, because TestCase itself is not
     * part of the Spoon source model.
     */
    public static boolean extendsTestCase(CtType<?> type) {
        CtTypeReference<?> superclass = type == null ? null : type.getSuperclass();
        while (superclass != null) {
            if (Configuration.JUNIT3_TEST_CASE_CLASS.equals(superclass.getQualifiedName())) {
                return true;
            }
            superclass = superclass.getSuperclass();
        }
        return false;
    }

    /** Whether any recognized test annotation is present, regardless of framework. */
    public static boolean hasTestAnnotation(CtMethod<?> method) {
        return method.getAnnotations().stream()
            .anyMatch(annotation -> TEST_ANNOTATIONS.containsKey(qualifiedAnnotationName(annotation)));
    }

    /**
     * The marker recorded in {@code test.test_annotation_name}: the annotation's simple name for
     * an annotated JUnit or jqwik test, a synthetic marker for a framework that declares tests
     * structurally or shares a simple name with JUnit, and null when the method is not a
     * recognized test. The marker must identify the framework on its own, because the filters
     * that consume it see only the persisted record.
     */
    public static String markerOf(CtMethod<?> method, CtClass<?> declaringClass) {
        for (CtAnnotation<?> annotation : method.getAnnotations()) {
            Framework framework = TEST_ANNOTATIONS.get(qualifiedAnnotationName(annotation));
            if (framework == Framework.TESTNG) {
                return Configuration.TEST_MARKER_TESTNG;
            }
            if (framework != null) {
                return annotation.getAnnotationType().getSimpleName();
            }
        }
        return isJUnit3TestMethod(method, declaringClass) ? Configuration.TEST_MARKER_JUNIT3 : null;
    }

    /**
     * Whether the test is disabled by its framework, on the method or on its class. A disabled
     * test never runs, so generalizing it would produce a property the developer switched off.
     */
    public static boolean isDisabled(CtMethod<?> method, CtClass<?> declaringClass) {
        return hasDisabledAnnotation(method) || hasDisabledAnnotation(declaringClass);
    }

    private static boolean isDisabledBy(List<? extends CtAnnotation<?>> annotations) {
        return annotations.stream()
            .anyMatch(annotation -> DISABLED_ANNOTATIONS.contains(qualifiedAnnotationName(annotation)));
    }

    private static boolean hasDisabledAnnotation(CtMethod<?> method) {
        return method != null && isDisabledBy(method.getAnnotations());
    }

    private static boolean hasDisabledAnnotation(CtType<?> type) {
        return type != null && isDisabledBy(type.getAnnotations());
    }

    // ----- Fixtures and lifecycle ----- //

    /**
     * The lifecycle phase this method belongs to, whether it declares itself by annotation
     * (JUnit 4/5) or by overriding a JUnit 3 fixture method. Empty when it is not a fixture.
     */
    public static LifecyclePhase lifecyclePhaseOf(CtMethod<?> method, CtClass<?> declaringClass) {
        for (CtAnnotation<?> annotation : method.getAnnotations()) {
            LifecyclePhase phase = LIFECYCLE_ANNOTATIONS.get(qualifiedAnnotationName(annotation));
            if (phase != null) {
                return phase;
            }
        }
        return isJUnit3Fixture(method, declaringClass)
            ? JUNIT3_FIXTURES.get(method.getSimpleName())
            : null;
    }

    /**
     * A JUnit 3 fixture: {@code setUp} or {@code tearDown} with no parameters, in a TestCase
     * subclass. Visibility is not constrained, because JUnit 3 fixtures are conventionally
     * {@code protected}.
     */
    public static boolean isJUnit3Fixture(CtMethod<?> method, CtClass<?> declaringClass) {
        return method != null
            && method.getParameters().isEmpty()
            && JUNIT3_FIXTURES.containsKey(method.getSimpleName())
            && extendsTestCase(declaringClass);
    }

    /** Whether the method is a fixture of any recognized framework. */
    public static boolean isFixture(CtMethod<?> method, CtClass<?> declaringClass) {
        return lifecyclePhaseOf(method, declaringClass) != null;
    }

    /** The jqwik annotation replacing a recognized lifecycle annotation, or empty. */
    public static LifecyclePhase phaseForLifecycleAnnotation(String qualifiedAnnotationName) {
        return LIFECYCLE_ANNOTATIONS.get(qualifiedAnnotationName);
    }

    // ----- Tables ----- //

    private static Map<String, Framework> createTestAnnotations() {
        Map<String, Framework> annotations = new LinkedHashMap<>();
        annotations.put("org.junit.Test", Framework.JUNIT4);
        annotations.put("org.junit.jupiter.api.Test", Framework.JUNIT5);
        annotations.put("org.junit.jupiter.api.RepeatedTest", Framework.JUNIT5);
        annotations.put("org.junit.jupiter.api.ParameterizedTest", Framework.JUNIT5);
        annotations.put("org.junit.jupiter.params.ParameterizedTest", Framework.JUNIT5);
        annotations.put("org.junit.jupiter.api.TestFactory", Framework.JUNIT5);
        annotations.put("org.junit.jupiter.api.TestTemplate", Framework.JUNIT5);
        annotations.put("net.jqwik.api.Property", Framework.JQWIK);
        annotations.put("net.jqwik.api.Example", Framework.JQWIK);
        // Present so a TestNG test is classified as TestNG and rejected as a foreign framework.
        annotations.put("org.testng.annotations.Test", Framework.TESTNG);
        return Collections.unmodifiableMap(annotations);
    }

    private static Map<String, LifecyclePhase> createLifecycleAnnotations() {
        Map<String, LifecyclePhase> annotations = new LinkedHashMap<>();
        annotations.put("org.junit.Before", LifecyclePhase.BEFORE_PROPERTY);
        annotations.put("org.junit.jupiter.api.BeforeEach", LifecyclePhase.BEFORE_PROPERTY);
        annotations.put("org.junit.After", LifecyclePhase.AFTER_PROPERTY);
        annotations.put("org.junit.jupiter.api.AfterEach", LifecyclePhase.AFTER_PROPERTY);
        annotations.put("org.junit.BeforeClass", LifecyclePhase.BEFORE_CONTAINER);
        annotations.put("org.junit.jupiter.api.BeforeAll", LifecyclePhase.BEFORE_CONTAINER);
        annotations.put("org.junit.AfterClass", LifecyclePhase.AFTER_CONTAINER);
        annotations.put("org.junit.jupiter.api.AfterAll", LifecyclePhase.AFTER_CONTAINER);
        return Collections.unmodifiableMap(annotations);
    }

    private static Map<String, LifecyclePhase> createJUnit3Fixtures() {
        Map<String, LifecyclePhase> fixtures = new LinkedHashMap<>();
        fixtures.put(Configuration.JUNIT3_SET_UP_METHOD, LifecyclePhase.BEFORE_PROPERTY);
        fixtures.put(Configuration.JUNIT3_TEAR_DOWN_METHOD, LifecyclePhase.AFTER_PROPERTY);
        return Collections.unmodifiableMap(fixtures);
    }

    private static String qualifiedAnnotationName(CtAnnotation<?> annotation) {
        CtTypeReference<?> type = annotation == null ? null : annotation.getAnnotationType();
        return type == null ? null : type.getQualifiedName();
    }

    private static boolean isVoid(CtMethod<?> method) {
        CtTypeReference<?> type = method.getType();
        return type != null && "void".equals(type.getSimpleName());
    }
}
