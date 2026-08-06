package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import teralizer.spoon.analysis.TestAnalysis;

public class AssertionInLoopFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final AssertionRecord assertionRecord;

    public AssertionInLoopFilter(Launcher spoonLauncher, AssertionRecord assertionRecord) {
        this.spoonLauncher = spoonLauncher;
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        // This filter only reports on loops. When it cannot locate the assertion it has no evidence
        // about one, so it accepts: an assertion that genuinely cannot be generalized is caught
        // downstream by the generalized test failing, which costs nothing, whereas excluding it
        // here would remove a generalizable assertion on no evidence.
        String absolutePath = this.assertionRecord.getAssertionAbsolutePath();
        if (absolutePath == null || absolutePath.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }
        CtElement assertionElement;
        try {
            CtPath path = new CtPathStringBuilder().fromString(absolutePath);
            CtModel model = this.spoonLauncher.getModel();
            assertionElement = path.evaluateOn(model.getRootPackage()).get(0);
        } catch (RuntimeException e) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        return TestAnalysis.isContainedInLoop(assertionElement) ?
            new FilterResult(this.getName(), FilterDecision.DEFER, "Assertion is inside a loop", FilterReasonCodes.ASSERTION_IN_LOOP)
            : new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
