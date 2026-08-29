## Context

See `proposal.md` for motivation. The RQ6 report currently constructs project-exclusion rows with stage, cause, type, and count. The type is derived by the report classifier and appears in the generated table consumed by the thesis. The database records the terminal stage and failure evidence; it does not store a stable internal/external/mixed ownership fact.

The sibling thesis change `restore-rq6-narrative` will consume the regenerated table and remove the same taxonomy from reader-facing prose.

## Goals / Non-Goals

**Goals:**

- Remove the inferred type from report construction, validation, metrics, and publication.
- Preserve the complete stage, cause, and count evidence.
- Replace zero-count fallbacks with classifications derived from entity evidence and task diagnostics.
- Reconstruct each stage transition from evidence recorded before the next stage starts.
- Aggregate internal mechanism combinations when they have the same reader-facing interpretation.
- Preserve detailed diagnostics in generated evidence and use selected examples in explanatory prose.
- Produce a compact canonical three-column thesis table through the existing report path.

**Non-Goals:**

- Change the eligible-project population, final inclusion count, or total project exclusions.
- Change database schema or pipeline persistence.
- Add a replacement ownership or actionability taxonomy.
- Split project rows by filter class, JPF exception subtype, widening-refusal subtype, or another reason taxonomy owned by an entity-level table.
- Rewrite thesis prose in this repository.

## Decisions

### 1. Delete the type at its producer

Remove the type field from the project-level cause model and row construction. Update the table declaration and metric identities to contain only stage, cause, and count. Do not retain a hidden field, deprecated metric, compatibility alias, or placeholder value.

Hiding the column only in the TeX renderer was rejected because other consumers and provenance would continue to expose unsupported semantics.

### 2. Preserve concrete cause wording

Keep `Cause of Project-level Exclusion` as the table heading and retain each concrete cause description. Retain pipeline-stage order. Within each stage, sort by descending count and then ascending cause text. This factual order foregrounds frequent observations without preserving the removed ownership classes. It does not rank actionability or expected project recovery.

`Exclusion condition` was considered, but rejected for this change because the report rows already record specific terminal descriptions and the approved thesis contract retains the established heading. The thesis will avoid interpreting those descriptions as exclusive blame.

### 3. Prove evidence reconciliation

A focused report test will assert the complete stage/cause/count row set, the count-based within-stage order, and the absence of the type column and type metrics. It will also assert the evidence-derived complete-test-loss and report-collection splits. Existing report reconciliation must continue to prove that project-level counts match the funnel.

Generated artifacts will be compared on semantic rows, not raw TeX layout. Each intentional row correction must trace to entity evidence or a task diagnostic.

### 4. Classify from observed evidence

Construct project causes from canonical entity-mechanism relations and task diagnostics. Do not infer filter rejection or processing failure from a zero included-entity count. Keep projects with no persisted test or assertion entities in a separate row so that missing evidence remains visible.

Preserve `MISSING_REPORT_FILE` and `UNSUPPORTED_REPORT_LAYOUT` in generated evidence. Aggregate both under the reader-facing JUnit report collection failure because the distinction does not change the project-level interpretation. The thesis prose can state the diagnostic split without adding table rows.

### 5. Reconstruct historical stage transitions

Use evidence that exists at each stage boundary instead of final mutable entity status. Stage 1 + 2 passes a project when at least one assertion survives the test and assertion filters. A later processing failure does not erase that transition. Stage 3 passes a project when at least one generalization attempt records entry into Stage 4. Stage 4 continues to require a generated test that passes filtering, and Stage 5 continues to require a final usable test.

A failed project-level task still supplies a direct cause in the stage that owns the task. If no direct task failure exists, classify complete loss from the entity mechanisms present at that boundary. Preserve the eligible-project set, final inclusion count, and total exclusion count while allowing intermediate stage bands to change.

Using final `is_included` values for earlier transitions was rejected because later JPF, generated-test, and report-collection failures mutate those values and pull downstream losses into Stage 1 + 2.

### 6. Use stable reader-facing cause categories

The table answers which material barrier prevents a project from entering the next stage. Use the established terms `filter rejection`, `processing failure`, and `widening refusal`. Do not expose internal state names or invent shorthand for project exclusion.

