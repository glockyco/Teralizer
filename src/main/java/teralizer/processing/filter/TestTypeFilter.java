package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;
import teralizer.util.Configuration;

public class TestTypeFilter extends AbstractFilter {

    private final TestRecord testRecord;

    public TestTypeFilter(TestRecord testRecord) {
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        if (Configuration.SUPPORTED_TEST_ANNOTATIONS.contains(this.testRecord.getTestAnnotationName())) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        String marker = this.testRecord.getTestAnnotationName();
        if (Configuration.TEST_MARKER_TESTNG.equals(marker)) {
            return new FilterResult(this.getName(), FilterDecision.REJECT,
                "Test is declared with TestNG, which this pipeline does not analyze.",
                FilterReasonCodes.UNSUPPORTED_FOREIGN_FRAMEWORK);
        }

        String reason = "Test uses unsupported test annotation: " + marker;
        return new FilterResult(this.getName(), FilterDecision.REJECT, reason, FilterReasonCodes.UNSUPPORTED_TEST_TYPE);
    }
}
