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

## Phase 0: JaCoCo-reduction spike (in progress)

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

## Phase 1: PIT timeout calibration (uncensored smoke)

- Run one or two reduction-reaching projects (e.g. JadConfig) through the current pipeline with
  PIT **on** and a deliberately generous cap (e.g. 3600 s) so the measurement is uncensored.
- Read actual INITIAL/GENERALIZED PIT `runtime`; set `pitest.max-execution-time` to a percentile
  that keeps timeouts a genuine minority (not ~35%), and set `REPOREAPERS_PROJECT_TIMEOUT` to
  comfortably exceed `2 x cap` + JaCoCo + the generalization pass.

## Phase 2: Full-pipeline PIT collection

New profile `project-configs/reporeapers-rq6.conf` (composed per-project like the rerun):

```hocon
teralizer {
  pitest {
    enabled = true              # global PIT on: the GENERALIZED PIT stage runs and its
                                # failures surface as Stage-5 exclusions. With PIT off the
                                # stage is skipped + recorded succeeded (no Stage-5 PIT signal).
    max-execution-time = <from Phase 1>
    original.enabled = false    # only INITIAL + GENERALIZED mutation consumed. ORIGINAL JaCoCo
                                # still runs (JacocoDataCollectionTask has no PIT gate) as the
                                # Stage-5 diagnostic (#12/#17).
  }
  generalizations {
    IMPROVED_200_TRIES { algorithm = "IMPROVED", jqwik { tries = 200 } }
  }
}
```

Kept from `reference.conf` (paper-aligned): `junit.max-execution-time = 60` (paper #3/#10);
generalized-suite scaled budget (calibrated, current improvement). `reference.conf` predefines no
variants, so `IMPROVED_200_TRIES` is the only one (no HOCON-merge accumulation).

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
2. **`pitest.max-execution-time`** — set from Phase 1, not the paper's 300 s (which clips ~35%).
3. **`REPOREAPERS_PROJECT_TIMEOUT`** — from Phase 1 wall-time observation.
4. **Canonical DB naming / promotion** — collect into `postgres_reporeapers_rq6`, decide final
   name after validation.

## Tasks

### Phase 0: JaCoCo spike
- [x] Add `REPOREAPERS_PROFILE` override to `run-reporeapers-rerun.sh`.
- [x] Write `reporeapers-jacoco-spike.conf` (reduction on, PIT off, IMPROVED_200).
- [x] Launch spike on 7 old-failing + 1 control into `postgres_jacoco_spike`.
- [ ] Read per-project JaCoCo outcomes; diagnose fails; fix fixable pipeline causes; classify genuine.
- [ ] Drop the spike DB.

### Phase 1: PIT timeout calibration
- [ ] PIT smoke (PIT on, generous cap) on a reduction-reaching project; record real PIT runtimes.
- [ ] Set `pitest.max-execution-time` and `REPOREAPERS_PROJECT_TIMEOUT`.

### Phase 2: Full collection (sign-off gate)
- [ ] Write `reporeapers-rq6.conf` (PIT on, original off, calibrated cap, IMPROVED_200).
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
