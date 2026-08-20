## Context

See `proposal.md` for motivation and the three delta specifications for observable behavior.

The current runner has three independent identities for one run. `ReportSpec` selects one default
physical database and passes one connection. Most report builders then write a physical database name
into `RQReport`, while two builders open a second database themselves. The manifest trusts the result
value except for an RQ0-only branch that reconstructs its second database and diagnostics from metrics.
The corpus registry change removes physical-name literals but does not currently correct this
single-input model.

Output has the inverse problem. Each renderer writes directly to a final generator directory and
returns a different result shape. The CLI translates those shapes into publication behavior while it
loops over reports. `declare-published-artifacts` already requires delivery after the complete run, but
its draft nested map does not establish a typed render or staging boundary.

The report set is small and static: eight registered reports, four frozen registry corpora, repository
files, and pinned project trees. A general workflow engine would add indirection without solving a new
problem. Concrete input, result, and artifact types are sufficient.

## Goals / Non-Goals

**Goals:**

- Make the registration the complete declaration of every input a report can read.
- Make the runner the sole owner of input resolution, connection lifetime, and input provenance.
- Make one built report independent of live database connections and final output paths.
- Give every renderer one typed artifact return contract.
- Delay final generator writes until the selected report set and every staged artifact pass validation.
- Remove report-specific branches from generic execution and provenance code.
- Preserve every existing generated value and rendered byte during the migration.
- Give the four downstream active changes stable interfaces instead of compatibility layers.

**Non-Goals:**

- Changing any evaluation value, corpus content, exclusion classification, caption, table layout, or
  figure appearance.
- Owning corpus naming, physical renames, dumps, or database lifecycle; those remain with
  `consolidate-evaluation-databases`.
- Owning value kinds and target formatting; those remain with
  `separate-report-values-from-presentation`.
- Owning consumer declaration policy; that remains with `declare-published-artifacts`.
- Building a task graph, plugin system, dependency-injection container, query DSL, ORM layer, or generic
  evidence graph.
- Guaranteeing recovery from process termination or machine failure during final file promotion. The
  contract covers build, render, and validation failures before promotion and ordinary promotion
  exceptions that the promotion journal can roll back.
- Rewriting any existing commit.

## Decisions

### 1. Report registration declares a closed tuple of typed inputs

`ReportSpec` becomes a builder plus an immutable tuple of input declarations. Each declaration carries
a unique semantic role.

```python
@dataclass(frozen=True)
class CorpusInputSpec:
    role: str
    corpus_id: str
    requires: tuple[Required, ...] = ()

@dataclass(frozen=True)
class FileInputSpec:
    role: str
    path: Path
    required: bool = True

@dataclass(frozen=True)
class TrackedTreeInputSpec:
    role: str
    path: Path

ReportInputSpec = CorpusInputSpec | FileInputSpec | TrackedTreeInputSpec

@dataclass(frozen=True)
class ReportSpec:
    build: Callable[[ReportContext], RQReport]
    inputs: tuple[ReportInputSpec, ...]
```

Registration rejects duplicate roles, absolute repository paths, unknown corpus ids once the corpus
registry is available, and an empty role. Required database objects move from the report level to the
corpus role they describe. `default_db`, `schema`, and the current optional single-corpus definition
disappear.

The actual declarations are explicit:

```text
dataset  controlled, real-world, project-sources
rq0      scenarios, benchmark, cut-values, completion-marker
rq1-rq5  controlled, plus any verified project-tree input they actually read
rq6      real-world
```

`materialize-exclusion-evidence` later adds `widening-audit` to RQ6. Its path is not introduced early
as an absent placeholder.

**Alternative considered:** Keep one primary connection argument and add a context only for secondary
inputs. Rejected because it retains two construction conventions and leaves primary input identity
outside the same validation path.

### 2. A resolved context owns handles; a snapshot owns identity

Input resolution produces two different concepts:

- a short-lived handle used during construction, such as a read-only connection or resolved path;
- an immutable snapshot retained after construction for provenance and validation.

