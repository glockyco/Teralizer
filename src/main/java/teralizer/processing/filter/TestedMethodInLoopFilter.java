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

        // This filter only reports on loops. When it cannot locate the tested call it has no
        // evidence about one, so it accepts: an assertion that genuinely cannot be generalized is
        // caught downstream by the generalized test failing, which costs nothing, whereas
        // excluding it here would remove a generalizable assertion on no evidence.
        if (testMethodCallPath == null || testMethodCallPath.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        CtElement testedMethodCall;
        try {
            CtPath path = new CtPathStringBuilder().fromString(testMethodCallPath);
            CtModel model = this.launcher.getModel();
            testedMethodCall = path.evaluateOn(model.getRootPackage()).get(0);
        } catch (RuntimeException e) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        return TestAnalysis.isContainedInLoop(testedMethodCall) ?
            new FilterResult(this.getName(), FilterDecision.DEFER, "Tested method call is inside a loop", FilterReasonCodes.TESTED_METHOD_IN_LOOP)
            : new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
