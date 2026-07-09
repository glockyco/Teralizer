---
title: RQ5 + RQ6 Causes Reports
type: plan
status: active
created: 2026-07-08
parent: 2026-07-08-evaluation-analysis-redesign
---

# RQ5 + RQ6 Causes Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the RQ5 (controlled conditions) and RQ6 (real-world) "causes of unsuccessful generalization" analyses into the `teralizer.eval` engine, replacing free-text regex classification with structured reason codes for RQ6 while RQ5 faithfully reproduces the published controlled-dataset tables from the old schema.

**Architecture:** RQ5 reads the paper-tagged old schema on `postgres_dev`; RQ6 reads the new schema on `postgres_reporeapers`. A shared presentation module `_causes_common.py` owns the test/assertion/generalization exclusion breakdown and filter-rejection tables (both RQs feed it a normalized frame from a thin, schema-specific query layer). RQ6 additionally owns the project-level exclusion funnel (`tab-processing-failures`): an eligible denominator, a per-project terminal-failure attribution over structured `(stage, reason_code)` signals, a five-stage grouping, and an Internal/External/Mixed cause taxonomy. This plan also implements `data.py`'s `connect(validate_schema=True)` as per-report required-object validation (the first old-schema report needs it).

**Tech Stack:** Python 3, pandas, SQLAlchemy (read-only), pytest, the `teralizer.eval` result model + renderers built by the framework foundation plan.

---

## Background the executor needs

The framework foundation (`teralizer.eval`) is built and green: `model.py` (frozen
`RQReport`/`Section`/`Prose`/`Table`/`ColumnSpec`/`Figure`/`Metric`), `format.py`
(`render_value`), `macros.py` (`macro_name`), `provenance.py` (`capture`), `data.py`
(`connect`/`read_sql`), `registry.py` (`ReportSpec`/`register`/`get`, starts empty),
`cli.py`, and `render/{markdown,latex,figures,manifest}.py`. No RQ report exists yet;
this plan builds the first two.

**Exact result-model API (use verbatim):**

```python
Metric(key: str, value: float | int | str, fmt: str = "int", provenance=None)
ColumnSpec(header: str, source: str, fmt: str = "str", align: str = "l")  # source = df column
Table(key, df, columns: list[ColumnSpec], caption, label, group_by=None, note=None, provenance=None)
Figure(key, build, caption, label, data=None, provenance=None)
Prose(text: str)               # "{metric.key}" placeholders substituted by markdown renderer
Section(title: str, blocks: list[Prose | Table | Figure])
RQReport(rq: str, title: str, db: str, sections: list[Section], metrics: list[Metric] = [])
# RQReport.metric(key), .metric_map(), .tables(), .figures()
ReportSpec(build: Callable[[Connection], RQReport], default_db: str, schema: str)  # schema "new"|"old"
register(rq: str, spec: ReportSpec)
```

`format.render_value` formatter keys: `str`, `int`, `count` (thousands commas),
`pct1` (`value*100` with 1 dp), `pct2`, `float2`, `runtime`. Percentage metrics
store the **fraction** (`0.017`) and use `fmt="pct1"`.

**Schema facts (verified against the live databases):**

- Old schema is the paper-tagged (`10.5281/zenodo.18242626`) 12-table schema on
  `postgres_dev`; the current working tree is a 22-table schema. `config.py`'s
  `_parse_sql_files` reads the **working-tree** DDL, so `get_engine(validate=True)`
  against `postgres_dev` fails on the new-schema objects it lacks — that is the bug
  this plan's validation replaces, not reuses.
- New-schema `postgres_reporeapers` structured columns:
  - `task(project_id, test_id, assertion_id, generalization_id, step, stage, variant, status, runtime, info)`.
    Project-level tasks have `test_id/assertion_id/generalization_id` all NULL. `status`
    is `SUCCEEDED`/`FAILED`. `runtime` is populated for the funnel-relevant failed tasks
    (verified: zero NULL among `EXECUTE_TESTS_ORIGINAL`/`SETUP_PROJECT`/`BUILD_PROJECT_ORIGINAL`
    first-failures).
  - `task_diagnostic(task_id, project_id, test_id, assertion_id, generalization_id, stage,
    reason_code, detail_json, first_error_file, first_error_line, first_error_message)`.
    **Supplementary, not exhaustive:** it carries `reason_code` for JPF/SPF, report-layout,
    and compile failures, but has **no** rows for `SETUP_PROJECT`, `BUILD_PROJECT_ORIGINAL`,
    `BUILD_SPOON_MODEL`, `EXECUTE_TESTS_ORIGINAL`, `COLLECT_JACOCO_*`, `COLLECT_PIT_*`.
  - `filter_result(project_id, test_id, assertion_id, generalization_id, filter_name,
    decision, reason, reason_code, depends_on, detail_json)`. `decision` is
    `ACCEPT`/`DEFER`/`REJECT`.
  - `generalization(project_id, test_id, assertion_id, variant, is_included, exclusion_info, ...)`.
    `variant` is a real column (scope on it; never parse variant from `exclusion_info` text).
    Typed `exclusion_info` labels: `ORACLE_NOT_WIDENABLE`, `INPUT_SPEC_NOT_SATISFIED_BY_SEED`;
    filter exclusions carry a `TestFilteringTask{...}` descriptor string.
- Project-level first-failure distribution on `postgres_reporeapers` (earliest `step`,
  `status<>'SUCCEEDED'`, all entity IDs NULL), with `task_diagnostic` LEFT-JOINed on
  `task_id`:

  | internal stage | projects | reason_code | note |
  |---|---|---|---|
  | SETUP_PROJECT | 355 | (none) | fail-at-start candidates |
  | ANALYZE_JPF | 330 | NO_INPUT_SPEC | downstream symptom of empty input |
  | BUILD_PROJECT_ORIGINAL | 188 | (none) | fail-at-start candidates |
  | EXECUTE_TESTS_ORIGINAL | 66 | (none) | 49 at 60.0s ceiling (timeout), 17 sub-2s (JUnit error) |
  | COLLECT_JACOCO_DATA_ORIGINAL | 44 | (none) | |
  | COLLECT_JUNIT_REPORTS_ORIGINAL | 31 | UNSUPPORTED_REPORT_LAYOUT | |
  | BUILD_SPOON_MODEL | 8 | (none) | Spoon error |
  | BUILD_PROJECT_GENERALIZED | 7 | OTHER_COMPILE_FAILURE | |
  | ADD_DEPENDENCIES | 7 | (none) | fail-at-start candidates |
  | EXECUTE_TESTS_GENERALIZED | 5 | LISTENER_BUG | |
  | BUILD_PROJECT_INSTRUMENTED | 2 | OTHER_COMPILE_FAILURE | |
  | GENERALIZE_TESTS | 1 | (none) | |

**Legacy source being ported** (`analysis/src/teralizer/rq4_limitations.py`,
`exclusions.py`, `stages.py`):

