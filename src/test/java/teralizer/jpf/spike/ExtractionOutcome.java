package teralizer.jpf.spike;

import gov.nasa.jpf.symbc.numeric.Expression;

/**
 * Spike for P2: the total, typed outcome of one extraction run, derived purely from the observer's
 * recorded state. Every run maps to exactly one {@link Kind} — there is no untyped "unknown reason"
 * branch, which is the production gap the unreachable-assertion case exposed.
 */
public final class ExtractionOutcome {

    public enum Kind {
        EXTRACTED,
        TARGET_NOT_ENTERED,
        TARGET_NOT_EXITED
    }

    public final Kind kind;
    public final String detail;
    public final String concreteOut;
    public final Expression symbolicOut;
    public final Integer matchedDepth;

    private ExtractionOutcome(Kind kind, String detail, String concreteOut, Expression symbolicOut, Integer matchedDepth) {
        this.kind = kind;
        this.detail = detail;
        this.concreteOut = concreteOut;
        this.symbolicOut = symbolicOut;
        this.matchedDepth = matchedDepth;
    }

    /** Pure classification from recorded observer state — no JPF, no VM, no I/O. */
    public static ExtractionOutcome classify(SpikeObserverListener listener) {
        if (!listener.targetEntered) {
            return new ExtractionOutcome(Kind.TARGET_NOT_ENTERED,
                "tested method never entered on the concrete path (unreachable assertion)", null, null, null);
        }
        if (!listener.targetExited) {
            return new ExtractionOutcome(Kind.TARGET_NOT_EXITED,
                "tested method entered but did not return in-state", null, null, null);
        }
        return new ExtractionOutcome(Kind.EXTRACTED, "ok", listener.concreteOut, listener.symbolicOut, listener.matchedDepth);
    }
}
