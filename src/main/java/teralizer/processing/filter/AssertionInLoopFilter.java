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
        String absolutePath = this.assertionRecord.getAssertionAbsolutePath();
        CtPath path = new CtPathStringBuilder().fromString(absolutePath);
        CtModel model = this.spoonLauncher.getModel();
        CtElement assertionElement;
        try {
            assertionElement = path.evaluateOn(model.getRootPackage()).get(0);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("Could not locate assertion at " + absolutePath);
        }

        return TestAnalysis.isContainedInLoop(assertionElement) ?
            new FilterResult(this.getName(), FilterDecision.DEFER, "Assertion is inside a loop")
            : new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
