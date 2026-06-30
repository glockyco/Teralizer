---
title: Beyond-JARVIS Generalization Census
type: spec
status: draft
created: 2026-06-29
parent: 2026-06-26-teralizer-overview
---

# Beyond-JARVIS Generalization Census

## Problem

JARVIS (VMCAI 2018) ran on the **full** JUnit suites of 12 Apache Commons projects — its
Table 1 is a whole-suite scenario census — including Commons-Lang and Commons-Math. But
its method, *safe generalization*, abstracts a value region from a **repetitive
scenario**: it needs >= 2 compatible test traces (positive/negative examples) to bound the
abstraction. It therefore reports generalizations only for the repetitive tests in its
Table 2: `CharUtilsTest::{isAscii,isPrintable}` (Lang); `FastMathTest::{testMinMaxDouble,
toIntExact}`, `IntervalTest`, `PolynomialFunctionTest::{testConstants,testLinear}`,
`PrecisionTest`, `UnivariateFunctionTest::testAbs` (Math).

Teralizer extracts the input partition from a **single** test via symbolic path execution
— no repetition requirement. So generalizing tests beyond JARVIS's Table-2 set is direct
evidence of **broader applicability**: JARVIS attempted these same suites and reported
successes only in Table 2, so each additional sound Teralizer generalization is an
applicability win over a case JARVIS did not — and, by its repetition gate, largely could
not — generalize. This is a Beat-JARVIS strengthener (strategy step 1) within the same two
projects, distinct from the broader full-corpus re-run the overview gates on step 1.

Claim to support: *within Commons-Lang/Commons-Math, Teralizer soundly generalizes N
properties beyond JARVIS's reported Table-2 set* — exclusion-honest, with the rejection
reasons for everything that does not generalize as the other half of the result.

Honesty bounds:

- The paper exposes JARVIS's successes (Table 2), not a per-test failure ledger. We claim
  "beyond JARVIS's reported set," never "JARVIS failed on test X".
- The candidate universes differ both ways: JARVIS targets loop/repetition tests, which
  Teralizer cannot generalize. Teralizer does not reject these at filtering — its
  `AssertionInLoopFilter`/`TestedMethodInLoopFilter` only annotate them (`DEFER`); they
  proceed and exit downstream (a degenerate spec or a failing generalized test). The funnel
  records that downstream exit, so the comparison stays honest in both directions.
- For non-Table-2 tests JARVIS gives no PVC baseline, so the JARVIS comparison there is
  **binary applicability** (it reported no generalization). Quality is not left at binary,
  though: the census reports **mutation score** for Teralizer's generalizations — the
  stronger fault-detection metric (`2026-06-29-pvc-budget-elasticity`: PVC tracks input
  diversity and inflates with the tries budget; kills do not), one JARVIS never reported.
  PVC-magnitude-vs-JARVIS stays scoped to the canonical JARVIS-10 rows.

## Current setup (what we build on)

- `scripts/prepare-jarvis-scoreboard-fixtures.sh` pins the upstream repos at exact SHAs
  in `data/jarvis-scoreboard/source-cache/{commons-lang,commons-math}`
  (`LANG_3_5` = `36f98d87b24c2f542b02abbf6ec1ee742f1b158b`,
  `MATH_3_5` = `b3c5dae8f253fcb4484e5cd3cc5662587803efc2`). The full upstream test suites
  are already cached there.
- For each project the script writes a minimal `pom.xml` (only `junit:4.12`), copies
  `src/main` from the cache, and writes a hand-authored `Jarvis{Lang,Math}ScorecardTest`
  — small loop-free scorecard methods (1-2 assertion probes each) replicating the Table-2
  rows in a `jarvis` package. The upstream `*Test.java` are cached but never promoted.
- `project-configs/jarvis-scoreboard/commons-{lang,math}-3.5.conf` do **not** enumerate
  tests; they point `root-path` at the fixture and the pipeline processes whatever test
  classes are present, across 6 variants (NAIVE/IMPROVED x 100/200/1000).
