## Context

See `proposal.md` for motivation. The RQ6 report currently constructs project-exclusion rows with stage, cause, type, and count. The type is derived by the report classifier and appears in the generated table consumed by the thesis. The database records the terminal stage and failure evidence; it does not store a stable internal/external/mixed ownership fact.

The sibling thesis change `restore-rq6-narrative` will consume the regenerated table and remove the same taxonomy from reader-facing prose.

## Goals / Non-Goals

**Goals:**

- Remove the inferred type from report construction, validation, metrics, and publication.
- Preserve the exact ordered stage, cause, and count evidence.
- Produce the canonical three-column thesis table through the existing report path.

**Non-Goals:**

- Reclassify causes, change stage attribution, or alter exclusion counts.
- Change database schema or pipeline persistence.
- Add a replacement ownership or actionability taxonomy.
- Rewrite thesis prose in this repository.

## Decisions

### 1. Delete the type at its producer

Remove the type field from the project-level cause model and row construction. Update the table declaration and metric identities to contain only stage, cause, and count. Do not retain a hidden field, deprecated metric, compatibility alias, or placeholder value.

Hiding the column only in the TeX renderer was rejected because other consumers and provenance would continue to expose unsupported semantics.

### 2. Preserve concrete cause wording

Keep `Cause of Project-level Exclusion` as the table heading and retain each concrete cause description. `Exclusion condition` was considered, but rejected for this change because the report rows already record specific terminal descriptions and the approved thesis contract retains the established heading. The thesis will avoid interpreting those descriptions as exclusive blame.

### 3. Prove evidence invariance

A focused report test will assert the complete ordered stage/cause/count row set and the absence of the type column and type metrics. Existing report reconciliation must continue to prove that project-level counts match the funnel.

Generated artifacts will be compared on semantic rows, not raw TeX layout, because removal necessarily changes column widths and markup.

### 4. Regenerate through the registered report

Use the normal RQ6 report command and declared corpus inputs. Do not hand-edit generated CSV, TeX, macro, manifest, or provenance files. Update every checked-in consumer emitted by that command in the same commit.

## Risks / Trade-offs

- **A downstream consumer still expects four columns.** Search generated declarations, tests, and thesis publication references; require a clean cutover rather than an alias.
- **A row disappears with its classification branch.** Build rows directly from stage and cause evidence, then compare the complete ordered projection before and after the change.
- **The cause heading is overread as sole responsibility.** Preserve the established evidence label, but leave responsibility and actionability interpretation to bounded thesis prose.
- **Generated artifacts drift for unrelated reasons.** Regenerate from the pinned corpus and inspect the artifact diff before committing.