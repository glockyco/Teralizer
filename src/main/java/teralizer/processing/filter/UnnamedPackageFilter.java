package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;

public class UnnamedPackageFilter extends AbstractFilter {

    private final TestRecord testRecord;

    public UnnamedPackageFilter(TestRecord testRecord) {
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        // Classes that are part of the default / unnamed package cannot be
        // imported by classes from named packages, so we cannot do any useful
        // processing for them (without the use of reflection).

        if (this.testRecord.getTestPackageName() == null || this.testRecord.getTestPackageName().isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "test.test_package_name is empty");
        }

        if (this.testRecord.getTestPackageName() == null || this.testRecord.getTestPackageName().isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, "test.tested_package_name is empty");
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