- `categorize_failure_type` (L29-98): the 17-cause Internal/External taxonomy
  (external = JUnit/Spoon/JaCoCo/PIT execution error + compilation error; the rest
  internal), raising on unknown causes. A Stage-3 override maps `all assertions excluded`
  to Mixed; a post-pass merges the two PIT-mapping causes into `failed to process PIT reports`.
- `compute_exclusion_breakdown_filtering_vs_failures` (L869-1080): per-`(variant, level)`
  Included/Filtering/Failures split.
- `get_filtering_exclusions_data` (L148-231) + `compute_filtering_exclusions_summary`
  (L382-428): per-`(level, filter, decision)` counts, `reject > 0` rows only.
- `get_processing_failures_by_cause_data` (L1649-1882) + `compute_processing_failures_by_stage_and_cause`
  (L1885-2029) + `generate_processing_failures_table` (L2101-2233): the RQ6 funnel over
  `v_project_failures.info` regex, hard-coded stage remaps, and the funnel arithmetic.
- `exclusions.py` (L1-125): eligibility (`dependency resolution error`, `sources / tests
  not found`, `BUILD_PROJECT_ORIGINAL` compile, zero-coverage) via regex over
  `v_project_failures.info`.
- `stages.py`: internal-stage -> paper-stage grouping (five stages, 1 and 2 combined) and
  `get_stage_order`.

**Target paper tables** (`~/Projects/test-generalization-paper/tables/`):

- `tab-processing-failures.tex` (RQ6 funnel, "Project-level exclusions by stage and cause"):
  five stage bands with entering/inclusion/exclusion counts + inclusion rate, 17 numbered
  cause rows `# | Type | Cause | Count`, and an Overall band. Published v1: 632 eligible ->
  130 -> 117 -> 114 -> 11 (1.7% overall inclusion).
- `tab-exclusions-breakdown{,-extended}.tex` (shared): `Level | Total | Included |
  Excluded{Filtering | Failures}`.
- `tab-exclusions-filtering{,-extended}.tex` (shared): `Level | Filter Name | Total |
  Accept | Defer | Reject`.

The `-extended` variants are the RQ6 (real-world) versions; the base variants are RQ5
(controlled). Same column shape, different corpus.

## Key design decisions

1. **RQ6 cause attribution = structured signals + bounded structural derivation, no
   free-text regex in the production path (fork A).** Rules, in priority order, over
   `(internal_stage, reason_code, runtime>=stage_ceiling, artifact_present, included_tests,
   included_assertions)`. `task.runtime >= ceiling` is the *structural* fact behind
   "timeout" (verified: `EXECUTE_TESTS_ORIGINAL` timeouts pile up exactly at 60.0s, errors
   are sub-2s, zero NULL runtimes). This is more direct than regexing a log line, and drops
   the legacy `v_project_failures.info` regex entirely. **Rejected fork B** (pipeline emits
   a `reason_code` for every terminal failure, then re-run the corpus): purest, but a Java
   change plus a full RepoReapers re-run — a measurement event gated behind operator
   sign-off (AGENTS.md), and it would move every v2 number. Fork B is recorded as the clean
   future improvement that would let the analysis delete the structural-derivation rules;
   it does not block this plan, which reads the existing snapshot. **Rejected fork C** (keep
   `task.info` regex for uncoded stages): reintroduces the baggage the redesign removes.

2. **Every attribution routes to a cause or to an explicit `UNCODED` bucket that fails
   loud.** No silent default. Tests assert `UNCODED` is empty on `postgres_reporeapers`; a
   non-empty bucket is a taxonomy-drift defect to investigate, mirroring the legacy
   `ValueError` on unmapped stages (never the legacy `other`-bucket warning-and-continue).

3. **Eligibility is confirmed, not assumed.** The eligible denominator drops
   dependency-resolution, missing-sources, and original-build failures (spec definition).
   The production rule is stage-based (first-failure at `SETUP_PROJECT`, `ADD_DEPENDENCIES`,
   or `BUILD_PROJECT_ORIGINAL`, plus zero-coverage projects), but a validation test
   *positively* audits that every such first-failure is one of those ineligible causes
   (a one-time `info` audit at test time only — the production path never reads `info`).
   Any first-failure at those stages that is *not* an ineligible cause is a defect the
   test surfaces, not a project silently dropped.

4. **Old-schema validation is per-report required objects with columns and type (fork B
   for validation), replacing the retired working-tree DDL parse.** `connect(db, *,
   require=...)` checks that each declared table/view exists **and carries each declared
   column** (an object-only check passes a same-named table with renamed columns, then
   fails at query time). This supersedes the spec's "reuses `config.py`'s schema-object
   check" line, which described the retired bug.

5. **No legacy deletion in this plan.** `rq4_limitations.py`, `exclusions.py`, `stages.py`,
   and the notebooks remain the paper's current source of truth until the migration plan
   (item 5) switches the paper to consume eval-engine output. Deleting them now breaks a
   working path; that is deferred, not "just-in-case" retention. This plan only removes
   helpers it proves are already dead (grep-confirmed no importers).

6. **RQ5 reproduces from the old schema as published.** The controlled dataset's exclusion
   categorization used the old schema's free-text `exclusion_info` parsing; RQ5 ports that
   retrieval faithfully (it is what produced the published numbers). The structured-code
   mandate applies to RQ6 (new schema) only. RQ5 and RQ6 share the *presentation*, not the
   retrieval.

## File structure

```
analysis/src/teralizer/eval/
  data.py                      # MODIFY: connect(validate_schema) -> per-report required objects
  registry.py                  # MODIFY: ReportSpec gains `requires`
  cli.py                       # MODIFY: pass spec.requires into connect
  reports/
    _causes_common.py          # CREATE: shared breakdown + filtering Table builders
    _taxonomy.py               # CREATE: paper-stage map + structured cause rules + classify()
    _funnel.py                 # CREATE: RQ6 project-level funnel (eligibility, attribution, arithmetic)
    rq5_causes.py              # CREATE: RQ5 report (old schema, postgres_dev), registers "rq5"
    rq6_causes.py              # CREATE: RQ6 report (new schema, postgres_reporeapers), registers "rq6"
analysis/tests/eval/
  test_data.py                 # MODIFY: validated connect (positive + missing-column negative)
  test_causes_common.py        # CREATE: breakdown/filtering builders (pure, synthetic frames)
  test_taxonomy.py             # CREATE: classify() rules (pure)
  test_funnel.py               # CREATE: funnel over postgres_reporeapers (DB fixture)
  test_rq5_causes.py           # CREATE: RQ5 build over postgres_dev (DB fixture)
  test_rq6_causes.py           # CREATE: RQ6 build over postgres_reporeapers (DB fixture)
```

DB-fixture tests follow the existing pattern (`test_reporeapers_rerun_report.py`,
`test_smoke.py`): open via `eval.data.connect`, `pytest.skip` on
`sqlalchemy.exc.OperationalError` so the suite passes without a database.

The report modules self-register on import; `cli.py` already imports the `reports`
package. Add explicit imports of `rq5_causes` and `rq6_causes` in
`reports/__init__.py` so registration fires.

---

