---
title: PVC Budget-Elasticity vs Mutation Score
type: audit
status: superseded
created: 2026-06-29
parent: 2026-06-26-teralizer-overview
superseded_by: 2026-06-30-jarvis-comparison
archived: 2026-06-30
---

# PVC Budget-Elasticity vs Mutation Score

The rejected paper's RQ1 leaned on parameter-value coverage (PVC) -- the count of
distinct input values a generalized test exercises -- as an effectiveness signal. PVC
is a count of distinct sampled values, so it should scale with jqwik's `tries` budget
whether or not the extra inputs find faults. This audit measures that: run both
generators across a 100/200/1000 tries sweep on the JARVIS fixtures and compare PVC to
PIT mutation kills.

## Method

Six variants -- `NAIVE` and `IMPROVED`, each at 100/200/1000 `jqwik.tries` -- declared
in `project-configs/jarvis-scoreboard/{commons-math,commons-lang}-3.5.conf`, run on a
freshly reset `postgres_jarvis_scoreboard` scratch DB (fixtures pinned at commons-math
`MATH_3_5` / commons-lang `LANG_3_5`). Mutants are counted distinct by stable PIT
identity from `COLLECT_PIT_DATA_GENERALIZED`; covered = killed + survived (the
mutants the tests reach), excluding `NO_COVERAGE`/`NON_VIABLE`. Point-in-time run
2026-06-29.

Reproduce:

```
DB_NAME=postgres_jarvis_scoreboard DATA_DIR=data/jarvis-scoreboard \
  DATASET_VARIANT=jarvis bash scripts/run-jarvis-scoreboard.sh
uv run --directory analysis python -m teralizer.jarvis_scoreboard --sweep
```

## Result

| variant | probes | total PVC | killed | covered | covered score |
|---|---|---|---|---|---|
| NAIVE_100_TRIES | 12 | 1073 | 51 | 78 | 0.654 |
| NAIVE_200_TRIES | 12 | 2245 | 51 | 78 | 0.654 |
| NAIVE_1000_TRIES | 12 | 11092 | 51 | 78 | 0.654 |
| IMPROVED_100_TRIES | 12 | 1120 | 51 | 78 | 0.654 |
| IMPROVED_200_TRIES | 12 | 2257 | 51 | 78 | 0.654 |
| IMPROVED_1000_TRIES | 12 | 11095 | 51 | 78 | 0.654 |

**PVC is budget-elastic; the covered mutation score is flat.** Total PVC rises ~10x from 100
to 1000 tries (NAIVE 1073 -> 11092, 10.3x; IMPROVED 1120 -> 11095, 9.9x), while the kill count
holds at 51 and the covered score at 65.4% for every variant -- flat across the whole budget,
both generators. Extra tries buy input diversity, not fault detection.

**Denominator: covered, not project-wide.** PIT mutates the whole project (2953 mutants), but
the generated tests reach only the 12 probe methods, so 2875 are `NO_COVERAGE` -- code the
probes never touch. Scoring against all 2953 gives a meaningless 1.7%. The meaningful
denominator is the 78 *covered* mutants the tests actually reach: 51 killed + 27 survived ->
65.4%, flat across the budget. The JARVIS MUTs are tiny (`isAscii`, `min`/`max`, `abs`, ...),
so the covered score does not discriminate NAIVE from IMPROVED -- both kill the same 51 of 78.

**Per-probe, the same pattern.** Each probe's PVC grows with the budget (roughly 7-20x from 100
to 1000 tries, the rate tracking the parameter's domain, not test quality), while its kill
count is unchanged. Exact per-probe figures belong in the analysis notebook, not hand-copied
here.

## Why the covered gap is structural, not a generator bug

The 27 covered-but-unkilled mutants are not reachable by path-exact generalization: boundary /
comparison flips, removed defensive checks, and arithmetic on edge paths. `toIntExact` is
illustrative -- the generated filter `n > MIN && n < MAX` is strict, not inclusive
`[MIN, MAX]`, which looks like a dropped-equality bug but is correct. `lcmp` is tri-state, and
`jpf-symbc`'s `LCMP` handler records the *concrete* outcome as its own symbolic path: for the
original input `n = 7`, `n vs MIN -> GT` and `n vs MAX -> LT`, so the path condition is the
strict `n > MIN && n < MAX` -- exactly the path that input took. The equality endpoints
(`n == MIN`/`MAX`), where the boundary mutant flips, are the `EQ` choice: a *different*
symbolic path the test never executed (collect-constraints mode records only the executed
outcome). The killing input is off the generalized path. So none of the survivors is an
in-scope generator fix: reaching them needs new *original* tests on the boundary / invalid-input
paths, or stronger assertions -- both outside "generalize the existing test." The gap is a
property of path-exact generalization and oracle strength, not a defect. The eps
`precisionEquals` probe is separately sound-excluded (raw bits; see `2026-06-26-jarvis-case-coverage`).

## Implication

Covered mutation score -- the fault-detection signal on the code the tests reach -- is
invariant to the sampling budget (65.4%, flat), while PVC inflates ~10x with it. PVC therefore
measures input diversity, not fault-detection power, and overstates effectiveness as the budget
grows. IMPROVED's value over NAIVE is path-exactness, fail-loud soundness, and low-budget
efficiency -- not raw diversity, which NAIVE matches given enough tries. jqwik sampling varies
run to run, so treat exact PVC totals as representative; the qualitative result -- PVC
budget-elastic, kills flat -- is robust.