```python
@dataclass(frozen=True)
class ReportContext:
    handles: Mapping[str, ResolvedInput]

@dataclass(frozen=True)
class BuiltReport:
    report: RQReport
    inputs: tuple[InputSnapshot, ...]
```

The concrete implementation may expose role lookup methods rather than a public mapping, but it does
not add a primary-input convenience property. Builders name the role they consume.

Corpus snapshots carry role, semantic corpus id, the verified registry entry identity, physical name
for local diagnostics, expected and observed project counts, declared corpus-definition status, and
published dump identity when available. They do not hash an entire live PostgreSQL database on every
report run. Read-only restoration from the recorded dump, registry validation, and observed count are
the established corpus boundary.

File snapshots carry role, repository-relative path, presence, SHA-256 when present, last file commit,
and dirty state. Tracked-tree snapshots carry the Git tree or gitlink identity and dirty state. A
tracked tree is only valid for version-controlled source. A generated or otherwise untracked input is
declared as an explicit file rather than hidden behind a tree snapshot.

The runner captures repository input identity before construction and recomputes it afterward. A
mismatch fails before rendering. Corpus connections use the read-only report account and remain open
only inside an `ExitStack` around one builder invocation. Frozen corpora make separate database
transactions a coherent multi-corpus input set; no false cross-database transaction guarantee is
claimed.

**Alternative considered:** Attach paths, connections, and provenance directly to `RQReport`. Rejected
because renderers would retain live resources and builders could again assert input identity.

### 3. Remove physical input overrides from the command interface

`--db`, `--corpus-data-dir`, and `--corpus-config-dir` are removed. `--db` has no defined meaning for
RQ0 or the dataset report, and all three flags bypass the semantic registry contract.

Tests inject a resolver function or fixture registry at the input-resolution boundary. Production CLI
options do not double as test dependency injection.

Target parsing uses a closed target type and rejects unknown values before resolving inputs. Consumer
declarations are read early enough to reject missing target coverage and dirty declared destinations
before any report builds.

**Alternative considered:** Add `--input role=value`. Rejected for production because corpus roles must
resolve through the registry and arbitrary file substitution would make the manifest describe an input
the report did not declare. Focused tests can replace the resolver without exposing that power to a
published command.

### 4. Split orchestration from argument parsing

`cli.py` parses arguments and calls a small functional run API. A new `run.py` owns these phases:

```text
select reports
resolve and preflight declarations
build every selected report
verify repository input snapshots
validate built reports
render all targets into staging
construct the manifest
validate and accumulate artifacts
preflight promotion and consumer delivery
promote generator artifacts
optionally deliver consumer artifacts
```

Reports build sequentially. Parallel database work would compete for one server and complicate failure
ordering without a measured need. Results are small enough to retain until rendering; figure closures
already retain their data after the current connection closes.

No long-lived runner class or service locator is introduced. Phase functions accept values and return
values so failure injection and unit testing remain direct.

### 5. Every renderer returns the same artifact abstraction

A render target and key identify an artifact. The producing report remains explicit for diagnostics and
partial-run ownership.

```python
class RenderTarget(StrEnum):
    MARKDOWN = "md"
    LATEX = "latex"
    CSV = "csv"
    FIGURES = "figures"
    MANIFEST = "manifest"

@dataclass(frozen=True)
class ArtifactId:
    target: RenderTarget
    key: str

@dataclass(frozen=True)
class RenderedArtifact:
    id: ArtifactId
    path: Path
    owner: str | RunAggregate

@dataclass
class ArtifactSet:
    ...
```

`ArtifactSet.add` validates a path beneath the staging root and rejects a duplicate `ArtifactId`,
naming both owners. `ArtifactSet.merge` preserves the same invariant across reports and targets. The
macro file and complete provenance manifest use a run-aggregate owner. A LaTeX table and CSV table may
share a key because the target is part of the identity.

Renderers accept a staging root and never infer a final consumer path. They return artifacts rather
than a bare list, an ad hoc figure map, or an implicit direct write.

