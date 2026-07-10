---
title: RQ6 Stage-5 Collection and Funnel Rebuild
type: plan
status: active
created: 2026-07-10
parent: 2026-07-08-evaluation-analysis-redesign
---

# RQ6 Stage-5 Collection and Funnel Rebuild

**Goal:** Deliver a valid RQ6 that reproduces the paper's Table 5.12 five-stage funnel — through
**Stage 5 (Test Suite Reduction)** — on the *current* pipeline, with a corrected survivorship
funnel and `generalization_lifecycle.final_usable` as the success metric. This supersedes the
gen-only applicability funnel that only covered Stages 1-4.

## Why this plan exists (findings)

Discovered while auditing the gen-only RQ6 output:

1. **The funnel success metric was wrong.** `_funnel.py:219` defined success as "eligible project
   with no failed project-level task" (133/611, 21.8%). That is a task-failure proxy, not
   generalization success. Direct DB truth: only projects with a usable generalization succeed.
2. **`is_included` is not a reliable "usable" flag.** It means "created with a sound spec, not
   filter-rejected" (944 gens / 84 projects). But 36 of those never compiled/executed — they stay
   `is_included=true` because `AbstractTask.markExcluded` only flips per-record failures, and
   generalized build/execute is project-scoped. The reliable flags come from
   `generalization_lifecycle`: `generated_filter_passed` (908 gens / 72 projects, compiled +
   executed + filtered) and **`final_usable`** (through reduction, incl. PIT).
3. **Stage 5 needs reduction + PIT, which the gen-only corpus lacks entirely.** `final_usable`
   requires `generated_pit_collected` (`GeneralizationLifecycleWriter:129-131`), so it is 0 across
   the `--no-reduction` corpus. Both alternatives are ruled out: `postgres_reporeapers` (gen-only)
   has no Stage 5; `postgres_test` (paper v1, `IMPROVED_200`, full PIT, reproduces 11/632) is
   ~2-week-stale code. So a fresh full-pipeline + PIT collection on current code is required.
4. **The paper's 300 s PIT timeout clips ~1/3 of the reduction corpus.** In `postgres_test`,
   INITIAL PIT: 40 of 64 failures were at the 300 s cap — 40/114 Stage-5 projects (~35%) timed
   out. Not a tail. The cap must be set from real uncensored durations, not copied.
5. **JaCoCo failures shadow PIT.** JaCoCo runs before PIT in reduction, so the paper's 41
   "JaCoCo outputs not found" (`Report file '.../jacoco.csv' does not exist`) projects are excluded
   at Stage 5 before PIT ever runs — inflating exclusions and hiding true PIT behavior. Many are
   Android/Volley forks (likely un-instrumentable); others (snakeyaml, plain Maven) may be fixable.

### PIT duration reality (task.runtime, seconds, SUCCEEDED — note the 300 s censoring)

| Corpus | GENERALIZED p50 / p90 / max | INITIAL p50 / p90 / max |
|---|---|---|
| RepoReapers (`postgres_test`, IMPROVED_200, 300 s cap) | 91 / 145 / 152 | 31 / 143 / 291 (40 fail at cap) |
| Census (commons, big, IMPROVED_100, 3600 s cap) | 285 / 1116 / 1467 | 90 / 886 / 1375 |
| Controlled (`postgres_dev`, few huge projects) | 3396 / 9738 / 23847 | 817 / 3452 / 3455 |

## Phase 0: JaCoCo-reduction spike (complete)

Reproduce the reduction-phase JaCoCo failures on the *current* restored-build pipeline; separate
fixable pipeline causes from genuinely un-instrumentable projects.

- Profile `project-configs/reporeapers-jacoco-spike.conf`: reduction on, **PIT off**,
  `IMPROVED_200_TRIES`.
- Runner made profile-overridable: `run-reporeapers-rerun.sh:39` now honours
  `REPOREAPERS_PROFILE`.
