---
title: MUT-id Targeting & Mutation-Data Coverage
type: audit
status: active
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

DB-grounded evidence on MUT-id targeting and mutation-data coverage: which RepoReapers
projects are the best concrete targets, how much killed-mutant-oracle coverage exists and
what would grow it, which barriers are realistically improvable, and which telemetry to add
for prioritization and reporting. The oracle-coverage sections were collected to prioritize
the killed-mutant runtime tier (`2026-06-27-ensemble-mut-identification`), which is now
abandoned (deferred indefinitely by operator decision); they remain the record of what that
tier could have contributed. The targeting, barrier, and telemetry evidence stands on its
own.

All figures are first-hand read-only queries on `postgres_test` (RepoReapers,
1161 projects), 2026-06-28, via the replication container. The reusable ranking
lives in `analysis/src/teralizer/mut_id_targets.py`
(`uv run --directory analysis python -m teralizer.mut_id_targets`).

The fusion-spike extraction audit below is first-hand read-only evidence from
`postgres_fusion_spike` (post-fusion, 23 projects) and
`postgres_reporeapers_rerun` (pre-fusion baseline, same projects matched on
`project.root_path`), 2026-07-03. Project ids are never joined across DBs.
The funnel module uses `db_config.get_test_engine(validate=False)`, so
`DB_NAME_TEST=postgres_fusion_spike` selects the spike DB for
`uv run --directory analysis python -m teralizer.mut_resolution_funnel`.

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
dependent filters are missing. The telemetry design for these gaps is
`2026-07-01-pipeline-observability-telemetry`; this section records the MUT-id-specific analysis
needs that spec should satisfy. Recommended additions, by leverage:

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

## Fusion-spike extraction and validation evidence (2026-07-03)

Scope: the 23-project `project-configs/fusion-spike/` corpus. The spike DB is
`postgres_fusion_spike`; the pre-fusion baseline is
`postgres_reporeapers_rerun`, with projects matched only by `project.root_path`
because project ids are DB-local. Counts in this section use one comparable
unit throughout: **per-variant `generalization` rows for
`IMPROVED_100_TRIES`**. Multi-variant totals from `reference.conf` are
aggregation artifacts and are not the comparison unit.

### Resolver tier funnel

`mut_resolution_observation` contains 25,306 assertions across 19 projects in
the definitive single-variant spike. The table differs from the 32,560-row
pre-validation-repair extraction table because `kouchat`, `gedcom4j`,
`xenqtt`, and `uaicriteria` stop at the uniform `EXECUTE_TESTS_ORIGINAL`
ceiling in this run, before `ANALYZE_TESTS` can record observations.

| tier | assertions | share | resolved | characterization-only | none | shallow picks | inspector unwraps |
|---|---:|---:|---:|---:|---:|---:|---:|
| `T1_PROVEN` | 15,791 | 62.4% | 10,578 | 5,213 | 0 | 412 | 4,694 |
| `T2_CORROBORATED` | 1,018 | 4.0% | 1,018 | 0 | 0 | 16 | 133 |
| `T3_SINGLE_WEAK` | 2,193 | 8.7% | 1,731 | 462 | 0 | 226 | 294 |
| `T4_GUESS` | 623 | 2.5% | 553 | 70 | 0 | 9 | 4 |
| `T5_NONE` | 5,681 | 22.4% | 0 | 0 | 5,681 | 0 | 0 |

`MissingValueFilter` rejects with `status='RESOLVED'` are zero:

| status | tier | no-pick reason | MissingValue rejects |
|---|---|---|---:|
| `NONE` | `T5_NONE` | `UNSUPPORTED_ASSERTION_SHAPE` | 4,673 |
| `CHARACTERIZATION_ONLY` | `T1_PROVEN` | `LIBRARY_DECLARATION` | 3,915 |
| `CHARACTERIZATION_ONLY` | `T1_PROVEN` | `UNRESOLVED_SOURCE_DECLARATION` | 1,298 |
| `NONE` | `T5_NONE` | `NO_VISIBLE_CALL` | 1,008 |
| `CHARACTERIZATION_ONLY` | `T3_SINGLE_WEAK` | `LIBRARY_DECLARATION` | 351 |
| `CHARACTERIZATION_ONLY` | `T3_SINGLE_WEAK` | `UNRESOLVED_SOURCE_DECLARATION` | 111 |
| `CHARACTERIZATION_ONLY` | `T4_GUESS` | `UNRESOLVED_SOURCE_DECLARATION` | 38 |
| `CHARACTERIZATION_ONLY` | `T4_GUESS` | `LIBRARY_DECLARATION` | 32 |