**Alternative considered:** Use the nested target-to-key-to-path dictionary drafted by
`declare-published-artifacts`. Rejected because it cannot carry ownership without another parallel map
and leaves duplicate, path, and merge validation spread across callers.

### 6. Rendering and validation happen in a same-filesystem staging root

The runner creates a temporary root beneath `analysis/`, so staged and final paths share a filesystem.
All selected report and aggregate outputs are rendered there. The runner then validates:

- every reported artifact exists and stays below the staging root;
- every artifact has a valid target, key, and owner;
- no target/key collision exists;
- every built report has complete code and input provenance;
- aggregate macros and manifest reconcile with the selected result set;
- a consumer declaration names only artifacts the complete staged set emits;
- final generator and consumer paths pass their dirty-change and path-safety guards.

No final path changes before these checks pass.

Promotion uses atomic per-path replacement plus a journal of prior files. Paths being replaced or
removed are moved to a same-filesystem backup location first. An ordinary exception restores journaled
paths in reverse order. A full run reconciles all generator-owned outputs and moves stale generated
paths into the journal before final deletion. A partial run promotes only selected report-owned paths
and run aggregates; it never prunes unselected report output.

Consumer delivery starts only after generator promotion completes. Consumer-maintained files remain
outside generator ownership and are never pruned.

**Alternative considered:** Render directly and copy back after validation. Rejected because the direct
write is the state change staging is meant to prevent. Directory swaps were also rejected: reports and
build output occupy separate directories, and replacing their common parent would include maintained
analysis source.

### 7. Full and partial runs have explicit manifest semantics

A full run constructs `provenance.json` from scratch using the registry's complete report set. Removed
reports therefore disappear. A partial run reads the last complete manifest, replaces only selected
entries in memory, and writes the resulting aggregate as one staged artifact. Before a partial
promotion, every preserved entry must still have a registered unselected owner; otherwise the command
requires a full run rather than preserving stale state.

Manifest construction consumes `BuiltReport` and `ArtifactSet`. It records, per report:

- declared input snapshots by role;
- metrics, tables, and figures with their existing per-source-file code provenance;
- artifact identities derivable from the staged set.

The RQ0 `report_basis` branch disappears. Values that are report results remain ordinary typed metrics;
corpus identities become input snapshots; table keys come from `ArtifactSet`. Migration proves that no
information used by a consumer is lost before deleting the branch.

**Alternative considered:** Write one manifest fragment per report and aggregate fragments later.
Rejected because fragments introduce a second durable generated format and make stale-fragment cleanup
the new version of the current stale-entry problem.

### 8. Existing code provenance and input provenance remain separate

The current provenance rule is correct: a produced value names the last commit that changed its source
file, not the checkout position. Rename the type to `CodeProvenance` only if doing so improves clarity
without forcing a compatibility alias; this is optional and not required by the contract.

Input snapshots sit at report level in `BuiltReport`. They do not get copied onto every metric and
table. This intentionally over-approximates lineage within a report: every report artifact declares
the report's closed input set. It avoids repeated per-output input annotations that could omit an input
and drift. Reports are already the unit of build and provenance manifest.

Publishing keeps the existing clean-generator requirement. It additionally rejects a dirty declared
repository input unless the documented local override is active, in which case the dirty input remains
visible in its snapshot.

**Alternative considered:** Build an artifact-level provenance graph with input references. Rejected as
unnecessary for eight reports and more fragile than one closed report input set.

### 9. Active changes consume this architecture without duplicated ownership

Before code migration, reconcile the active artifacts:

- `consolidate-evaluation-databases` keeps registry entries, validation, corpus lifecycle, dumps, and
  renames. Its singular report-corpus wording becomes one-or-more corpus roles and it defers input
  resolution to this change.
- `separate-report-values-from-presentation` keeps value kinds, entity rendering, and target formatting.
  It performs no output-path or artifact-set work.
