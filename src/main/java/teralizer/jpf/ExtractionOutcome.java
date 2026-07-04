package teralizer.jpf;

/**
 * The total, typed outcome of one specification-extraction run. Every run maps to exactly one
 * {@link Kind}, so the pipeline never records an untyped "unknown reason" failure — the gap the
 * beyond-JARVIS census exposed when an unreachable assertion produced no specification and no error.
 */
public final class ExtractionOutcome {

    public enum Kind {
        /** The tested method returned in-state; a specification was captured. */
        EXTRACTED,
        /** The tested method never executed on the concrete path (e.g. an unreachable assertion). */
        TARGET_NOT_ENTERED,
        /** The tested method was entered but did not return in-state (no capture point reached). */
        TARGET_NOT_EXITED,
        /** A symbolic operation SPF cannot model soundly was reached (recorded as an exclusion). */
        UNSUPPORTED_TERM
    }

    private final Kind kind;
    private final String detail;

    private ExtractionOutcome(Kind kind, String detail) {
        this.kind = kind;
        this.detail = detail;
    }

    public Kind getKind() {
        return this.kind;
    }

    /** A human-readable reason, never empty — replaces the "unknown reason" catch-all. */
    public String getDetail() {
        return this.detail;
    }

    /**
     * Classify a run from the listener's observable state: the tested method either never executed
     * on the concrete path, entered but never returned in-state, or returned and produced a spec.
     * An exit without an entry is impossible; it fails fast rather than being mislabeled.
     */
    public static ExtractionOutcome fromState(
        boolean targetEntered,
        boolean targetExited,
        boolean expressionRecipe
    ) {
        if (expressionRecipe) {
            if (targetExited) {
                return new ExtractionOutcome(Kind.EXTRACTED, targetEntered
                    ? "specification extracted; tested method entered"
                    : "specification extracted; tested method not entered on the concrete path");
            }
            return new ExtractionOutcome(Kind.TARGET_NOT_EXITED,
                targetEntered
                    ? "instrumented expression wrapper did not return in-state after tested method entry"
                    : "instrumented expression wrapper did not return in-state");
        }
        if (targetExited && !targetEntered) {
            throw new IllegalArgumentException(
                "corrupt extraction state: tested method exited without being entered");
        }
        if (!targetEntered) {
            return new ExtractionOutcome(Kind.TARGET_NOT_ENTERED,
                "tested method never entered on the concrete path (unreachable assertion)");
        }
        if (!targetExited) {
            return new ExtractionOutcome(Kind.TARGET_NOT_EXITED,
                "tested method entered but did not return in-state");
        }
        return new ExtractionOutcome(Kind.EXTRACTED, "specification extracted");
    }

    public static ExtractionOutcome fromState(boolean targetEntered, boolean targetExited) {
        return fromState(targetEntered, targetExited, false);
    }

    /** An unsupported/unsound symbolic term was reached at run time — recorded as an exclusion. */
    public static ExtractionOutcome unsupportedTerm(String detail) {
        return new ExtractionOutcome(Kind.UNSUPPORTED_TERM, detail);
    }
}
