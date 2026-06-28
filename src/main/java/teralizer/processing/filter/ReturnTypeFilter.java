package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;

import teralizer.util.TypeCapability;

public class ReturnTypeFilter extends AbstractFilter {

    private final AssertionRecord assertionRecord;

    public ReturnTypeFilter(AssertionRecord assertionRecord) {
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        if (this.assertionRecord.getTestedMethodReturnType() == null) {
            return new FilterResult(this.getName(), FilterDecision.DEFER, "The assertion.tested_method_return_type column is null.");
        }

        if (this.assertionRecord.getTestedMethodReturnType().equals("void") || this.assertionRecord.getTestedMethodReturnType().equals("java.lang.Void")) {
            String reason = "Tested method has void return type: " + this.assertionRecord.getTestedMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
        }

        if (!TypeCapability.supportsReturnValue(this.assertionRecord.getTestedMethodReturnType())) {
            String reason = "Tested method has unsupported return type: " + this.assertionRecord.getTestedMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
