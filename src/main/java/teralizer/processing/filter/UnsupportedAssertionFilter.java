package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;
import teralizer.spoon.analysis.TestAnalysis;

public class UnsupportedAssertionFilter extends AbstractFilter {

    private final AssertionRecord assertionRecord;

    public UnsupportedAssertionFilter(AssertionRecord assertionRecord) {
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        if (TestAnalysis.isGeneralizable(this.assertionRecord.getAssertionName())) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }
        String assertionName = this.assertionRecord.getAssertionName();
        return new FilterResult(this.getName(), FilterDecision.REJECT, "Unsupported assertion '" + assertionName + "'.",
            FilterReasonCodes.unsupportedAssertion(assertionName), FilterReasonCodes.DEPENDS_ON_UNSUPPORTED_ASSERTION);
    }
}
