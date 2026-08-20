## 1. Reconcile Knowledge Ownership

- [ ] 1.1 Re-read the current artifacts for `consolidate-repository-knowledge`,
  `consolidate-evaluation-databases`, `make-report-runs-explicit`,
  `separate-report-values-from-presentation`, and `declare-published-artifacts`; map every overlapping
  task to one active owner and record dependency order without duplicating implementation.
- [ ] 1.2 Reopen the six-document claim audit and cutover checks in
  `consolidate-repository-knowledge`; add the explicit five-mechanism collapse, true
  filter-adjudication boundary, qualitative-evidence provenance, and EM-7 attempted-stage limitation to
  its proposal, design, capability deltas, and tasks where they belong.
- [ ] 1.3 Enumerate every substantive claim from the six deleted sources using their deletion parent's
  tree only as a checklist; create
  `evidence/retired-knowledge-claims.yaml` with one normalized, schema-valid entry per claim and no
  blanket file-level dispositions.
- [ ] 1.4 Re-derive the initial disposition of every claim from current executable source,
  configuration, focused tests, registered reports, or read-only corpus observations; mark unresolved,
  stale, disproven, unreproducible, and intentionally discarded claims explicitly.
- [ ] 1.5 Validate every affected OpenSpec change and prove no active change has competing ownership for
  the same implementation task or capability requirement.
- [ ] 1.6 Create one new `docs(openspec)` commit containing only the reconciled planning contracts and
  claim-audit structure, with a causal body and the focused OpenSpec validation results; do not amend,
  rebase, squash, reset, or rewrite any existing commit.

## 2. Derive Canonical Exclusion Facts

- [ ] 2.1 Add focused classifier fixtures for each included or excluded mechanism, the valid
  quarantine/filter storage overlap, contradictory multi-mechanism records, unknown codes, unknown
  record shapes, and unknown decision producers.
- [ ] 2.2 Implement one canonical static SQL entity-fact CTE for eligible tests, assertions, and
  generalizations using the finalized `ReportContext` corpus role and consistent snapshot; aggregate
  typed mechanism and reconciliation counts in SQL before transfer, and do not embed a physical
  database name or add a compatibility path.
- [ ] 2.3 Encode mechanism precedence and explicit writer classes so actual filter decisions,
  pre-emission gate refusals, unsupported capabilities, build quarantines, and task failures remain
  distinct despite overlapping persistence channels.
- [ ] 2.4 Add fail-loud exclusivity, population, and unknown-producer checks that report the offending
  level and entity identifiers before any renderer or publisher receives output.
- [ ] 2.5 Materialize the typed five-mechanism partition and declared three-outcome semantic collapse
  as database-side aggregates over the same fact CTE; transfer only aggregate rows and remove any
  duplicate mapping from report or renderer code.
- [ ] 2.6 Run the focused classifier, mechanism-partition, provenance, and typed-renderer checks against
  controlled fixtures and the frozen v7 corpus.
- [ ] 2.7 Create one new `feat(eval)` commit for the canonical fact relation and mechanism partition,
  with a causal body and focused verification; split it further if deriving facts and presenting the
  partition prove independently reviewable.

## 3. Materialize the Generalization Funnel

- [ ] 3.1 Add focused tests for attempts, first seed-gate refusal, first widening refusal, emitted tests,
  pre-filter failure, build quarantine, true filter adjudication, filter rejection, filter pass,
  downstream attrition, and final usability.
- [ ] 3.2 Derive the ordered funnel from the canonical fact relation, excluding non-filter validator rows
  from filter adjudication and assigning each refused attempt to its first failing gate exactly once.
- [ ] 3.3 Add construction-time identities for every funnel transition and reconcile attempts, mechanism
  counts, persisted refusal codes, filter decisions, and final-use outcomes over one corpus and variant.
- [ ] 3.4 Register typed count metrics and denominator-bearing rate metrics for filter-passed per attempt,
  emitted test, and filter-adjudicated test, plus final-usable per filter-passed test and per attempt.
- [ ] 3.5 Materialize `rq6-generalization-funnel.csv` and its Markdown and LaTeX views through the common
  typed renderer; keep raw numeric values separate from labels, spacing, macros, and target formatting.
- [ ] 3.6 Verify the v7 funnel against independent SQL identities, including the distinction between
  filter-shaped quarantine rows and actual filter-class adjudication; retain EM-7 as a strict expected
  failure and avoid attempted-stage claims.
- [ ] 3.7 Create one new `feat(eval)` commit for the denominator-explicit funnel and metrics, with a
  causal body and the focused fixture and v7 reconciliation results.

## 4. Rebuild Reproducible Widening Evidence

- [ ] 4.1 Define the versioned `analysis/audits/rq6-widening-v7.json` schema for corpus and source
  identity, population and strata, deterministic selection, selected entity identities, stored
  observations, human causal labels, rationale, reviewer, and review timestamp.
- [ ] 4.2 Implement a deterministic candidate-selection command that resolves the v7 corpus through the
  registry, emits a stable ordered sample, and refuses to overwrite reviewed labels or accept corpus
  drift.
