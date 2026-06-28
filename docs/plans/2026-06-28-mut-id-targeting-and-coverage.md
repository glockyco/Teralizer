---
title: MUT-id Targeting & Mutation-Data Coverage
type: audit
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

DB-grounded evidence for prioritizing the ensemble MUT-identification work
(`2026-06-27-ensemble-mut-identification`): which RepoReapers projects are the
best concrete targets, how much mutation-data (the killed-mutant oracle) coverage
exists and how to grow it, which barriers are realistically improvable, and which
telemetry to add for prioritization and reporting.

All figures are first-hand read-only queries on `postgres_test` (RepoReapers,
1161 projects), 2026-06-28, via the replication container. The reusable ranking
lives in `analysis/src/teralizer/mut_id_targets.py`
(`uv run --directory analysis python -m teralizer.mut_id_targets`).

## The pipeline funnel — where projects are lost

Distinct projects passing each project-level stage (`task`, SUCCEEDED):

| stage | ok | failed | note |
|---|---:|---:|---|
| DOWNLOAD_PROJECT | 1161 | 0 | |
| SETUP_PROJECT | 806 | **355** | ~329 Maven dependency-resolution (dead deps), ~26 structure |
| BUILD_PROJECT_ORIGINAL | 617 | 189 | compile errors |
| BUILD_SPOON_MODEL | 609 | 2 | |
| EXECUTE_TESTS_ORIGINAL | 548 | 61 | |
| COLLECT_JUNIT_REPORTS_ORIGINAL | 517 | 31 | |
| FILTER_TESTS_ORIGINAL → ANALYZE_TESTS → FILTER_ASSERTIONS | **517** | 0 | MUT-id (ANALYZE_TESTS) runs here |
| EXECUTE_JPF / ANALYZE_JPF | ~516 | 0 | |
| BUILD_PROJECT_INITIAL | 515 | 0 | |
| EXECUTE_TESTS_INITIAL | 385 | **130** | rebuild+rerun of the included suite |
| COLLECT_JACOCO_DATA_INITIAL | 344 | 41 | |
| COLLECT_PIT_DATA_INITIAL | **280** | 64 | 40 of 64 are command timeouts (tunable) |
| GENERALIZE → … → COLLECT_PIT_DATA_GENERALIZED | 280 → 10 | | |

**517 projects reach MUT-id** (the addressable population). **280 reach
mutation collection.** 14 produce generalizations.

## Mutation-data (oracle) coverage

The ensemble resolver's primary signal is per-test killed-mutant data. Today:

- **280 / 1161 projects (24%) have any mutation data**, all from
  `COLLECT_PIT_DATA_INITIAL` (step 25).
- **`COLLECT_PIT_DATA_ORIGINAL` has 0 rows** — it is disabled in the committed
  pipeline. The ensemble spec's "7,455 MissingValue tests have kills" figure came
  from a one-off experiment, not reproducible state.

The catch this exposes: PIT_INITIAL runs at step 25, **after** the whole
INITIAL-block attrition (EXECUTE_TESTS_INITIAL −130, JaCoCo_INITIAL −41,
PIT_INITIAL −64). The MissingValue-*excluded* tests are also gone by then. So the
current data has **no oracle coverage for the exact tests MUT-id needs to fix**
(verified: 0 of the 21,616 MUT-id-sole-blocked assertions are in tests with
INITIAL kills — disjoint by construction).

### Growing coverage

| lever | opportunity size | caveat |
|---|---|---|
| **Enable `PIT_ORIGINAL` (step 12)** — the spec's step 1 | up to ~517 projects reach `FILTER_TESTS_ORIGINAL`, the gate before it | **upper bound, not expected coverage** — PIT_ORIGINAL has its own timeouts/failures (see the 64 PIT_INITIAL failures) and is expensive on large suites; realized coverage will be lower |
| Raise the PIT command timeout | ~40 of 64 PIT_INITIAL failures are timeouts | some are genuinely too large |
| POM output-dir / non-Maven layout detection | a fraction of the ~26 structure setup failures | the ~329 dependency-resolution failures are dead deps — not cheaply fixable |

The headline: enabling `PIT_ORIGINAL` is the single biggest oracle-coverage
lever (it runs *before* the INITIAL-block attrition), and it is already a step
of the ensemble plan. Treat 280 → ~517 as the opportunity ceiling, not a promise.

## Concrete targets (RepoReapers)

21,616 assertions are blocked **solely** by MUT-id (`MissingValueFilter` rejects,
no other filter rejects the same assertion). This is the addressable surface and
an **upper bound** on realized gain: `ParameterTypeFilter`/`ReturnTypeFilter`
*defer* under a MUT-id reject, so once a focal method is resolved they become the
next gate. Top projects, tiered by pipeline viability:

**Tier 1 — already generalizes (highest-confidence expansion).** The full
pipeline demonstrably works; unblocking more assertions almost certainly lets
more flow further (the success bar of "more assertions continue further").

| project | MUT-id-blocked | generalizations | has PIT |
|---|---:|---:|:--:|
| `joschi/JadConfig` | 885 | 72 | yes |
| `quux00/simplecsv` | 854 | 14 | yes |
| `ManfredTremmel/gwt-commons-codec` | 361 | 23 | yes |
| `whizzosoftware/WZWave` | 323 | 5 | yes |

**Tier 2 — partial progress (a downstream blocker exists too).** Included
assertions but 0 generalizations → MUT-id alone may not produce a *new* pass;
the post-filter stage (JPF/generalization) also blocks them.

| project | MUT-id-blocked | included assertions | has PIT | note |
|---|---:|---:|:--:|---|
| `frizbog/gedcom4j` | 1225 | 4 | no | highest blocker count; PIT_ORIGINAL would newly cover it |
| `keystrokex/htm_java` | 541 | 37 | no | |
| `wojtask/CormenImpl` | 287 | 5 | no | numeric algorithms — cleanest new-pass probe |

**Tier 3 — no progress yet** (e.g. `urbanairship/java-library` 1020,
`shaunjohnson/suafe` 578): high blocker counts but nothing flows through;
MUT-id is unlikely to be sufficient alone.

## Factor analysis — what makes a project a target, and what is improvable

Factors the ranking encodes: MUT-id blocked count (upside), already-generalizes
(viability), reaches `FILTER_TESTS_ORIGINAL` (oracle-eligible under PIT_ORIGINAL),
has-PIT-now.

Barriers ranked by realistic improvability:

1. **MUT-id** — 517 projects reach it; 70,736 assertion rejects (the single
   biggest blocker). This work. MED.
2. **Oracle coverage via PIT_ORIGINAL** — the spec's step 1; ~2× ceiling. MED.
3. **PIT timeouts** — ~40 cases, tunable. LOW.
4. **Project structure** (POM output dirs, non-Maven layouts) — ~26 setup
   failures, plus general robustness. LOW–MED. (Concrete + fixable per
   `2026-06-26-applicability-barriers` #14.)
5. **EXECUTE_TESTS_INITIAL (130)** — projects that pass the original + JPF
   pipeline but fail the INITIAL rerun; worth diagnosing (likely infrastructure),
   matters for RQ1 more than for the oracle once PIT_ORIGINAL exists.

Not cheaply improvable: dependency resolution (329 dead deps), compile errors
(189), and the object/string **type ceiling** (the research wall, >½ of assertion
rejects — see `2026-06-26-applicability-barriers`).

## Telemetry to add (prioritization + reporting)

The analysis above was harder than it should be because verdicts for
dependent filters are missing. Recommended additions, by leverage:

1. **Independent verdicts for MUT-id-dependent filters.** `filter_result` records
   every filter's ACCEPT/REJECT/DEFER per assertion, but the method-dependent
   filters (`ParameterTypeFilter`, `ReturnTypeFilter`) **DEFER** when MUT-id
   fails — so for a MUT-id-blocked assertion we never learn whether its focal
   method's parameter/return types are supported. This audit had to approximate
   "true reach" with the sole-blocker proxy, which is only an upper bound.
   Recording what those filters *would* decide against the oracle-resolved focal
   method (or computing type-eligibility independently of MUT-id) makes every
   fix's net reach — and the multi-blocker distribution — a direct query.
   **Highest-value addition for prioritization.**
2. **`PIT_ORIGINAL` killed-mutant data** (the ensemble spec's step 1) — both the
   oracle and the only way to pre-screen MUT-id targets by focal-method type.
3. **Focal-method resolution provenance.** Once the ensemble runs, record which
   signal resolved each focal method (oracle / coverage / LCBA / name-match),
   whether it agreed with LCBA, and the focal method's param/return types. Lets
   us measure the ensemble's contribution and present "X% resolved by the oracle
   vs LCBA, Y% newly type-eligible."
4. **Generation-coverage shape telemetry** (`2026-06-28-generation-coverage-telemetry`)
   — residual clause shapes, for generator-recipe prioritization (e.g. the modulo
   decision).
5. **First-class funnel + distance-to-inclusion artifact.** The per-stage
   attrition (this audit's funnel) and per-excluded-assertion blocker count are
   derivable but not surfaced; saving them as analysis outputs (`save_csv_data`)
   supports the paper's applicability narrative directly.

## Relationship to existing docs

- Design of the resolver: `2026-06-27-ensemble-mut-identification` (the oracle is
  the primary signal there; this audit supplies its targeting + coverage evidence).
- Corpus-wide barrier evidence: `2026-06-26-applicability-barriers` (this audit
  is the MUT-id- and oracle-coverage-specific deep dive).
- Reusable ranking: `analysis/src/teralizer/mut_id_targets.py`.
