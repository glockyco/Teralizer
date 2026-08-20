## Context

See proposal.md - Why.

The accepted exclusion-accounting contract defines which mechanism owns each excluded entity and how
reader-facing outcomes collapse those mechanisms. The report still implements parts of that mapping in
separate SQL fragments and exposes only collapsed headline tables. This change must materialize facts
without becoming a second semantic authority.

Six retired narrative files remain useful only as a migration checklist. Their historical claims mix
contracts, stale inventories, empirical observations, and explanations. A permanent claim-retirement
ledger would reproduce that mixed narrative authority in a new format. The useful operation is a
one-time audit against current source, configuration, tests, reports, provenance, and read-only corpus
observations.

## Goals / Non-Goals

**Goals:**

- One canonical entity-to-mechanism fact relation for RQ6 evidence.
- Every thesis-consumed RQ6 quantity is emitted with semantic identity, population, denominator, and
  provenance.
- Funnel counts and mechanism counts reconcile at every published entity level.
- Causal explanations beyond persisted codes have deterministic, reviewable audit evidence.
- The retired-source checklist receives a complete one-time disposition.

**Non-Goals:**

- A permanent repository-wide claim ledger, runtime retirement validator, or deletion workflow.
- Reopening `consolidate-repository-knowledge` or duplicating its exclusion-accounting requirements.
- Changing pipeline scheduling, classification decisions, measured corpus rows, or report-run
  architecture.
- Publishing directly into the thesis or deciding final prose.

## Decisions

### 1. The accepted accounting contract is the semantic authority

The report consumes the exclusion-accounting mapping established by
`consolidate-repository-knowledge`. This change may encode that mapping once in executable form and
prove it, but it does not redefine which mechanism owns an entity.

Every entity-level fact resolves to one of the declared mechanisms or inclusion. Unknown exclusion
codes, record shapes, and non-filter producers fail before aggregation. A catch-all bucket is forbidden.

### 2. One canonical fact relation feeds every table and metric

A normalized relation carries, at minimum:

- semantic corpus id and entity level;
- stable entity identity;
- mechanism identity and reader-facing outcome;
- relevant stage and producer evidence;
- attempted/emitted/adjudicated/filter-passed/validated/reduced/final-usable state; and
- provenance identifiers needed to reproduce the row.

The fact relation is built once from typed writers and explicit joins. Headline collapse, detailed
mechanism tables, funnel bands, composition metrics, and macros aggregate from it. No renderer or report
section may recreate the mechanism mapping.

Filter adjudication uses producer semantics, not storage shape alone. A quarantine record written
through `filter_result` remains a build-quarantine outcome rather than filter rejection.

### 3. The funnel is an ordered typed record, not labels on one rate

The funnel names its populations: attempted, emitted, filter-adjudicated, filter-passed, validated,
reduced, and final usable. Each count names its entering population and exclusions. First-failing-gate
attribution assigns an entity once even when later conditions would also reject it.

Project, test, assertion, and generalization funnels use the same vocabulary where the underlying state
exists. A missing attempt record is reported as unknown attempt state, not inferred from a later
failure. Final-use reporting remains valid where historical attempt state is incomplete, with that
limitation recorded.

### 4. Every metric key binds value, population, denominator, and provenance

A metric is not only a formatted scalar. It carries a stable key, raw value, unit, population, optional
denominator key, semantic corpus id, and provenance record. Rate publication fails if its numerator and
denominator do not belong to compatible populations.

The complete RQ6 consumer inventory is derived from current thesis citations and the retired-source
audit. It includes mechanism and funnel facts plus assertion-kind composition, filter and failure
causes, class-level cascades, test exclusions, output and exception-model splits, legacy and unresolved
cases, parameter and return-type composition, symbolic-argument reach, and controlled-comparison
quantities. Adding a thesis-consumed quantity later requires a registered metric or table key, never an
ad hoc prose query.

### 5. The qualitative audit is deterministic evidence, not a hidden notebook

Causal claims not already supported by persisted refusal codes or focused fixtures use a registered
audit input. Selection records the corpus id, source revision, deterministic seed or complete candidate
set, selected entity ids, observations, labels, reviewer rationale, and exclusions. The report validates
and summarizes that input without rewriting it.

The audit must be reproducible by another reviewer from the retained identifiers and source revision.
A source sample without identities is not evidence and is discarded.

### 6. Retired knowledge receives one migration record

Read the six deleted sources from their deletion parent only as a checklist. For every substantive
claim, record exactly one disposition:

- durable accepted contract;
- executable behavior with a focused check;
- registered empirical result with provenance;
- deterministic qualitative evidence;
- stale or disproved claim with current evidence; or
- intentionally discarded material with rationale.

The record lives under this change, is reviewed before archive, and then becomes historical change
evidence. No application code reads it. Repository validation does not require future documents to
append to it.

### 7. Existing architecture owns identity, rendering, and delivery

`make-report-runs-explicit` supplies `ReportContext`, `BuiltReport`, `ArtifactId`, and `ArtifactSet`.
`separate-report-values-from-presentation` supplies typed values, semantic entities, row keys, and
per-target rendering. `declare-published-artifacts` supplies consumer selection and transactional
delivery. This change supplies normalized RQ6 facts, metrics, tables, and audit artifacts only.

All generated output remains in the producer build until a consumer declaration requests it. The
thesis migration runs later from one finalized producer revision.

### 8. Commits follow causal subjects

Recommended implementation subjects:

1. normalized exclusion fact relation and invariant tests;
2. denominator funnel and metric model;
3. complete RQ6 registered metric/table surface;
4. deterministic widening audit;
5. report rendering and provenance integration;
6. source-comment corrections that share the classification cause; and
7. one-time retired-claim audit completion.

Do not mix thesis publication into these commits. Do not preserve intermediate output as a new
baseline; regenerate from the reviewed corpus and source revision.

## Risks / Trade-offs

- **The normalized relation can become a second classifier.** -> It may only consume typed producer
  evidence and the accepted mapping, with fail-loud unknowns and reconciliation against existing
  accounting totals.
- **Historical attempt state is incomplete.** -> Publish unknown state explicitly and avoid claims
  about a stage running without an independent attempt record.
- **The consumer inventory may miss a prose literal.** -> Build it from generated macro/table usage,
  current thesis claim inventory, and the one-time audit. The final thesis reconciliation performs a
  second complete consumer search.
- **A permanent ledger feels safer.** -> Rejected. It would be another narrative authority that can go
  stale. Accepted specs, executable checks, registered outputs, and change history already provide the
  durable homes.
- **The audit may overstate causality.** -> Separate persisted-code conclusions from human-reviewed
  explanations and require retained entity identities and rationale for the latter.

## Migration Plan

1. Finish and archive `consolidate-repository-knowledge`; consume its accounting contract unchanged.
2. Land corpus identity, report-run, typed rendering, and declaration prerequisites.
3. Implement and prove the canonical fact relation and denominator funnel.
4. Materialize the complete registered RQ6 metric and artifact surface.
5. Run and retain the deterministic causal audit.
6. Complete the one-time retired-source disposition and correct any stale source comments found.
7. Regenerate all registered reports once from the finalized producer revision and verify
   reconciliation, provenance, and publication into a scratch consumer.
8. Hand the exact revision and artifact manifest to `reconcile-reporeapers-claims`.

Rollback is a normal revert of the responsible producer commit. Frozen corpus rows and historical Git
state are never edited.

## Open Questions

None. Permanent knowledge-retirement machinery is rejected; the one-time audit is sufficient for this
migration.
