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

- One executable mechanism mapping and shared vocabulary for RQ6 evidence.
- Separate typed relations preserve the evidence available at each entity level.
- Every retained thesis RQ6 quantity is emitted with semantic identity, population, denominator where
  applicable, and resolvable provenance.
- Funnel counts and mechanism counts reconcile at each entity level that has the required evidence.
- A surviving causal explanation beyond persisted codes and focused fixtures has deterministic,
  reviewable audit evidence.
- The retired-source checklist receives a complete one-time disposition.

**Non-Goals:**

- A permanent repository-wide claim ledger, runtime retirement validator, or deletion workflow.
- Reopening `consolidate-repository-knowledge` or duplicating its exclusion-accounting requirements.
- Changing pipeline scheduling, classification decisions, measured corpus rows, or report-run
  architecture.
- Publishing directly into the thesis or deciding final prose.

## Decisions

### 0. Finalized upstream contracts are preconditions

Implementation starts only after the changes that own its inputs are accepted. `consolidate-repository-knowledge` owns the exclusion-accounting semantics. `make-report-runs-explicit` owns complete report runs and artifact manifests. `separate-report-values-from-presentation` owns typed values and target rendering. `declare-published-artifacts` owns declaration validation and consumer delivery.

All prerequisites are synced and archived. This change consumes `consolidate-evaluation-databases` at
`edf5ae290a0659266fec28530c4873ab0db0a808`, `make-report-runs-explicit` at
`6409d66588c271ffdcd4b75229319fa7459579da`, `separate-report-values-from-presentation` at
`2cb26ea0f852c0163a0805dd06d464399e6787ee`, `consolidate-repository-knowledge` at
`4042046a87e67048cdde506642320435a5865759`, and `declare-published-artifacts` at
`595db740d4a8e2c860d5e658e9e0755467c54a33`. The apply phase verifies those accepted interfaces; it does
not rediscover their dependency order or duplicate their implementation. `materialize-exclusion-evidence`
is then the sole producer of the finalized mechanism keys, denominator keys, and retained claim-facing
RQ6 evidence consumed by downstream release and thesis changes.

### 1. The accepted accounting contract is the semantic authority

The report consumes the exclusion-accounting mapping established by
`consolidate-repository-knowledge`. This change may encode that mapping once in executable form and
prove it, but it does not redefine which mechanism owns an entity.

Every entity-level fact resolves to one of the declared mechanisms or inclusion. Unknown exclusion
codes, record shapes, and non-filter producers fail before aggregation. A catch-all bucket is forbidden.

### 2. One semantic mapping feeds several typed evidence relations

The executable mechanism mapping and reader-facing collapse are defined once. Each typed relation then
carries only evidence that exists for its entity level. The expected relations cover project and test
lifecycle observations, assertion observations, filter adjudication, and generated-generalization
lifecycle observations. Their keys and joins are explicit; an absent attempt record is not manufactured
from a later-stage row.

A relation carries the stable entity identity, mechanism evidence, relevant stage or producer evidence,
and the keys needed to join it to the owning report input. Corpus identity, source revision, and dirty
state remain in the run-captured input snapshots and provenance manifest rather than being copied into
every entity row.

Headline collapse, mechanism tables, funnel bands, retained metrics, and macros aggregate from the
appropriate typed relations. Cross-level invariants compare compatible aggregates. No renderer, report
section, or relation may recreate the mechanism mapping.

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

### 4. Metric metadata describes the measured fact; the run manifest describes its inputs

A metric carries a stable key, raw value, value kind, population, optional denominator key, and the
existing code-provenance reference. Rate publication fails if its numerator and denominator do not
belong to compatible populations. The owning report and run manifest resolve semantic corpus identity,
repository-file input revisions, content identities, and dirty state. Copying those report-input facts
into every scalar would duplicate the accepted provenance authority.

The downstream thesis claim inventory determines the retained RQ6 consumer surface. This producer emits
the mechanism partition, funnel populations, their denominators, and only the comparison or causal
quantities that the final thesis argument keeps. Current macros, table cells, and prose literals are
candidate consumers, not an instruction to preserve every historical scalar. A retained quantity
requires a registered metric or semantic table key, never an ad hoc prose query.

