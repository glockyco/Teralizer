## Context

Measurement is finished, so the corpora are frozen archival inputs. The analysis code that reads them
is still live, because the remaining prose needs new tables from the same data. Everything below
follows from that asymmetry: freeze the run machinery, fix the live path.

Verified state (probed read-only on both machines):

| Fact | Evidence |
|---|---|
| 24 databases on the evaluation machine, 8 locally | `psql -l` |
| `postgres_dev` = 13 controlled projects; `postgres_test` = 1,161 RepoReapers projects | `SELECT count(*) FROM project` |
| `_rq6_v7` written by 3 commits (411/373/48) plus 329 projects with NULL version | `SELECT tool_git_version, count(*) FROM project GROUP BY 1` |
| `postgres_verification` holds 1 project on the Air and 22 locally, and its runner recreates it every run | inventory probe; `run-verification-corpus.sh:50-51` |
| `ReportSpec.schema` is `"old"` for the 6 reports that declare `REQUIRES` and `"new"` for the 2 that declare none | `register(...)` in all 8 report modules |
| Its only consumer is `validate = spec.schema == "old"` | `cli.py:38` |
| Reports refuse a corpus without its definition inputs; those ship only behind opt-in flags | `report_basis.py:105-157` vs `prepare-zenodo-package.sh:376-424` |
| Definition inputs are tiny: 116 KB ledger, 1,161 empty markers, 4.6 MB configs | `du`, `ls \| wc -l` |
| `data/jarvis-census` is 13 GB of run material no report reads | `du -sh` |
| Compressed dumps are small: 197 MB, 71 MB, 75 MB for the three largest corpora | `pg_dump -Fc -Z6` |
| Four spike CLIs read `postgres_test`; none is part of `teralizer.eval` | module docstrings; no reference from `eval/` |
| The thesis contrasts "controlled" (30 uses) with "real-world" (43); `census` and `scoreboard` appear 0 times | `grep` over `chapters/05-teralizer/` |
| RQ0's two databases hold the 2 projects with JARVIS's reported scenarios and all 12 benchmark projects | `04-evaluation-rq0.tex:158-169`; project counts 2 and 12 |
| Only RQ6 declares a corpus definition to check, so only its inputs must ship | `Corpus(...)` appears once, in `rq6_causes.py` |

## Goals / Non-Goals

**Goals:**

- One place in the live code knows a database name.
- A name states what the data is.
- A wrong or partial corpus fails loudly.
- A third party reproduces the reported figures from the artifact alone.

**Non-Goals:**

- Re-running or re-measuring anything. Settled: the corpora stand, mixed provenance included.
- Rewriting the Java pipeline, run configs, or runner scripts. They are the record of what was run.
- Preventing future mixed-provenance runs. There are no future runs.
- Migrating any corpus to a newer schema.

## Decisions

### 1. Delete `ReportSpec.schema`; let the requirement declaration decide

The field holds `"old"` or `"new"`, and its only use is `validate = spec.schema == "old"` at
`cli.py:38`. Across all eight reports the value correlates perfectly with whether the report declares
`requires`, so the field carries no information. It also cannot be trusted: `"old"` with an empty
`requires` raises at runtime, and `"new"` with a non-empty `requires` **silently skips the check the
report asked for**.

Beyond redundancy the name is wrong twice. It describes a schema generation, which is a property of a
database, not of a report — two reports on the same database could disagree. And what it actually
controls is whether declared objects are checked.

So: remove the field and derive `validate = bool(spec.requires)`. A report that declares objects gets
them checked, always; a report that declares none gets no check and surfaces a missing object as the
failing query's own error. Verified against the table above: behavior is unchanged for all eight
reports, and the silent-skip case becomes impossible.

*Alternative considered:* rename it to `validates_schema: bool`. Rejected — it would still be state
that can disagree with `requires`, and the declaration already says everything.

### 2. Names state the evaluation condition, in the thesis's own words

Every current name fails for a different reason, and each failure rules out a class of replacement:

- `dev` / `test` describe a **deployment role the data never had**.
- `_rq6_` names a corpus after a **question that reads it**. A new question would force a rename.
- `_v7` is a **counter**, and a counter in a replication package invites the reader to ask where v1 to
  v6 are. They are not missing; they were superseded during development and were never published.
- `census` / `scoreboard` are **implementation identifiers**. Both appear in the thesis exactly zero
  times, and `rule://prose-style` forbids adopting an implementation term as published vocabulary.