## Phase 1: Old-schema validated connect

### Task 1: Per-report required-object validation in `data.py`

**Files:**
- Modify: `analysis/src/teralizer/eval/data.py`
- Modify: `analysis/src/teralizer/eval/registry.py`
- Modify: `analysis/src/teralizer/eval/cli.py`
- Test: `analysis/tests/eval/test_data.py`

- [ ] **Step 1: Write the failing tests**

```python
# analysis/tests/eval/test_data.py  (add to the existing file)
import pytest
import sqlalchemy.exc

from teralizer.eval.data import Required, connect


def _skip_no_db(exc):
    if isinstance(exc, sqlalchemy.exc.OperationalError):
        pytest.skip("database unavailable")
    raise exc


def test_validated_connect_accepts_present_objects():
    require = (Required("project", "table", ["id", "use_test_generalization"]),)
    try:
        with connect("postgres_dev", validate_schema=True, require=require) as conn:
            assert conn is not None
    except Exception as exc:  # noqa: BLE001
        _skip_no_db(exc)


def test_validated_connect_rejects_missing_column():
    require = (Required("project", "table", ["column_that_does_not_exist"]),)
    try:
        cm = connect("postgres_dev", validate_schema=True, require=require)
        with pytest.raises(RuntimeError, match="column_that_does_not_exist"):
            cm.__enter__()
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")


def test_validated_connect_requires_objects():
    with pytest.raises(ValueError, match="require"):
        connect("postgres_dev", validate_schema=True).__enter__()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `uv run --directory analysis pytest tests/eval/test_data.py -v`
Expected: FAIL with `ImportError: cannot import name 'Required'`.

- [ ] **Step 3: Implement the validated connect**

```python
# analysis/src/teralizer/eval/data.py  (replace the module body below the imports)
from __future__ import annotations

from collections.abc import Iterator, Sequence
from contextlib import contextmanager
from dataclasses import dataclass

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer.report_basis import open_report_connection


@dataclass(frozen=True)
class Required:
    """One schema object a report reads, with the columns it depends on.

    Validation checks existence AND columns: a same-named object with renamed or
    dropped columns would pass an object-only check, then fail at query time.
    """

    name: str
    kind: str  # "table" | "view"
    columns: Sequence[str]


_KIND_RELKINDS = {
    "table": {"r", "p"},  # ordinary + partitioned table
    "view": {"v", "m"},   # view + materialized view (the report only SELECTs from it)
}


def _validate(conn: Connection, require: Sequence[Required]) -> None:
    """Check each required object exists with the right kind and columns.

    Uses pg_catalog, not information_schema: a materialized view (e.g.
    mv_exclusions_all) is absent from information_schema.tables/columns, so an
    information_schema check would falsely report it missing.
    """
    missing: list[str] = []
    for obj in require:
        relkind = conn.execute(
            text(
                "SELECT c.relkind FROM pg_class c "
                "JOIN pg_namespace n ON n.oid = c.relnamespace "
                "WHERE n.nspname = 'public' AND c.relname = :name"
            ),
            {"name": obj.name},
        ).scalar()
        if relkind is None:
            missing.append(f"{obj.kind} {obj.name} (absent)")
            continue
        if relkind not in _KIND_RELKINDS[obj.kind]:
            missing.append(
                f"{obj.name} (expected {obj.kind}, found relkind {relkind!r})"
            )
            continue
        present_cols = {
            row[0]
            for row in conn.execute(
                text(
                    "SELECT a.attname FROM pg_attribute a "
                    "JOIN pg_class c ON c.oid = a.attrelid "
                    "JOIN pg_namespace n ON n.oid = c.relnamespace "
                    "WHERE n.nspname = 'public' AND c.relname = :name "
                    "AND a.attnum > 0 AND NOT a.attisdropped"
                ),
                {"name": obj.name},
            )
        }
        for col in obj.columns:
            if col not in present_cols:
                missing.append(f"{obj.name}.{col}")
    if missing:
        raise RuntimeError(
            "database is missing required schema objects/columns: "
            + ", ".join(missing)
        )


@contextmanager
def connect(
    db: str,
    *,
    validate_schema: bool = False,
    require: Sequence[Required] | None = None,
) -> Iterator[Connection]:
    """Open a read-only connection to `db`.

    validate_schema=True checks that every object in `require` exists with its
    declared columns (the old-schema RQ path). validate_schema=False uses the
    report_basis open connection unchanged (RQ0/RQ6, new schema).
    """
    with open_report_connection(db) as conn:
        if validate_schema:
            if not require:
                raise ValueError(
                    "validate_schema=True requires a non-empty `require` list"
                )
            _validate(conn, require)
        yield conn


def read_sql(conn: Connection, sql: str, params: dict | None = None) -> pd.DataFrame:
    return pd.read_sql_query(text(sql), conn, params=params or {})
```

Then thread `requires` through the registry and CLI:

```python
# analysis/src/teralizer/eval/registry.py  (modify ReportSpec)
from teralizer.eval.data import Required  # add import

@dataclass(frozen=True)
class ReportSpec:
    build: Callable[[Connection], RQReport]
    default_db: str
    schema: str  # "new" | "old"
    requires: tuple[Required, ...] = ()
```

```python
# analysis/src/teralizer/eval/cli.py
# where the report connection is opened, replace the connect call with:
#   validate = spec.schema == "old"
#   with connect(db, validate_schema=validate, require=spec.requires) as conn:
#       report = spec.build(conn)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `uv run --directory analysis pytest tests/eval/test_data.py -v`
Expected: PASS (or `skip` if no database).

- [ ] **Step 5: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): validate old-schema reports against declared objects" \
COMMIT_BODY="The old-schema reports need to fail fast when pointed at a database that lacks the objects and columns they read. The connect path now takes a per-report required-object list and checks existence plus columns, replacing the retired working-tree DDL parse that broke on the paper-tagged schema." \
bun ~/.omp/skills/commit/commit-helper.ts
```

---

## Phase 2: Shared exclusion breakdowns

### Task 2: Filtering breakdown builder (`_causes_common.build_filtering_table`)

**Files:**
- Create: `analysis/src/teralizer/eval/reports/_causes_common.py`
- Test: `analysis/tests/eval/test_causes_common.py`

The filtering table is schema-agnostic: both schemas expose `filter_result(filter_name,
decision, {test,assertion,generalization}_id)`. The builder takes a normalized frame
`(level, filter, total, accept, defer, reject)` and returns a `Table`. Percentages are
`ColumnSpec` formatters, computed as fractions in the frame.

- [ ] **Step 1: Write the failing test**

```python
# analysis/tests/eval/test_causes_common.py
import pandas as pd

from teralizer.eval.model import Table
from teralizer.eval.reports._causes_common import build_filtering_table


