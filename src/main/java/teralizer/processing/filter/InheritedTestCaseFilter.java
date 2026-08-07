package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import teralizer.spoon.codegen.TestCaseDetachment;

/**
 * Rejects a test whose generalized class would keep extending {@code junit.framework.TestCase}.
 *
 * <p>{@link TestCaseDetachment} normally deletes that ancestry, because the vintage engine runs such
 * a class in addition to jqwik, and the property then runs twice and fails the second time. The
 * rewrite handles inherited assertions and inherited empty fixture calls. If the test calls anything
 * else that it inherited, the rewrite cannot help, so this filter rejects the test.
 */
public class InheritedTestCaseFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final TestRecord testRecord;

    public InheritedTestCaseFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.spoonLauncher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        CtClass<?> testClass = this.spoonLauncher.getFactory().Class().get(this.testRecord.getTestClassQualifiedName());
        if (testClass == null) {
            // A class that is absent from the model gives no evidence about its ancestry. The
            // filters that only report shape accept in the same situation.
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }
        CtMethod<?> testMethod = testClass.getMethodsByName(this.testRecord.getTestMethodName()).stream()
            .findFirst()
            .orElse(null);
        if (TestCaseDetachment.isDetachable(testClass, testMethod)) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }
        String reason = "Test inherits members that its generalization would still need: "
            + this.testRecord.getTestClassQualifiedName();
        return new FilterResult(this.getName(), FilterDecision.REJECT, reason,
            FilterReasonCodes.INHERITED_TEST_CASE_MEMBERS);
    }
}
