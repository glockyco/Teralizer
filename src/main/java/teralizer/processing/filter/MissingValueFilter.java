package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;

import java.util.ArrayList;
import java.util.List;

public class MissingValueFilter extends AbstractFilter {

    private final TestRecord testRecord;

    public MissingValueFilter(TestRecord testRecord) {
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        List<String> rejectionReasons = new ArrayList<>();

        if (this.testRecord.getTestedFilePath() == null) {
            rejectionReasons.add("The test.tested_class_path column is null.");
        }

        if (this.testRecord.getTestedClassName() == null) {
            rejectionReasons.add("The test.tested_class_name column is null.");
        }

        if (this.testRecord.getTestedMethodName() == null) {
            rejectionReasons.add("The test.tested_method_name column is null.");
        }

        if (this.testRecord.getTestedMethodParamTypes() == null) {
            rejectionReasons.add("The test.tested_method_param_types column is null.");
        }

        if (rejectionReasons.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        return new FilterResult(this.getName(), FilterDecision.REJECT, String.join(" ", rejectionReasons));
    }
}
