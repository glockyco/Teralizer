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
        return TestAnalysis.isGeneralizable(this.assertionRecord.getAssertionName())
            ? new FilterResult(this.getName(), FilterDecision.ACCEPT)
            : new FilterResult(this.getName(), FilterDecision.REJECT, "Unsupported assertion '" + this.assertionRecord.getAssertionName() + "'.");
    }
}
