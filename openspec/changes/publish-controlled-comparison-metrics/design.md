## Context

See `proposal.md` - Why.

The controlled RQ5 and real-world RQ6 reports do not derive their generalization outcome from the same
schema shape:

- RQ5 groups `generalization` rows by variant and treats the mutable `generalization.is_included` flag
  as the terminal included bucket. Rejected filters and later task failures can both set that flag to
  false; the controlled input does not declare `generalization_lifecycle` as required evidence.
- RQ6 treats every eligible `generalization` row as an attempt, preserves pre-emission gates, and uses
  `generalization_lifecycle` to distinguish emission, filter adjudication, filter passage, validation,
  reduction, and final usability. Its mechanism partition deliberately does not interpret the generic
  inclusion flag as proof of a later lifecycle state.
- RQ5 selects projects through `project.use_test_generalization`. RQ6 first applies its accepted
  real-world eligibility CTE. The corpora contain different projects, so this is not a matched-project
  experiment.

Pipeline order is also revision-dependent. Git history shows that test generation and post-processing
were split, failing-test filtering moved relative to original-data collection, and reduction stages were
later reordered after generalization. The thesis's clean RQ6 Stage 1-5 presentation therefore cannot be
used as a historical execution model for RQ5. `ProcessingStage`, `PipelinePlanner`, task dependencies,
and persisted task results at each corpus's producer commit are the executable evidence.

The existing controlled row is therefore candidate evidence, not yet a normalized comparison metric.
Equal current counts would not prove equal meaning. The accepted exclusion-accounting contract requires
cross-corpus values to come from one registered implementation with explicit populations and source
mappings.

## Goals / Non-Goals

**Goals:**

- Record every producer revision in each corpus and reconstruct the latest recorded revision as its
  representative pipeline graph.
- Establish the strongest comparison measure both representative pipeline and schema shapes support.
- Make every translation from task transition and stored state to that measure explicit and executable.
- Distinguish exact mappings, qualified descriptive mappings, and unmappable cases.
- Stop for operator review whenever evidence supports more than one reasonable translation.
- Emit both sides from one registered implementation only after the mapping is approved.

**Non-Goals:**

- Treating a common column name, stage number, current task order, or equal count as semantic equivalence.
- Projecting the RQ6 Stage 1-5 presentation backward onto the RQ5 producer revision.
- Inferring lifecycle stages absent from the controlled database.
- Retrofitting controlled-run lifecycle records or changing either database.
- Presenting disjoint corpora as paired projects or attributing a rate difference to one mechanism.
- Expanding the thesis comparison to stages, mechanisms, or variants that cannot be mapped reliably.

## Decisions

### 1. Complete a mapping matrix before implementation

The first deliverable is a change-local mapping matrix with one row per candidate side. Each row records:

- semantic corpus and physical input role;
- complete producer-revision set, latest-revision selection evidence, representative revision, report
  revision, and required schema objects;
- representative historical `ProcessingStage` order and scheduler graph;
- scheduled task dependencies and persisted success/failure evidence;
- project eligibility predicate;
- variant identity;
- entity identity and denominator predicate;
- numerator predicate;
- writer operations that set or clear each source field;
- task transition and lifecycle boundary the predicate proves;
- contradictions and missing evidence found by executable audits;
- mapping classification: exact, qualified, or unmappable; and
- the interpretation wording allowed by that classification.

For each side, first record every `project.tool_git_version` independently from the later report-query
commit. Project-id insertion order selects the latest recorded non-null revision as representative. At
that Git tree, recover the declared processing-stage order, scheduler rules, task prerequisites, and
every task that can create, reject, validate, reduce, or otherwise change a generalization outcome.
Record a graph keyed by task and persisted transition, not by display stage number. Compare the two
representative graphs to identify task splits, moves, and reordered gates before inspecting similarly
named columns.

The controlled audit traces every writer of `generalization.is_included` in the latest recoverable
controlled producer tree and reconciles included, filtering, and failure rows with filter and task
evidence. The real-world audit uses its representative planner plus the accepted eligibility, lifecycle,
and mechanism relations rather than a same-named convenience field. Both denominators retain rows from
other recorded revisions, so the mapping is qualified even when corpus invariants agree. If a selected
Git object cannot be recovered, use the latest recoverable recorded producer tree and validate it against
persisted stage and task rows; record that additional qualification rather than substituting current
source intent.

No comparison code or metric key is finalized before this matrix is presented to and approved by the
operator.

### 2. Normalize semantics, not storage columns

After approval, two corpus adapters return the same small typed observation contract for the chosen
measure. The contract contains corpus id, source entity identity, normalized variant, denominator
membership, numerator membership, and mapping identity. It does not require both databases to expose the
same tables or fields.

The initial candidate is a bounded generalized-test outcome between attempt creation and completion of
generated-test validation. The audit must determine whether controlled `generalization.is_included`
proves one specific boundary and which RQ6 lifecycle predicate proves the same boundary. Candidate
counterparts include filter passage and validation; the implementation must not choose between them from
current count equality.

