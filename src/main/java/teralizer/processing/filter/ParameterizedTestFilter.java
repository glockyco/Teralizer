package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;

public class ParameterizedTestFilter extends AbstractFilter {

    private final TestRecord testRecord;

    public ParameterizedTestFilter(TestRecord testRecord) {
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        if (this.testRecord.getIsParameterized()) {
            String reason = "Test is a parameterized test: " + this.testRecord.getTestMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