### No-regression census

Per-variant `IMPROVED_100_TRIES` rows increase from 594 in the matched
pre-fusion baseline to 1,184 in the spike. Every project that completes both
runs keeps the exact baseline count or gains rows; the zero-row spike projects
are either at the uniform original-suite ceiling in this run or never generate
rows in this corpus.

| project | baseline gens | spike gens | delta | note |
|---|---:|---:|---:|---|
| `AncientMariner/TDD-Katas` | 208 | 278 | +70 | gain |
| `ManfredTremmel/gwt-commons-codec` | 0 | 179 | +179 | gain |
| `nclarkekb/antiaction-common-json` | 0 | 125 | +125 | gain |
| `joschi/JadConfig` | 72 | 124 | +52 | gain |
| `hampelratte/svdrp4j` | 24 | 83 | +59 | gain |
| `bfh-evg/unicrypt` | 34 | 63 | +29 | gain |
| `srcc-msu/octotron_core` | 46 | 60 | +14 | gain |
| `keystrokex/htm_java` | 0 | 53 | +53 | gain |
| `dpaukov/combinatoricslib` | 39 | 39 | +0 | unchanged |
| `cjmcgraw/MarkupTagScanner` | 34 | 35 | +1 | gain |
| `quux00/simplecsv` | 0 | 34 | +34 | gain |
| `chamelaeon/Dicebot` | 22 | 29 | +7 | gain |
| `wojtask/CormenImpl` | 0 | 28 | +28 | gain |
| `spotify/sparkey-java` | 25 | 25 | +0 | unchanged |
| `bojantomic/jeff` | 0 | 24 | +24 | gain |
| `whizzosoftware/WZWave` | 5 | 5 | +0 | unchanged |
| `almondtools/rexlex` | 0 | 0 | +0 | never-generating in this corpus |
| `blurpy/kouchat` | 41 | 0 | −41 | original-suite timeout at 60s |
| `blurpy/kouinject` | 0 | 0 | +0 | never-generating in this corpus |
| `frizbog/gedcom4j` | 24 | 0 | −24 | original-suite timeout at 60s |
| `TwoGuysFromKabul/xenqtt` | 20 | 0 | −20 | original-suite timeout at 60s |
| `uaihebert/uaicriteria` | 0 | 0 | +0 | original-suite timeout, no baseline/spike generalizations |
| `urbanairship/java-library` | 0 | 0 | +0 | never-generating in this corpus |

### Validation coverage and exclusions

The validation-layer denominator is the included spike generalizations, not
all generated rows. Every included generalization has a jqwik execution row;
no `BUILD_PROJECT_GENERALIZED` or `EXECUTE_TESTS_GENERALIZED` task fails.

| measure | count |
|---|---:|
| `jqwik_property_execution` rows | 850 |
| projects with jqwik rows | 16 |
| distinct generalizations with jqwik rows | 850 |
| included generalizations | 630 |
| included generalizations covered by jqwik | 630 / 630 (100%) |
| excluded generalizations before validation | 554 |
| failed `BUILD_PROJECT_GENERALIZED` tasks | 0 |
| failed `EXECUTE_TESTS_GENERALIZED` tasks | 0 |

Excluded rows are pre-validation exclusions and are therefore outside the
630-row validation-coverage denominator:

| exclusion stage | excluded rows |
|---|---:|
| `GENERALIZE_TESTS` | 334 |
| `FILTER_GENERALIZATIONS` | 220 |

The uniform original-suite ceiling is the accepted exclusion category for the
borderline suites that can jitter across the 60s budget:

| project | stopping stage | runtime | accepted category |
|---|---|---:|---|
| `blurpy/kouchat` | `EXECUTE_TESTS_ORIGINAL` | 60.024s | timeout at the uniform 60s ceiling |
| `frizbog/gedcom4j` | `EXECUTE_TESTS_ORIGINAL` | 60.032s | timeout at the uniform 60s ceiling |
| `TwoGuysFromKabul/xenqtt` | `EXECUTE_TESTS_ORIGINAL` | 60.009s | timeout at the uniform 60s ceiling; an identical-config run passed earlier the same day |