def test_filtering_table_shapes_columns_and_percentages():
    df = pd.DataFrame(
        {
            "level": ["Test", "Assertion"],
            "filter": ["NonPassingTest", "AssertionType"],
            "total": [100, 200],
            "accept": [88, 152],
            "defer": [0, 0],
            "reject": [12, 48],
        }
    )
    table = build_filtering_table(df, key="filtering", label="tab:x", caption="C")
    assert isinstance(table, Table)
    assert [c.source for c in table.columns] == [
        "level", "filter", "total", "accept_pct", "defer_pct", "reject_pct",
    ]
    # 12/100 -> 0.12 fraction, rendered as pct1 downstream
    row = table.df.set_index("filter").loc["NonPassingTest"]
    assert row["reject_pct"] == 0.12
    assert row["accept_pct"] == 0.88
```

- [ ] **Step 2: Run test to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_causes_common.py -v`
Expected: FAIL with `ModuleNotFoundError` / `ImportError`.

- [ ] **Step 3: Implement the builder**

```python
# analysis/src/teralizer/eval/reports/_causes_common.py
"""Presentation shared by RQ5 and RQ6: exclusion breakdown and filter-rejection
tables. Each RQ supplies a normalized frame from its own schema-specific query
layer; the Table shape, columns, and percentage formatting live here once."""

from __future__ import annotations

import pandas as pd

from teralizer.eval.model import ColumnSpec, Table


def build_filtering_table(
    df: pd.DataFrame, *, key: str, label: str, caption: str
) -> Table:
    """df columns: level, filter, total, accept, defer, reject (integer counts)."""
    out = df.copy()
    for decision in ("accept", "defer", "reject"):
        out[f"{decision}_pct"] = out[decision] / out["total"]
    out = out.sort_values(["level", "filter"]).reset_index(drop=True)
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Filter Name", "filter"),
        ColumnSpec("Total", "total", fmt="count", align="r"),
        ColumnSpec("Accept", "accept_pct", fmt="pct1", align="r"),
        ColumnSpec("Defer", "defer_pct", fmt="pct1", align="r"),
        ColumnSpec("Reject", "reject_pct", fmt="pct1", align="r"),
    ]
    return Table(
        key=key, df=out, columns=columns, caption=caption, label=label, group_by="level"
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_causes_common.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): shared filter-rejection table builder" \
COMMIT_BODY="Both causes reports render the same per-filter accept/defer/reject table over a normalized frame, so the builder and its percentage formatting live in the shared presentation module while each report keeps its own schema-specific query." \
bun ~/.omp/skills/commit/commit-helper.ts
```

### Task 3: Exclusion breakdown builder (`_causes_common.build_breakdown_table`)

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/_causes_common.py`
- Test: `analysis/tests/eval/test_causes_common.py`

The breakdown table splits each level's excluded entities into Filtering (proactive
filter rejection) versus Failures (pipeline exception). The builder takes a normalized
frame `(level, total, included, filtering, failures)` of integer counts.

- [ ] **Step 1: Write the failing test**

```python
# analysis/tests/eval/test_causes_common.py  (append)
from teralizer.eval.reports._causes_common import build_breakdown_table


def test_breakdown_table_percentages_over_total():
    df = pd.DataFrame(
        {
            "level": ["Test", "Assertion", "Generalization"],
            "total": [81810, 122153, 239],
            "included": [33385, 711, 206],
            "filtering": [40583, 121060, 23],
            "failures": [7842, 382, 10],
        }
    )
    table = build_breakdown_table(df, key="breakdown", label="tab:y", caption="C")
    row = table.df.set_index("level").loc["Test"]
    assert round(row["included_pct"], 3) == 0.408
    assert round(row["filtering_pct"], 3) == 0.496
    assert round(row["failures_pct"], 3) == 0.096
    assert [c.source for c in table.columns] == [
        "level", "total", "included", "included_pct",
        "filtering", "filtering_pct", "failures", "failures_pct",
    ]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_causes_common.py::test_breakdown_table_percentages_over_total -v`
Expected: FAIL (`ImportError`).

- [ ] **Step 3: Implement the builder**

```python
# analysis/src/teralizer/eval/reports/_causes_common.py  (append)
def build_breakdown_table(
    df: pd.DataFrame, *, key: str, label: str, caption: str
) -> Table:
    """df columns: level, total, included, filtering, failures (integer counts)."""
    out = df.copy()
    for part in ("included", "filtering", "failures"):
        out[f"{part}_pct"] = out[part] / out["total"]
    columns = [
        ColumnSpec("Level", "level"),
        ColumnSpec("Total", "total", fmt="count", align="r"),
        ColumnSpec("Included", "included", fmt="count", align="r"),
        ColumnSpec("Incl. %", "included_pct", fmt="pct1", align="r"),
        ColumnSpec("Filtering", "filtering", fmt="count", align="r"),
        ColumnSpec("Filt. %", "filtering_pct", fmt="pct1", align="r"),
        ColumnSpec("Failures", "failures", fmt="count", align="r"),
        ColumnSpec("Fail. %", "failures_pct", fmt="pct1", align="r"),
    ]
    return Table(key=key, df=out, columns=columns, caption=caption, label=label)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_causes_common.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): shared exclusion breakdown table builder" \
COMMIT_BODY="The included/filtering/failures breakdown renders identically for both causes reports, so the builder and its over-total percentages join the shared presentation module alongside the filter-rejection builder." \
bun ~/.omp/skills/commit/commit-helper.ts
```

---

## Phase 3: RQ5 report (controlled, old schema)

### Task 4: RQ5 causes report

**Files:**
- Create: `analysis/src/teralizer/eval/reports/rq5_causes.py`
- Modify: `analysis/src/teralizer/eval/reports/__init__.py` (import for registration)
- Test: `analysis/tests/eval/test_rq5_causes.py`

RQ5's query layer reproduces the legacy old-schema retrieval:
- Filtering frame: the `get_filtering_exclusions_data` CTE (L179-226) — level from which
  `filter_result` FK is non-null, short filter name via
  `substring(fr.filter_name from 'filter\.(\w+)Filter$')`, `decision` counts, `reject > 0`
  rows only, scoped to `project.use_test_generalization`. Rename `UnsupportedAssertion` ->
  `AssertionType` (legacy `compute_filtering_exclusions_summary`).
- Breakdown frame: `compute_exclusion_breakdown_filtering_vs_failures` (L869-1080) — tests
  and assertions categorized by `is_included` then `exclusion_info LIKE '%TestFilteringTask%'`
  with a matching `filter_result ... decision='REJECT'` (Filtering) versus other exclusion
  (Failures); generalizations from `mv_exclusions_all` `excluded_by`.

RQ5 reads `postgres_dev`. Required objects for validation: `project(id,
use_test_generalization)`, `test(id, project_id, is_included, exclusion_info)`,
`assertion(id, test_id, is_included, exclusion_info)`, `generalization(id, variant,
is_included, exclusion_info)`, `filter_result(filter_name, decision, test_id,
assertion_id, generalization_id, project_id)`, and view `mv_exclusions_all(level,
is_included, excluded_by, count)`.

- [ ] **Step 1: Write the failing test**

```python
# analysis/tests/eval/test_rq5_causes.py
import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.model import RQReport
from teralizer.eval.registry import get
import teralizer.eval.reports.rq5_causes  # noqa: F401  (registers "rq5")


