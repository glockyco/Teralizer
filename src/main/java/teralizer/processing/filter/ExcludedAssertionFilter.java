package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;

public class ExcludedAssertionFilter extends AbstractFilter {

    private final AssertionRecord assertionRecord;

    public ExcludedAssertionFilter(AssertionRecord assertionRecord) {
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        if (this.assertionRecord.getIsIncluded()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        String reason = "Generalization is part of an excluded assertion.";
        return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
    }
}
