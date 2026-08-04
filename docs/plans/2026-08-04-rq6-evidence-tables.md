---
title: RQ6 Evidence Tables for Oracle Refusal and Extraction Limits
type: plan
status: active
created: 2026-08-04
parent: 2026-07-08-evaluation-analysis-redesign
superseded_by:
archived:
---

# RQ6 Evidence Tables for Oracle Refusal and Extraction Limits

Generate the evidence the thesis chapter cites for why assertions are excluded and
why licensed oracles are refused, so no number in the chapter comes from an ad-hoc
query. Findings this reproduces: `2026-08-04-oracle-refusal-taxonomy`.

Every table here reads `postgres_reporeapers_rq6` and scopes on the single variant
resolved by `_funnel.resolve_variant`. RQ5 is frozen: `rq5_causes.py`, its SQL, and
`postgres_dev` are out of scope, so none of these sections may be routed through
`_causes_common.py` in a way that changes RQ5 output.

Depends on `2026-08-04-rq6-stage4-applicability-funnel` for the Stage-4 success signal.

## File map

- Create `analysis/src/teralizer/eval/reports/_refusal.py`: owns refusal bucketing SQL, the pattern table, and the blocked-project leverage table.
- Modify `analysis/src/teralizer/eval/reports/rq6_causes.py`: owns the RQ6 report sections and registers the new tables and metrics.
- Create `analysis/tests/eval/test_refusal.py`: covers bucket disjointness, totals, and leverage arithmetic.
- Modify `analysis/tests/eval/test_rq6_causes.py`: covers the added sections and metrics.

## Tasks

### Task 1: Stage-4 exclusion causes

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

- [ ] Add a table keyed `rq6_stage4_causes` with columns cause, attempts, share, decomposing the
      Stage-4 exclusions into `ORACLE_NOT_WIDENABLE`, `NonPassingTest` generalization filter
      rejects, `ExcludedTest` rejects, `OTHER_COMPILE_FAILURE`, and generalized-suite execution
      failures, each also carrying the number of distinct projects.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k stage4_causes`
  Expected: rows sum to 3,060 attempts; `ORACLE_NOT_WIDENABLE` is 2,708 and `NonPassingTest` 287.

- [ ] Commit.
  Message: `feat(eval): report RQ6 Stage-4 exclusion causes`

### Task 2: Refusal patterns and their project leverage

**Files:**
- Create: `analysis/src/teralizer/eval/reports/_refusal.py`
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_refusal.py`

- [ ] Implement `build_refusal_patterns(conn, variant)` returning one row per pattern with
      refusals, share of refusals, and distinct projects. Buckets are disjoint and derived from
      `assertion.concretization_events` and `assertion.concretized_methods`: no symbolic output
      when `concretization_events = 0`; boxing only when every concretized key matches
      `^java\.lang\.(Integer|Long|Double|Float|Short|Byte|Character|Boolean)\.valueOf`;
      string composition when any key matches `StringBuilder`, `StringBuffer`, or `String\.`;
      other unmodeled otherwise.
  Verification: `uv run --directory analysis pytest tests/eval/test_refusal.py -k patterns`
  Expected: buckets are disjoint, sum to 2,708, and read 957 / 881 / 767 / 103 refusals
  over 74 / 78 / 16 / 12 projects.

- [ ] Implement `build_refusal_leverage(conn, variant)` restricted to projects with zero
      `generated_filter_passed` generalizations and at least one refusal, returning refusals and
      distinct blocked projects touched per pattern.
  Verification: `uv run --directory analysis pytest tests/eval/test_refusal.py -k leverage`
  Expected: 57 blocked projects; string composition touches 40, no symbolic output 33,
  boxing only 6, other unmodeled 5.

- [ ] Add a table of refusals by `assertion.output_spec_class` with attempts, refusals, and
      refusal rate.
  Verification: `uv run --directory analysis pytest tests/eval/test_refusal.py -k spec_class`
  Expected: `SYMBOLIC` refusal rate is 0.0 and `NULL_CONCRETE` is 81.0%.