Aggregate internal mechanism combinations when they do not change interpretation. Keep a split when it separates filtering from processing, a timeout from report collection, an initial-suite failure from a generalized-suite failure, or another distinction with a different empirical implication. For Stage 4, group every project with a widening refusal and state that widening contributes when other mechanisms also occur; keep filter-only and processing-failure-only projects separate.

The project table remains coarser than entity evidence. Dedicated generated artifacts own filter classes, JPF exception subtypes, widening-refusal subtypes, and complete internal combinations.

### 7. Explain aggregates with selected validated evidence

The thesis result description gives selected concrete examples for broad causes. It names dominant recorded mechanisms, states when subtype project counts overlap, and leaves the complete distribution in the replication package. It does not list every rare diagnostic.

Treat a recorded outcome or catch-all diagnostic as evidence requiring further classification, not as a validated root cause. For specification extraction, use concrete recorded examples such as missing JPF native peers, uncaught exceptions during JPF execution, and unsupported bytecode. State their project counts only with the overlap qualification.

### 8. Regenerate through the registered report

Use the normal RQ6 report command and declared corpus inputs. Do not hand-edit generated CSV, TeX, macro, manifest, or provenance files. Update every checked-in consumer emitted by that command in the same commit.

### 9. Fit and pair the entity-exclusion tables

Use one width strategy for each generated table. A table that needs scaling SHALL use a natural-width `tabular` inside an unindented `\\resizebox{\\textwidth}{!}`. The explicit target matches the full-width `tabular*` contract used by adjacent tables. Keep counter setup outside the resize argument, and suppress whitespace between the inner table and the closing brace. Otherwise, interword glue becomes part of the measured box and leaves both table rules inset after scaling. The scaled table SHALL NOT also use `tabular*` full-width stretching. Reject that conflicting renderer state so another report cannot reproduce the hidden overflow.

Place the entity-exclusion summary and filter-detail inputs at the same source boundary before the assertion-level discussion. Give both floats local top placement so LaTeX queues them together after the preceding page break and can place them together. Do not change the document-wide float policy or shorten additional evidence labels to compensate for layout defects.

### 10. Classify filters by proactive exclusion behavior

Keep the filter-detail columns and established PascalCase names. Add `InheritedTestMethod`, `SeedSpecConsistency`, and `WideningLicense` without creating synthetic `filter_result` records.

Build one normalized filter-evidence relation from persisted decisions. Existing filter producers supply their recorded `ACCEPT`, `DEFER`, and `REJECT` verdicts. A stored method path identifies inherited-test-method evaluations. Generalization attempt codes and lifecycle evidence identify the ordered seed-consistency and widening-license verdicts.

Require complete evidence before aggregation. Every persisted method path must expose its declaring type. Every non-seed-rejected generalization attempt must either record a widening refusal or create generated source. Fail report generation on an unparsed path, contradictory verdict, or incomplete pre-emission transition.

Use the execution order for the new generalization rows. `SeedSpecConsistency` evaluates all attempts. `WideningLicense` evaluates attempts that pass seed consistency. `NonPassingTest` evaluates emitted tests that survive intervening processing. The three rejection sets are disjoint and must reconcile to the generalization-level filtering outcome.

Keep `InheritedTestMethod` distinct from `InheritedTest`. The former evaluates whether an inherited method can be flattened during collection. The latter is an existing later filter with a different predicate and population.

Deriving the new rows from `is_included` was rejected because later tasks can mutate that flag. Writing synthetic database rows was rejected because the report is read-only and producer storage does not define filtering semantics.

### 11. Group filter decisions by their evaluated population

Use five semantic row groups: first-round test filters, second-round test filters, inherited-method screening, assertion filters, and generalization filters. `NonPassingTest` and `TestType` form the first test round. The remaining ordinary test filters form the second round. `InheritedTestMethod` forms a separate source-screening group because its conditional population is not a test-filter round. Insert a midrule whenever the group changes.

Preserve the entity-level order: test, assertion, then generalization. Within each group, sort by descending `Evaluated`, descending `Reject`, and ascending filter name. The first key makes conditional denominators visible. The second key ranks decisions that evaluate the same population. Apply the same grouping and ordering through the shared renderer so controlled and real-world tables use one contract.

