---
title: PVC Budget-Elasticity vs Mutation Score
type: audit
status: active
created: 2026-06-29
parent: 2026-06-26-teralizer-overview
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
| NAIVE_100_TRIES | 14 | 1325 | 54 | 87 | 0.621 |
| NAIVE_200_TRIES | 13 | 2937 | 54 | 87 | 0.621 |
| NAIVE_1000_TRIES | 13 | 16071 | 54 | 87 | 0.621 |
| IMPROVED_100_TRIES | 14 | 1281 | 54 | 87 | 0.621 |
| IMPROVED_200_TRIES | 14 | 2766 | 54 | 87 | 0.621 |
| IMPROVED_1000_TRIES | 13 | 13797 | 51 | 87 | 0.586 |

**PVC is budget-elastic; the covered mutation score is flat.** Total PVC rises
~10-12x from 100 to 1000 tries (NAIVE 1325 -> 16071, 12.1x; IMPROVED 1281 -> 13797,
10.8x), while the kill count holds at 54 and the covered score at 62% for every
variant. The one exception, `IMPROVED_1000_TRIES` (51 kills, 58.6%), is an excluded
probe (below), not a detection change. Extra tries buy input diversity, not fault
detection.

**Denominator: covered, not project-wide.** PIT mutates the whole project (2953
mutants), but the generated tests reach only the 10-14 probe methods, so 2866 are
`NO_COVERAGE` -- code the probes never touch. Scoring against all 2953 gives a
meaningless 1.8%. The meaningful denominator is the 87 *covered* mutants the tests
actually reach: 54 killed + 33 survived -> 62%. The 33 survived are the genuine
quality gap (covered code where neither generator's oracle catches the mutation), and
they are flat across the budget too. The JARVIS MUTs are tiny (`isAscii`, `min`/`max`,
`abs`, ...), so the covered score does not discriminate NAIVE from IMPROVED -- both
kill the same 54 of 87.

### Per-probe PVC scales with the budget

Each non-excluded probe's PVC grows with tries; the rate tracks the parameter's
domain, not test quality. NAIVE, 100 -> 1000 tries:

| probe | 100 | 200 | 1000 | growth |
|---|---|---|---|---|
| intervalGetSize | 88 | 260 | 1771 | ~20x |
| precisionEquals | 252 | 724 | 5011 | ~20x |
| maxDouble | 141 | 330 | 1800 | ~13x |
| toIntExact | 93 | 182 | 845 | ~9x |
| isAscii | 159 | 290 | 1069 | ~7x |

A metric that swings 7-20x on the same probe with the same generator, purely from the
sampling budget, is measuring the input space rather than fault-finding power -- the
kill count for these probes is unchanged across the budget.

## Exclusions at high tries

The sweep is not exclusion-free; three probes drop out at higher budgets, each a known
limitation surfaced by deeper sampling (prior runs only reached 200 tries):

- `isAsciiPrintable` in `NAIVE_200_TRIES` and `NAIVE_1000_TRIES`: jqwik
  `TooManyFilterMissesException`. NAIVE's random+filter exhausts the miss budget for
  the sparse printable-char precondition -- the filter-based generation limit.
  IMPROVED's by-construction generation passes it at every budget.
- `precisionEquals` in `IMPROVED_1000_TRIES`: `AssertionError`. The documented
  `Precision.equals` eps-soundness edge surfaces at 1000 tries; the unsound
  generalization fails its own oracle and is correctly excluded (fail-loud), which is
  why kills drop 54 -> 51.

Both are real findings consistent with the documented generator limits, not
regressions. The NAIVE filter-miss strengthens the by-construction case; the IMPROVED
exclusion shows the fail-loud design catching an unsound generalization rather than
silently passing it.

## Implication

Covered mutation score -- the RQ1 fault-detection signal on the code the tests reach
-- is invariant to the sampling budget (62%, flat; the project-wide 1.8% is the
discarded denominator), while PVC inflates monotonically with it. PVC therefore
measures input diversity, not fault-detection power, and overstates effectiveness as
the budget
grows. IMPROVED's value over NAIVE is path-exactness, fail-loud soundness, and
low-budget efficiency -- not raw diversity, which NAIVE matches given enough tries.
This is point-in-time evidence (jqwik sampling varies run to run); the qualitative
result -- PVC budget-elastic, kills flat -- is robust.
