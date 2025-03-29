package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;

public class VoidReturnTypeFilter extends AbstractFilter {

    private final AssertionRecord assertionRecord;

    public VoidReturnTypeFilter(AssertionRecord assertionRecord) {
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        if (this.assertionRecord.getTestedMethodReturnType().equals("void") || this.assertionRecord.getTestedMethodReturnType().equals("java.lang.Void")) {
            String reason = "Tested method has void return type: " + this.assertionRecord.getTestedMethodQualifiedName();
            return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
