package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;

public class ExcludedTestFilter extends AbstractFilter {

    private final TestRecord testRecord;

    public ExcludedTestFilter(TestRecord testRecord) {
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        if (this.testRecord.getIsIncluded()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        String reason = "Assertion / Generalization is part of an excluded test.";
        return new FilterResult(this.getName(), FilterDecision.REJECT, reason,
            FilterReasonCodes.EXCLUDED_PARENT_TEST, FilterReasonCodes.DEPENDS_ON_EXCLUDED_TEST);
    }
}
