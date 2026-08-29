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

## Risks / Trade-offs

- **A downstream consumer still expects four columns.** Search generated declarations, tests, and thesis publication references; require a clean cutover rather than an alias.
- **A row disappears with its classification branch.** Build rows directly from stage and cause evidence, then compare the complete ordered projection before and after the change.
- **A later entity failure is assigned to an earlier stage.** Assert each survivor set from boundary evidence and require nested survivor sets before rendering counts.
- **Fine-grained project rows duplicate entity tables.** Aggregate internal combinations by material reader-facing cause; leave complete subtype distributions in generated evidence and the replication package.
- **The cause heading is overread as sole responsibility.** Preserve the established evidence label, but leave responsibility and actionability interpretation to bounded thesis prose.
- **Generated artifacts drift for unrelated reasons.** Regenerate from the pinned corpus and inspect the artifact diff before committing.