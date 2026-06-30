package teralizer.jpf;

/**
 * A deliberate, typed abort of one specification-extraction run, thrown by
 * {@link TestGeneralizationListener} when SPF cannot extract a specification for an assertion
 * within Teralizer's configured limits. These are expected, frequent exclusions (an assertion whose
 * path condition or search depth grows past the configured ceiling, a run that exceeds the time
 * budget, or a JDK class with an incomplete native peer) — not bugs — so each carries a typed
 * {@link Reason} rather than a free-text "unknown" failure.
 *
 * <p>The run terminates by throwing: these conditions interrupt SPF mid-search (or react to an error
 * JPF has already raised), so unwinding the listener callback stack is the correct control flow. The
 * exception propagates out of {@code jpf.run()} and the task is recorded as {@code FAILED}, which the
 * census treats as a per-assertion exclusion.
 *
 * <p>The {@link Reason} name is embedded at the front of the message so it survives into the
 * persisted {@code task.info} stack trace, letting the exclusion classifiers (the
 * {@code mv_exclusions_jpf} view, the JARVIS run script) key on a stable token instead of brittle
 * English substrings.
 */
public final class ExtractionAborted extends RuntimeException {

    /**
     * Why extraction was aborted. The four cases are siblings — each is a Teralizer-configured
     * ceiling or a recognized model gap, classified uniformly; none is special-cased.
     */
    public enum Reason {
        /** The symbolic path condition grew past {@code test_generalization.max_path_condition_size}. */
        PATH_CONDITION_TOO_LARGE,
        /** The run exceeded {@code test_generalization.max_execution_time}. */
        EXECUTION_TIMEOUT,
        /** The search reached {@code search.depth_limit} before the tested method returned. */
        SEARCH_DEPTH_LIMIT,
        /** SPF hit a JDK class whose native peer is incomplete (e.g. an unmodeled atomic). */
        NATIVE_MODEL_GAP
    }

    private final Reason reason;

    public ExtractionAborted(Reason reason, String detail) {
        super(reason.name() + ": " + detail);
        this.reason = reason;
    }

    public Reason getReason() {
        return this.reason;
    }
}
