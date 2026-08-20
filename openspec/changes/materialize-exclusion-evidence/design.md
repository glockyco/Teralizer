## Context

See `proposal.md` for motivation and `specs/repository/knowledge-retirement/spec.md` and
`specs/reporting/exclusion-evidence/spec.md` for the required behavior.

The current RQ6 implementation already has the right core distinction: an internal mechanism table
separates included, filter rejection, pre-emission refusal, unsupported capability, and failure, then
a declared mapping collapses those mechanisms into reader-facing inclusion, filtering, and failures.
The registered output exposes only the collapsed table. Its generalization row also mixes attempted,
emitted, filter-adjudicated, filter-passed, and final-usable populations.

Current persistence is intentionally heterogeneous. Filter decisions use `filter_result`; gate refusals
use typed generalization exclusion codes without lifecycle rows; unsupported tests use a typed test
exclusion; build quarantine uses a filter-shaped rejection with a non-filter producer; task failures
use task, diagnostic, exclusion, or lifecycle records. Classification must therefore follow the writer
and typed code, not the storage table or the shape of `exclusion_info` alone.

The implementation must compose with four active changes:

- `consolidate-evaluation-databases` owns corpus registry and schema validation.
- `separate-report-values-from-presentation` owns typed report values and renderers.
- `declare-published-artifacts` owns consumer declarations and publication manifests.
- `consolidate-repository-knowledge` owns the broader knowledge-authority cutover and currently contains
  the incomplete retirement assessment this change repairs.

The v7 corpus is frozen evidence. Report code may read it through the corpus registry, but no task in
this change may mutate it or manufacture a cleaner rerun. The existing commits remain in place.

## Goals / Non-Goals

**Goals:**

- Derive one auditable exclusion fact model and use it for both mechanism and funnel outputs.
- Preserve typed values until target rendering and attach one consistent provenance record to every
  emitted result.
- Make every arithmetic identity executable and fail before publication on disagreement.
- Replace the anonymous historical source sample with a deterministic, reviewable v7 audit.
- Produce a claim ledger that proves the six retired sources no longer carry unique current knowledge.
- Land implementation and consumer updates as append-only, single-subject commits.

**Non-Goals:**

- Changing exclusion decisions, stage scheduling, persistence semantics, or the frozen v7 corpus.
- Treating `is_included` as evidence of final yield.
- Repairing the EM-7 lifecycle writer defect. This change preserves its strict expected-failure check
  and prevents the report from overstating attempted stages.
- Restoring a free-standing narrative documentation tree.
- Rewriting, squashing, rebasing, amending, or force-pushing existing commits.
- Folding the later thesis prose refresh into the analysis repository's commit history.

## Decisions

### 1. Build one entity-fact relation before aggregation

The report will first derive one row per reportable test, assertion, or generalization with these
logical fields:

```text
corpus, variant, level, entity_id, included,
mechanism, mechanism_code, writer_class,
was_emitted, was_filter_adjudicated, was_filter_passed,
was_final_usable, downstream_failure_stage
```

Mechanism assignment uses explicit precedence because the physical channels overlap:

```text
pre-emission typed gate
    > unsupported typed capability
    > non-filter quarantine producer
    > actual filter-class decision
    > task/diagnostic/lifecycle failure
    > included
```

The implementation will reject contradictory matches rather than silently accepting precedence as a
repair. The precedence only resolves valid overlapping storage representations, such as quarantine in
`filter_result`.

Both the mechanism partition and generalization funnel aggregate this relation. This avoids two SQL
queries that independently encode classification and later drift.

**Alternative considered:** Keep the current breakdown query and add an unrelated funnel query. This is
smaller initially, but it duplicates the hardest semantic boundary and makes reconciliation a test of
two implementations rather than a property of one model.

### 2. Keep mechanism identity separate from reader-facing outcome

The fact relation carries one of six values: `included`, `filter-rejection`, `pre-emission-refusal`,
`unsupported-capability`, `build-quarantine`, or `task-failure`. A single declared mapping produces the
three reader-facing outcomes:

```text
included                                      -> inclusion
filter-rejection, pre-emission-refusal,
unsupported-capability                        -> filtering
build-quarantine, task-failure                -> failures
```

