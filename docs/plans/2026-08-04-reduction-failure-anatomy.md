---
title: Reduction-Stage Failure Anatomy and Run Cost
type: audit
status: active
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Reduction-Stage Failure Anatomy and Run Cost

Why the 31 reduction exclusions on `postgres_reporeapers_rq6` occur, and what a full
RepoReapers collection costs. Read from the stored diagnostics and the captured PIT
command logs on 2026-08-04. Companion evidence: `2026-08-04-oracle-refusal-taxonomy`.

## The 31 exclusions

Projects that reach reduction holding a validated generalized test and are still
excluded. The funnel groups these into three rows; the console failures behind the
`PIT execution error` row are heterogeneous, so they are decomposed here.

| Cause | Projects | Stage |
|---|---|---|
| PIT coverage minion dies at startup | 8 | `COLLECT_PIT_DATA_INITIAL` |
| PIT refuses a non-green suite | 6 | 4 at `_INITIAL`, 2 at `_GENERALIZED` |
| JaCoCo report absent | 8 | `COLLECT_JACOCO_DATA_INITIAL` |
| Mutation budget exceeded (3600 s) | 5 | `COLLECT_PIT_DATA_INITIAL` |
| Project pins an unusable `pitest-maven` | 2 | `COLLECT_PIT_DATA_INITIAL` |
| PIT minion connection lost | 1 | `COLLECT_PIT_DATA_INITIAL` |
| Teralizer cannot parse jqwik test identifiers | 1 | `COLLECT_PIT_DATA_GENERALIZED` |

28 of the 31 fail while collecting data for the `INITIAL` suite, that is for the
baseline a generalized suite would be compared against, so they bound what can be
measured rather than what can be generalized. None of them is a timeout in the sense
of the harness losing patience with generalization: the mutation budget accounts for
5, and the remaining 26 are distinct failures.

## Two of these families are self-inflicted, with one root

`MavenDependencyManager.mergeArgLine` (`src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java:393-398`)
prepends `@{argLine} ` to any static surefire `argLine` in the floored
`pom.teralizer.generalized.xml`, relying on Maven Surefire's late property
replacement. `pitest-maven` reads that configuration and passes it to its own coverage
minion verbatim, without late replacement, so the literal token becomes the JVM's main
class:

```
PIT >> INFO  : MINION : Error: Could not find or load main class @{argLine}
PIT >> SEVERE: Coverage generator Minion exited abnormally due to MINION_DIED
```

`github_com_astina_console` is among the 8. It is the project the JaCoCo argLine fix
was built for, so the change that recovered its coverage broke its mutation run.

The non-green-suite family shares the root cause. `NonPassingTestFilter` derives
pass/fail from the `ORIGINAL` suite executed against the *native* POM, while PIT
executes the `INITIAL` suite against the *floored* POM. Those environments do not
agree, and the Phase-0 spike already recorded that flooring surefire to 2.22.2 flips
outcomes (1 of 15 tests in astina). The consequence is visible per project:

| Project | Tests | `NonPassingTest` rejects | PIT reports not passing |
|---|---|---|---|
| `github_com_dicebot` (910) | 246 | 0 | 1 |
| `github_com_sshclient` (996) | 360 | 0 | 3 |
| `github_com_blurpy_kouchat` (919) | 1,218 | 126 | 146 |

Two projects had no failing test by our measurement and still failed PIT's unmutated
run. So the filter and the mutation run disagree by construction, not by flakiness.

The remaining families are external or narrow: two projects pin `pitest-maven` 0.24 and
0.30, which fail with `Cannot construct org.pitest.mutationtest.MutationCoverageReport`
and `failed: null`; one loses its minion to `PitError: Read timed out`; one hits
Teralizer's own report parser, which does not recognize
`SequenceMatcherCompilerTest.[engine:jqwik]`.

## Diagnostics cannot express any of this today

`TaskDiagnosticClassifier` returns `LISTENER_BUG` from its default branch, so every
failure above is stored under a code named for JPF listener faults, and the funnel needs
a `_fallback_cause` of "PIT reports not found" that is a guess rather than a reading.
Characterizing these 31 exclusions required reading raw command logs from disk. The next
collection will produce the same unreadable diagnostics unless the reduction stages get
real reason codes.

## Run cost

The collection ran serially over all 1,161 projects on an M2 MacBook Air.

```
started  2026-07-10T22:30:54Z      (data/detached/reporeapers-rq6.meta)
last log 2026-07-12T03:53Z         → 29 h 22 min wall clock
final    attempted=1161 skipped(already done)=0 gradle-nonzero=1070 capped=0
```

Recorded task time is 24.4 h of that; the rest is checkout, dependency resolution, and
driver overhead that no task owns.

| Stage group | Tasks | Hours | Slowest task |
|---|---|---|---|
| Reduction — PIT | 298 | 12.6 | 60.0 min (the budget) |
| Test execution | 371,476 | 5.8 | 5.0 min |
| Setup + build | 3,915 | 2.5 | 1.4 min |
| Specification extraction | 26,630 | 2.0 | 46.8 min |
| Other | 141,992 | 1.3 | 0.5 min |
| Reduction — JaCoCo | 313 | 0.1 | 0.1 min |

Successful PIT work is only 4.2 h of the 12.6 h: `INITIAL` p50 43 s, p90 326 s, max
1,686 s over 86 tasks; `GENERALIZED` p50 45 s, p90 255 s, max 902 s over 42 tasks. The
five budget-exceeded projects consume 5 h between them, more than all successful PIT
work combined. That cost is accepted: resource limits are part of the measurement, not
a defect to tune away.