`uaihebert/uaicriteria` also stops at `EXECUTE_TESTS_ORIGINAL` (60.020s) in
this DB, but it has zero baseline and spike generalizations, so it stays in
the zero-generation bucket rather than the validation-coverage denominator.
`spotify/sparkey-java` completes validation; its `ReadOnlyMemMapTest` native
crash on aarch64 is a known intermittent native flake.

### Seed-kill share

Raw validation backstop rate: 23 of 850 jqwik rows fail on the seed trial
(`tries = 1 AND diagnostic_kind = 'ASSERTION_FAILED'`), or 2.7%.

The newly-attempted subset uses the cross-DB assertion key
`(project.root_path, test.test_method_qualified_name, assertion_source_code,
occurrence ordinal within that method/source text)` and requires the matched
baseline assertion to be rejected by `MissingValueFilter`. On that stricter
assertion-level key, 17 jqwik rows are newly attempted and 4 seed-kill:

| denominator | seed kills | share | qualifier |
|---|---:|---:|---|
| all jqwik rows | 23 / 850 | 2.7% | all included validation executions |
| baseline-`MissingValue` matched rows | 4 / 17 | 23.5% | exact cross-DB assertion-key match; 833 / 850 jqwik rows have no baseline `MissingValue` key match |

### Cost delta

`ANALYZE_TESTS` is the resolver-memoization signal: the definitive spike records
8,273 `ANALYZE_TESTS` tasks, 119.0s total, 0.014s/task on average. The
pre-memoization spike measurement was 0.13s/task, so the average per-task cost
is about 9× lower. `EXECUTE_TESTS_*` runtimes are not compared across DBs
because the pre-fusion baseline and the spike binary differ in test-forking
behavior.

| stage | tasks | total seconds | avg seconds/task | note |
|---|---:|---:|---:|---|
| `ANALYZE_TESTS` | 8,273 | 119.0 | 0.014 | post-memoization; was 0.13s/task pre-memoization |
| `EXECUTE_JPF` | 2,036 | 198.3 | 0.097 | same spike DB |
| `ANALYZE_JPF` | 1,203 | 8.0 | 0.007 | same spike DB |
| `SETUP_PROJECT` | 23 | 101.0 | 4.390 | warm-cache 23-project spike; corpus cold-cache setup remains a separate measurement |

The DB stores runtime sums, not process wall-clock timestamps. In the spike DB,
`project.runtime` sums to 3,144s (52.4 min) and `task.runtime` sums to 2,939s
(49.0 min); the operator wall-clock for the full 23-project single-variant run
is approximately 62 min including orchestration overhead outside those sums.

### Input topology and R1/R2 gate

`actual_shape × receiver_provenance` in `mut_resolution_observation`:

| actual shape | receiver provenance | assertions | resolved |
|---|---|---:|---:|
| `SINGLE_CALL` | `LOCAL_OTHER` | 4,840 | 2,410 |
| `NONE` | `NONE` | 4,700 | 19 |
| `VARIABLE` | `NONE` | 3,442 | 2,134 |
| `CHAINED_CALLS_END0ARG` | `LOCAL_OTHER` | 2,099 | 779 |
| `OPERATOR_COMPOSITE` | `NONE` | 1,919 | 1,374 |
| `SINGLE_CALL` | `FIELD` | 1,872 | 1,776 |
| `SINGLE_CALL` | `PARAM_OR_STATIC` | 1,701 | 1,479 |
| `SINGLE_CALL` | `LOCAL_CTOR_MUTATED` | 987 | 958 |
| `LITERAL` | `NONE` | 516 | 392 |
| `CTOR_ONLY` | `NONE` | 502 | 484 |
| `SINGLE_CALL` | `LOCAL_CTOR` | 469 | 423 |
| `CHAINED_CALLS_ENDNARG` | `PARAM_OR_STATIC` | 450 | 336 |
| `ARRAY_INDEX` | `NONE` | 441 | 419 |
| `CHAINED_CALLS_END0ARG` | `FIELD` | 342 | 236 |
| `CHAINED_CALLS_END0ARG` | `LOCAL_CTOR_MUTATED` | 277 | 225 |
| `CHAINED_CALLS_ENDNARG` | `LOCAL_OTHER` | 202 | 65 |
| `CHAINED_CALLS_END0ARG` | `PARAM_OR_STATIC` | 179 | 100 |
| `CTOR_RECEIVER_CALL` | `INLINE_CTOR` | 161 | 123 |
| `CHAINED_CALLS_ENDNARG` | `FIELD` | 105 | 85 |
| `CHAINED_CALLS_END0ARG` | `LOCAL_CTOR` | 45 | 41 |
| `CHAINED_CALLS_ENDNARG` | `LOCAL_CTOR_MUTATED` | 22 | 0 |
| `SINGLE_CALL` | `NONE` | 20 | 18 |
| `CHAINED_CALLS_ENDNARG` | `LOCAL_CTOR` | 11 | 0 |
| `CHAINED_CALLS_END0ARG` | `INLINE_CTOR` | 2 | 2 |
| `CHAINED_CALLS_END0ARG` | `NONE` | 1 | 1 |
| `CHAINED_CALLS_ENDNARG` | `INLINE_CTOR` | 1 | 1 |

