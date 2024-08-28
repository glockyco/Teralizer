package teralizer.processing.filter;

public class FilterResult {

    private final String filter;
    private final FilterDecision decision;
    private final String reason;

    public FilterResult(String filter, FilterDecision decision) {
        this(filter, decision, "");
    }

    public FilterResult(String filter, FilterDecision decision, String reason) {
        this.filter = filter;
        this.decision = decision;
        this.reason = reason;
    }

    public String getFilter() {
        return this.filter;
    }

    public FilterDecision getDecision() {
        return this.decision;
    }

    public String getReason() {
        return this.reason;
    }

    @Override
    public String toString() {
        return this.filter + ": " + this.decision + (this.reason == null || this.reason.isEmpty() ? "" : ( " -> " + this.reason));
    }
}