Tables that answer "what excluded the entity" use the mechanism values. Headline tables use the
collapse. No renderer or report section may recreate this mapping.

**Alternative considered:** Add more reader-facing columns to every existing exclusion table. That
would preserve detail but overload the comparative tables and spread mechanism semantics into
presentation code.

### 3. Model the generalization funnel as count identities, not labels on one rate

The funnel is an ordered typed record with named counts:

```text
attempts
  - seed_refused
  - widening_refused
= emitted
  - pre_filter_failures
  - build_quarantines
= filter_adjudicated
  - filter_rejected
= filter_passed
  - downstream_attrition
= final_usable
```

Every equality is checked during report construction. Rates are derived only after the counts pass and
have denominator-bearing metric keys. At minimum, the report exposes:

- filter passed / attempts;
- filter passed / emitted;
- filter passed / filter adjudicated;
- final usable / filter passed;
- final usable / attempts.

A build validator row is quarantine, not filter adjudication. A pre-emission refusal has no emitted
row. First-failing-gate attribution assigns an attempt to exactly one gate even when later conditions
would also reject it.

**Alternative considered:** Preserve `generalizations_validated` as the sole success metric. The name
hides whether it means filter-passed or final-usable and cannot support denominator-specific claims.

### 4. Publish normalized evidence and render consumer artifacts from it

The registered report emits two normalized data tables in addition to its Markdown section:

- `rq6-exclusion-mechanisms.csv`: entity level by mechanism, count, and share;
- `rq6-generalization-funnel.csv`: ordered funnel step, count, preceding-step share, and attempt share.

The common typed table model produces any LaTeX tables and Markdown views. Metrics expose all counts and
rates needed by downstream macros. Publication declarations identify only the LaTeX artifacts and
metrics that the thesis consumes; normalized CSVs remain supporting build evidence unless explicitly
declared.

All rows and metrics share one captured corpus and source provenance object. Report construction occurs
inside one read-only consistent database snapshot. The corpus is selected through the registry; the
physical v7 database name is not embedded in report code or tests.

**Alternative considered:** Generate thesis-specific LaTeX directly inside RQ6. That repeats the
presentation coupling being removed by `separate-report-values-from-presentation`.

### 5. Store a deterministic widening audit as versioned source evidence

The replacement audit lives in `analysis/audits/rq6-widening-v7.json`. It contains:

- audit schema version and audit purpose;
- corpus registry ID, database snapshot identity, report variant, and selection-query identity;
- implementation and inspected-project source revisions;
- complete population and stratum definitions;
- deterministic selection seed and ordered selected generalization/assertion IDs;
- project, test, method, output-shape, literal-signal, and refusal-code observations;
- reviewer-assigned causal label and rationale;
- reviewer identity and review timestamp.

A command derives the immutable candidate records from the frozen corpus and checks stored observations
against them. Human labels and rationale remain versioned input. The registered report summarizes only
validated records and states the audit's sampling limits. The audit supports causal examples and
within-sample observations, not corpus-wide prevalence unless its sampling design justifies that
estimator.

**Alternative considered:** Re-run `ORDER BY random()` and keep aggregate notes. That cannot recover the
reviewed entities, source state, or selection probability and repeats the evidence loss.

### 6. Record retirement proof in a structured claim ledger

Implementation creates
`openspec/changes/materialize-exclusion-evidence/evidence/retired-knowledge-claims.yaml`. Each entry has:

```text
source_file, source_anchor, normalized_claim,
knowledge_class, disposition, current_owner,
verification, status, notes
```

The six deleted sources are read from the parent of their deletion commit only to enumerate candidate
claims. Every conclusion is re-derived from current source, configuration, tests, registered reports,
or read-only corpus checks. The ledger rejects duplicate entries, missing owners, unverifiable
"derivable" dispositions, and retained claims whose owner does not exist or produce the asserted fact.

The ledger stays with the OpenSpec change and moves to its archive with the completed change. Durable
requirements move into accepted capabilities; current empirical values remain report outputs. The
ledger is audit evidence for the cutover, not a second documentation authority.

**Alternative considered:** Restore the six Markdown files and correct them. That recreates a manually
maintained shadow of contracts, implementation, and report results and will drift again.

### 7. Reconcile overlapping active changes before code work