def _report() -> RQReport:
    spec = get("rq5")
    try:
        with connect(
            spec.default_db, validate_schema=True, require=spec.requires
        ) as conn:
            return spec.build(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")
    except RuntimeError as exc:
        pytest.skip(f"schema unavailable: {exc}")


def test_rq5_has_breakdown_and_filtering_tables():
    report = _report()
    assert report.rq == "rq5"
    assert report.db == "postgres_dev"
    labels = {t.label for t in report.tables()}
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)


def test_rq5_breakdown_levels_and_nonnegative_counts():
    report = _report()
    breakdown = next(t for t in report.tables() if "breakdown" in t.label)
    assert set(breakdown.df["level"]) <= {"Test", "Assertion", "Generalization"}
    for col in ("total", "included", "filtering", "failures"):
        assert (breakdown.df[col] >= 0).all()
        # included + filtering + failures == total, per level
    reconstructed = breakdown.df[["included", "filtering", "failures"]].sum(axis=1)
    assert (reconstructed == breakdown.df["total"]).all()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_rq5_causes.py -v`
Expected: FAIL (`ModuleNotFoundError: teralizer.eval.reports.rq5_causes`).

- [ ] **Step 3: Implement the RQ5 report**

Write `rq5_causes.py` with:
- `REQUIRES: tuple[Required, ...]` (the objects listed above).
- `_fetch_filtering(conn) -> pd.DataFrame` returning the normalized filtering frame
  (`level, filter, total, accept, defer, reject`) via the legacy CTE.
- `_fetch_breakdown(conn) -> pd.DataFrame` returning `(level, total, included, filtering,
  failures)` via the legacy categorization.
- `build(conn) -> RQReport` that assembles both tables through `_causes_common`, attaches
  `provenance.capture(_fetch_*, query=<sql>)`, and returns the report with a short prose
  section and any headline `Metric`s (e.g. `controlled.assertion_included_pct`).
- Module-bottom `register("rq5", ReportSpec(build, "postgres_dev", "old", REQUIRES))`.

Add to `reports/__init__.py`: `from teralizer.eval.reports import rq5_causes  # noqa: F401`.

The SQL is a direct port of `get_filtering_exclusions_data` (L179-226) and
`compute_exclusion_breakdown_filtering_vs_failures` (L899-1079); reproduce those queries,
dropping the excluded-project injection (empty on `postgres_dev`) but keeping
`WHERE p.use_test_generalization`.

- [ ] **Step 4: Run test to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_rq5_causes.py -v`
Expected: PASS (or skip without database).

- [ ] **Step 5: Smoke the report end to end**

Run: `uv run --directory analysis python -m teralizer.eval rq5 --targets md`
Expected: writes `analysis/reports/rq5.md` with both tables; no exception.

- [ ] **Step 6: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): RQ5 controlled-conditions causes report" \
COMMIT_BODY="RQ5 is the first old-schema report. It reproduces the controlled dataset exclusion breakdown and filter-rejection tables from postgres_dev through the shared presentation builders, validating the connection against the exact objects and columns it reads." \
bun ~/.omp/skills/commit/commit-helper.ts
```

---

## Phase 4: RQ6 funnel + report (real-world, new schema)

### Task 5: Structured cause taxonomy (`_taxonomy.py`)

**Files:**
- Create: `analysis/src/teralizer/eval/reports/_taxonomy.py`
- Test: `analysis/tests/eval/test_taxonomy.py`

Pure, DB-free classification. Ports `stages.py`'s paper-stage grouping and
`categorize_failure_type`'s Internal/External/Mixed rule into structured predicates over a
per-project attribution record.

- [ ] **Step 1: Write the failing test**

```python
# analysis/tests/eval/test_taxonomy.py
from teralizer.eval.reports._taxonomy import (
    UNCODED,
    Attribution,
    classify,
    paper_stage,
    STAGE_ORDER,
)


def test_paper_stage_grouping():
    assert paper_stage("SETUP_PROJECT") == "1 + 2"
    assert paper_stage("EXECUTE_JPF") == "3"
    assert paper_stage("FILTER_GENERALIZATIONS") == "4"
    assert paper_stage("COLLECT_PIT_DATA_GENERALIZED") == "5"
    assert STAGE_ORDER["1 + 2"] < STAGE_ORDER["3"] < STAGE_ORDER["4"] < STAGE_ORDER["5"]


def test_execute_tests_original_timeout_vs_error():
    timeout = Attribution("EXECUTE_TESTS_ORIGINAL", None, at_ceiling=True,
                          included_tests=5, included_assertions=5)
    err = Attribution("EXECUTE_TESTS_ORIGINAL", None, at_ceiling=False,
                      included_tests=5, included_assertions=5)
    assert classify(timeout).type == "Internal"
    assert "timeout" in classify(timeout).cause
    assert classify(err).type == "External"
    assert "JUnit" in classify(err).cause


def test_no_input_spec_reattributes_upstream():
    all_tests = Attribution("ANALYZE_JPF", "NO_INPUT_SPEC", at_ceiling=False,
                            included_tests=0, included_assertions=0)
    all_asserts = Attribution("ANALYZE_JPF", "NO_INPUT_SPEC", at_ceiling=False,
                              included_tests=4, included_assertions=0,
                              assertion_exclusions_all_filtered=True)
    assert classify(all_tests).stage == "1 + 2"
    assert "all tests excluded" in classify(all_tests).cause
    assert classify(all_asserts).stage == "1 + 2"
    assert "all assertions excluded" in classify(all_asserts).cause
    assert classify(all_asserts).type == "Mixed"


def test_spoon_error_external():
    a = Attribution("BUILD_SPOON_MODEL", None, at_ceiling=False,
                    included_tests=0, included_assertions=0)
    assert classify(a).type == "External"
    assert "Spoon" in classify(a).cause


def test_unknown_signal_is_uncoded():
    a = Attribution("SOME_FUTURE_STAGE", "NEW_CODE", at_ceiling=False,
                    included_tests=1, included_assertions=1)
    assert classify(a) is UNCODED
```

- [ ] **Step 2: Run test to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_taxonomy.py -v`
Expected: FAIL (`ImportError`).

- [ ] **Step 3: Implement the taxonomy**

```python
# analysis/src/teralizer/eval/reports/_taxonomy.py
"""Structured cause taxonomy for the RQ6 project-level funnel.

Replaces the legacy free-text regex classifier and stage-remap hacks with rules
over structured signals: internal stage, task_diagnostic reason_code, whether the
failing task hit its runtime ceiling, and per-project inclusion counts. Every
attribution resolves to a Cause or to UNCODED (which the funnel treats as a
loud defect, never a silent bucket)."""

from __future__ import annotations

from dataclasses import dataclass, field