- `declare-published-artifacts` keeps declaration syntax, declared-set policy, consumer guards, and
  delivery. Its nested emitted-map tasks are replaced by consumption of `ArtifactSet`; generator
  staging remains owned here.
- `materialize-exclusion-evidence` depends on this change. It declares the audit through the input
  model, returns domain evidence to the existing report view model, and adds no runner, manifest,
  renderer-return, or publication special case.

Task ownership changes land as planning-only edits before implementation, in one append-only
`docs(openspec)` commit. No previously completed implementation commit is rewritten.

### 10. Atomic implementation commits follow architectural boundaries

The intended new commit sequence is:

1. `docs(openspec)`: reconcile active change ownership and dependencies;
2. `refactor(eval)`: declare and resolve complete report input sets, migrating every builder in one
   clean API cutover;
3. `fix(eval)`: record declared input provenance and remove report-specific manifest identity;
4. `refactor(eval)`: unify renderer output as `ArtifactSet` without changing rendered bytes;
5. `fix(eval)`: stage and promote complete report runs before delivery.

A commit may split when a narrower subject independently passes all affected tests. It may not merge
unrelated boundaries or use a compatibility alias to make an intermediate commit pass. Existing
history is unchanged.

## Risks / Trade-offs

- **[Risk] The input inventory misses a filesystem dependency hidden behind an imported helper.** ->
  Trace every registered builder transitively, add an architecture check for direct connection and
  path-resolution calls under report modules, and compare output after migration.
- **[Risk] Tree hashing is expensive over pinned project sources.** -> Use Git tree or gitlink identity
  plus dirty state for tracked trees; reserve content SHA-256 for explicit files.
- **[Risk] Multi-corpus reports imply a cross-database snapshot that PostgreSQL cannot provide.** ->
  Rely only on the established frozen, read-only corpus identities and record each input separately;
  never claim a cross-database transaction.
- **[Risk] A partial run preserves a removed report's manifest entry.** -> Refuse preservation when an
  existing unselected entry has no registered owner and require a full run.
- **[Risk] Promotion fails after replacing some generator files.** -> Journal prior paths, use
  same-filesystem atomic replacement, restore in reverse order on ordinary failure, and test injected
  failures at each replacement boundary.
- **[Risk] Staging doubles temporary disk use for figures.** -> Stage only selected targets and delete
  the temporary root after promotion or failure; current generated output is small enough for one
  duplicate set.
- **[Risk] Active changes continue implementing their draft local representations.** -> Reconcile their
  tasks first and validate all changes before any code commit.
- **[Trade-off] Report-level inputs overstate lineage for an artifact that uses only some inputs.** ->
  Accepted. A closed report input set is complete and stable; per-artifact dependency graphs would add
  omission risk without changing reproducibility.
- **[Trade-off] Removing `--db` reduces ad hoc diagnostics.** -> Accepted. Diagnostic queries may use
  explicit read-only tooling, while report generation remains bound to published semantic corpora.

## Migration Plan

1. Apply the corpus-registry foundation owned by `consolidate-evaluation-databases` without yet adding
   report-local compatibility paths.
2. Reconcile all active OpenSpec artifacts and validate that every task and capability has one owner.
3. Inventory direct and transitive inputs for every registered report and freeze a byte-level baseline
   of all current generated output.
4. Introduce input declarations, snapshots, context resolution, and builder migration as one clean API
   cutover. Remove physical database and hidden secondary-connection paths.
5. Build every report and prove generated output remains byte-identical while the new manifest gains
   only the intended generic input records.
6. Introduce the artifact abstraction and migrate every renderer without changing rendered files.
7. Add complete-run staging, validation, promotion journaling, and partial/full manifest behavior.
8. Exercise build, render, validation, promotion, and consumer-delivery failure paths with injected
   failures, then run every registered report from its declared inputs.
9. Allow the value, publication, exclusion-evidence, and thesis changes to proceed in dependency order.

Rollback reverts the new commits in reverse dependency order. It restores the prior report API and
output path without modifying corpus data or existing Git history.
