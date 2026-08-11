package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.TestShape;

/**
 * Rejects a test when the generalized class inherits a test method.
 *
 * <p>The generalized class keeps the superclass of the original test class. The fixture fields and
 * the helper methods are in that superclass, and the generalized class needs them. The generator
 * deletes the test methods that the original class declares. It cannot delete a test method that
 * the superclass declares, because the original tests also use that superclass.
 *
 * <p>An inherited test method then runs as part of the generalized class. That class sets the
 * fixture for one recorded scenario. An inherited test method expects a different fixture, thus it
 * can fail. PIT stops when a test fails before mutation, and the project gets no mutation data.
 *
 * <p>The failure is not visible before PIT. Surefire runs the property with the jqwik engine and
 * finds no inherited test method. PIT also runs the vintage engine, which finds them.
 *
 * <p>{@link InheritedTestCaseFilter} rejects the related condition for JUnit 3, where the test
 * methods come from {@code junit.framework.TestCase}.
 */
public class InheritedTestMethodsFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final TestRecord testRecord;

    public InheritedTestMethodsFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.spoonLauncher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        CtClass<?> testClass = this.spoonLauncher.getFactory().Class()
            .get(this.testRecord.getTestClassQualifiedName());
        if (testClass == null) {
            // The model does not contain the class, so there is no data about its superclasses.
            // The other filters that read only the shape of a class also accept in this condition.
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        for (CtClass<?> superclass : SpoonUtils.superclasses(testClass)) {
            for (CtMethod<?> method : superclass.getMethods()) {
                // An abstract method has no body and does not run.
                if (method.getBody() != null && TestShape.hasTestAnnotation(method)) {
                    String reason = "Test class " + this.testRecord.getTestClassQualifiedName()
                        + " inherits the test method " + method.getSimpleName()
                        + " from " + superclass.getQualifiedName()
                        + ", and the generalized class cannot delete it";
                    return new FilterResult(this.getName(), FilterDecision.REJECT, reason,
                        FilterReasonCodes.INHERITED_TEST_METHODS);
                }
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