### 5. A qualitative audit exists only for a surviving evidence gap

First inventory the causal explanations the thesis proposes to retain. Persisted refusal codes own
measured mechanism counts. Focused executable fixtures own deterministic mechanism behavior. If those
sources support every retained explanation, record that result and create no qualitative audit input.

If a retained explanation still requires reviewer interpretation, create one registered audit input for
that bounded question. Selection records the corpus id, source revision, deterministic seed or complete
candidate set, selected entity ids, observations, labels, reviewer rationale, and exclusions. The report
validates and summarizes that input without rewriting it. Another reviewer must be able to reconstruct
the selected entities. An identity-free source sample is not evidence and is discarded.

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
delivery. This change supplies typed RQ6 evidence relations, retained metrics and tables, and a
conditional audit artifact only.

All generated output remains in the producer build until a consumer declaration requests it. The
thesis change may audit and commit that declaration against a clean producer candidate while this
change remains active. The declaration records consumer selection only; it is not an evidence baseline.
This change uses the committed declaration for scratch publication, freezes the evidence revision, and
confirms that the thesis change records that exact revision before archive. The thesis claim and artifact
migration starts only after this change is archived.

### 8. Commits follow causal subjects

Recommended implementation subjects:

1. executable mechanism mapping and typed evidence relations;
2. denominator funnel and metric metadata;
3. retained RQ6 registered metric and table surface;
4. bounded causal audit, only if the claim inventory proves it necessary;
5. report rendering and provenance integration;
6. source-comment corrections that share the classification cause; and
7. one-time retired-claim audit completion.

Do not mix thesis publication into these commits. Do not preserve intermediate output as a new
baseline; regenerate from the reviewed corpus and source revision.

## Risks / Trade-offs

- **A typed evidence relation can become a second classifier.** -> Every relation consumes the one
  executable mapping and typed producer evidence, fails on unknowns, and reconciles against accepted
  accounting totals.
- **Separate relations can drift.** -> Share mechanism keys and compare only compatible keyed
  aggregates. Do not align unrelated levels through nullable columns or synthetic rows.
- **Historical attempt state is incomplete.** -> Publish unknown state explicitly and avoid claims
  about a stage running without an independent attempt record.
- **The retained consumer inventory may miss a claim.** -> Build it from the downstream thesis claim
  inventory, generated macro and semantic-table usage, and the one-time retired-source audit. The final
  thesis reconciliation performs a second complete consumer search.
- **A permanent ledger feels safer.** -> Rejected. It would be another narrative authority that can go
  stale. Accepted specs, executable checks, registered outputs, and change history already provide the
  durable homes.
- **The audit may overstate causality or exist without need.** -> Prefer persisted codes and executable
  fixtures. Create an audit only for a surviving bounded question, and retain identities and rationale.

## Migration Plan

1. Verify the five archived prerequisite revisions and consume their accepted contracts unchanged.
2. Complete the one-time retired-source claim inventory and identify retained thesis quantities and
   causal explanations.
3. Implement and prove the shared mechanism mapping, typed evidence relations, and denominator funnel.
4. Materialize the retained registered RQ6 metric and artifact surface.
5. If persisted codes and fixtures leave a retained causal explanation unsupported, run and retain the
   bounded deterministic audit.
6. Correct stale source comments found by the inventory.
7. Regenerate all registered reports once from a clean producer candidate and verify reconciliation
   and provenance.
8. Have `reconcile-reporeapers-claims` audit and commit its explicit consumer declaration against that
   candidate without migrating thesis artifacts or claims.
9. Use the committed declaration to prove transactional publication and thesis compatibility in a clean
   scratch checkout.
10. Freeze the exact producer evidence revision and manifest, confirm that the thesis change records
    them, and archive this change before the thesis migration starts.

Rollback is a normal revert of the responsible producer commit. Frozen corpus rows and historical Git
state are never edited.

## Open Questions

None. Permanent knowledge-retirement machinery is rejected; the one-time audit is sufficient for this
migration.