- Filtering runs **before** the expensive SPF/PIT stages, but only a `REJECT` excludes a
  record — `TestFilteringTask` sets `is_included=false` solely on `REJECT`; `DEFER`/`ACCEPT`
  do not, and every result is recorded in `filter_result` as telemetry. Rejecting filters:
  `NonPassingTestFilter`, `NoAssertionsFilter`, `TestTypeFilter`, `ParameterTypeFilter` (no
  generalizable-typed parameter — the type ceiling), `ReturnTypeFilter` (void/unsupported),
  `UnsupportedAssertionFilter`, `UnnamedPackageFilter`, `MissingValueFilter`,
  `Excluded{Test,Assertion}Filter`. Informational-only (`DEFER`, never excludes):
  `AssertionInLoopFilter`, `TestedMethodInLoopFilter`, `AssertionInMethodFilter`,
  `NestedClassesFilter`, `StaticInitializersFilter`. The eligibility decision proper is
  `GeneralizableInput.derive()` (`2026-06-27-generalizable-input-rule`); SPF-stage exclusions
  happen later at `ANALYZE_JPF` (`NonGeneralizableExpressionException`,
  `UnsupportedSpfTermException`).

## Approach: promote real upstream test classes (compile-gated)

Run the projects' **own** numeric/char test classes through the existing pipeline; let the
filters and SPF stage decide what generalizes. No new selector is built — the existing
filter pipeline *is* the feasible-case automation. The expansion is purely about feeding
more of the cached upstream suite into a buildable fixture and reporting the outcome.

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

Allowlist in priority order (exact paths resolved by the prep step; refined empirically by
the compile-gate and the funnel):

1. **JARVIS's Table-2 source classes** — the tightest head-to-head: for these we know
   exactly which methods JARVIS generalized, so the census measures whether and by how much
   Teralizer's generalized-method set exceeds JARVIS's within the same class. Commons-Math
   `util/{FastMath,Precision}Test`, `geometry/euclidean/oned/IntervalTest`,
   `analysis/polynomials/PolynomialFunctionTest`; Commons-Lang `CharUtilsTest`. (The Table-2
   abs row maps to `analysis.function.Abs`; the cache has no dedicated `AbsTest`, so abs may
   have no promotable upstream test — itself a finding to record, not an allowlist entry.)
2. **Adjacent numeric/char classes** for breadth: Commons-Math `util/{ArithmeticUtils,
   MathArrays}Test`; Commons-Lang `{NumberUtils,BooleanUtils}Test`, `math/*`.

Separate census fixtures (`commons-{lang,math}-3.5-census`) and separate census configs
keep the canonical JARVIS-10 Table-2 run pristine and fast; the census is an opt-in,
heavier run. The JARVIS-10 fixture, config, and `compare_to_jarvis` output are unchanged.

### Run scope

Census configs run two variants — `NAIVE_100` and `IMPROVED_100`. The census measures
breadth (how many sound generalizations exist), and the 100/200/1000 tries-elasticity is
already characterized on the JARVIS-10 (`2026-06-29-pvc-budget-elasticity`), so re-sweeping
every promoted test is unnecessary. NAIVE is the baseline; IMPROVED is the generator under
test. 100 tries also matches JARVIS's own measurement budget (Table 2 PVC was collected at
ScalaCheck's default of 100 tests), so any per-row PVC sanity check is apples-to-apples.

### Selection stays automatic

No new selector: the existing pipeline already decides feasibility. We record, per
non-generalized probe, the rejecting filter (e.g. the type-ceiling `ParameterTypeFilter`)
and any `DEFER` annotation plus the stage at which the probe actually exits — that tally is
half the result. Loop/repetition cases are the sharpest contrast with JARVIS: it targets
them specifically, while Teralizer only annotates them (`DEFER`) and cannot soundly
generalize them.

### The funnel (the measured result)

Per promoted test class, recorded from the existing DB (`assertion.is_included` +
`exclusion_info`, `jqwik_property_execution.diagnostic_kind`, the PIT mutation tables):

1. `@Test` methods in the class
2. assertion-level probes
3. probes not REJECTed by any filter (only `REJECT` excludes; `DEFER` is informational)
4. probes that SPF generalizes (reach `GENERALIZE_TESTS`)
5. probes that generalize **FULL** (sound) under IMPROVED
6. probes whose generated property kills >= 1 mutant

