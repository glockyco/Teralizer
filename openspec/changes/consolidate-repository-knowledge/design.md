## Context

See `proposal.md` for motivation. The six remaining files under `docs/` mix four kinds of material:
implementation inventories, operating instructions, empirical snapshots, and durable behavioral
constraints. They are not maintained as one system. `database.md` and `rq6-analysis.md` still name
the v6 real-world database while the registered report reads v7. `architecture.md` and
`artifacts.md` refer to a harness-support spec that no longer exists. `exclusion-model.md` combines
current invariants with v6 counts, historical defect status, and an open lifecycle defect already
recorded by a strict expected-failure test.

The active `consolidate-evaluation-databases` change currently plans to generate `docs/database.md`
and a corpus table under the already-removed `docs/evaluation-run-map`. A deletion without
reconciling that plan would make another accepted change recreate the retired surface.

`project-configs/spikes/r1-viability.conf` has no current caller or repository reference beyond the
spike project and project-config guidance. Commit `cd71c553` promoted its expression-slice behavior
into `verification/fixtures/expression-slice`, including an observed golden. The older spike has no
remaining unique acceptance role.

## Goals / Non-Goals

**Goals:**

- Make every retained fact point to one maintained authority.
- Preserve stable cross-stage and exclusion-reporting behavior as testable requirements without
  preserving implementation snapshots.
- Remove the obsolete R1 experiment only after proving the promoted fixture covers its behavior.
- Prevent the same duplicate surfaces from returning through the ordinary repository gate.
- Keep active OpenSpec changes coherent with the cutover.

**Non-Goals:**

- Change pipeline scheduling, generalization, exclusion classification, report queries, or measured
  values.
- Rewrite repository history or erase historical path names from OpenSpec migration records.
- Turn source inventories, generated examples, database names, counts, or line-number citations into
  accepted requirements.
- Resolve the lifecycle's existing "failed" versus "not attempted" ambiguity. Its strict expected
  failure remains the current owner.
- Generalize the cleanup to other repositories.

## Decisions

### 1. Retire each document by content class

| File | Durable content retained | Authority after cutover | Content deliberately discarded |
|---|---|---|---|
| `architecture.md` | Phase isolation, persisted recipe consistency, evidence-bounded widening, build immutability, reproducible generation, failure scope | `pipeline/cross-stage-contracts`; source and verification tests | Stage numbers, package inventory, class list, dependency list, line references |
| `artifacts.md` | None beyond the generated-artifact behavior already covered by the pipeline spec | `verification/fixtures/expression-slice`, its golden, and regenerated run output | Verbatim generated listings and the point-in-time database row |
| `database.md` | No new requirement; exclusion outcome semantics move with the exclusion contract | DDL and views; the corpus registry and evaluation-data specs owned by `consolidate-evaluation-databases` | Hand-maintained table inventory, database names, counts, commands already exposed by scripts |
| `exclusion-model.md` | Total mechanism classification, filter/non-filter distinction, lifecycle authority, denominators, fail-loud drift | `reporting/exclusion-accounting`, report implementation, invariant tests, report provenance | v6 counts, SQL copies, historical defect ledger, line-number citations |
| `local-state.md` | Only safety-critical deletion and retention boundaries | Path-scoped agent guidance beside `data/`, project configs, databases, and verification fixtures | Volatile inventory and "revisit later" notes without a current owner |
| `rq6-analysis.md` | Registered-report authority, denominator distinctions, completeness and snapshot requirements | `reporting/exclusion-accounting`, report registry, CLI and runner scripts | v6 defaults, duplicated commands, table inventory, source map |

A new narrative replacement is prohibited. Accepted specs state observable contracts. Source,
configuration, tests, reports, and provenance continue to state current mechanisms and evidence.

**Alternative:** Move the six files below `openspec/specs/` unchanged. Rejected because most content
is not a requirement and would make stale counts and source inventories look normative.

**Alternative:** Keep generated `database.md`. Rejected because DDL already defines the schema and
the corpus registry change defines database identity. Generating a second inventory adds a build
artifact without a consumer.

### 2. Keep OpenSpec configuration minimal

Replace `openspec/config.yaml` with the single effective setting:

```yaml
schema: spec-driven
```

