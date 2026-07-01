package teralizer.processing.filter;

import java.util.ArrayList;
import java.util.List;
import org.jooq.generated.tables.records.AssertionRecord;

public class MissingValueFilter extends AbstractFilter {

    private final AssertionRecord assertionRecord;

    public MissingValueFilter(AssertionRecord assertionRecord) {
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        List<String> rejectionReasons = new ArrayList<>();

        if (this.assertionRecord.getTestedFilePath() == null) {
            rejectionReasons.add("The test.tested_class_path column is null.");
        }

        if (this.assertionRecord.getTestedClassName() == null) {
            rejectionReasons.add("The test.tested_class_name column is null.");
        }

        if (this.assertionRecord.getTestedMethodName() == null) {
            rejectionReasons.add("The test.tested_method_name column is null.");
        }

        if (this.assertionRecord.getTestedMethodParameters() == null) {
            rejectionReasons.add("The test.tested_method_param_types column is null.");
        }

        if (rejectionReasons.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        return new FilterResult(this.getName(), FilterDecision.REJECT, String.join(" ", rejectionReasons));
    }
}
