package teralizer.processing.filter;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.TestRecord;

import java.util.List;

public class AssertionCountFilter extends AbstractFilter {

    private final DSLContext create;
    private final TestRecord testRecord;

    public AssertionCountFilter(DSLContext create, TestRecord testRecord) {
        this.create = create;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        List<AssertionRecord> assertions = this.create.selectFrom(Tables.ASSERTION)
            .where(Tables.ASSERTION.TEST_ID.equal(this.testRecord.getId()))
            .fetch();

        if (assertions.size() != 1) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "assertions.size() != 1.");
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
