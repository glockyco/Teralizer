package teralizer.spoon.analysis;

import org.jooq.generated.tables.records.TestRecord;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;

/**
 * Resolves a test record's method against the Spoon model. Inherited test methods store the
 * declaring (parent) class in {@code test_method_qualified_name} while
 * {@code test_class_qualified_name} keeps the JUnit-reported child, so resolution evaluates
 * the method-relative CtPath against the declaring class and falls back to the child when no
 * declaring class is derivable.
 */
public final class TestMethodResolver {

    private TestMethodResolver() {
    }

    public static CtMethod<?> resolve(Factory factory, TestRecord testRecord) {
        CtClass<?> declaringClass = factory.Class().get(declaringClassQualifiedName(testRecord));
        if (declaringClass == null) {
            declaringClass = factory.Class().get(testRecord.getTestClassQualifiedName());
        }

        CtPath testMethodPath = new CtPathStringBuilder().fromString(testRecord.getTestMethodRelativePath());
        return (CtMethod<?>) testMethodPath.evaluateOn(declaringClass).get(0);
    }

    private static String declaringClassQualifiedName(TestRecord testRecord) {
        String testMethodQualifiedName = testRecord.getTestMethodQualifiedName();
        if (testMethodQualifiedName == null) {
            return testRecord.getTestClassQualifiedName();
        }
        int separator = testMethodQualifiedName.lastIndexOf(".");
        return separator < 0
            ? testRecord.getTestClassQualifiedName()
            : testMethodQualifiedName.substring(0, separator);
    }
}
