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

## Fusion-spike extraction evidence (2026-07-03)

Scope: the 23-project `project-configs/fusion-spike/` corpus, matched to the
baseline by `project.root_path`. Counts below are extraction-side only; the
validation-layer rerun remains the source of the seed-kill share.

### Resolver tier funnel

`mut_resolution_observation` contains 32,560 assertions in the spike DB:

| tier | assertions | share | resolved | characterization-only | none | shallow picks | inspector unwraps |
|---|---:|---:|---:|---:|---:|---:|---:|
| `T1_PROVEN` | 19,251 | 59.1% | 13,116 | 6,135 | 0 | 809 | 5,637 |
| `T2_CORROBORATED` | 1,748 | 5.4% | 1,748 | 0 | 0 | 204 | 140 |
| `T3_SINGLE_WEAK` | 2,770 | 8.5% | 2,292 | 478 | 0 | 314 | 324 |
| `T4_GUESS` | 908 | 2.8% | 832 | 76 | 0 | 11 | 6 |
| `T5_NONE` | 7,883 | 24.2% | 0 | 0 | 7,883 | 0 | 0 |

`MissingValueFilter` rejects with `status='RESOLVED'` are effectively zero
(6 / 17,988 resolved picks = 0.03%):

| status | tier | no-pick reason | MissingValue rejects |
|---|---|---|---:|
| `NONE` | `T5_NONE` | `UNSUPPORTED_ASSERTION_SHAPE` | 6,271 |
| `CHARACTERIZATION_ONLY` | `T1_PROVEN` | `LIBRARY_DECLARATION` | 4,671 |
| `NONE` | `T5_NONE` | `NO_VISIBLE_CALL` | 1,612 |
| `CHARACTERIZATION_ONLY` | `T1_PROVEN` | `UNRESOLVED_SOURCE_DECLARATION` | 1,464 |
| `CHARACTERIZATION_ONLY` | `T3_SINGLE_WEAK` | `LIBRARY_DECLARATION` | 367 |
| `CHARACTERIZATION_ONLY` | `T3_SINGLE_WEAK` | `UNRESOLVED_SOURCE_DECLARATION` | 111 |
| `CHARACTERIZATION_ONLY` | `T4_GUESS` | `UNRESOLVED_SOURCE_DECLARATION` | 38 |
| `CHARACTERIZATION_ONLY` | `T4_GUESS` | `LIBRARY_DECLARATION` | 38 |
| `RESOLVED` | `T1_PROVEN` | — | 4 |
| `RESOLVED` | `T3_SINGLE_WEAK` | — | 1 |
| `RESOLVED` | `T4_GUESS` | — | 1 |

The same-project `MissingValueFilter` reject count drops from 22,404 baseline
assertions to 14,578 spike assertions (−7,826, −34.9%). This 7,826 reduction is
the newly-attempted assertion count used in the extraction-cost table.

### No-regression census

Counts are all `generalization` rows, matching the spike/baseline census
contract. Total: 594 baseline → 2,003 spike (+1,409). The only loss is
`TwoGuysFromKabul/xenqtt` 20 → 12, the SPF symbolic-sibling-throw residual
tracked by `2026-07-03-symbolic-sibling-throws`; no other project loses
generalizations.

| project | baseline gens | spike gens | delta | note |
|---|---:|---:|---:|---|
| `AncientMariner/TDD-Katas` | 208 | 278 | +70 | gain |
| `ManfredTremmel/gwt-commons-codec` | 0 | 179 | +179 | gain |
| `TwoGuysFromKabul/xenqtt` | 20 | 12 | −8 | `2026-07-03-symbolic-sibling-throws` SPF sibling-throw residual |
| `almondtools/rexlex` | 0 | 0 | +0 | unchanged |
| `bfh-evg/unicrypt` | 34 | 252 | +218 | gain |
| `blurpy/kouchat` | 41 | 138 | +97 | gain |
| `blurpy/kouinject` | 0 | 0 | +0 | unchanged |
| `bojantomic/jeff` | 0 | 24 | +24 | gain |
| `chamelaeon/Dicebot` | 22 | 29 | +7 | gain |
| `cjmcgraw/MarkupTagScanner` | 34 | 140 | +106 | gain |
| `dpaukov/combinatoricslib` | 39 | 39 | +0 | unchanged |
| `frizbog/gedcom4j` | 24 | 42 | +18 | gain |
| `hampelratte/svdrp4j` | 24 | 332 | +308 | gain |
| `joschi/JadConfig` | 72 | 124 | +52 | gain |
| `keystrokex/htm_java` | 0 | 53 | +53 | gain |
| `nclarkekb/antiaction-common-json` | 0 | 125 | +125 | gain |
| `quux00/simplecsv` | 0 | 34 | +34 | gain |
| `spotify/sparkey-java` | 25 | 25 | +0 | unchanged |
| `srcc-msu/octotron_core` | 46 | 60 | +14 | gain |
| `uaihebert/uaicriteria` | 0 | 0 | +0 | unchanged |
| `urbanairship/java-library` | 0 | 0 | +0 | unchanged |
| `whizzosoftware/WZWave` | 5 | 5 | +0 | unchanged |
| `wojtask/CormenImpl` | 0 | 112 | +112 | gain |

