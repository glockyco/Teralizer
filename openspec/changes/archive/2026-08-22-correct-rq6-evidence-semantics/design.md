## Context

See `proposal.md` for motivation. The preserved RepoReapers database is the measured first run and cannot be mutated or replaced. Current report artifacts expose one retired filter-boundary identity, collapse inherited-test failures into a generic test capability, attribute two post-execution PIT report-import failures to PIT execution, misclassify five anonymous or local-class picks as resolved despite unpathable declarations, and audit seven valid constant-input `ParameterType` rejections against the wrong capability predicate.

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

### 2. Make resolution status reflect generalization pathability

The five incomplete picks target methods declared in anonymous or local classes. Spoon can resolve their declarations for telemetry, but the pipeline cannot form the stable declaration paths required to rematerialize them during generalization. Classify these picks as characterization-only with a dedicated unpathable-source reason before persisting the observation. Do not weaken the all-or-nothing tested-method invariant or manufacture paths for anonymous declarations.

The seven `ParameterType` rejections are valid. Their methods declare at least one supported type, but the selected calls supply only constants or `null`, so `GeneralizableInput.derive` persists an empty generated-input list. Keep the filtering behavior and correct audit queries and prose inputs to use `assertion.tested_method_parameters`, not declaration-level `candidate_param_supported`.

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
- **Declaration capability is mistaken for generated-input availability.** Keep the two predicates explicit in code, queries, and labels; do not turn constants or `null` into generated inputs to satisfy an audit.
- **Regeneration changes unrelated evidence.** Stop on any artifact outside the expected manifest or any unexplained value drift.
- **PIT attribution becomes too broad.** Classify only retained tasks whose evidence proves report generation succeeded before import or persistence failed.
- **Diagnostic evidence leaks into publication.** Test the reader-facing partition against a known `DEFER` record and a known rejecting record.