# internal stage -> paper stage (ports stages.py; five stages, 1 and 2 combined)
_STAGE_1_2 = {
    "SETUP_PROJECT", "ADD_DEPENDENCIES", "BUILD_PROJECT_ORIGINAL", "BUILD_SPOON_MODEL",
    "EXECUTE_TESTS_ORIGINAL", "COLLECT_JUNIT_REPORTS_ORIGINAL",
    "COLLECT_JACOCO_DATA_ORIGINAL", "FILTER_TESTS_ORIGINAL", "ANALYZE_TESTS",
    "FILTER_TESTS", "FILTER_ASSERTIONS",
}
_STAGE_3 = {
    "ADD_JPF_INSTRUMENTATION", "BUILD_PROJECT_INSTRUMENTED", "EXECUTE_JPF", "ANALYZE_JPF",
    "CLEANUP_JPF_INSTRUMENTATION", "BUILD_PROJECT_INITIAL", "EXECUTE_TESTS_INITIAL",
    "COLLECT_JUNIT_REPORTS_INITIAL",
}
_STAGE_4 = {
    "CLEANUP_GENERALIZATION", "GENERALIZE_TESTS", "BUILD_PROJECT_GENERALIZED",
    "EXECUTE_TESTS_GENERALIZED", "COLLECT_JUNIT_REPORTS_GENERALIZED",
    "FILTER_GENERALIZATIONS",
}
_STAGE_5 = {
    "COLLECT_PIT_DATA_ORIGINAL", "COLLECT_JACOCO_DATA_INITIAL", "COLLECT_PIT_DATA_INITIAL",
    "RESTORE_GENERALIZED_BUILD", "COLLECT_JACOCO_DATA_GENERALIZED",
    "COLLECT_PIT_DATA_GENERALIZED",
}
STAGE_ORDER = {"1 + 2": 0, "3": 1, "4": 2, "5": 3}


def paper_stage(internal_stage: str) -> str | None:
    for group, members in (
        ("1 + 2", _STAGE_1_2), ("3", _STAGE_3), ("4", _STAGE_4), ("5", _STAGE_5)
    ):
        if internal_stage in members:
            return group
    return None


@dataclass(frozen=True)
class Attribution:
    """Structured signals for one excluded project's terminal failure."""

    internal_stage: str
    reason_code: str | None
    at_ceiling: bool                       # task.runtime >= configured stage ceiling
    included_tests: int
    included_assertions: int
    included_generalizations: int = 0
    assertion_exclusions_all_filtered: bool = False  # every excluded assertion via filter REJECT
    artifact_present: bool = True          # collection-stage artifact table has a row


@dataclass(frozen=True)
class Cause:
    stage: str
    cause: str
    type: str  # "Internal" | "External" | "Mixed"


UNCODED = Cause(stage="?", cause="UNCODED", type="?")


def classify(a: Attribution) -> Cause:
    stage = paper_stage(a.internal_stage)
    if stage is None:
        return UNCODED

    # Downstream "no input" symptom -> re-attribute to the emptying stage.
    if a.reason_code == "NO_INPUT_SPEC":
        if a.included_tests == 0:
            return Cause("1 + 2", "all tests excluded due to filter rejections and failures", "Mixed")
        if a.included_assertions == 0:
            up_stage = "1 + 2" if a.assertion_exclusions_all_filtered else "3"
            return Cause(up_stage, "all assertions excluded due to filter rejections", "Mixed")
        return UNCODED

    # Stage-1+2 execution failures.
    if a.internal_stage == "EXECUTE_TESTS_ORIGINAL":
        if a.at_ceiling:
            return Cause("1 + 2", "timeout exceeded (60 seconds per original test suite)", "Internal")
        return Cause("1 + 2", "JUnit execution error during test execution", "External")
    if a.internal_stage == "BUILD_SPOON_MODEL":
        return Cause("1 + 2", "Spoon execution error during test analysis", "External")
    if a.internal_stage == "COLLECT_JUNIT_REPORTS_ORIGINAL":
        return Cause("1 + 2", "JUnit reports not found", "Internal")
    if a.internal_stage == "BUILD_PROJECT_INSTRUMENTED" and a.reason_code in {
        "OTHER_COMPILE_FAILURE", "TEST_COMPILE_OUTPUT_MISSING",
    }:
        return Cause("1 + 2", "compilation outputs not found", "Internal")

    # Stage-3 failures.
    if a.internal_stage in {"ADD_JPF_INSTRUMENTATION"}:
        return Cause("3", "Spoon execution error during test instrumentation", "External")
    if a.internal_stage == "EXECUTE_TESTS_INITIAL" and a.at_ceiling:
        return Cause("3", "timeout exceeded (60 seconds per initial test suite)", "Internal")

    # Stage-4 failures.
    if a.internal_stage in {"FILTER_GENERALIZATIONS", "BUILD_PROJECT_GENERALIZED",
                            "EXECUTE_TESTS_GENERALIZED"} and a.included_generalizations == 0:
        return Cause("4", "all generalizations excluded due to filter rejections and failures", "Internal")

    # Stage-5 failures (mutation + coverage).
    if a.internal_stage in {"COLLECT_JACOCO_DATA_INITIAL", "COLLECT_JACOCO_DATA_GENERALIZED"}:
        if a.at_ceiling:
            return Cause("5", "timeout exceeded (300 seconds per test suite variant)", "Internal")
        if not a.artifact_present:
            return Cause("5", "JaCoCo outputs not found", "Internal")
        return Cause("5", "JaCoCo execution error during coverage collection", "External")
    if a.internal_stage in {"COLLECT_PIT_DATA_INITIAL", "COLLECT_PIT_DATA_GENERALIZED"}:
        if a.at_ceiling:
            return Cause("5", "timeout exceeded (300 seconds per test suite variant)", "Internal")
        if a.reason_code in {"PIT_MAPPING_FAILURE"}:
            return Cause("5", "failed to process PIT reports", "Internal")
        if not a.artifact_present:
            return Cause("5", "PIT reports not found", "Internal")
        return Cause("5", "PIT execution error during mutation testing", "External")

    return UNCODED
```

The exact `reason_code` and artifact-presence signals for the Stage-5 JaCoCo/PIT rows must
be confirmed against `postgres_reporeapers` in Task 6's validation; adjust the predicates
there if the observed codes differ, keeping the `UNCODED` fallthrough.

- [ ] **Step 4: Run test to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_taxonomy.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): structured cause taxonomy for the RQ6 funnel" \
COMMIT_BODY="The project-level funnel classifies each excluded project from structured signals rather than free-text log matching. This adds the pure classifier: the paper-stage grouping, the internal or external or mixed cause rules over stage, reason code, runtime ceiling, and inclusion counts, and an uncoded sentinel so an unmapped signal surfaces instead of falling into a silent bucket." \
bun ~/.omp/skills/commit/commit-helper.ts
```

### Task 6: RQ6 project-level funnel (`_funnel.py`)

**Files:**
- Create: `analysis/src/teralizer/eval/reports/_funnel.py`
- Test: `analysis/tests/eval/test_funnel.py`

Builds, over `postgres_reporeapers`:
1. **Eligibility.** Ineligible = a project whose earliest project-level failed task is at
   `SETUP_PROJECT`, `ADD_DEPENDENCIES`, or `BUILD_PROJECT_ORIGINAL`, plus projects with zero
   actual coverage. Eligible denominator = registered projects − ineligible.
