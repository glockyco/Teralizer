package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;

import java.util.ArrayList;
import java.util.List;

public class MissingValueFilter extends AbstractFilter {

    @Override
    public FilterResult check(TestRecord testRecord) {
        List<String> rejectionReasons = new ArrayList<>();

        if (testRecord.getTestedClassPath() == null) {
            rejectionReasons.add("The test.tested_class_path column is null.");
        }

        if (testRecord.getTestedClassName() == null) {
            rejectionReasons.add("The test.tested_class_name column is null.");
        }

        if (testRecord.getTestedMethodName() == null) {
            rejectionReasons.add("The test.tested_method_name column is null.");
        }

        if (testRecord.getTestedMethodParamTypes() == null) {
            rejectionReasons.add("The test.tested_method_param_types column is null.");
        }

        if (rejectionReasons.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        return new FilterResult(this.getName(), FilterDecision.REJECT, String.join(" ", rejectionReasons));
    }
}