R1 expression-slice opportunity from the cross-tab classes is 6,157 assertions:
`CHAINED_CALLS_END0ARG` 2,945, `CHAINED_CALLS_ENDNARG` 791,
`OPERATOR_COMPOSITE` 1,919, and `CTOR_ONLY` 502.

R2 statement-slice gate, spike-scaled, is 469 `SINGLE_CALL × LOCAL_CTOR`
assertions and 987 `SINGLE_CALL × LOCAL_CTOR_MUTATED` assertions. That is not
evidence to prioritize R2 ahead of R1; keep T3 statement slicing out of scope
until the full-corpus rerun sizes it.

### Widened-failure triage (why validated generalizations fail after the seed)

Of 850 validated generalizations, 156 fail on a post-seed trial
(`tries > 1 ∧ ASSERTION_FAILED`); 155 of those are `output_spec_class = 'NULL_CONCRETE'`.
Failure rate by oracle class: NULL_CONCRETE 155/716 (21.6%) vs SYMBOLIC 1/131 (0.76%).
126/156 additionally carry an empty path condition. Mechanism (verified on octotron
`_ValueTest_Generalized_TestGet_641`): inputs are widened while the expected side stays the
original concrete literal because no output model exists (boxed-primitive returns lose the
symbolic attr at boxing) — the property claims a universal the extraction never licensed and
jqwik correctly falsifies it. Not target-program bugs; self-inflicted by construction.
`NonPassingTestFilter` excluded every one (shipped-suite soundness held).

Dispositions:
- Prevention at source: `2026-07-03-widening-license` (generation-time license rule +
  `ORACLE_NOT_WIDENABLE` typed exclusion); completeness recovery:
  `2026-07-03-boxed-output-capture` (boxed returns become SYMBOLIC).
- The single SYMBOLIC widened failure (CormenImpl,
  `assertTrue((a <= pickedNumber) && (pickedNumber <= b))`, 1/7 clauses used) is a
  dropped-clause case on a randomness-driven MUT — singleton; no action until populated.
- Seed-kills: 23/850 (2.7%) — below R-B's pay-off threshold; R-B rejected per its own gate
  (see `2026-07-02-recipe-seam-review`).
- 48/850 end filter-degenerate (`FILTER_EXHAUSTED_SEED_ONLY` 40,
  `LIMITED_TOO_MANY_FILTER_MISSES` 8) — completeness evidence for
  `2026-06-28-clause-driven-input-generation` phases C/D.

### Post-license verification (widening license + boxed capture implemented)

Definitive re-run with `2026-07-03-widening-license` and `2026-07-03-boxed-output-capture`
in place (all 19 gen-producing projects completed — kouchat, gedcom4j, and xenqtt passed the
execution ceiling this run; census per-project counts bit-identical, which also verifies the
R-A `GeneralizationRecipe` extraction as behavior-preserving):

- License outcomes: 1,376 generalizations; 417 included; 587 `ORACLE_NOT_WIDENABLE`
  (all NULL_CONCRETE — zero SYMBOLIC/CONSTANT/EXCEPTION refusals). 278 NULL_CONCRETE stay
  included (licensed boolean-in-PC cases and non-widening shapes).
