package teralizer.processing.filter;

import java.util.List;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import teralizer.spoon.analysis.TestShape;

/**
 * Rejects a test its framework switches off, whether the marker sits on the method or on its
 * class. A disabled test never runs, so its assertions describe behavior nobody is asserting and
 * generalizing it would produce a property the developer turned off.
 */
public class DisabledTestFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final TestRecord testRecord;

    public DisabledTestFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.spoonLauncher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        CtClass<?> testClass = this.spoonLauncher.getFactory().Class()
            .get(this.testRecord.getTestClassQualifiedName());
        if (testClass == null) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        List<CtMethod<?>> methods = testClass.getMethodsByName(this.testRecord.getTestMethodName());
        CtMethod<?> testMethod = methods.isEmpty() ? null : methods.get(0);
        if (testMethod == null) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        if (TestShape.isDisabled(testMethod, testClass)) {
            String reason = "Test is disabled by its framework: "
                + this.testRecord.getTestMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason,
                FilterReasonCodes.DISABLED_TEST);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
