---
title: Beyond-JARVIS Generalization Census
type: spec
status: draft
created: 2026-06-29
parent: 2026-06-26-teralizer-overview
---

# Beyond-JARVIS Generalization Census

## Problem

The JARVIS scoreboard runs Teralizer against exactly the 10 hand-picked
commons-lang/commons-math cases in JARVIS Table-2. That proves Teralizer *matches*
JARVIS on JARVIS's own ground, but says nothing about reach: how many sound property
generalizations Teralizer produces from these two projects' **own** test suites that
JARVIS never attempted. This census measures that reach within the same two projects —
a Beat-JARVIS strengthener (strategy step 1), distinct from the broader full-corpus
re-run that the overview gates on step 1 landing.

The claim to support: *Teralizer soundly generalizes N properties across M
commons-lang/commons-math test classes that JARVIS never attempted* — exclusion-honest,
with the rejection reasons for everything that does not generalize as the other half of
the result.

## Current setup (what we build on)

- `scripts/prepare-jarvis-scoreboard-fixtures.sh` pins the upstream repos at exact SHAs
  in `data/jarvis-scoreboard/source-cache/{commons-lang,commons-math}`
  (`LANG_3_5` = `36f98d87b24c2f542b02abbf6ec1ee742f1b158b`,
  `MATH_3_5` = `b3c5dae8f253fcb4484e5cd3cc5662587803efc2`). The full upstream test suites
  are already cached there.
- For each project the script writes a minimal `pom.xml` (only `junit:4.12`), copies
  `src/main` from the cache, and writes a hand-authored `Jarvis{Lang,Math}ScorecardTest`
  — small loop-free scorecard methods (1-2 assertion probes each) replicating the 10 Table-2 rows in a `jarvis`
  package. The upstream `*Test.java` are cached but never promoted into the fixture.
- `project-configs/jarvis-scoreboard/commons-{lang,math}-3.5.conf` do **not** enumerate
  tests; they point `root-path` at the fixture and the pipeline processes whatever test
  classes are present, across 6 variants (NAIVE/IMPROVED x 100/200/1000).
- Feasibility selection already exists and runs **before** the expensive SPF/PIT stages:
  the `teralizer.processing.filter` pipeline (`NonPassingTestFilter`, `NoAssertionsFilter`,
  `TestTypeFilter`, `AssertionInMethodFilter`, `AssertionInLoopFilter`,
  `TestedMethodInLoopFilter`, `NestedClassesFilter`, `UnnamedPackageFilter`,
  `StaticInitializersFilter`, `ParameterTypeFilter`, `ReturnTypeFilter`,
  `UnsupportedAssertionFilter`, `ExcludedAssertionFilter`) plus `GeneralizableInput.derive()`
  (`2026-06-27-generalizable-input-rule`) and the SPF-stage exclusions at `ANALYZE_JPF`
  (`NonGeneralizableExpressionException`, `UnsupportedSpfTermException`).

## Approach: promote real upstream test classes (compile-gated)

Run the projects' **own** numeric/char test classes through the existing pipeline; let
the filters and SPF stage decide what generalizes. No new selector is built — the
existing filter pipeline *is* the feasible-case automation. The expansion is purely
about feeding more of the cached upstream suite into a buildable fixture and reporting
the outcome.

### Fixtures and the allowlist

A new census fixture per project, assembled by extending
`prepare-jarvis-scoreboard-fixtures.sh`:

- Reuse the pinned `source-cache` and the existing `src/main` copy.
- Copy a curated **allowlist** of upstream `*Test.java` (and only those) from the cache
  into the fixture test tree, preserving package paths.
- **Compile-gate (mandatory):** for each promoted slice, `mvn test-compile` must pass.
  Add the minimal test dependencies the slice needs (e.g. `hamcrest`) to the census POM,
  and **drop** any class that requires heavy test-support base classes, test resources,
  or non-trivial extra dependencies. Every dropped class is recorded with its reason.
- Starter allowlist (refined empirically by the compile-gate and the funnel):
  - commons-math: `util/FastMathTest`, `util/PrecisionTest`, `util/ArithmeticUtilsTest`
  - commons-lang: `CharUtilsTest`, `NumberUtilsTest`, `BooleanUtilsTest`
