---
title: Census Completion — Generated-Build Robustness & PIT Scale
type: spec
status: draft
created: 2026-06-30
parent: 2026-06-29-beyond-jarvis-generalization-census
---

# Census Completion — Generated-Build Robustness & PIT Scale

## Context

The first beyond-JARVIS census (`2026-06-29-beyond-jarvis-census-implementation`) ran but did
not complete cleanly. After the listener-correctness fixes (I1 boxed-value capture and P1 typed
return-attr read; P2 was investigated and found unnecessary — see
`2026-06-29-beyond-jarvis-census-findings`), two completion blockers remain. This spec defines
each precisely and recommends an approach.

- **I2** — one malformed generated test fails the *entire* variant build.
- **I3** — PIT mutation testing times out at census scale.

The I1 fix removes the boxed-value codegen bug that triggered I2 in the first run, so **I2 is now
defense-in-depth**: it stops *any* future uncompilable generated file from sinking a whole variant,
rather than being the immediate unblocker.

## I2 — Generated-build robustness

### Problem

`ProjectBuildTask.buildGradle` / `buildMaven` compiles the entire generated suite in one atomic
step (`./gradlew … clean compileJava compileTestJava`, or `mvn … clean compile test-compile`).
`TestGeneralizationTask` physically writes every generated test to `generalization.file_path` in
the project's test source tree. A single generated file that does not compile fails
`compileTestJava` / `test-compile`, which fails the whole `BUILD_PROJECT_GENERALIZED` stage and
drops **all** generalizations for that variant — plus the downstream execution/JaCoCo/PIT. In the
census's commons-lang `IMPROVED_100` run, one malformed file (the I1 boxed-Integer case) lost the
entire variant; `CENSUS_EXIT=0` hid it because the pipeline swallows per-task failures.

### Recommended approach: validate-and-quarantine before the variant build

Validate each generated test in isolation, then exclude the ones that don't compile **both in the
database and on disk**, so the batch build only ever sees valid files.

1. **Validate** the generated tests with an in-process compile check
   (`javax.tools.JavaCompiler`) against the project's test classpath. Feed all generated test
   files in a single invocation and collect per-file `Diagnostic`s — structured, no build-output
   parsing, per-file attribution, and it catches semantic/type errors, not just syntax.
2. **Quarantine** every generalization whose file has an `ERROR` diagnostic:
   - mark `generalization.is_included = false` with an `exclusion_info` reason, consistent with the
     filter mechanism in `TestFilteringTask` / `NonPassingTestFilter`; **and**
   - **remove the `.java` from the build source set** (skip the write, or delete/rename before the
     build). Flagging the row alone is insufficient: `ProjectBuildTask` compiles the whole test
     tree regardless of `is_included`. The data-dir provenance copy
     (`…/teralizer-data/tests/<variant>/…`) is kept.
3. **Build** the variant — now guaranteed to compile.

This isolates failures to the single offending test and yields a clean, queryable per-generalization
exclusion reason (the same shape downstream analysis already understands).

#### Alternatives considered

- **Parse-only validation** (Spoon/JavaParser, no classpath): cheaper and catches syntax errors
  (the I1 class), but misses semantic/type errors. Acceptable as a fast fallback only if the
  validation-compile cost proves prohibitive.
- **Resilient batch build** (run the build, parse gradle/maven/ant compiler output to find and
  exclude failures, rebuild): fragile across build systems and slower (repeated full builds).
  Rejected.

### Open question

- **Where to run validation:** inside `TestGeneralizationTask` as each test is generated (natural
  per-generalization exclusion, but compiles incrementally), or as a dedicated pre-build pass over
  all generated files for the variant (one compile invocation, simpler classpath story). The
  pre-build pass is the leaning recommendation; confirm during implementation.

### Acceptance criteria

- A deliberately uncompilable generated test is excluded with `is_included = false` + an
  `exclusion_info` reason and its file is absent from the build source set; the rest of the variant
  compiles, runs, and produces generalization data.
- Validation runs at most once per variant build (no per-file build spawn).
- Provenance copies under the data dir are unaffected.

## I3 — PIT at census scale

### Problem

`PitDataCollectionTask.executeMutationTesting` sets `targetClasses` to **all** JaCoCo-covered
classes from `COLLECT_JACOCO_DATA_INITIAL` (`SQLiteRepository.fetchCoveredClasses`), passed to PIT
via `-PtargetClasses=` (Gradle) or the POM (Maven). On the JARVIS-10 scorecard that is a handful of
classes; at census scale it is every class transitively covered by the full commons-math / lang
suites — hundreds. PIT then mutates and runs against far more classes than are under test. The whole
PIT invocation is killed by the `ConsoleCommand` wall-clock timeout
(`pitest.max-execution-time = 300s`, set in the task constructor and enforced at
`PitDataCollectionTask:185`); the census hit exactly this, after which the variant's remaining tasks
were dropped and no mutation-gain data was produced.

### Recommended approach: scope PIT to the classes under test, plus a configurable backstop

1. **Scope `targetClasses` to the classes actually under test** — the distinct `tested_class` of
   the included tests/generalizations — rather than all JaCoCo-covered classes. This is the
   meaningful mutation denominator (mutation score on the CUTs, per
   `2026-06-29-pvc-budget-elasticity`; aligns with `2026-06-28-mut-id-targeting-and-coverage`), and
   it collapses PIT's work from hundreds of classes to the tens under test. **Preserve the existing
   INITIAL == GENERALIZED `targetClasses` consistency** (the comment at `PitDataCollectionTask:130`):
   both stages scope to the same CUT set, so the mutation-gain set difference stays comparable.
2. **Parameterize the timeout** as a backstop. `pitest.max-execution-time` is the wall-clock kill
   for the whole invocation; mutation testing over many CUTs is inherently slow even when scoped, so
   census configs need a larger value. It already reads from HOCON, so set a generous per-config
   census value.

#### Alternatives considered

- **Raise the timeout only** (no scoping): may finish but can take hours over full coverage, and
  still measures mutation on transitively-covered library classes that are not the test target —
  less meaningful. Insufficient alone.
- **Trim the allowlist** (drop FastMathTest / MathArraysTest): a census-config workaround that
  reduces the evidence rather than fixing the cost. Out of scope here.

### Open question

- **CUT source:** scope to `tested_class` from the assertion/generalization rows, or reuse the
  MUT-identification work (`2026-06-28-mut-id-targeting-and-coverage`) if it already yields a precise
  per-test class set. Confirm which query is authoritative during implementation.

### Acceptance criteria

- Census PIT (`COLLECT_PIT_DATA_GENERALIZED` and `_INITIAL`) over commons-math completes within the
  configured timeout and produces mutation data.
- `targetClasses` is the CUT set, identical between INITIAL and GENERALIZED, so the mutation-gain
  metric (`jarvis_scoreboard --census`) populates and stays comparable.
- The timeout is configurable per census config.

## Sequencing & out of scope

- Land **I2** before the next census rerun (so a stray bad file cannot sink a variant), then **I3**,
  then one full census rerun to validate end-to-end (tracked in the findings note).
- Out of scope: the larger spf-eval ports (P3/P4/P5), the `reference.conf` variant leak (I4, already
  mitigated by report scoping), and the mutation-gain metric itself (already implemented).
