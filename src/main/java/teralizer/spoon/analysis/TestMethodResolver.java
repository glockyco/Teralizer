package teralizer.spoon.analysis;

import java.util.List;
import org.jooq.generated.tables.records.TestRecord;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;

/**
 * Resolves a test record's method against the Spoon model. The stored absolute CtPath points
 * at the method's declaration site, so inherited test methods (declared in an abstract parent,
 * run through a concrete subclass) resolve without deriving the declaring class from any name:
 * {@code test_method_qualified_name} carries the concrete run identity that JUnit, PIT, and
 * jqwik report, never the declaring class.
 */
public final class TestMethodResolver {

    private TestMethodResolver() {
    }

    public static CtMethod<?> resolve(Factory factory, TestRecord testRecord) {
        CtPath testMethodPath = new CtPathStringBuilder().fromString(testRecord.getTestMethodAbsolutePath());
        List<CtElement> resolved = testMethodPath.evaluateOn(factory.getModel().getRootPackage());
        if (resolved.isEmpty()) {
            throw new RuntimeException("Cannot resolve test method at path '"
                + testRecord.getTestMethodAbsolutePath() + "' for test "
                + testRecord.getTestMethodQualifiedName() + ".");
        }
        return (CtMethod<?>) resolved.get(0);
    }
}