2. **Per-project attribution.** For each eligible, non-successful project, build an
   `Attribution` from its earliest project-level failed task (`stage`, `reason_code`,
   `runtime >= ceiling`) joined with per-project inclusion counts
   (`test/assertion/generalization.is_included`) and the assertion-exclusion source
   (all-filter-REJECT vs JPF failure). Success = the project reaches the terminal reduction
   stage (a `RESTORE_GENERALIZED_BUILD`/`COLLECT_PIT_DATA_GENERALIZED` success or the
   reduced-project set). Classify via `_taxonomy.classify`.
3. **Funnel arithmetic.** Per paper stage in order: `entering` (stage 1+2 = eligible; each
   later stage = previous entering − previous exclusions), `exclusions` (sum of that stage's
   cause counts), `passing = entering − exclusions`, inclusion rate. Overall row: inclusions
   = success count, rate = success / eligible.
4. **Table.** One `Table` (`tab:processing-failures`) with numbered cause rows grouped by
   stage (`group_by="stage"`), Type and Count columns, plus the per-stage band metadata as
   a `note` or as separate metrics. Headline `Metric`s: `realworld.eligible_projects`,
   `realworld.overall_inclusion_pct` (fraction, `pct1`).

The project-level terminal-failure CTE (per the schema investigation), with strict
project-level scoping:

```sql
WITH first_fail AS (
  SELECT DISTINCT ON (t.project_id)
         t.id AS task_id, t.project_id, t.stage AS internal_stage,
         t.runtime, t.step
  FROM task t
  WHERE t.test_id IS NULL AND t.assertion_id IS NULL AND t.generalization_id IS NULL
    AND t.status <> 'SUCCEEDED'
  ORDER BY t.project_id, t.step
),
coded AS (
  SELECT ff.*, td.reason_code
  FROM first_fail ff
  LEFT JOIN task_diagnostic td
    ON td.task_id = ff.task_id
   AND td.test_id IS NULL AND td.assertion_id IS NULL AND td.generalization_id IS NULL
)
SELECT * FROM coded;
```

- [ ] **Step 1: Write the failing tests (validation against the live snapshot)**

```python
# analysis/tests/eval/test_funnel.py
import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.reports import _funnel


def _funnel_result():
    try:
        with connect("postgres_reporeapers") as conn:
            return _funnel.build_funnel(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")


def test_no_uncoded_attributions():
    result = _funnel_result()
    assert result.uncoded_projects == [], (
        f"unclassified projects: {result.uncoded_projects[:10]}"
    )


def test_eligibility_audit_only_ineligible_causes_at_setup_stages():
    # Every first-failure at a fail-at-start stage must be an ineligible cause.
    # This reads task.info ONCE, at test time, purely to prove the stage-based
    # production rule drops only genuine dependency/sources/build failures.
    result = _funnel_result()
    assert result.eligibility_audit_unexpected == [], (
        f"eligible-looking failures at fail-at-start stages: "
        f"{result.eligibility_audit_unexpected[:10]}"
    )


def test_funnel_arithmetic_is_consistent():
    result = _funnel_result()
    stages = result.stages  # ordered list of stage bands
    assert stages[0].entering == result.eligible
    for prev, cur in zip(stages, stages[1:]):
        assert cur.entering == prev.entering - prev.exclusions
        assert prev.passing == prev.entering - prev.exclusions
    assert stages[-1].passing == result.success_count


def test_every_cause_row_has_a_known_type():
    result = _funnel_result()
    assert set(result.table.df["type"]) <= {"Internal", "External", "Mixed"}
    assert (result.table.df["count"] > 0).all()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `uv run --directory analysis pytest tests/eval/test_funnel.py -v`
Expected: FAIL (`AttributeError: module ... has no attribute 'build_funnel'`).

- [ ] **Step 3: Implement the funnel**

Write `_funnel.py` with a `FunnelResult` dataclass (`eligible: int`, `success_count: int`,
`stages: list[StageBand]`, `table: Table`, `uncoded_projects: list[int]`,
`eligibility_audit_unexpected: list[int]`) and `build_funnel(conn) -> FunnelResult`:
- Query the terminal-failure CTE, per-project inclusion counts, assertion-exclusion source,
  zero-coverage set, and the success set.
- Read the stage ceilings from config (60s original/initial, 300s reduction) rather than
  hard-coding literals in two places; the display strings in `_taxonomy` name the seconds.
- Build one `Attribution` per eligible non-success project, `classify`, collect `UNCODED`
  project ids into `uncoded_projects`.
- Populate `eligibility_audit_unexpected` by checking (via `task.info` at build time) that
  every first-failure at `SETUP_PROJECT`/`ADD_DEPENDENCIES`/`BUILD_PROJECT_ORIGINAL` matches
  a dependency-resolution, missing-sources, or original-build cause; append any that do not.
- Group classified causes into stage bands, compute the funnel arithmetic, and assemble the
  `Table` with `group_by="stage"` and provenance.

- [ ] **Step 4: Run tests; investigate any failure at the source**

Run: `uv run --directory analysis pytest tests/eval/test_funnel.py -v`
Expected: PASS. If `test_no_uncoded_attributions` fails, a real `(stage, reason_code)` is
unmapped — add the rule to `_taxonomy` (do not widen a catch-all). If
`test_eligibility_audit...` fails, a fail-at-start stage carries an eligible cause — decide
its funnel attribution explicitly. If arithmetic fails, the success signal or the entering
recurrence is wrong — fix the query, never the assertion.

- [ ] **Step 5: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): RQ6 project-level exclusion funnel" \
COMMIT_BODY="The real-world funnel reconstructs the per-stage inclusion and exclusion counts from structured diagnostics: an eligible denominator that drops dependency, sources, and build failures, a per-project terminal-failure attribution over stage and reason code and runtime ceiling and inclusion counts, and the five-stage arithmetic. Validation asserts no project is left unclassified and that the stage-based eligibility rule drops only genuine fail-at-start causes." \
bun ~/.omp/skills/commit/commit-helper.ts
```

### Task 7: RQ6 causes report

**Files:**
- Create: `analysis/src/teralizer/eval/reports/rq6_causes.py`
- Modify: `analysis/src/teralizer/eval/reports/__init__.py`
- Test: `analysis/tests/eval/test_rq6_causes.py`

RQ6 assembles the funnel `Table` plus the shared breakdown/filtering tables (new-schema
retrieval), and headline metrics.

RQ6's new-schema query layer:
- Filtering frame: `filter_result` grouped by derived level (which entity FK is non-null),
  short filter name, `decision` counts — same normalized shape as RQ5, scoped by
  `generalization.variant = 'IMPROVED_100_TRIES'` on the generalization level.
- Breakdown frame: per level, Included = `is_included`; Filtering = excluded with a
  `filter_result ... decision='REJECT'`; Failures = excluded otherwise. Generalization level
  scoped on the real `generalization.variant` column, categorized via typed `exclusion_info`
  (`ORACLE_NOT_WIDENABLE` etc. = Failures; `TestFilteringTask` descriptor = Filtering).

