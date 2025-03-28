package teralizer.processing.filter;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TestRecord;

public class NoAssertionsFilter extends AbstractFilter {

    private final DSLContext create;
    private final TestRecord testRecord;

    public NoAssertionsFilter(DSLContext create, TestRecord testRecord) {
        this.create = create;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        int assertionCount = this.create
            .selectCount()
            .from(Tables.ASSERTION)
            .where(Tables.ASSERTION.TEST_ID.eq(this.testRecord.getId()))
            .fetchOneInto(Integer.class);

        if (assertionCount == 0) {
            String reason = "No assertions found in test: " + this.testRecord.getTestMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
