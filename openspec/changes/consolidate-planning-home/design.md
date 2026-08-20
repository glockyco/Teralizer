## Context

See `proposal.md` for motivation and `specs/repository/planning-state/spec.md` for the contract.

The legacy root contains 27 non-index records: 20 marked active, three draft, two implemented, one complete, and one superseded. Those labels do not establish current intent. One active audit has all ten tasks checked, while the complete recollection plan still contains unchecked work. `INDEX.md` also disagrees with record metadata.

The archive contains 80 more records. Current surfaces cite both root and archived records as design authority, evidence, and operating guidance. These surfaces include `AGENTS.md`, one repository skill, `docs/architecture.md`, `docs/exclusion-model.md`, `docs/local-state.md`, and a source docstring.

Three active OpenSpec changes already own report presentation, artifact delivery, and database consolidation. Migration must not duplicate or rewrite their scope.

The retired convention also survives in two owning repositories. `omp-agent-setup` has 17 historical planning records and one guidance pointer to them. `nix-darwin` has eight records, an index, and a canonical architecture document that cites the legacy plan and carries unscheduled work. The active personal plugin and global OMP configuration contain no `docs/plans/`, `planning-files`, or `omp-plans` instruction, so no deployed global carrier blocks this repository's cutover.

## Goals / Non-Goals

**Goals:**

- Decide the disposition of every legacy record from evidence, not its status label.
- Preserve current requirements, durable technical facts, empirical evidence, and unresolved intended work in one authoritative owner.
- Remove the complete `docs/plans/` tree after all retained content has an owner.
- Make a second planning home fail in the normal repository validation path.
- Keep each migration commit reviewable and leave the repository checks green.

**Non-Goals:**

- Implement technical work described by a legacy record.
- Preserve a legacy document only because it exists or is linked.
- Convert every legacy record into an OpenSpec change.
- Change measured data, report definitions, runtime behavior, or an existing change's task ownership.
- Edit `omp-agent-setup` or `nix-darwin`; each repository has its own OpenSpec change and validation gates.

## Decisions

### 1. Classify content before moving files

Create a disposition ledger under this change. Give every root record one row with its evidence, disposition, destination, and verification. Treat the 80 archived records as historical by default, but inspect every live reference to them before removal.

Use four dispositions:

1. **Current work.** Move independently confirmed intended work into one named OpenSpec change or capability contract.
2. **Durable knowledge.** Move verified technical facts or empirical evidence into the current document that owns the subject.
3. **History only.** Remove the tracked copy after confirming that no current source depends on it. Git history remains the historical record.
4. **No retained value.** Remove superseded, duplicated, or weightless content.

A metadata label, an unchecked box, or a statement inside the record does not confirm intent. Confirm current work from accepted contracts, active changes, executable behavior, current evidence, or an explicit operator decision. If repository evidence does not confirm intent, classify the record as history instead of preserving an unowned backlog.

**Alternative:** Move the complete tree to `docs/history/plans/`. Rejected because it preserves 107 documents as a second body of apparent authority and leaves readers to interpret stale labels.

### 2. Migrate claims to subject owners, not to replacement plan files

Move implementation facts to the relevant technical document, such as `docs/architecture.md`, `docs/database.md`, `docs/exclusion-model.md`, or `docs/local-state.md`. Move empirical values to registered reports or provenance-bearing evidence. Move requirements to capability specs. Move intended work to an active change.

Do not create a generic legacy-findings document. It would reproduce the same mixed ownership under a new name.

**Alternative:** Preserve each record under `openspec/changes/`. Rejected because OpenSpec changes describe intended work, not point-in-time audits or completed history.

### 3. Reuse existing OpenSpec owners before creating a change

Compare each surviving task with all active changes and accepted specs. If an owner exists, remove the duplicate and record the owner in the ledger. Create a new change only for coherent, independently confirmed work with no owner.

Do not add legacy tasks to an unrelated active change. An inbound note is valid only when that change owns the decision or implementation surface.

**Alternative:** Create one umbrella backlog change. Rejected because unrelated work would share status, dependencies, and acceptance criteria.

### 4. Delete the legacy namespace in one cutover