- Targets (temp `REPOREAPERS_CONFIG_DIR`, scratch DB `postgres_jacoco_spike`): 7 old-failing
  projects (`project-3` snakeyaml, `-142` astina_console, `-263` tabula-java, `-721`
  mcxiaoke_android-volley [expected-genuine], `-761` geophile_erdo, `-1037` webbit, `-1049`
  jline2) + `project-974` JadConfig (proven reduction control — SUCCEEDED JaCoCo + PIT in
  `postgres_test`).
- Launched detached as `jacoco-spike` (`scripts/detached-run.sh`).
- Read per project: do ORIGINAL/INITIAL/GENERALIZED JaCoCo succeed now? For fails, diagnose why
  `jacoco.csv` is absent (empty `.exec`, plugin/goal error, Android). Fix fixable pipeline causes;
  flag genuinely un-instrumentable projects as legitimate Stage-5 exclusions.

**Findings (spike + manual fix spike, validated):**
- Control JadConfig: ORIGINAL/INITIAL/GENERALIZED JaCoCo all SUCCEEDED. Three targets (astina,
  geophile, webbit) FAILED at `COLLECT_JACOCO_DATA_ORIGINAL` on current code; four never reached
  reduction. Root cause (built-in grep): each failing POM hardcodes a surefire `<argLine>` (astina
  `-Dfile.encoding`, geophile `-ea -Xmx...`, webbit `-Xmx1024m`) that overrides JaCoCo's `argLine`
  property, so `-javaagent:jacocoagent` never attaches -> no `.exec` -> `jacoco:report` finds no
  CSV. Control JadConfig has no surefire `argLine`. So a large share of the paper's 40 "JaCoCo
  outputs not found" exclusions are fixable pipeline issues, not genuine un-instrumentability.
- Manual fix spike on astina (scratch POM in the checkout, removed after): surefire floored
  2.15 -> 2.22.2 and `<argLine>@{argLine} -Dfile.encoding=...</argLine>` -> agent attached,
  `jacoco.exec` (40 KB) and `jacoco.csv` (4.3 KB) produced (both previously absent). `@{argLine}`
  resolved on 2.22.2.
- Semantic shift confirmed + bounded: flooring flips 1/15 astina tests to failing under 2.22.2.
  `TestExecutionTask:122-145` tolerates "There are test failures." (logs, does not fail the stage)
  and the `.exec` is produced regardless, so the project is recovered rather than excluded earlier;
  INITIAL and GENERALIZED stay floored-vs-floored consistent.

