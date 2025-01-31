package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import teralizer.spoon.analysis.TestAnalysis;

public class TestedMethodInLoopFilter extends AbstractFilter {

    private final Launcher launcher;
    private final AssertionRecord assertionRecord;

    public TestedMethodInLoopFilter(Launcher spoonLauncher, AssertionRecord assertionRecord) {
        this.launcher = spoonLauncher;
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        String testMethodCallPath = this.assertionRecord.getTestedMethodCallAbsolutePath();

        if (testMethodCallPath == null || testMethodCallPath.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        CtPath path = new CtPathStringBuilder().fromString(testMethodCallPath);
        CtModel model = this.launcher.getModel();
        CtElement testedMethodCall;
        try {
            testedMethodCall = path.evaluateOn(model.getRootPackage()).get(0);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("Could not locate tested method call at " + testMethodCallPath);
        }

        return TestAnalysis.isContainedInLoop(testedMethodCall) ?
            new FilterResult(this.getName(), FilterDecision.DEFER, "Tested method call is inside a loop")
            : new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