### Stratified mis-targeting spot check

Sampling used `SELECT setseed(0.42)` and `ORDER BY random()` in
`postgres_fusion_spike`, then retained spike `status='RESOLVED'` assertions
whose matching baseline assertion had no `tested_method_name`. The cross-DB
assertion key is `(project.root_path, test.test_method_qualified_name,
assertion.assertion_source_code, occurrence ordinal of that source text within
the test method ordered by assertion id)`. This key is unique for all 35,056
baseline assertions in the 23-project intersection; 17 spike resolved
assertions had no baseline key and were not sampled. The newly-resolved
population is 4,102 assertions: T1 1,936; T2 710; T3 1,062; T4 394.

| tier | sampled | intended MUT | not intended | rate |
|---|---:|---:|---:|---:|
| `T1_PROVEN` | 10 | 10 | 0 | 100% |
| `T2_CORROBORATED` | 5 | 5 | 0 | 100% |
| `T3_SINGLE_WEAK` | 10 | 10 | 0 | 100% |
| `T4_GUESS` | 20 | 10 | 10 | 50% |

Not-intended sampled cases:

| tier | project / test | picked method | assertion target | rationale |
|---|---|---|---|---|
| `T4_GUESS` | `urbanairship/java-library` `PayloadDeserializerTest.testIos10Extras` | `IOSMediaOptions.getTime` | crop `x` | Sibling option getter; the assertion checks crop coordinate, not media time. |
| `T4_GUESS` | `hampelratte/svdrp4j` `TimerParserTest.testRepeatingTimerStartingOnDay` | `Timer.getStartTime` | `getRepeatingDays()[0]` | Prior assertion checks start date; sampled assertion checks repeating-day array. |
| `T4_GUESS` | `urbanairship/java-library` `StyleTest.testInboxStyle` | `InboxStyle.getTitle` | `getSummary()` | Same object, wrong sibling getter. |
| `T4_GUESS` | `urbanairship/java-library` `SelectorDeserializerTest.testImplicitOR` | `ValueSelector.getValue` | `instanceof ValueSelector` | Picked a later value check for an assertion that only checks selector type. |
| `T4_GUESS` | `urbanairship/java-library` `StyleDeserializerTest.testInboxStyle` | `InboxStyle.getType` | `lines.get(0)` | Type getter is unrelated to the content-line assertion. |
| `T4_GUESS` | `urbanairship/java-library` `StyleDeserializerTest.testInboxStyle` | `InboxStyle.getType` | `lines.get(1)` | Type getter is unrelated to the content-line assertion. |
| `T4_GUESS` | `urbanairship/java-library` `PushPayloadBasicSerializationTest.testInAppMessage` | `InApp.getDisplayType` | display position | Sibling getter: display type was asserted earlier; sampled assertion checks nested position. |
| `T4_GUESS` | `nclarkekb/antiaction-common-json` `TestJSONStructureMarshaller_Converter.test_jsonobjectmapper_converter_toobject` | `JSONObjectMappings.getConverterNameId` | unmarshalled array length | Registration helper, not the converted-object assertion target. |
| `T4_GUESS` | `nclarkekb/antiaction-common-json` `TestJSONStructureMarshaller_Converter.test_jsonobjectmapper_converter_toobject` | `JSONObjectMappings.getConverterNameId` | unmarshalled integer field | Registration helper, not the converted-object assertion target. |
| `T4_GUESS` | `urbanairship/java-library` `PushPayloadBasicSerializationTest.testRichPush1` | `PushPayload.getMessage` | `RichPushMessage.getTitle` | Producer of the local message, but the assertion target is the message title getter. |

### Extraction-stage cost delta

Both DBs have `ANALYZE_TESTS`; the extraction-stage comparison therefore sums
`ANALYZE_TESTS`, `EXECUTE_JPF`, and `ANALYZE_JPF` across the same 23 projects.
`EXECUTE_TESTS_*` runtimes are excluded because per-class JVM forking was
removed after the baseline binary, so those runtimes are not comparable. The
seed-kill share is pending the validation-layer rerun.

| stage | baseline seconds | spike seconds | delta seconds |
|---|---:|---:|---:|
| `ANALYZE_TESTS` | 22.4 | 1,485.2 | +1,462.8 |
| `EXECUTE_JPF` | 58.8 | 157.0 | +98.3 |
| `ANALYZE_JPF` | 5.5 | 6.3 | +0.8 |
| **total** | 86.7 | 1,648.5 | +1,561.8 |

