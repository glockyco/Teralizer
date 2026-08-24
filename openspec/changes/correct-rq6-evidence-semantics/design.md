## Context

See `proposal.md` for motivation. The preserved RepoReapers database is the measured first run and cannot be mutated or replaced. Current report artifacts expose one retired filter-boundary identity, collapse inherited-test failures into a generic test capability, attribute two post-execution PIT report-import failures to PIT execution, and contain twelve assertion-filter rows that disagree with retained MUT-resolution evidence.

The correction spans Java persistence and filtering, Python report queries and registered outputs, generated macros and provenance, and accepted contracts. The report registry and transactional artifact publisher remain the only publication path.

## Goals / Non-Goals

**Goals:**

- Make every filter-result identity and mechanism label semantically exact and consistent across all producers and consumers.
- Restore one coherent persisted MUT shape before filters derive limitation evidence.
- Attribute retained project failures to the operation that failed.
- Regenerate traceable artifacts from the preserved measurement record.
- Keep diagnostic-only filters available for audits without presenting them as exclusion mechanisms.
- Land independently reviewable, atomic commits whose tests and contracts pass at each boundary.

**Non-Goals:**

- Rerunning any project or corpus.
- Mutating or cleaning the first-run database or run root.
- Adding thesis-facing tables or diagnostic-filter discussion.
- Preserving aliases for retired metric, row, symbol, or label identities.
- Editing the thesis repository in this producer change.

## Decisions

### 1. Use clean identity cutovers

Rename the filter-result boundary and inherited-test mechanism at their owning declarations, then migrate queries, models, rendering, provenance, tests, and accepted specifications in the same commit. Generated artifacts are regenerated rather than hand-edited. Compatibility aliases are rejected because they would keep ambiguous evidence identities alive.

### 2. Repair persistence before report interpretation

Trace the five resolved picks from resolution through the transaction that stores tested-method fields. Persist the observation and required fields atomically or record an explicit persistence defect. Do not make the report infer missing fields from later state.

For the seven contradictory `ParameterType` rows, compare the filter's actual supported-input predicate with the normalized persisted parameter list. Correct the producer or report query according to that predicate. If retained evidence cannot establish a component count after correction, remove that component claim rather than estimate it.

### 3. Attribute PIT failures by pipeline operation

Separate successful PIT execution from subsequent report import and persistence. The task diagnostic and exclusion query use the failing stage and operation, not the upstream tool name. Existing first-run task evidence is sufficient; no execution retry is permitted.

### 4. Keep `DEFER` evidence audit-only

Diagnostic filters remain persisted because they are useful for engineering audits. Reader-facing mechanism partitions include only outcomes that exclude an entity. This boundary is enforced in report queries and focused tests, not by deleting diagnostic records.

### 5. Regenerate through registered reports

After focused implementation verification, run the complete registered report set once against the preserved database and publish through the transactional declaration. Compare the resulting manifest and provenance with the preserved inputs. Do not invoke a corpus runner or per-report manual copy.

### 6. Commit by causal boundary

Use separate commits for: MUT persistence consistency; evidence identity and failure-attribution corrections; and regenerated report artifacts plus accepted contracts. A commit that changes an identity also migrates all of that identity's consumers. Do not split a producer declaration from its required call-site migration.

## Risks / Trade-offs

- **A clean rename breaks an overlooked consumer.** Search generated and hand-authored consumers, update focused contract tests, and reject the retired identity in validation.
- **The twelve contradictory rows have more than one cause.** Repair observed producer defects separately; omit unsupported subcounts rather than forcing one explanation.
- **Regeneration changes unrelated evidence.** Stop on any artifact outside the expected manifest or any unexplained value drift.
- **PIT attribution becomes too broad.** Classify only retained tasks whose evidence proves report generation succeeded before import or persistence failed.
- **Diagnostic evidence leaks into publication.** Test the reader-facing partition against a known `DEFER` record and a known rejecting record.
