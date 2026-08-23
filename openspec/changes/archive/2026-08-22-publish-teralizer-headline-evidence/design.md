## Context

See `proposal.md` for motivation. RQ0 already publishes stable breadth metrics. RQ1 computes the mutation-improvement frame used by its figure but exposes only project and row counts as metrics, so summary ranges are prose-derived. RQ6 already publishes project applicability, assertion survival, widening-refusal counts, and final-usable generalizations, but the aggregate macro set omits some of these values and no overall widening-refusal share uses all attempts as its denominator.

The producer owns evidence identities and generated values. The thesis owns reader-facing wording. Internal names such as `final_usable` are suitable semantic keys but are not automatically suitable abstract prose.

## Goals / Non-Goals

**Goals:**
- Compute headline values once from registered report results.
- Preserve cohort, population, denominator, corpus input, and source provenance.
- Fail on incomplete RQ1 cohorts or inconsistent RQ6 lifecycle/project populations.
- Make every approved evidence operand available in aggregate macros and provenance.
- Hand off evidence dimensions without deciding final thesis wording.

**Non-Goals:**
- No new corpus query semantics, pipeline run, database schema, or lifecycle state.
- No composite headline or single “success score.”
- No direct RQ5/RQ6 success-rate comparison.
- No thesis prose edits or lifecycle-jargon-to-prose translation.
- No reinterpretation of existing RQ0, RQ1, RQ5, or RQ6 tables.

## Decisions

### 1. Keep headline values in their owning registered reports

RQ1 owns effectiveness values, RQ0 owns JARVIS breadth, and RQ6 owns real-world applicability, output, and mechanism evidence. The aggregate macro artifact remains the publication join point. Do not create a second report that fetches the same databases or copies values from other reports.

**Alternative:** Add a registered `headline` report. Rejected because it would duplicate report inputs and either repeat queries or depend on presentation artifacts.

### 2. Derive RQ1 ranges from one validated result frame

Add a pure RQ1 summary function over the already fetched mutation-improvement frame. It classifies rows into three explicit cohorts:

- EvoSuite-generated EqBench projects;
- EvoSuite-generated Apache Commons projects; and
- developer-written Apache Commons projects.

For each cohort, validate the expected project/budget matrix, one baseline row per project, and the declared generalized variants before calculating minimum and maximum absolute improvement. Publish the developer-written baseline separately. Use stable keys such as:

- `effectiveness.eqbench_evosuite.mutation_improvement_min_pp` / `_max_pp`;
- `effectiveness.commons_evosuite.mutation_improvement_min_pp` / `_max_pp`;
- `effectiveness.commons_developer.mutation_improvement_min_pp` / `_max_pp`; and
- `effectiveness.commons_developer.baseline_mutation_score_pct`.

The implementation must verify these names against repository key conventions and keep the final names semantic rather than RQ-number based. Capture provenance from the source query/result builder used by the frame, not from a prose literal.

**Alternative:** Store the currently printed range endpoints as constants. Rejected because regeneration could change the table while leaving the headline stale.

### 3. Reuse existing applicability metrics without a new aggregate

RQ0 already exposes the intended 12-project benchmark denominator, Teralizer project breadth, and JARVIS reported project breadth. RQ6 already exposes 85 end-to-end applicable projects out of 584 eligible projects. Add missing keys to aggregate macro publication if necessary, but do not compute a cross-evaluation applicability score.

### 4. Publish final-usable output at both entity levels

Keep `realworld.generalizations_final_usable` as the generalized-test count. Extend the existing lifecycle query/result to expose the distinct project count containing those rows as `realworld.final_usable_projects` (final key subject to convention review). Validate equality with `realworld.applicability_projects` before returning the report. Publish both values as macros with their existing RQ6 corpus and query provenance.

This gives the thesis evidence for a reader-facing statement about tangible output and project breadth while leaving the phrase replacing “final usable” to the later wording session.

### 5. Add one overall widening-refusal rate

`widening_refusal_metrics` already emits the total refusal count and branch shares. Add `realworld.widening_refusals_pct` with:

- numerator `realworld.widening_refusals`;
- denominator `realworld.generalization_attempts`;
- population `Generalization`, input role `real-world`; and
- the same widening query provenance as the count.

Branch percentages continue to use total refusals. The overall percentage must not reuse a branch-share denominator.

Assertion survival already has count/share metrics. Promote the existing metrics to aggregate macros rather than creating a second “barrier” calculation.

### 6. Use macro selection as the explicit thesis API

Add only the approved headline evidence keys to each report's expected aggregate macro set. Manifest publication already preserves metric type, population, operands, input snapshot, and source provenance. No general-purpose headline metadata model is needed: the four-dimension grouping belongs in the producer handoff and thesis planning record, not in every scalar metric.

**Alternative:** Add a `headline_dimension` field to `Metric`. Rejected because the grouping is a downstream communication decision, not part of the measured value, and would add metadata to every renderer for one consumer.

### 7. Make the wording session an external acceptance gate

Producer completion ends with a handoff listing stable keys under the four approved dimensions. The thesis reconciliation change must schedule a separate planning/discussion session before prose edits. That session must:

1. choose reader-facing language for demonstrated output and mechanism insight;
2. avoid internal lifecycle or table terminology in the abstract;
3. decide which dimensions appear at each summary site;
4. preserve values, entity populations, and denominators; and
5. review the resulting wording before the numbers-only/semantic prose cutover resumes.

The producer does not block artifact generation on wording, but the thesis change blocks summary prose edits on this session.

## Risks / Trade-offs

- **Range selection silently changes.** Fail on missing or extra RQ1 cohort rows and test the exact selection matrix.
- **A positive headline hides real-world limits.** Always pair the final-usable output count with its project population; do not publish the count as an unbounded total.
- **Widening share uses the wrong denominator.** Encode numerator/denominator keys and test arithmetic plus branch-total conservation.
- **Internal terminology leaks into the abstract.** Keep wording explicitly deferred and add a thesis planning gate.
- **The four dimensions become four unrelated number lists.** The later wording session owns narrative hierarchy: effectiveness and applicability primary, output and insight supporting.
- **Generated values change after a report refresh.** Stable metric keys and macros update from the same registered run; no prose literal is authoritative.
