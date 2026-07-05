package teralizer.processing.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
        List<String> reasonCodes = new ArrayList<>();

        if (this.assertionRecord.getTestedFilePath() == null) {
            rejectionReasons.add("The test.tested_class_path column is null.");
            reasonCodes.add(FilterReasonCodes.MISSING_TESTED_FILE);
        }

        if (this.assertionRecord.getTestedClassName() == null) {
            rejectionReasons.add("The test.tested_class_name column is null.");
            reasonCodes.add(FilterReasonCodes.MISSING_TESTED_CLASS);
        }

        if (this.assertionRecord.getTestedMethodName() == null) {
            rejectionReasons.add("The test.tested_method_name column is null.");
            reasonCodes.add(FilterReasonCodes.MISSING_TESTED_METHOD);
        }

        if (this.assertionRecord.getTestedMethodParameters() == null) {
            rejectionReasons.add("The test.tested_method_param_types column is null.");
            reasonCodes.add(FilterReasonCodes.MISSING_TESTED_PARAMS);
        }

        if (rejectionReasons.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        return new FilterResult(this.getName(), FilterDecision.REJECT, String.join(" ", rejectionReasons),
            reasonCodes.get(0), FilterReasonCodes.DEPENDS_ON_MISSING_MUT, details(reasonCodes));
    }

    private static String details(List<String> reasonCodes) {
        JsonArray array = new JsonArray();
        for (String reasonCode : reasonCodes) {
            array.add(reasonCode);
        }
        JsonObject detail = new JsonObject();
        detail.add("all_reason_codes", array);
        return detail.toString();
    }
}
