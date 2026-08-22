## Why

Current planning state is split between `openspec/` and 27 documents under `docs/plans/`. The index labels contradictory records as current, and repository guidance requires readers to judge which home is authoritative.

## What Changes

- Declare `openspec/` as the only home for current planning state.
- Inventory every document under `docs/plans/` and every reference to that directory.
- Carry intended work into named OpenSpec changes and capability specs without duplicating active tasks.
- Move durable technical evidence into the documentation that owns its subject.
- Move records retained only for history out of the planning namespace, and remove superseded or weightless records.
- Remove `docs/plans/INDEX.md` and all current planning documents from `docs/plans/` after their contents have an owner.
- Update repository guidance, skills, documentation, and source references to point to the authoritative replacement.
- Add an automated check that rejects current planning state outside `openspec/` and references to the retired planning home.
- Record the separate repository-local changes that retire the same convention from `omp-agent-setup` and `nix-darwin`; do not edit sibling repositories from this change.
- **BREAKING**: contributors and agents no longer use `docs/plans/` for plans, audits, specifications, roadmap state, or current decisions.

## Capabilities

### New Capabilities

- `repository/planning-state`: Defines one authoritative planning home, the disposition of legacy planning records, and enforcement against a second home.

### Modified Capabilities

None.

## Impact

The change affects contributor guidance, OpenSpec configuration, repository checks, the 27 non-index documents under `docs/plans/`, the 80 archived records below that directory, and all links to them. It does not change Teralizer runtime behavior, measured data, report definitions, or the tasks of existing OpenSpec changes. Companion changes own the historical archive in `omp-agent-setup` and the second planning home in `nix-darwin`.