- `data/jarvis-scoreboard/PROVENANCE.md` records the pinned SHAs, the final allowlist,
  and the dropped-class list with reasons.

Separate census fixtures (`commons-{lang,math}-3.5-census`) and separate census configs
keep the canonical JARVIS-10 Table-2 run pristine and fast; the census is an opt-in,
heavier run. The JARVIS-10 fixture, config, and `compare_to_jarvis` output are unchanged.

### Run scope

Census configs run two variants — `NAIVE_100` and `IMPROVED_100`. The census measures
breadth (how many sound generalizations exist), and the 100/200/1000 tries-elasticity is
already characterized on the JARVIS-10 (`2026-06-29-pvc-budget-elasticity`), so
re-sweeping every promoted test is unnecessary. NAIVE is the baseline; IMPROVED is the
generator under test.

### The funnel (the measured result)

Per promoted test class, recorded from the existing DB (`assertion.is_included` +
`exclusion_info`, `jqwik_property_execution.diagnostic_kind`, the PIT mutation tables):

1. `@Test` methods in the class
2. assertion-level probes
3. probes passing the structural + type + assertion filters
4. probes that SPF generalizes (reach `GENERALIZE_TESTS`)
5. probes that generalize **FULL** (sound) under IMPROVED
6. probes whose generated property kills >= 1 mutant

The per-stage drop is tallied **by reason** (non-passing, no/loop/structural assertion,
type ceiling, unsupported assertion, SPF raw-bits / native-peer / transcendental). That
tally is half the result: it shows both the type ceiling and the loop-style limit of
real upstream tests, quantified.

### Report

A new census report — an analysis CLI entry plus an audit doc under `docs/plans/` —
separate from `jarvis_scoreboard.compare_to_jarvis` (the promoted tests have no JARVIS
baseline). The census runs in a dedicated database and data dir
(`postgres_jarvis_census`, `data/jarvis-census`) so census rows can never pollute the
canonical `compare_to_jarvis` aggregation. The report reads that database and emits the per-class
funnel, the by-reason rejection tally, and the headline counts (N sound generalizations
across M classes, vs JARVIS's 10).

## Reproducibility

- `prepare-jarvis-scoreboard-fixtures.sh` (or a sibling census-prep step) stays
  idempotent and pins the same SHAs; the allowlist and dropped classes are explicit in
  the script and `PROVENANCE.md`.
- A census run script mirrors `run-jarvis-scoreboard.sh`'s structure but guards on the
  dedicated census DB/data dir (`postgres_jarvis_census`, `data/jarvis-census`), so a
  census run can never target or perturb `postgres_jarvis_scoreboard`.
- Generated census artifacts (fixtures, value logs, generated tests) follow the existing
  gitignore policy — never committed.

## Verification

- The extended prep step is idempotent and the compile-gate is green for the final
  allowlist (`mvn test-compile` passes; dropped classes are recorded).
- A small end-to-end run on 1-2 promoted classes proves: some assertions generalize
  **FULL** under IMPROVED, soundness holds (no unsound green — the raw-bits fail-loud fix
  already guards this), and the funnel numbers reproduce from the DB.
- `omp-plans check` passes; any touched Java/Python tests pass.

## Out of scope

- Object/string/array/collection tests — the type ceiling (`Configuration.SUPPORTED_TYPES`)
  rejects them; this census does not extend the supported input surface.
- Other projects / the full RepoReapers re-run — gated on step 1 (overview).
- Any change to the canonical JARVIS-10 Table-2 comparison or `compare_to_jarvis`.
- Authoring new scorecard tests — the census uses the projects' own upstream tests only.

## Acceptance criteria

- A census run produces, per promoted class, the six-stage funnel and the by-reason
  rejection tally, from the existing DB tables (no new schema unless measurement proves
  it necessary).
- The headline "N sound generalizations across M classes beyond JARVIS's 10" is derived
  from FULL IMPROVED diagnostics, exclusion-honest (raw-bits / unsound paths excluded,
  not counted).
- Every promoted class compiled (`mvn test-compile` green); every dropped class is
  recorded with a reason in `PROVENANCE.md`.
- The canonical JARVIS-10 run, config, and `compare_to_jarvis` output are unchanged.
- The census is reproducible from pinned SHAs + an explicit allowlist.
