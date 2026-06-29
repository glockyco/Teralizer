package teralizer.processing.filter;

public class FilterResult {

    private final String filter;
    private final FilterDecision decision;
    private final String reason;
    private final Integer distinctNewTuples;

    public FilterResult(String filter, FilterDecision decision) {
        this(filter, decision, "", null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason) {
        this(filter, decision, reason, null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason, Integer distinctNewTuples) {
        this.filter = filter;
        this.decision = decision;
        this.reason = reason;
        this.distinctNewTuples = distinctNewTuples;
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

    public Integer getDistinctNewTuples() {
        return this.distinctNewTuples;
    }

    @Override
    public String toString() {
        return this.filter + ": " + this.decision + (this.reason == null || this.reason.isEmpty() ? "" : ( " -> " + this.reason));
    }
}