Do not copy project context elsewhere. Root and path-scoped agent guidance already carries operating
constraints. The repository tree, build files, and active artifacts provide current technical
context on demand. The validator compares the parsed configuration with the one allowed key and
value, so comments and formatting are irrelevant while project-specific keys fail.

**Alternative:** Retain a shortened context block. Rejected because every candidate sentence is
already owned elsewhere or can drift independently of the executable source.

### 3. Remove the spike and its configuration as one unit

Delete `project-configs/spikes/r1-viability.conf` and
`verification/spikes/r1-viability/`, then remove the empty `spikes/` configuration directory and its
active-lane row from project-config guidance. Before deletion, map every spike test shape to the
promoted expression-slice fixture and confirm its config and golden are part of the ordinary
verification manifest. No alias or compatibility config remains.

Version control is sufficient for the historical spike rationale. The promoted fixture is the only
current owner because it runs through the normal verification gate and records expected pipeline
output.

### 4. Reconcile the database change before the documentation cutover

Update `consolidate-evaluation-databases` proposal, design, specs, and tasks where necessary so it:

- does not generate `docs/database.md`;
- does not name the removed `docs/evaluation-run-map`;
- derives corpus documentation from the registry's own machine-readable output or replication
  manifest only where a consumer exists;
- treats DDL, registry data, and accepted evaluation-data requirements as the maintained authorities.

Validate both changes strictly after the revision. This is scope reconciliation, not implementation
of the database change.

### 5. Use one repository-state guard with positive controls

Generalize the planning-home repository test into a repository-state guard rather than adding a
second scanner. Keep the OpenSpec-only planning checks and add four knowledge checks:

1. no tracked path below `docs/`;
2. no operative source, guidance, or test outside `openspec/` prescribes one of the six retired paths;
3. `openspec/config.yaml` parses to exactly `schema: spec-driven`;
4. the retired spike config and project are absent while the promoted fixture config and golden are
   present.

OpenSpec artifacts are excluded from retired-path text scanning because proposals and migration
records legitimately name removed paths as historical evidence or change targets. The explicit
revision of `consolidate-evaluation-databases` prevents its tasks from recreating them.

Each new check receives an injected failing case before the real repository assertion is updated.
The guard reads tracked or staged paths, so it catches a reintroduced document during commit hooks.

### 6. Cut references and files over atomically

Make source diagnostics and invariant-test messages self-contained or point to the accepted
capability path. Replace README and agent-guidance document links with direct executable owners and
the minimum safety rules needed at the action point. Then remove all six documents in the same
change. A state where current guidance points to a missing file is not a valid intermediate commit.

## Risks / Trade-offs

- **Lower narrative discoverability.** A reader loses one prose tour of the implementation. Mitigate
  with concise links from README and agent guidance to stage declarations, DDL, report registry,
  accepted specs, and the regenerable expression-slice fixture.
- **Specs can become another snapshot.** Limit them to observable invariants and scenarios. Exclude
  names, counts, source locations, and current package structure.
- **Cross-change drift.** The database change can recreate a retired path if left unchanged. Revise
  and strictly validate it before deleting the path.
- **Lost local-state warning.** Moving only safety-critical rows may omit a useful convenience note.
  Prefer code and script help for regenerable state; retain only rules whose omission risks deleting
  evidence or protected databases.
- **False-positive repository guard.** Historical OpenSpec artifacts must be allowed to name old
  paths. Restrict reference checks to operative surfaces outside `openspec/` and prove behavior with
  positive controls.

## Migration Plan

1. Prove the R1 spike's case set is covered by the promoted fixture, config, and golden.
2. Revise and strictly validate `consolidate-evaluation-databases` so it cannot recreate either
   retired documentation path.
3. Make the repository-state guard fail on injected docs, retired references, duplicated OpenSpec
   context, and a restored known spike.
4. Move safety-critical local-state rules to scoped guidance and make source/test diagnostics
   self-contained.
5. Reduce OpenSpec configuration, remove the spike, update repository navigation, and delete the six
   documents as one cutover.
6. Run focused repository-state tests, all non-database analysis tests, project hygiene hooks, and
   strict OpenSpec validation for every change.

Rollback is a normal commit revert. No database, corpus, generated report, or measurement is changed.
If a retained authority is missing, revert the cutover as a unit rather than restoring one narrative
file beside the new authority.

## Open Questions

None. The current tree resolves the document dispositions, database-change overlap, configuration
minimum, and spike ownership.