What survives all four is the axis the thesis actually argues along. It contrasts **controlled** with
**real-world** — 30 uses against 43, and the two are the titles of RQ5 ("Barriers Under Controlled
Settings") and RQ6 ("Barriers Under Real-World Settings"). RQ0's two databases are likewise
distinguished by what they measure, not by their implementation: `postgres_jarvis_scoreboard` holds
the 2 projects containing the scenarios JARVIS reports, and `postgres_jarvis_census` holds all 12
projects of the JARVIS benchmark (`04-evaluation-rq0.tex:158-169`). "Scenario" and "benchmark" are
the thesis's terms; "scenario" is fixed vocabulary by `rule://prose-style`.

| Corpus id | Database | Was | Serves |
|---|---|---|---|
| `controlled` | `teralizer_controlled` | `postgres_dev` | RQ1-RQ5, controlled rows of the corpus table |
| `real-world` | `teralizer_real_world` | `postgres_reporeapers_rq6_v7` | RQ6, real-world rows of the corpus table |
| `jarvis-benchmark` | `teralizer_jarvis_benchmark` | `postgres_jarvis_census` | RQ0 applicability across the 12 benchmark projects |
| `jarvis-scenarios` | `teralizer_jarvis_scenarios` | `postgres_jarvis_scoreboard` | RQ0 value coverage on the scenarios JARVIS reports |

Four names for the four things the thesis measures, and the first two form the contrast the thesis
draws. No counter, so nothing implies a missing series. `real-world` also resolves the ambiguity that
produced `_v7` in the first place: two RepoReapers corpora exist, but exactly one real-world corpus is
published, so the condition names it unambiguously where the dataset name would not.

The prefix is spelled out rather than abbreviated, so `psql -l` explains itself to a replicator, and
because `postgres_` collided with the server's own `postgres` database — which is on the protected
list and has itself accumulated 15 project rows.

Renames use `ALTER DATABASE ... RENAME TO`: metadata-only, no copy, no measurement row rewritten.

The registry records **only the current name**. An earlier draft kept a `published_as` field so a
holder of the archived artifact could map the old dump names, which contradicted this change's own
rule that a stale name must fail rather than work: it is an alias stored as data. The archived
artifact is immutable and self-describing, so it needs nothing from this registry, and the rename
itself is recorded where history belongs, in the commit that performs it. The frozen run inputs keep
the names they wrote, and a note in their directory says so.

### 2a. Two databases leave the set entirely

`postgres_test` is **retired, not renamed**. It holds a real 1,161-project RepoReapers measurement,
but once the corpus-characteristics table is regenerated from the real-world corpus, no reported
figure reads it. Its only remaining readers are four CLIs whose own docstrings call them spikes, and
none of them is part of `teralizer.eval`. A corpus that backs no published figure does not belong in a
replication package: shipping it would invite a reviewer to ask which RepoReapers result is the real
one. It is dumped for the archive and dropped, and the spike CLIs move to the real-world corpus.

`postgres_verification` is **not a corpus at all**. Its runner recreates it on every use
(`run-verification-corpus.sh:50-51`), and it holds 1 project on the Air against 22 locally, so its
content is not a measurement of record. It becomes `scratch_verification`, which matches the term the
repository already uses for it: "Experiments use scratch DBs (`postgres_verification`, ...) created
and dropped by their runner scripts."

Dropping both removes the two databases most likely to make a replicator doubt the artifact, and it
leaves the registry with exactly the corpora the reported figures come from.

### 3. The registry is read by Python and shell, not by Java

The pipeline will not run again, so the Java side never needs to resolve a corpus id. Leaving Java,
`project-configs/**`, `reference.conf`, the runner scripts, and `protected-databases.txt` untouched is
not laziness: those files record which database each run wrote, and rewriting them to new names would
falsify that record. A note in each directory states that its names predate the rename.

The boundary is therefore explicit: **frozen run machinery** keeps its old names and its denylist;
**live artifact machinery** — the analysis package, packaging, import, generated docs — uses the
registry. The name-literal check applies to the live side only.

`corpora.toml` lives in `src/main/resources/db/` beside the existing schema files, read by `tomllib`
in Python and by a small Python helper from shell, so no format is parsed twice.

*Dropped from the earlier draft:* a Java registry reader, and rewriting the run configs.

### 4. Provenance is one derived statement per corpus, not a subsystem

An earlier draft added a `run` table and refused writes that would make a corpus a mosaic. Both only
pay off on a future run, and there is none; the corpora are already mosaics, so the machinery would
have been born legacy.

What is actually needed is that the artifact states what produced each corpus. Because the corpora are
frozen, that statement is a one-time measurement: a query emits commits with per-project counts and
the unattributed count, and the result is recorded in the manifest. No regeneration loop, no
idempotence check, no enforcement.

*Also dropped:* the schema-era taxonomy from the previous draft. It invented a three-value
classification to guard a report-by-corpus matrix that does not exist — each report has one fixed
corpus — and it duplicated `ReportSpec.schema`, the very field decision 1 deletes.

### 5. The dump is the unit of record

`publish-corpora` dumps each corpus and writes `replication/datasets/manifest.json` with corpus id,
file, sha256, bytes, project count, and provenance. Import restores from the manifest and verifies
checksum and project count. The author restores from the same dumps, which is what keeps the
artifact's path the tested path.

Checksum plus project count is the whole verification. A sha256 over the dump already covers every
byte of schema and data, so adding a schema check on top would be redundant.

Immutability then costs one `GRANT`: reports connect as the existing `teralizer_ro` role, nothing
writes a corpus, and a damaged local copy is re-restored.

### 6. Two classes replace the denylist, on the live side

Corpus: in the registry. Scratch: `scratch_<purpose>`. Reports refuse anything else and refuse a
corpus whose project count disagrees with its entry, which is what kills the `_local` trap. The
`protected-databases.txt` denylist stays where it is, serving the frozen runners; the live tooling
does not consult it.

### 7. Disposition of the 24 databases

| Disposition | Databases | Evidence |
|---|---|---|
| Registry corpus, shipped | `postgres_dev`, `_rq6_v7`, `postgres_jarvis_census`, `postgres_jarvis_scoreboard` | the reported figures come from these four |
| Scratch | `postgres_verification` | recreated per run; 1 project on the Air against 22 locally |
| Retain as dump, then drop | `postgres_test`, `_rq6`, `_v2`, `_v4`, `_v6`, `postgres_reporeapers` | real measurements that no published figure reads; `_v6` retained until the docs naming it are regenerated |
| Drop | `_v3` (8 projects), `_v5` (8), `*_detach_gate*`, `*_shape_gate*`, `postgres_source_level_gate`, `_rq6_fix_smoke`, `_rq6_junit3_smoke`, `jooq_codegen_scratch`, local `_rq6_v7_local` | small experiment scratch; nothing reads them |
| Investigate, then clean | `postgres` (server default, 15 project rows) | a pipeline once wrote into the server's own database |

Dropping runs last, after each retained dump has been restored and counted.

## Risks / Trade-offs

- **A frozen corpus cannot be improved.** Any defect found in the data is now a documentation task. →
  The provenance statement makes the limits explicit so a reader can judge a figure.
- **Renaming breaks muscle memory and any uncommitted local script.** → One-time, and deliberate: a
  stale name must fail rather than resolve through an alias.
- **Frozen run configs will name databases that no longer exist.** → Deliberate. They record what ran.
  A note in that directory says so.
- **Deriving validation from `requires` means a report can opt out by declaring nothing.** → That is
  already true today via `"new"`, and it is now visible in one place instead of two.
- **Retiring `postgres_test` breaks four spike CLIs.** → They move to the real-world corpus, which is
  the same dataset. Their SQL was written against the legacy schema, so each is run once to confirm it
  still works; one that depends on legacy-only structure is deleted rather than kept alive by a dump.

## Migration Plan

1. Land the registry and the lifecycle patterns, pointing at the current physical names, plus the
   name-literal check. Nothing renamed; the sprawl is described.
2. Delete `ReportSpec.schema` and derive validation from `requires`.
3. Move the analysis package, packaging, import, and generated docs onto corpus ids.
4. Rename the four corpora and `postgres_verification`, updating only the registry.
5. Point reports at `teralizer_ro` and confirm no write path reaches a corpus.
6. Build publication and the manifest, including the provenance statements and the definition inputs
   that RQ6 checks.
7. Import into a clean environment and reproduce the report set.
8. Only then act on the dispositions: archive retained dumps, verify each restores, drop the rest.

**Rollback:** steps 1 to 3 revert with git. Step 4 reverses with a rename back. Step 5 reverses with a
`GRANT`. Step 8 is the only irreversible step and runs last.

**Verification at every step:** the full report set must produce byte-identical output against the
canonical baseline, which is confirmed reproducible on this workstation.

## Open Questions

- **Do the 15 project rows in the server's own `postgres` database back any reported figure?** Must be
  traced before cleaning. Either way it is one row in the disposition table.
- **Does any code read a view from `create-views.sql`?** No path applies that file today. The answer
  decides whether it is folded into the code generator's scratch setup or deleted.
- **Do all four spike CLIs still run against the real-world corpus?** Each is run once after the
  repoint. The answer decides per CLI whether it is kept or deleted, and changes no other decision.