- [ ] **Step 1: Write the failing test**

```python
# analysis/tests/eval/test_rq6_causes.py
import pytest
import sqlalchemy.exc

from teralizer.eval.data import connect
from teralizer.eval.registry import get
import teralizer.eval.reports.rq6_causes  # noqa: F401  (registers "rq6")


def _report():
    spec = get("rq6")
    try:
        with connect(spec.default_db) as conn:
            return spec.build(conn)
    except sqlalchemy.exc.OperationalError:
        pytest.skip("database unavailable")


def test_rq6_has_funnel_and_shared_tables():
    report = _report()
    assert report.rq == "rq6"
    assert report.db == "postgres_reporeapers"
    labels = {t.label for t in report.tables()}
    assert any("processing-failures" in lbl for lbl in labels)
    assert any("exclusions-breakdown" in lbl for lbl in labels)
    assert any("exclusions-filtering" in lbl for lbl in labels)


def test_rq6_overall_inclusion_metric_is_a_fraction():
    report = _report()
    pct = report.metric("realworld.overall_inclusion_pct")
    assert pct.fmt == "pct1"
    assert 0.0 <= float(pct.value) <= 1.0
    eligible = report.metric("realworld.eligible_projects")
    assert int(eligible.value) > 0
```

- [ ] **Step 2: Run test to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -v`
Expected: FAIL (`ModuleNotFoundError`).

- [ ] **Step 3: Implement the RQ6 report**

Write `rq6_causes.py`: `_fetch_filtering(conn)`, `_fetch_breakdown(conn)`, and `build(conn)`
that calls `_funnel.build_funnel(conn)`, builds the shared tables via `_causes_common`,
emits `Metric`s from the funnel result (`realworld.eligible_projects`,
`realworld.overall_inclusion_pct`), attaches provenance, and returns the `RQReport`. Bottom:
`register("rq6", ReportSpec(build, "postgres_reporeapers", "new"))`. Add the import to
`reports/__init__.py`.

- [ ] **Step 4: Run test to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_causes.py -v`
Expected: PASS (or skip without database).

- [ ] **Step 5: Smoke the report end to end**

Run: `uv run --directory analysis python -m teralizer.eval rq6 --targets md,latex,figures`
Expected: writes `analysis/reports/rq6.md`, `analysis/build/.../tab-processing-failures.tex`,
and `macros.tex`; no exception. Eyeball the funnel bands against
`tab-processing-failures.tex` for structural parity (five stages, Internal/External/Mixed
types, numbered causes) — numbers are v2 and will differ from published v1.

- [ ] **Step 6: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="feat(eval): RQ6 real-world causes report" \
COMMIT_BODY="RQ6 assembles the project-level exclusion funnel with the shared exclusion breakdown and filter-rejection tables over the new-schema postgres_reporeapers snapshot, and emits the eligible-projects and overall-inclusion metrics as macros for the paper." \
bun ~/.omp/skills/commit/commit-helper.ts
```

---

## Phase 5: Consolidation and dead-code check

### Task 8: Verify suite, wire registry, prune proven-dead helpers

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/__init__.py` (confirm both imports present)
- Possibly delete: helpers grep-confirmed to have no importers

- [ ] **Step 1: Full eval test run**

Run: `uv run --directory analysis pytest tests/eval -v`
Expected: all pass or skip; no failures. The smoke test (`test_smoke.py`) now builds `rq5`
and `rq6` from the registry.

- [ ] **Step 2: Lint and types**

Run: `uv run --directory analysis ruff check reports tests/eval && uv run --directory analysis ty check src/teralizer/eval`
Expected: clean.

- [ ] **Step 3: Dead-code check (no premature deletion)**

Run: grep for importers of the legacy modules.
Command: use the grep tool for `rq4_limitations|from teralizer.exclusions|import exclusions|from teralizer.stages|import stages` across `analysis/`.
Expected: the only importers are the legacy notebook and legacy `rqN_*.py` modules, which
remain the paper's source until the migration plan. **Do not delete them here.** If grep
shows a helper with zero importers anywhere, delete only that helper and note it in the
commit body.

- [ ] **Step 4: Update the redesign spec's data-access note**

In `docs/plans/2026-07-08-evaluation-analysis-redesign.md`, the "Data access" section says
`validate_schema=True` "reuses `config.py`'s schema-object check." Edit it to describe the
per-report required-object validation this plan implemented (existence + columns), so the
spec matches reality. Forward-looking edit, no changelog line.

- [ ] **Step 5: Commit**

```bash
COMMIT_ACTION=commit \
COMMIT_SUBJECT="docs(eval): align redesign spec with per-report validation" \
COMMIT_BODY="The old-schema reports validate against per-report required objects and columns rather than the retired working-tree schema parse, so the redesign spec's data-access note is updated to match. The legacy causes modules stay until the migration plan retires the notebooks." \
bun ~/.omp/skills/commit/commit-helper.ts
```

---

## Acceptance criteria

- `rq5` and `rq6` register and build; `python -m teralizer.eval rq5` and `... rq6` write
  `analysis/reports/rq5.md` / `rq6.md` plus the paper-track `.tex`/`macros.tex` under
  `analysis/build/`.
- RQ5 reproduces the controlled exclusion breakdown and filter-rejection tables from
  `postgres_dev`, validated against the exact objects and columns it reads.
- RQ6 reproduces the shared breakdown/filtering tables and the project-level funnel
  (`tab-processing-failures` shape: five stages, numbered Internal/External/Mixed causes,
  entering/inclusion/exclusion bands) from structured diagnostics on `postgres_reporeapers`,
  with **no free-text regex** in the production path.
- `test_funnel.py` proves: no `UNCODED` attributions, the eligibility audit finds no
  eligible cause hiding at a fail-at-start stage, the funnel arithmetic is internally
  consistent, and every cause row is typed and positive.
- Full `pytest tests/eval` and ruff/ty are clean.
- No legacy module deleted unless grep-proven to have no importers; the notebook path still
  works.

## Self-review checklist (run after drafting, before execution)

1. **Spec coverage:** the shared `_causes_common` presentation, the RQ6 funnel with the
   eligible denominator and Internal/External/Mixed typing, RQ5 without a funnel, and the
   old-schema `connect(validate_schema=True)` all map to tasks (2-3, 5-7, 4, 1). Confirm.
2. **Structural risk:** the funnel attribution is the highest-risk piece; its validation
   tests (uncoded empty, eligibility audit, arithmetic) are the guardrails — verify each
   asserts a real invariant, not a restatement of the code.
3. **Type consistency:** `Attribution`/`Cause`/`classify` names match between `_taxonomy`
   and `_funnel`; `Required` matches between `data.py`, `registry.py`, and the report
   `REQUIRES`; metric keys (`realworld.eligible_projects`,
   `realworld.overall_inclusion_pct`) match between `_funnel`/`rq6_causes` and the tests.