- Widened failures: 156 → 28 SYMBOLIC + 1 NULL_CONCRETE of 510 validated rows. Manual read
  of the T1_PROVEN widened failures (11 rows: 9 kouchat, 1 htm_java, 1 svdrp4j; the
  validation net excluded every one): **zero mis-picks**. The kouchat 9 are all
  `ByteCounter.updateTimeSpent`, whose pick is provably correct (the asserted local IS the
  method's return); the property fails because `previousTime` is mutable receiver state
  seeded from `System.currentTimeMillis()` and mutated per call, so the oracle depends on
  hidden state and wall-clock the input model does not carry. htm_java's
  `MovingAverage.next` is the same class (sliding-window receiver state mutated per call).
  svdrp4j's `Timer.hasState` is the license's documented NULL_CONCRETE residual risk (PC
  clause from an unrelated branch), as predicted. The fusion spec's bar — any T1 miss is a
  resolver bug — is met. The recurring failure class is hidden-mutable-receiver-state
  dependence, a widening-model limitation, not an attribution one.
- Seed-kills 72/510: 61 kouchat + 7 htm_java — first-time validators whose incoherent picks
  (`updateTimeSpent` resolved for inspector assertions) were previously invisible, not a
  regression; 4 CormenImpl as before.
- Boxed capture: characterization showed the vendored fork preserves the box-field attr for
  `Integer.valueOf` (both cache paths) and explicit `new Long/Boolean(...)`, but loses it on
  `Long.valueOf`/`Boolean.valueOf` autoboxing — so recovery is partial by design (octotron's
  `Value` accessors stay NULL_CONCRETE); the lossy paths degrade to refusal, never
  unsoundness. Full recovery is upstream jpf-symbc work (bounded task, not inline).
- New finding (pre-existing, unmasked by the license fix letting EXCEPTION gens through
  again): 74/75 EXCEPTION and 138 SYMBOLIC generalizations die in GENERALIZE_TESTS with an
  NPE at `SpoonUtils.getTypeReference:70` via `TestParametersFactory.createParametersClass`
  — identical counts across pre-license runs (1/75 EXCEPTION included in every run), so not
  a regression. Root cause (diagnosed): the method's default branch calls
  `factory.Type().get(typeName).getReference()`, and Spoon's `Type().get` is model-only —
  null for any type outside the source model (`java.lang.String`, JDK types) — while the
  null-safe `createReference` is what the surrounding factories already use for fields.
  The class appears now because fusion resolves String-parameter MUTs for the first time
  (pre-fusion funnel had ≈0), so String-typed inputs reach the parameter factories at all.
  Fix: null-safe reference creation in the default branch; verify with a parameter-factory
  model test over a `java.lang.String` input plus a single-project pipeline run (antiaction).

### Fixture-corpus findings (verification tier live)

The nine-fixture pipeline corpus (`2026-07-04-pipeline-fixture-corpus`) pins every behavior
family from the 2026-07-03/04 sessions; two findings from recording its goldens:

- Pass-through booleans split by boxing, not by pass-through: a primitive store/load keeps the
  symbolic attr on the stack slot (SYMBOLIC, licensed, sound — pinned), while the refusal arm
  the license was designed for requires the boxed shape (`Boolean.valueOf`), matching the
  octotron evidence. The license spec's justification holds; the fixture sketch was corrected.
- String `length()` predicate extraction is faithful: SPF models `length()` as a
  `SymbolicLengthInteger` carrying the parent string expression, and ingestion keeps it
  tied to its receiver as an output-renderable invocation instead of flattening it to a
  free integer (which had detached the clause and refused sound length predicates as
  `ORACLE_NOT_WIDENABLE`). Other string-derived integer subclasses (the `indexOf` family)
  refuse as typed `UNSUPPORTED_TERM` at extraction. Pinned by the `string-sound-set`
  golden: the length row widens FULL 100/100 (fix `8303a4fd`).

## Relationship to existing docs

- Design of the resolver: `2026-06-27-ensemble-mut-identification` (the oracle is
  the primary signal there; this audit supplies its targeting + coverage evidence).
- Corpus-wide barrier evidence: `2026-06-26-applicability-barriers` (this audit
  is the MUT-id- and oracle-coverage-specific deep dive).
- Reusable ranking: `analysis/src/teralizer/mut_id_targets.py`.
