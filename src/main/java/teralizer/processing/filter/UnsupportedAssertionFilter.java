package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import teralizer.spoon.analysis.TestAnalysis;

public class UnsupportedAssertionFilter extends AbstractFilter {

    private final Launcher spoonLauncher;
    private final AssertionRecord assertionRecord;

    public UnsupportedAssertionFilter(Launcher spoonLauncher, AssertionRecord assertionRecord) {
        this.spoonLauncher = spoonLauncher;
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        if (isSupported()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }
        String assertionName = this.assertionRecord.getAssertionName();
        return new FilterResult(this.getName(), FilterDecision.REJECT, "Unsupported assertion '" + assertionName + "'.",
            FilterReasonCodes.unsupportedAssertion(assertionName), FilterReasonCodes.DEPENDS_ON_UNSUPPORTED_ASSERTION);
    }

    private boolean isSupported() {
        String assertionName = this.assertionRecord.getAssertionName();
        if (!"assertThat".equals(assertionName)) {
            return TestAnalysis.isGeneralizable(assertionName);
        }
        CtInvocation<?> assertion = resolveAssertionInvocation();
        return assertion != null && TestAnalysis.isGeneralizable(assertion);
    }

    private CtInvocation<?> resolveAssertionInvocation() {
        String absolutePath = this.assertionRecord.getAssertionAbsolutePath();
        if (this.spoonLauncher == null || absolutePath == null || absolutePath.isEmpty()) {
            return null;
        }
        CtPath path = new CtPathStringBuilder().fromString(absolutePath);
        CtModel model = this.spoonLauncher.getModel();
        CtElement assertionElement;
        try {
            assertionElement = path.evaluateOn(model.getRootPackage()).get(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
        return assertionElement instanceof CtInvocation<?> ? (CtInvocation<?>) assertionElement : null;
    }
}