**Alternative:** Align RQ5 and RQ6 by thesis Stage 1-5 labels or current `ProcessingStage` ordinals.
Rejected because task placement and ordering changed between collection revisions; stage labels are
presentation, not stable evidence identities.

**Alternative:** Compare raw `is_included` values in both databases. Rejected because RQ6 explicitly
separates that mutable flag from lifecycle evidence.

**Alternative:** Compare the RQ5 row directly with `realworld.generalizations_validated`. Rejected until
writer semantics and invariants prove the boundaries match.

### 3. Classify comparability explicitly

An **exact** mapping requires equivalent entity, numerator, denominator, and lifecycle semantics.

A **qualified** mapping may compare the same bounded outcome across different corpus eligibility or
instrumentation when each side remains denominator-explicit and the output states that the comparison is
descriptive, not paired or causal.

An **unmappable** result means required state is absent, multiple lifecycle interpretations survive, or
one denominator cannot be related to the normalized population without inference. The report emits no
value for that pair. The implementation pauses and presents the evidence, alternatives, and consequence
for the thesis claim to the operator.

The likely project-population difference is at least a qualification even if the generalized-test
outcome maps exactly. No claim may describe the disjoint corpora as corresponding projects.

### 4. One registered comparison owner reads both inputs

The owning registered report declares both `controlled` and `real-world` corpus inputs and invokes the
two approved adapters in one build. The RQ6 report is the default owner because the retained thesis
comparison belongs to RQ6 and the accepted contract requires one registered implementation. The mapping
review may select a small shared comparison module, but it must not leave independent RQ5 and RQ6
calculations that merely happen to agree.

Existing RQ5 and RQ6 tables remain presentation outputs of their current reports. Where a table row
represents the approved normalized measure, a test checks agreement. Where it does not, the comparison
must not reuse or relabel that row.

### 5. Metric identities follow the approved measure

The registered comparison emits six metrics: numerator, denominator, and share for each corpus. Their
stable key segment names the approved normalized lifecycle boundary, not `rq5`, `rq6`, `success`, a table
row, or display text. Each share references its corpus-specific operands; every metric carries
`Generalization` population metadata, input role, source provenance, and mapping identity.

The existing metric model owns arithmetic and population-relation checks. The aggregate macro and
manifest renderers remain unchanged unless the mapping identity cannot be represented without losing
traceability. That case is a design issue to bring back to the operator, not metadata to omit.

### 6. Mapping audits are positive and negative controls

Focused fixtures and corpus audits prove:

- the controlled writer semantics place each retained row in one terminal bucket;
- the selected RQ6 lifecycle predicate conserves its declared denominator;
- changing a source field to a competing lifecycle state makes the mapping invariant fail;
- missing controlled evidence does not get synthesized from `exclusion_info` text;
- each adapter emits one normalized observation per source entity; and
- the two metric triplets agree with their source adapters and declared qualification.

A test that only checks the current values `11,597 / 13,836` and `1,615 / 5,356` is insufficient. Those
values are expected evidence after the mapping is established, not proof of the mapping.

### 7. Operator review is a hard gate

Before implementation, present the complete matrix and every exact, qualified, and unmappable decision.
For a qualified mapping, present the precise thesis-safe interpretation. For an unmappable mapping,
present the competing translations and whether the retained comparison must be removed, narrowed, or
supported by additional producer evidence. Record the operator's decision in this change before code
edits.

## Risks / Trade-offs

- **The controlled flag has no single lifecycle meaning.** -> Mark it unmappable and discuss removal or a
  more limited measure; do not choose the closest RQ6 count.
- **Stage names survived while task order changed.** -> Reconstruct both historical planner graphs and
  map task transitions; never align by stage ordinal or the cleaner RQ6 presentation.
- **A corpus contains several producer revisions.** -> Preserve the full denominator, use the latest
  recorded revision as representative, and publish the mixed-revision limitation as a qualification.
- **The latest recorded producer object is no longer recoverable.** -> Use the latest recoverable
  recorded producer tree, check it against persisted task/stage rows, and add a source-recovery
  qualification rather than substituting the current tree.
- **Controlled writer code differs from the current source.** -> Use the selected historical producer
  revision and persisted evidence, not current implementation intent.
- **The schemas support a common outcome but not common eligibility.** -> Publish a qualified,
  denominator-explicit descriptive comparison and prohibit paired or causal language.
- **A table row disagrees with the approved adapter.** -> Treat the table or mapping as defective and
  stop; do not make metrics copy the table to force agreement.
- **Mapping metadata does not survive current provenance rendering.** -> Extend the typed provenance
  contract in this change or return for operator review; do not hide the qualification in prose only.
- **The audit finds several unmappable mechanisms.** -> Compare only an independently meaningful mapped
  aggregate after operator approval. Never merge unknown cases into an included, filtering, or failure
  bucket.

## Open Questions

None at planning time. Task 1 resolves the lifecycle boundary and provenance representation from source
and executable evidence, then presents every exact, qualified, and unmappable result to the operator.
Implementation remains blocked until that review records the approved mapping and any metadata change.