**Implemented fix (built at ADD_DEPENDENCIES):** native `pom.teralizer.xml` serves ORIGINAL only;
the floored `pom.teralizer.generalized.xml` (surefire >= 2.22.2 + JaCoCo agent merged via
`@{argLine}`, preserving the project's own argLine flags) serves INITIAL + GENERALIZED.
`AbstractTask.mavenBuildFileFor` selects the POM by stage (ORIGINAL stages -> native,
INITIAL/GENERALIZED stages -> floored), keyed on stage because ORIGINAL and INITIAL both carry a
null variant; `JacocoDataCollectionTask` / `PitDataCollectionTask` / `TestExecutionTask` route
through it. `COLLECT_JACOCO_DATA_ORIGINAL` is skipped when ORIGINAL PIT is off (its only consumer,
and it runs first in reduction). The argLine merge is unconditional. The spike and unit tests
(`MavenSurefireFloorTest` argLine cases, `AbstractTaskBuildFileTest` routing) validate explicit
surefire pins. Versionless or property pins are not tested and rely on the effective POM resolving
to a surefire that supports `@{argLine}` (Maven 3.9.11 defaults to 3.x), an accepted assumption.

**Recovery spike (`postgres_jacoco_spike`, reduction on, PIT off, IMPROVED_200):** astina and
webbit, which produced zero coverage before, now emit INITIAL + GENERALIZED coverage (98 and 111
`jacoco_coverage_report` rows); control JadConfig steady at 202. geophile does not recover for a
separate reason: its instrumented INITIAL suite exceeds the 60 s `junit.max-execution-time` ceiling
(it passed natively without the agent in the first spike), so `EXECUTE_TESTS_INITIAL` times out.
Per the execution-ceiling policy that is a legitimate exclusion, not a fix defect — the agent stays
attached and the slow suite is excluded like any other over-ceiling suite.

## Phase 1: PIT timeout calibration (resolved by the unified timeout policy)

Superseded by the unified timeout-budget work (`92704acf` and the timeout-policy commits). That
refactor removed the per-stage `*.max-execution-time` keys and replaced them with a single
stage-keyed budget schema (`junit.timeout.{original,initial,generalized}`,
`pitest.timeout.{original-initial,generalized}`) whose defaults were calibrated data-driven from
the JARVIS census plus a four-project PIT calibration run. The PIT cap landed at **3600 s** for
both INITIAL and GENERALIZED.

That 3600 s cap is generous for RepoReapers rather than clipping: this plan's own duration table
puts RepoReapers PIT at p90 ~145 s / max ~291 s (censored at the paper's 300 s), an order of
magnitude below the cap, so it cannot repeat the paper's ~35% clip. A separate RepoReapers PIT
smoke was considered and declined — 3600 s is >20x the observed tail. `REPOREAPERS_PROJECT_TIMEOUT`
is set at launch to ~14400 s, comfortably exceeding `2 x 3600` PIT + JaCoCo + the generalization
pass.

## Phase 2: Full-pipeline PIT collection

New profile `project-configs/reporeapers-rq6.conf` (composed per-project like the rerun). It is
minimal because the unified timeout policy already carries the calibrated budgets and
`pitest.original.enabled = false` in `reference.conf`; the profile only turns global PIT on and
declares the single variant:

```hocon
teralizer {
  database { name = "postgres_reporeapers_scratch" }
  pitest {
    enabled = true              # global PIT on: the GENERALIZED PIT stage runs and its failures
                                # surface as Stage-5 exclusions. With PIT off the stage is skipped
                                # + recorded succeeded (no Stage-5 PIT signal).
  }
  generalizations {
    IMPROVED_200_TRIES { algorithm = "IMPROVED", jqwik { tries = 200 } }
  }
}
```

Inherited from `reference.conf`: the stage-keyed budgets (`junit.timeout` 300/300/1800,
`pitest.timeout` 3600/3600) and `pitest.original.enabled = false` -- so ORIGINAL PIT and its
leading JaCoCo step stay off and Stage-5 diagnostics come from INITIAL + GENERALIZED. `reference.conf`
predefines no variants, so `IMPROVED_200_TRIES` is the only one (no HOCON-merge accumulation).

- Reduction on (no `--no-reduction`).
- Fresh scratch DB `postgres_reporeapers_rq6`; prior DBs preserved.
- Launch detached + `caffeinate`. Multi-day (PIT on ~100+ reduction-reaching projects).
- Measurement event: runs once, first-run numbers stand, gated on sign-off.

## Phase 3: Funnel rebuild + RQ6 regeneration

Rebuild `_funnel.py` on the survivorship model (entity-survivorship, first stage whose surviving
set is empty), all five stages:

| Stage | Survives if the project still has... |
|---|---|
| 1 + 2 (Project Analysis) | >=1 included test AND >=1 statically-filter-surviving assertion |
| 3 (Spec Extraction) | >=1 spec-surviving (included) assertion |
| 4 (Gen Test Creation) | >=1 `generated_filter_passed` generalization |
| 5 (Test Suite Reduction) | >=1 `final_usable` generalization |

- `success_count` = eligible projects with >=1 `final_usable` generalization (through reduction).
- Eligibility pre-filter stays task/stage-based (SETUP_PROJECT / ADD_DEPENDENCIES /
  BUILD_PROJECT_ORIGINAL failures = ineligible).
- Per-cause rows enumerate causes (all-X-excluded, Spoon/JUnit/timeout, JaCoCo/PIT) and must
  reconcile to the survivorship bands.
- Regression test: `success_count == COUNT(project with >=1 final_usable generalization)`; bands
  sum to `eligible`; no `UNCODED`; every cause typed and positive — the real invariant the prior
  "arithmetic consistency" test failed to assert.
- Regenerate `analysis/reports/rq6.md` (uncommitted per standing directive); confirm five stages
  with real PIT/JaCoCo causes. `pytest tests/eval` green. Paper reference 632->130->117->114->11
  (1.7%) is a shape/sanity check, not a target — current levers + IMPROVED_200 shift it.

## Databases

- `postgres_reporeapers` — gen-only, Stages 1-4 applicability (current canonical, will be
  superseded for RQ6 by the full-PIT DB).
- `postgres_reporeapers_invalid_jacoco` — pre-fix gen-only data, audit baseline for the
  44-project delta.
- `postgres_test` — paper v1 RepoReapers, `IMPROVED_200`, full PIT. Reference only (stale code),
  read-only.
- `postgres_jacoco_spike` — Phase 0 scratch (throwaway).
- `postgres_reporeapers_rq6` — Phase 2 target (to be created).

## Open decisions

1. **JaCoCo per-variant timeout** — the paper's #13 "300 s per test-suite variant" could be read
   as covering JaCoCo too, but `JacocoDataCollectionTask` has no per-invocation cap. Recommend
   leaving JaCoCo bounded by the project cap (the timeout cause is mutation-driven); add
   `jacoco.max-execution-time` only if exact fidelity is wanted.
2. **PIT cap** — resolved: the unified `pitest.timeout` (3600 s, both stages) replaces the removed
   `pitest.max-execution-time`. Generous for RepoReapers (>20x the observed tail), so it avoids the
   paper's 300 s ~35% clip.
3. **`REPOREAPERS_PROJECT_TIMEOUT`** — ~14400 s at launch (exceeds `2 x 3600` PIT + JaCoCo + gen).
4. **Canonical DB naming / promotion** — collect into `postgres_reporeapers_rq6`, decide final
   name after validation.

## Tasks

### Phase 0: JaCoCo spike
- [x] Add `REPOREAPERS_PROFILE` override to `run-reporeapers-rerun.sh`.
- [x] Write `reporeapers-jacoco-spike.conf` (reduction on, PIT off, IMPROVED_200).
- [x] Launch spike on 7 old-failing + 1 control into `postgres_jacoco_spike`.
- [x] Read per-project JaCoCo outcomes; diagnose fails; implement + prove the argLine fix; classify genuine.
- [x] Drop the spike DB.

### Phase 1: PIT timeout calibration
- [x] Resolved by the unified timeout policy: `pitest.timeout` = 3600 s (both stages), calibrated
      from the census + PIT calibration run; generous for RepoReapers, no separate smoke needed.
- [x] `REPOREAPERS_PROJECT_TIMEOUT` ~14400 s at launch.

### Phase 2: Full collection (sign-off gate)
- [x] Write `reporeapers-rq6.conf` (PIT on, original off inherited, unified 3600 cap, IMPROVED_200).
- [ ] `./gradlew build` green.
- [ ] Launch full run detached into `postgres_reporeapers_rq6`; monitor to completion.

### Phase 3: Funnel rebuild
- [ ] Rebuild `_funnel.py` on survivorship (five stages, `final_usable` success); regression test.
- [ ] Regenerate `rq6.md`; confirm five stages; `pytest tests/eval` green.
- [ ] Decide canonical DB naming/promotion.

## Acceptance criteria

- `postgres_reporeapers_rq6` holds a full-pipeline `IMPROVED_200_TRIES` run with reduction stages,
  `pit_mutation_report` populated (INITIAL + GENERALIZED), and real `generalization_lifecycle`
  `final_usable` values.
- RQ6 reproduces the five-stage funnel including Stage 5, `final_usable` as success, from
  structured lifecycle signals (no free-text regex in the production path).
- Fixable JaCoCo-reduction failures fixed; genuinely un-instrumentable ones classified as
  legitimate Stage-5 exclusions.
- Full `pytest tests/eval` and ruff/ty clean.

## Prior commits (context)

Baseline-JaCoCo pipeline fix `750191de`/`b8edca5e`/`3355005d`; funnel ty-fix + spec alignment
`7c0f7066`/`c6c72316`; earlier plan archival `fa9b3d3b`.