Keep source records until their retained content and references have migrated. Then remove `docs/plans/`, including `INDEX.md` and `archive/`, in one cutover commit. Update all current references in the same commit.

`AGENTS.md` will name `openspec/changes/` for current work and `openspec/specs/` for accepted contracts. It will not require a second search location. `openspec/config.yaml` will not point back to the removed index.

**Alternative:** Leave `docs/plans/archive/` in place. Rejected because the directory name remains an attractive location for new plans and current sources already cite archived files as authority.

### 5. Enforce the boundary through the existing analysis test gate

Add a repository-level test under `analysis/tests/`. The test will scan tracked current surfaces outside `openspec/` and report conflicting paths. It will reject:

- a `docs/plans/` tree or a reference to it from current guidance, documentation, skills, or source;
- Markdown planning metadata outside `openspec/` that marks a record as current;
- guidance that declares another current planning location.

The test will include a positive control that creates a known conflict and confirms detection. The existing non-database pytest job runs in CI and the pre-push hook, so no second validation mechanism is needed.

OpenSpec change history is excluded from the retired-path scan because migration artifacts must name the path they remove. Submodules, generated output, and Git metadata are also excluded.

**Alternative:** Rely on prose in `AGENTS.md`. Rejected because that is the current failure mode and gives no signal when a second home returns.

### 6. Preserve reviewability with evidence-first commits

Commit the ledger before destructive edits. Migrate one subject at a time, with its source record still present for review. Remove the legacy tree only after every ledger row has a verified destination or removal reason.

Do not combine technical claim correction with the final mechanical deletion. If verification contradicts a legacy claim, correct the authoritative owner and record the contradiction in that subject's commit.

### 7. Keep cross-repository cleanup in repository-local changes

`retire-legacy-planning-archive` in `omp-agent-setup` owns its 17 historical records and guidance pointer. `consolidate-planning-home` in `nix-darwin` owns its eight records, index, and planning content in the canonical architecture document. This change records those owners but does not edit or validate sibling worktrees.

The three changes can proceed independently because the deployed plugin and global OMP configuration already omit the retired convention. Completion in one repository does not claim completion in another.

**Alternative:** Expand this change across three worktrees. Rejected because its OpenSpec action context is repository-local, the repositories have different gates, and a cross-repository commit would blur ownership and rollback.

## Risks / Trade-offs

- **A record marked active can contain work the operator still wants.** → Require independent evidence of intent. Ask only when no repository evidence can resolve the decision.
- **Moving an empirical claim can strip its denominator or provenance.** → Move the complete claim with its measure, denominator, source corpus, and provenance.
- **A bulk deletion can hide a missed dependency.** → Build the ledger first, update all live references, and run the boundary check against a positive control.
- **New changes can recreate the legacy backlog one file at a time.** → Create a change only for coherent confirmed work with no existing owner.
- **The detector cannot understand arbitrary prose.** → Enforce structural indicators and known guidance surfaces, and keep the sole-home rule explicit in `AGENTS.md`.
- **Large file movement can obscure substantive edits.** → Migrate durable content in subject commits, then make the final cutover mostly deletion and reference updates.
- **One repository can finish while a sibling still retains the old convention.** → Track three named changes and report completion per repository, never as a fleet-wide claim.

## Migration Plan

1. Record the two companion change names and the evidence that no deployed global carrier still instructs `docs/plans/` or `omp-plans`.
2. Record all 27 root records and all current references in the disposition ledger.
3. Map duplicate work to the three active changes and accepted capability specs.
4. Verify each remaining requirement, technical claim, and empirical claim against its current source.
5. Move retained content to its subject owner. Create focused OpenSpec changes only for confirmed unowned work.
6. Update current references to the new owners and confirm that each target exists.
7. Add the repository-level planning-home check and prove its positive control fails for a known conflict.
8. Remove the complete `docs/plans/` tree and update `AGENTS.md` and `openspec/config.yaml` in the same cutover.
9. Run the focused planning-home test, the non-database analysis tests, and `openspec validate --all --strict`.

Rollback is a normal commit revert because the change does not modify measurements, databases, or runtime state. Revert the cutover as one unit if a retained owner or reference is missing. Keep earlier verified subject migrations because they remain valid without the cutover.