Each drop is tallied **by reason**: filter `REJECT`s (non-passing, no-assertion, unsupported
assertion/return type, type ceiling, unnamed package), SPF-stage exclusions (raw-bits /
native-peer / transcendental), and the downstream exit stage for `DEFER`-annotated cases
(loop / nested-class / static-init), since `DEFER` never excludes on its own. Each
FULL/sound generalization that falls outside JARVIS's Table-2 source-method set is flagged
an **applicability win**.

### Report

A new census report — an analysis CLI entry plus an audit doc under `docs/plans/` —
separate from `jarvis_scoreboard.compare_to_jarvis` (the promoted tests have no JARVIS PVC
baseline). The census runs in a dedicated database and data dir (`postgres_jarvis_census`,
`data/jarvis-census`) so census rows can never pollute the canonical `compare_to_jarvis`
aggregation. The report reads that database and emits, per class: the funnel, the by-reason
rejection tally, and — as the fault-detection quality metrics — the augmented GENERALIZED-suite
**mutation score** (killed / covered) plus, as the improvement attribution, the **mutant-key set
difference** in killed PIT mutants between two already-collected stages over the *same*
mutated classes: `COLLECT_PIT_DATA_INITIAL` (the included seed tests) and
`COLLECT_PIT_DATA_GENERALIZED` (those seed tests *plus* the generated properties). Because
GENERALIZED's suite is a superset of INITIAL's, the key-set difference is exactly the mutants
the added properties kill that the single-value seed tests miss — the generalization's net
fault-detection gain, not a count delta. This is an analysis-only addition to
`get_mutation_scores` (which today reads only the generalized stage), NAIVE vs IMPROVED — no
extra PIT, no `ProjectSetupTask` change. Headline counts:
**N sound generalizations across M classes beyond JARVIS's reported Table-2 set**, and —
within JARVIS's own Table-2 source classes — Teralizer's generalized-method
count vs JARVIS's K.

## Reproducibility

- `prepare-jarvis-scoreboard-fixtures.sh` (or a sibling census-prep step) stays idempotent
  and pins the same SHAs; the allowlist and dropped classes are explicit in the script and
  `PROVENANCE.md`.
- A census run script mirrors `run-jarvis-scoreboard.sh`'s structure but guards on the
  dedicated census DB/data dir (`postgres_jarvis_census`, `data/jarvis-census`), so a
  census run can never target or perturb `postgres_jarvis_scoreboard`.
- Generated census artifacts (fixtures, value logs, generated tests) are run evidence, not
  committed source; they follow the existing gitignore policy. Only prep/config/report/doc
  changes are committed.

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
- Per-test claims about which specific tests JARVIS failed on — the paper exposes only its
  successes; the claim is "beyond JARVIS's reported set."
- Authoring new scorecard tests — the census uses the projects' own upstream tests only.

## Acceptance criteria

- A census run produces, per promoted class, the six-stage funnel and the by-reason
  rejection tally, from the existing DB tables (no new schema unless measurement proves it
  necessary).
- The headline "N sound generalizations across M classes beyond JARVIS's reported Table-2
  set" is derived from FULL IMPROVED diagnostics, exclusion-honest (raw-bits / unsound
  paths excluded, not counted).
- The report states, per class, the augmented GENERALIZED-suite **mutation score** (killed /
  covered) and the net fault-detection gain as the **killed mutant-key set difference** between
  `COLLECT_PIT_DATA_GENERALIZED` (seed + properties) and `COLLECT_PIT_DATA_INITIAL` (seed only)
  over the same mutated classes — the mutants the added properties kill that the seed misses;
  a key-set difference, not a count delta, no extra PIT.
- Within JARVIS's Table-2 source classes, the report states Teralizer's generalized-method
  count against JARVIS's reported methods for the same class.
- Every promoted class compiled (`mvn test-compile` green); every dropped class is recorded
  with a reason in `PROVENANCE.md`.
- The canonical JARVIS-10 run, config, and `compare_to_jarvis` output are unchanged.
- The census is reproducible from pinned SHAs + an explicit allowlist.