Rename `Total` to `Evaluated`. The value counts entities for which the decision was applicable and a verdict was reconstructed or recorded. Keep `Accept`, `Defer`, and `Reject` as per-filter verdicts. Do not use `included` or `excluded` for those three columns because one entity can receive decisions from multiple filters.

Keeping the hand-curated filter-name order was rejected because it hides both denominator differences and dominant rejection causes. Grouping `InheritedTestMethod` with the second round was rejected because its 6,259-test population is conditional on inherited source methods. Adding rules only to checked-in TeX was rejected because regeneration would remove them.

### 12. Use included and excluded for aggregate filtering outcomes

Use `included` and `excluded` at the filtering-result boundary. Rename producer symbols, metric keys, macro documentation, generated labels, tests, provenance, accepted reporting contracts, and thesis contracts that use `retained` as that outcome. Preserve unrelated uses of `retain`, such as repository artifact retention and mutation-useful test-suite reduction.

Keep `filtering` as the approved process term. Keep the entity summary partition `Included`, `Filtering`, and `Failures`, where the last two columns sit under the centered `Excluded` spanner. Do not introduce `proactive exclusion` as reader-facing thesis terminology. That phrase can describe the internal classification rule only.

A compatibility alias was rejected because it would leave two names for one outcome and allow stale generated consumers to survive the cutover.

### 13. Materialize test-flow reconciliation without expanding the thesis narrative

Publish registered metrics and provenance for identified tests, inherited-method screening, pre-filter failures, both test rounds, overlapping first-round rejections, and intervening failures. Reconcile the first-round population from identified tests minus inherited-method rejections and pre-filter failures. Reconcile the second-round population from the first-round population minus the union of first-round rejections and intervening failures.

Use set identities from persisted evidence. Do not add rejection counts because the two first-round filters can reject the same test. Keep these counts as generated evidence and focused regression invariants. The thesis needs only the existing concise two-round explanation and the separate conditional inherited-method paragraph.

Hand-written thesis arithmetic was rejected because it would duplicate report logic and lack registered provenance. Publishing every diagnostic count in the prose was rejected because it would obscure the result.

### 14. Correct shared rendering and mechanism prose at their owners

Remove the RQ5-only right-alignment override so the shared renderer centers the `Excluded` spanner in both summary tables. Add a focused rendered-header regression. Do not hand-edit generated TeX.

Describe `ExcludedTest` as rejecting an assertion whose test was already excluded during collection, filtering, or processing. Use the shorter equivalent in the discussion and cite the `ExcludedTest` rows. Do not publish the diagnostic cause split unless it becomes registered evidence required by a reader-facing claim.

Give the long real-world filter-detail table an explicit local compact density through the table-style interface. Preserve semantic midrules and the shared source boundary with the summary table. Do not change global float parameters, add negative spacing, or hand-edit generated TeX.

## Risks / Trade-offs

- **A downstream consumer still expects four columns.** Search generated declarations, tests, and thesis publication references; require a clean cutover rather than an alias.
- **A row disappears with its classification branch.** Build rows directly from stage and cause evidence, then compare the complete ordered projection before and after the change.
- **A later entity failure is assigned to an earlier stage.** Assert each survivor set from boundary evidence and require nested survivor sets before rendering counts.
- **Fine-grained project rows duplicate entity tables.** Aggregate internal combinations by material reader-facing cause; leave complete subtype distributions in generated evidence and the replication package.
- **The cause heading is overread as sole responsibility.** Preserve the established evidence label, but leave responsibility and actionability interpretation to bounded thesis prose.
- **A text-wide terminology replacement changes valid uses of `retained`.** Migrate only filtering-outcome symbols and reader-facing consumers; keep retention and test-suite-reduction language unchanged.
- **A denominator-first order obscures semantic stages.** Keep the five decision groups and entity-level order fixed; apply denominator ranking only within a group.
- **One extra rule splits the paired tables.** Use a local compact-density contract and inspect the rendered page; do not weaken group boundaries.
- **Generated artifacts drift for unrelated reasons.** Regenerate from the pinned corpus and inspect the artifact diff before committing.