- [ ] 4.3 Generate the candidate set, inspect every selected project and method at the recorded source
  revision, and record causal labels and concise evidence-backed rationale without inferring
  unobserved source causes from aggregate telemetry.
- [ ] 4.4 Implement audit validation that re-derives database-backed observations, verifies source and
  corpus identities, rejects missing or duplicate selected entities, and detects label-schema or
  population drift.
- [ ] 4.5 Add a registered typed audit summary whose wording distinguishes immediate persisted refusal
  causes, controlled-fixture mechanism evidence, sampled qualitative observations, and any estimator
  justified by the selection design.
- [ ] 4.6 Add focused tests for deterministic selection, audit round-trip, provenance mismatch, drift,
  incomplete review, unsupported labels, and report refusal on invalid evidence.
- [ ] 4.7 Create one new `feat(eval)` commit for the deterministic audit input, validator, and registered
  summary, with a causal body and the audit and report verification results.

## 5. Declare and Generate Evidence Artifacts

- [ ] 5.1 Return `rq6-exclusion-mechanisms.csv`, `rq6-generalization-funnel.csv`, the audit summary, and
  all new metrics through the existing report and `ArtifactSet` interfaces; let the generic run
  manifest attach the captured corpus, audit-file, and source provenance without an RQ6 branch.
- [ ] 5.2 Use `declare-published-artifacts` interfaces to declare only thesis-consumed LaTeX artifacts and
  metrics; leave undeclared normalized tables and audit evidence in the generator build tree.
- [ ] 5.3 Add or update thesis-facing macros for every count and denominator-bearing rate that downstream
  prose may quote, without formatting numeric values inside report computation.
- [ ] 5.4 Correct the `ProjectBuildTask` quarantine description and the stale RQ6 invariant comment so
  both describe current writer-based classification; make no pipeline behavior change.
- [ ] 5.5 Build every registered report once from a read-only consistent snapshot, regenerate declared
  artifacts, and fail if the mechanism table, funnel, audit summary, metrics, provenance, and
  publication manifest do not reconcile.
- [ ] 5.6 Compare regenerated output with the committed tree and independently confirm that every
  unrelated table, CSV, figure, metric, and report remains byte-identical or has an explained
  database-backed change.
- [ ] 5.7 Create one new `feat(eval)` commit for publication declarations and regenerated evidence, with
  a causal body and full report-generation proof; keep diagnostic-comment corrections in a separate
  `fix` or `docs` commit if they are not causal to the published artifact change.

## 6. Complete the Knowledge Cutover

- [ ] 6.1 Resolve every open claim-ledger entry to exactly one verified owner or explicit stale,
  disproven, unreproducible, or discarded disposition; reject Git history alone as a current owner.
- [ ] 6.2 For every retained contract, executable fact, empirical result, operator instruction, and
  qualitative claim, exercise the named owner and record focused verification in the ledger.
- [ ] 6.3 Search current source, configuration, tests, reports, README, scoped guidance, and OpenSpec
  artifacts for references to the six retired documents, obsolete physical database names,
  hand-maintained RQ6 values, and superseded mechanism descriptions; fix every second instance of the
  same defect.
- [ ] 6.4 Extend the repository-state guard to validate claim-ledger schema and completion, reject
  unowned retired knowledge and reintroduced narrative authority, and include positive controls for
  every forbidden state.
- [ ] 6.5 Run the focused repository-state module and prove each positive control fails for its intended
  reason before the real repository passes.
- [ ] 6.6 Create one new `docs(repo)` or `test(repo)` commit for the completed claim ledger and activated
  retirement guard, splitting evidence completion from guard mechanics when either subject stands
  alone; include causal bodies and focused verification.

## 7. Verify the Analysis Repository

- [ ] 7.1 Run all focused exclusion-classification, funnel, widening-audit, provenance, manifest,
  publication, renderer, and repository-state tests.
- [ ] 7.2 Run the complete non-database analysis suite once and record the exact pass, skip, and expected
  failure totals; investigate every new skip or expected failure.
- [ ] 7.3 Run every registered report once against its declared corpus and confirm the run emits no
  partial artifact before validation completes.
- [ ] 7.4 Run repository formatting, lint, type, file-hygiene, and commit hooks once over the completed
  change.
- [ ] 7.5 Validate every active OpenSpec change, inspect the final diff by subject, and confirm no Java
  pipeline behavior, frozen corpus, undeclared consumer artifact, or existing commit changed.
- [ ] 7.6 Confirm the branch remains append-only relative to its starting history and that each new
  commit contains one subject, a causal body, and the verification appropriate to that subject.

## 8. Hand Off the Thesis Refresh

- [ ] 8.1 Record the finalized artifact names, metric keys, provenance identifiers, denominator
  definitions, publication declarations, and known EM-7 limitation in the change completion notes.
- [ ] 8.2 Create or update a thesis-repository OpenSpec change that depends on these finalized outputs and
  covers generated-artifact synchronization, claim replacement across every consumer chapter,
  thesis build, style and LaTeX checks, and rendered-page inspection; do not edit the thesis from this
  repository-scoped change.
- [ ] 8.3 Verify the downstream plan requires separate atomic thesis commits for generated artifact sync,
  each independent prose subject, and final cross-chapter reconciliation, with no history rewrite.