- [ ] Register the three tables in the RQ6 report and add metrics
      `realworld.oracle_refusals` (2,708), `realworld.oracle_refusal_pct` (0.657),
      `realworld.symbolic_output_attempts_pct` (0.169), and
      `realworld.license_blocked_projects` (57).
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k metrics`
  Expected: keys resolve and percentages are fractions.

- [ ] Commit.
  Message: `feat(eval): add the RQ6 oracle-refusal taxonomy`

### Task 3: Tested-method resolution and type reach

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

- [ ] Add a table over `mut_resolution_observation` grouping eligible observations by
      `no_pick_reason`, with a picked row.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k resolution`
  Expected: picked 83,090; `UNSUPPORTED_ASSERTION_SHAPE` 24,156; `LIBRARY_DECLARATION` 17,182;
  `UNRESOLVED_SOURCE_DECLARATION` 6,683; `NO_VISIBLE_CALL` 4,517. The no-pick rows sum to within
  ten of the `MissingValue` reject count, which the test asserts as a reconciliation bound.

- [ ] Add a two-by-two table over picked observations crossing `candidate_param_supported` with
      `candidate_return_supported`.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k type_support_matrix`
  Expected: both supported 14,582; parameters only 17,368; return only 23,904; neither 27,236.

- [ ] Add type-known rejection metrics computed over distinct assertions with a non-`DEFER`
      decision: `realworld.parameter_type_known_reject_pct` (0.592, 51,222 of 86,572) and
      `realworld.return_type_known_reject_pct` (0.530, 44,031 of 83,088).
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k type_known`
  Expected: both metrics resolve to the stated fractions.

- [ ] Commit.
  Message: `feat(eval): report RQ6 tested-method resolution and type reach`

### Task 4: Specification-extraction failure taxonomy

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

- [ ] Add a table of assertion-level `EXECUTE_JPF` failures grouped by
      `task_diagnostic.reason_code`, plus metrics `realworld.assertions_reaching_spf` (10,040)
      and `realworld.spf_failure_pct` (0.586).
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k spf_failures`
  Expected: `UNCAUGHT_EXCEPTION_PATH` 1,783; `MISSING_NATIVE_PEER` 1,404;
  `UNSUPPORTED_BYTECODE` 899; `LISTENER_BUG` 393; `NO_INPUT_SPEC` 333;
  `SEARCH_DEPTH_LIMIT` 331; `JPF_DIVERGENT_ASSERTION` 289; `MISSING_JPF_MODEL_CLASS` 270;
  `MISSING_JPF_MODEL_METHOD` 178.

- [ ] Commit.
  Message: `feat(eval): report RQ6 specification-extraction failures`

### Task 5: Assertion forms and oracle kinds

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

- [ ] Add a table of `UnsupportedAssertion` decisions grouped by `assertion.assertion_name` and
      decision, so accepted and rejected forms are visible side by side.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k assertion_forms`
  Expected: `assertThat` shows 10,057 accepts and 4,076 rejects; `assertNotNull` 7,011 rejects;
  `fail` 4,825 rejects.

- [ ] Add a table of validated generalizations grouped by `assertion.output_spec_class` with
      distinct projects, which separates value oracles from exception oracles.
  Verification: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -k oracle_kinds`
  Expected: `NULL_CONCRETE` 595 over 41 projects; `SYMBOLIC` 498 over 57; `EXCEPTION` 7 over 2;
  `CONSTANT` 2 over 1.

- [ ] Commit.
  Message: `feat(eval): report RQ6 assertion forms and oracle kinds`

### Task 6: Regenerate and gate

**Files:**
- Modify: `analysis/reports/rq6.md` (generated, untracked)
- Modify: `analysis/reports/provenance.json` (generated, untracked)

- [ ] Regenerate the report and confirm every new section renders with a source link.
  Run: `uv run --directory analysis python -m teralizer.eval rq6 --targets md`
  Expected: refusal, resolution, type-reach, extraction-failure, assertion-form, and
  oracle-kind sections present; each carries a `source:` link and a provenance entry.

- [ ] Regenerate the macro file after the funnel plan's copy step, so both metric sets land
      together.
  Run: `uv run --directory analysis python -m teralizer.eval rq6 --targets latex`
  Expected: `analysis/build/macros.tex` carries the funnel metrics plus
  `\TzRealworldOracleRefusals`, `\TzRealworldOracleRefusalPct`,
  `\TzRealworldParameterTypeKnownRejectPct`, `\TzRealworldReturnTypeKnownRejectPct`,
  `\TzRealworldAssertionsReachingSpf`, and `\TzRealworldSpfFailurePct`.

- [ ] Run the full gate.
  Run: `uv run --directory analysis pytest tests/eval` then `uv run --directory analysis ruff check .` then `uv run --directory analysis ty check .`
  Expected: all green.

- [ ] Commit.
  Message: `chore(eval): regenerate the RQ6 report with refusal evidence`