Before report implementation, update `consolidate-repository-knowledge` so it:

- reopens the six-document claim audit and final cutover checks;
- states the explicit five-mechanism collapse and filter-adjudication boundary;
- records that derived lifecycle failure stage does not prove attempted stage;
- requires reproducible qualitative evidence;
- treats this change's accepted capabilities and evidence ledger as replacement owners.

Then re-read the active database, typed-value, and publication designs and use their finalized
interfaces. If those changes have not established the required interfaces, complete their prerequisite
tasks first under their own change ownership rather than adding compatibility shims here.

**Alternative considered:** Duplicate provisional registry, rendering, or publishing helpers in this
change. That would create the parallel architecture the active changes exist to remove.

### 8. Use append-only atomic commits

Implementation uses new Conventional Commits only. Each commit has one subject, a causal body, and its
focused verification. The intended boundaries are:

1. `docs(openspec)`: reconcile retirement and exclusion contracts and record the claim audit method;
2. `feat(eval)`: derive and validate the canonical exclusion fact relation and mechanism partition;
3. `feat(eval)`: materialize the denominator-explicit generalization funnel and metrics;
4. `feat(eval)`: add and validate the deterministic widening audit and registered summary;
5. `feat(eval)`: declare consumer artifacts and regenerate report outputs;
6. `docs(repo)`: complete the six-source claim ledger and activate the retirement guard;
7. separate thesis-repository commits: sync generated artifacts, update prose by subject, and verify the
   rendered thesis.

A boundary may be split further when tests and production code would otherwise cover two independent
contracts. It may not be merged merely to reduce commit count. Existing commits are not modified.

## Risks / Trade-offs

- **[Risk] The canonical fact relation becomes one large, opaque query.** -> Keep named classification
  CTEs or typed intermediate frames, expose each mechanism predicate, and test positive and negative
  examples for every writer.
- **[Risk] First-match precedence hides contradictory records.** -> Assert exclusivity after valid
  storage overlaps are normalized; fail with entity IDs on every other multi-match.
- **[Risk] EM-7 contaminates downstream attribution.** -> Compute final usability without claiming an
  attempted failure stage, retain the strict expected-failure invariant, and publish the limitation.
- **[Risk] Audit labels overstate prevalence.** -> Separate deterministic selection from human labels,
  record strata and population, and constrain report prose to what the sampling design supports.
- **[Risk] Active changes revise their interfaces during implementation.** -> Gate code work on their
  finalized accepted contracts and migrate directly; do not add aliases or fallback paths.
- **[Risk] The claim ledger becomes another stale narrative.** -> Keep normalized claims short, require
  executable/reported owners, validate the schema, and archive it after the cutover rather than using
  it as ongoing implementation documentation.
- **[Risk] Publication emits partial artifacts before an invariant fails.** -> Build all values and run
  reconciliation before renderers or publishers receive output.
- **[Trade-off] The deterministic audit requires manual review.** -> Store the reviewed entities and
  rationale once so the work is inspectable and reusable instead of cheap but irreproducible.

## Migration Plan

1. Reconcile the overlapping OpenSpec artifacts and mark their dependency boundaries. Do not modify
   existing commits.
2. Build and test the canonical fact relation against controlled fixtures and the frozen v7 corpus.
3. Materialize the mechanism table, funnel, metrics, and fail-loud reconciliation inside one report
   snapshot.
4. Generate the deterministic audit candidate set, review and record every selected case, then validate
   and register its summary.
5. Update publication declarations, regenerate every registered report once, and prove no unrelated
   report or figure changed.
6. Complete the six-source claim ledger. Correct stale source comments and diagnostics in the same
   subject commit as their authoritative replacement.
7. Activate the repository retirement guard only after all ledger entries pass.
8. In the thesis repository, regenerate and sync declared artifacts, update each consumer claim to a
   denominator-bearing macro or table, build the thesis, run style and LaTeX checks, and inspect rendered
   pages containing changed tables.
9. Archive changes only after their overlapping requirements and task ownership are coherent.

Rollback is append-only. Revert the offending new commit or commits in reverse dependency order. Never
repair rollback by mutating the frozen corpus, restoring hand-maintained numbers, or rewriting existing
history.