Cost per newly-attempted assertion is 0.21 seconds when using the
MissingValue-delta denominator (7,826 assertions).

### Input topology and R1/R2 gate

`actual_shape × receiver_provenance` in `mut_resolution_observation`:

| actual shape | receiver provenance | assertions | resolved |
|---|---|---:|---:|
| `NONE` | `NONE` | 6,298 | 19 |
| `SINGLE_CALL` | `LOCAL_OTHER` | 5,508 | 2,846 |
| `VARIABLE` | `NONE` | 4,222 | 2,318 |
| `SINGLE_CALL` | `FIELD` | 3,228 | 2,985 |
| `CHAINED_CALLS_END0ARG` | `LOCAL_OTHER` | 2,537 | 884 |
| `OPERATOR_COMPOSITE` | `NONE` | 2,394 | 1,810 |
| `SINGLE_CALL` | `LOCAL_CTOR_MUTATED` | 2,025 | 1,967 |
| `SINGLE_CALL` | `PARAM_OR_STATIC` | 1,953 | 1,682 |
| `SINGLE_CALL` | `LOCAL_CTOR` | 761 | 676 |
| `LITERAL` | `NONE` | 516 | 392 |
| `CTOR_ONLY` | `NONE` | 502 | 484 |
| `ARRAY_INDEX` | `NONE` | 470 | 445 |
| `CHAINED_CALLS_END0ARG` | `FIELD` | 470 | 349 |
| `CHAINED_CALLS_ENDNARG` | `PARAM_OR_STATIC` | 454 | 336 |
| `CHAINED_CALLS_END0ARG` | `LOCAL_CTOR_MUTATED` | 392 | 326 |
| `CHAINED_CALLS_ENDNARG` | `LOCAL_OTHER` | 225 | 65 |
| `CTOR_RECEIVER_CALL` | `INLINE_CTOR` | 191 | 150 |
| `CHAINED_CALLS_END0ARG` | `PARAM_OR_STATIC` | 181 | 102 |
| `CHAINED_CALLS_ENDNARG` | `FIELD` | 110 | 85 |
| `CHAINED_CALLS_END0ARG` | `LOCAL_CTOR` | 49 | 45 |
| `CHAINED_CALLS_ENDNARG` | `LOCAL_CTOR_MUTATED` | 33 | 0 |
| `SINGLE_CALL` | `NONE` | 24 | 18 |
| `CHAINED_CALLS_ENDNARG` | `LOCAL_CTOR` | 13 | 0 |
| `CHAINED_CALLS_END0ARG` | `INLINE_CTOR` | 2 | 2 |
| `CHAINED_CALLS_END0ARG` | `NONE` | 1 | 1 |
| `CHAINED_CALLS_ENDNARG` | `INLINE_CTOR` | 1 | 1 |

R1 expression-slice opportunity from the cross-tab classes is 7,364 assertions
(4,490 already resolved by attribution): `CHAINED_CALLS_END0ARG` 3,632,
`CHAINED_CALLS_ENDNARG` 836, `OPERATOR_COMPOSITE` 2,394, `CTOR_ONLY` 502.

R2 statement-slice gate, spike-scaled:

| receiver provenance | candidate param count | assertions | resolved |
|---|---:|---:|---:|
| `LOCAL_CTOR` | 0 | 307 | 307 |
| `LOCAL_CTOR` | 1 | 267 | 267 |
| `LOCAL_CTOR` | 2 | 66 | 66 |
| `LOCAL_CTOR` | 3 | 30 | 30 |
| `LOCAL_CTOR` | 5 | 6 | 6 |
| `LOCAL_CTOR` | null | 85 | 0 |
| `LOCAL_CTOR_MUTATED` | 0 | 704 | 704 |
| `LOCAL_CTOR_MUTATED` | 1 | 950 | 950 |
| `LOCAL_CTOR_MUTATED` | 2 | 277 | 277 |
| `LOCAL_CTOR_MUTATED` | 3 | 36 | 36 |
| `LOCAL_CTOR_MUTATED` | null | 58 | 0 |

The full-corpus gate in `2026-07-02-input-topology-spike` is >5k clean
`LOCAL_CTOR`-rooted zero-argument inspectors. The 23-project spike has 307
clean `SINGLE_CALL × LOCAL_CTOR × candidate_param_count=0` assertions and 704
mutated-local-ctor zero-arg assertions. Spike-scaled, this is not evidence to
prioritize R2 ahead of R1; keep T3 statement slicing out of scope until the
full-corpus rerun sizes it.

## Relationship to existing docs

- Design of the resolver: `2026-06-27-ensemble-mut-identification` (the oracle is
  the primary signal there; this audit supplies its targeting + coverage evidence).
- Corpus-wide barrier evidence: `2026-06-26-applicability-barriers` (this audit
  is the MUT-id- and oracle-coverage-specific deep dive).
- Reusable ranking: `analysis/src/teralizer/mut_id_targets.py`.
