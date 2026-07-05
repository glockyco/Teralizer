package teralizer.processing.filter;

public class FilterResult {

    private final String filter;
    private final FilterDecision decision;
    private final String reason;
    private final String reasonCode;
    private final String dependsOn;
    private final String detailJson;

    public FilterResult(String filter, FilterDecision decision) {
        this(filter, decision, "");
    }

    public FilterResult(String filter, FilterDecision decision, String reason) {
        this(filter, decision, reason, null, null, null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason, String reasonCode) {
        this(filter, decision, reason, reasonCode, null, null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason, String reasonCode, String dependsOn) {
        this(filter, decision, reason, reasonCode, dependsOn, null);
    }

    public FilterResult(String filter, FilterDecision decision, String reason, String reasonCode, String dependsOn, String detailJson) {
        this.filter = filter;
        this.decision = decision;
        this.reason = reason;
        this.reasonCode = reasonCode;
        this.dependsOn = dependsOn;
        this.detailJson = detailJson;
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

    public String getReasonCode() {
        return this.reasonCode;
    }

    public String getDependsOn() {
        return this.dependsOn;
    }

    public String getDetailJson() {
        return this.detailJson;
    }

    @Override
    public String toString() {
        return this.filter + ": " + this.decision + (this.reason == null || this.reason.isEmpty() ? "" : ( " -> " + this.reason));
    }
}
