---
title: Eval Framework and RQ6 Implementation
type: plan
status: draft
created: 2026-07-08
parent: 2026-07-08-evaluation-analysis-redesign
---

# Eval Framework and RQ6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. Every test step is authored by the Tester agent per the repo rule; the code in each test step is the contract the Tester implements, not a verbatim mandate.

**Goal:** Build the `teralizer.eval` package and deliver RQ6 (real-world unsuccessful-generalization causes on `postgres_reporeapers`) end to end as a committed markdown report with raster figures and a provenance manifest.

**Architecture:** A single `build_report(conn, cfg) -> RQReport` per research question produces a render-agnostic result object (typed sections of prose, tables, figures, and metrics). Independent renderers consume that one object: `render/markdown.py` and `render/figures.py` here, `render/latex.py` in Plan 2. Compute is pure and asserted on directly; renderers are covered by golden tests.

**Tech Stack:** Python 3.13, uv, pandas, SQLAlchemy, matplotlib, PostgreSQL (`postgres_reporeapers`, new-schema structured diagnostics), ruff/ty/pytest.

**Plan sequence (redesign `2026-07-08-evaluation-analysis-redesign`):** Plan 1 (this) = framework + RQ6 + markdown/figures renderers + provenance. Plan 2 = `render/latex.py` + `macros.tex` + paper export. Plan 3 = RQ0 (JARVIS, `postgres_jarvis_scoreboard`/`_census`). Plan 4 = RQ1–5 + dataset-characteristics (old schema, `postgres_dev`). Plan 5 = doc/packaging migration + notebook retirement.

**Conventions for every task:** run commands from the repo root. Python runs under `uv run --directory analysis`. Commit with the helper (`bun skill://commit/commit-helper.ts`, env `COMMIT_ACTION`/`COMMIT_SUBJECT`/`COMMIT_BODY`), never `git commit -m` for body commits. No em-dash-free rule, but keep commit prose plain (no semicolon-chained clauses, no "operator", no temporal/marketing words). Gate each task with `uv run --directory analysis ruff check --fix . && ruff format . && ty check . && pytest <the task's tests>`.

---

## File Structure

New package `analysis/src/teralizer/eval/`. Each file has one responsibility; files that change together live together.

```
analysis/src/teralizer/eval/
  __init__.py       # exports the public types from model
  model.py          # render-agnostic result types + Provenance capture
  format.py         # named value formatters (pct1, count, int, float2, runtime)
  macros.py         # Metric.key -> LaTeX-legal macro name; dataset/tool name maps
  data.py           # connect(db, validate_schema) + read_sql helper
  provenance.py     # git commit + GitHub permalink helpers
  registry.py       # rq id -> (build_report, default_db, schema_kind)
  cli.py            # python -m teralizer.eval <rq|all> [--db] [--targets] [--paper-out]
  __main__.py       # enables python -m teralizer.eval
  plots.py          # reusable plot builders (created when a report first needs one)
  render/
    __init__.py
    markdown.py     # RQReport -> analysis/reports/<rq>.md (+ figure refs + provenance links)
    figures.py      # materialize each Figure once -> .png (committed) [+ .pdf in Plan 2]
    manifest.py     # RQReport -> provenance.json sidecar
  reports/
    __init__.py
    rq6_causes_realworld.py   # eligibility classifier + funnel/exclusion + build_report
```

Committed outputs land under `analysis/reports/` (markdown + `figures/<rq>/*.png` + `provenance.json`). `.tex`/`.pdf`/`.csv` build output (`analysis/build/`) arrives in Plan 2 and is gitignored. Kept and reused unchanged: `plotting.py` (ACM style), `report_basis.py` (`open_report_connection`), `config.py` (`db_config`). The old `rqN_*.py`, `exclusions.py`, `stages.py`, notebooks, and `reporeapers_rerun_report.py` are NOT touched in Plan 1; they are retired in later plans as each RQ is ported.

---

## Task 1: Package scaffold and result model

**Files:**
- Create: `analysis/src/teralizer/eval/__init__.py`, `analysis/src/teralizer/eval/model.py`, `analysis/src/teralizer/eval/render/__init__.py`, `analysis/src/teralizer/eval/reports/__init__.py`
- Test: `analysis/tests/eval/test_model.py`, `analysis/tests/eval/__init__.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

`analysis/tests/eval/test_model.py`:

```python
import pandas as pd
from teralizer.eval.model import (
    ColumnSpec, Figure, Metric, Prose, RQReport, Section, Table,
)


def test_rqreport_collects_metrics_by_key():
    m = Metric(key="realworld.eligible_projects", value=632, fmt="int")
    report = RQReport(
        rq="rq6", title="RQ6", db="postgres_reporeapers",
        sections=[Section(title="Overview", blocks=[Prose("Eligible: {realworld.eligible_projects}.")])],
        metrics=[m],
    )
    assert report.metric("realworld.eligible_projects").value == 632
    assert report.metric_map()["realworld.eligible_projects"] is m


def test_table_and_figure_are_frozen_and_carry_keys():
    t = Table(key="funnel", df=pd.DataFrame({"a": [1]}),
              columns=[ColumnSpec(header="A", source="a", fmt="int")],
              caption="Funnel", label="tab:rq6-funnel")
    f = Figure(key="bar", build=lambda ax: None, caption="Bar", label="fig:rq6-bar")
    assert t.key == "funnel" and f.label == "fig:rq6-bar"
    # frozen: mutation raises
    import dataclasses, pytest
    with pytest.raises(dataclasses.FrozenInstanceError):
        t.caption = "x"  # type: ignore[misc]
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_model.py -q`
Expected: FAIL — `ModuleNotFoundError: teralizer.eval`.

- [ ] **Step 3: Implement `model.py`**

`analysis/src/teralizer/eval/model.py`:

```python
"""Render-agnostic result types for the eval reports.

A report is computed once into an RQReport and rendered independently by each
renderer. Nothing here imports a renderer, pandas styling, or LaTeX.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import pandas as pd
    from matplotlib.axes import Axes

    from teralizer.eval.provenance import Provenance


@dataclass(frozen=True)
class Metric:
    """A single named scalar the report cites. `key` is semantic and stable
    (`realworld.eligible_projects_pct`), never keyed on an RQ number."""

    key: str
    value: float | int | str
    fmt: str = "int"
    provenance: "Provenance | None" = None


@dataclass(frozen=True)
class ColumnSpec:
    """One rendered table column. Formatting is defined here once and reused by
    every renderer, killing the table/CSV duplication of the old modules."""

    header: str
    source: str  # source DataFrame column name
    fmt: str = "str"
    align: str = "l"  # l | r | c


@dataclass(frozen=True)
class Table:
    key: str
    df: "pd.DataFrame"
    columns: list[ColumnSpec]
    caption: str
    label: str
    group_by: str | None = None  # column that drives midrules / section splits
    note: str | None = None
    provenance: "Provenance | None" = None


@dataclass(frozen=True)
class Figure:
    key: str
    build: "Callable[[Axes], None]"  # draws onto a single Axes
    caption: str
    label: str
    data: "pd.DataFrame | None" = None  # underlying data, for CSV export + provenance
    provenance: "Provenance | None" = None


@dataclass(frozen=True)
class Prose:
    """Markdown-flavored text. `{metric.key}` placeholders are substituted by the
    markdown renderer; the LaTeX track ignores Prose (Plan 2)."""

    text: str


Block = Prose | Table | Figure


@dataclass(frozen=True)
class Section:
    title: str
    blocks: list[Block]


@dataclass(frozen=True)
class RQReport:
    rq: str  # "rq6"
    title: str
    db: str
    sections: list[Section]
    metrics: list[Metric] = field(default_factory=list)

    def metric_map(self) -> dict[str, Metric]:
        out: dict[str, Metric] = {}
        for m in self.metrics:
            if m.key in out:
                raise ValueError(f"duplicate metric key: {m.key}")
            out[m.key] = m
        return out

    def metric(self, key: str) -> Metric:
        return self.metric_map()[key]

    def tables(self) -> list[Table]:
        return [b for s in self.sections for b in s.blocks if isinstance(b, Table)]

    def figures(self) -> list[Figure]:
        return [b for s in self.sections for b in s.blocks if isinstance(b, Figure)]
```

`analysis/src/teralizer/eval/__init__.py`:

```python
"""Pure-Python evaluation reports: compute once, render independently."""

from teralizer.eval.model import (
    ColumnSpec,
    Figure,
    Metric,
    Prose,
    RQReport,
    Section,
    Table,
)

__all__ = [
    "ColumnSpec",
    "Figure",
    "Metric",
    "Prose",
    "RQReport",
    "Section",
    "Table",
]
```

Create empty `analysis/src/teralizer/eval/render/__init__.py`, `analysis/src/teralizer/eval/reports/__init__.py`, and `analysis/tests/eval/__init__.py`.

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_model.py -q`
Expected: PASS (2 tests).

- [ ] **Step 5: Gate and commit**

Run: `uv run --directory analysis ruff check --fix src/teralizer/eval tests/eval && ruff format src/teralizer/eval tests/eval && ty check src/teralizer/eval`
Then commit (subject `feat(eval): add render-agnostic result model`, body: one paragraph on the compute-once/render-independently contract and why the types are frozen).

---

## Task 2: Value formatters

**Files:**
- Create: `analysis/src/teralizer/eval/format.py`
- Test: `analysis/tests/eval/test_format.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
import pytest
from teralizer.eval.format import render_value


@pytest.mark.parametrize("value,fmt,expected", [
    (0.794, "pct1", "79.4%"),
    (0.7943, "pct2", "79.43%"),
    (11547, "int", "11547"),
    (3598, "count", "3,598"),
    (1.5, "float2", "1.50"),
    (3661.0, "runtime", "1h 1m 1s"),
    ("FULL", "str", "FULL"),
])
def test_render_value(value, fmt, expected):
    assert render_value(value, fmt) == expected


def test_unknown_format_raises():
    with pytest.raises(KeyError):
        render_value(1, "nope")
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_format.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `format.py`**

```python
"""Named value formatters. One source of truth used by every renderer and by
`ColumnSpec.fmt` / `Metric.fmt`."""

from __future__ import annotations

from collections.abc import Callable


def _runtime(value: float) -> str:
    total = int(round(float(value)))
    h, rem = divmod(total, 3600)
    m, s = divmod(rem, 60)
    parts = []
    if h:
        parts.append(f"{h}h")
    if h or m:
        parts.append(f"{m}m")
    parts.append(f"{s}s")
    return " ".join(parts)


_FORMATTERS: dict[str, Callable[[object], str]] = {
    "str": lambda v: str(v),
    "int": lambda v: str(int(v)),
    "count": lambda v: f"{int(v):,}",
    "pct1": lambda v: f"{float(v) * 100:.1f}%",
    "pct2": lambda v: f"{float(v) * 100:.2f}%",
    "float2": lambda v: f"{float(v):.2f}",
    "runtime": lambda v: _runtime(float(v)),
}


def render_value(value: object, fmt: str) -> str:
    if fmt not in _FORMATTERS:
        raise KeyError(f"unknown formatter: {fmt}")
    return _FORMATTERS[fmt](value)
```

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_format.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): add value formatters`).

---

## Task 3: Macro naming

**Files:**
- Create: `analysis/src/teralizer/eval/macros.py`
- Test: `analysis/tests/eval/test_macros.py`

Encodes the spec decision: LaTeX command names are letters-only, so a `Metric.key` (semantic, dotted or digit-bearing) maps to a `\newcommand`-legal, prefixed CamelCase name. Plan 2's LaTeX renderer consumes it. It is built and tested now to lock the letters-only naming decision early with a cheap, dependency-free unit.

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
import pytest
from teralizer.eval.macros import macro_name


@pytest.mark.parametrize("key,expected", [
    ("realworld.eligible_projects_pct", "TzRealworldEligibleProjectsPct"),
    ("rq6.foo", "TzRqsixFoo"),                 # digits spelled out (LaTeX-legal)
    ("controlled.mutation_score", "TzControlledMutationScore"),
])
def test_macro_name(key, expected):
    assert macro_name(key) == expected


def test_macro_name_is_letters_only():
    name = macro_name("a1.b2c3")
    assert name.isalpha(), f"{name} must be letters-only for \\newcommand"


def test_macro_name_rejects_empty():
    with pytest.raises(ValueError):
        macro_name("")
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_macros.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `macros.py`**

```python
"""Metric key -> LaTeX-legal macro name.

`\\newcommand` names must be letters-only, so digits are spelled out and every
separator becomes a CamelCase boundary. A short `Tz` prefix avoids clobbering
existing paper/package commands.
"""

from __future__ import annotations

import re

_PREFIX = "Tz"
_DIGIT_WORDS = {
    "0": "zero", "1": "one", "2": "two", "3": "three", "4": "four",
    "5": "five", "6": "six", "7": "seven", "8": "eight", "9": "nine",
}


def _spell_digits(token: str) -> str:
    return "".join(_DIGIT_WORDS.get(ch, ch) for ch in token)


def macro_name(key: str) -> str:
    tokens = [t for t in re.split(r"[^0-9a-zA-Z]+", key) if t]
    if not tokens:
        raise ValueError(f"macro key has no alphanumeric content: {key!r}")
    camel = "".join(_spell_digits(t)[:1].upper() + _spell_digits(t)[1:] for t in tokens)
    name = _PREFIX + camel
    if not name.isalpha():
        raise ValueError(f"macro name not letters-only: {name!r} (from {key!r})")
    return name
```

Note: within a token a leading digit is spelled then title-cased (`rq6` -> tokens `rq6` -> `rqsix` -> `Rqsix`); this is deterministic and letters-only, which is all the LaTeX constraint requires.

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_macros.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): map metric keys to latex-legal macros`).

---

## Task 4: Provenance capture

**Files:**
- Create: `analysis/src/teralizer/eval/provenance.py`
- Test: `analysis/tests/eval/test_provenance.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
from teralizer.eval.provenance import Provenance, capture, git_commit


def sample_fn():
    return capture(sample_fn, query="SELECT 1")


def test_capture_records_function_location_and_query():
    p = sample_fn()
    assert p.qualname == "sample_fn"
    assert p.module.endswith("test_provenance")
    assert p.lineno > 0
    assert p.query == "SELECT 1"


def test_git_commit_is_hex_maybe_dirty():
    c = git_commit()
    core = c.removesuffix("-dirty")
    assert len(core) >= 7 and all(ch in "0123456789abcdef" for ch in core)


def test_source_url_builds_permalink():
    p = Provenance(module="teralizer.eval.reports.rq6_causes_realworld",
                   qualname="build_report", lineno=42, query=None, commit="abc1234")
    url = p.source_url("https://github.com/glockyco/Teralizer")
    assert url == (
        "https://github.com/glockyco/Teralizer/blob/abc1234/"
        "analysis/src/teralizer/eval/reports/rq6_causes_realworld.py#L42"
    )
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_provenance.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `provenance.py`**

```python
"""Provenance: link every generated artifact to the exact code + commit.

Captured at build time, never hand-maintained. The commit carries a `-dirty`
suffix when the working tree has uncommitted changes, so a number is never
falsely pinned to a clean commit.
"""

from __future__ import annotations

import inspect
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

# analysis/src/teralizer/eval/provenance.py -> repo root is parents[4]
_REPO_ROOT = Path(__file__).resolve().parents[4]


@dataclass(frozen=True)
class Provenance:
    module: str
    qualname: str
    lineno: int
    query: str | None
    commit: str

    def rel_path(self) -> str:
        parts = self.module.split(".")
        return "analysis/src/" + "/".join(parts) + ".py"

    def source_url(self, repo_url: str) -> str:
        return f"{repo_url}/blob/{self.commit}/{self.rel_path()}#L{self.lineno}"


@lru_cache(maxsize=1)
def git_commit() -> str:
    head = subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd=_REPO_ROOT, capture_output=True, text=True, check=True,
    ).stdout.strip()
    dirty = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=_REPO_ROOT, capture_output=True, text=True, check=True,
    ).stdout.strip()
    return f"{head}-dirty" if dirty else head


def capture(fn: Callable[..., object], *, query: str | None = None) -> Provenance:
    module = getattr(fn, "__module__", "?")
    qualname = getattr(fn, "__qualname__", getattr(fn, "__name__", "?"))
    try:
        lineno = inspect.getsourcelines(fn)[1]
    except (OSError, TypeError):
        lineno = 0
    return Provenance(
        module=module, qualname=qualname, lineno=lineno,
        query=query, commit=git_commit(),
    )
```

Secret-hygiene guard (spec open item): `Provenance` records only `module`/`qualname`/`lineno`/`query`/`commit`; it never reads `os.environ`, the DSN, or `.env`. The `source_url` `repo_url` argument is passed in by the CLI (Task 8) from a public constant, not from the environment. This is asserted in Task 8's test.

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_provenance.py -q`
Expected: PASS. (`test_git_commit_is_hex_maybe_dirty` runs against the real repo.)

- [ ] **Step 5: Gate and commit** (subject `feat(eval): capture artifact provenance`).

---

## Task 5: Data access

**Files:**
- Create: `analysis/src/teralizer/eval/data.py`
- Test: `analysis/tests/eval/test_data.py`

Thin wrapper over the existing connection paths: new-schema RQs open a read-only connection via `report_basis.open_report_connection`; old-schema RQs (Plan 4) validate via `db_config`. Plan 1 uses only the new-schema path.

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
import pandas as pd
from sqlalchemy import create_engine, text
from teralizer.eval.data import read_sql


def test_read_sql_returns_dataframe():
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as conn:
        conn.execute(text("CREATE TABLE t (a INTEGER, b TEXT)"))
        conn.execute(text("INSERT INTO t VALUES (1, 'x'), (2, 'y')"))
    with engine.connect() as conn:
        df = read_sql(conn, "SELECT a, b FROM t ORDER BY a")
    assert list(df.columns) == ["a", "b"]
    assert df["a"].tolist() == [1, 2]
    engine.dispose()
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_data.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `data.py`**

```python
"""Connection resolution and a read_sql helper for eval reports."""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Connection

from teralizer.report_basis import open_report_connection


@contextmanager
def connect(db: str, *, validate_schema: bool = False) -> Iterator[Connection]:
    """Open a read-only connection to `db`.

    validate_schema is reserved for the old-schema RQs ported in Plan 4; the
    new-schema RQ0/RQ6 path uses the report_basis open connection.
    """
    if validate_schema:
        raise NotImplementedError("schema-validated connect arrives with RQ1-5 (Plan 4)")
    with open_report_connection(db) as conn:
        yield conn


def read_sql(conn: Connection, sql: str, params: dict | None = None) -> pd.DataFrame:
    return pd.read_sql_query(text(sql), conn, params=params or {})
```

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_data.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): add read-only data access`).

---

## Task 6: RQ6 eligibility classifier (the spike)

**Files:**
- Create: `analysis/src/teralizer/eval/reports/rq6_causes_realworld.py` (first function only)
- Test: `analysis/tests/eval/test_rq6_eligibility.py`

This is the one novel load-bearing computation. Eligibility partitions the corpus's projects into "ineligible" (failed on their own build/dependency/compile/test-execution before our pipeline could be measured) versus "eligible" (reached the point where Teralizer is what is being measured). It is derived from the per-project stage outcomes in the `task` table (see `reporeapers_rerun_report.py:408-421`, `get_project_stage_summary`), not from free-text.

A project's terminal outcome is the furthest stage it reached. A project is **ineligible** if its furthest-reached stage is one of the "their-fault" stages: `SETUP_PROJECT`, `BUILD_PROJECT_ORIGINAL`, `EXECUTE_TESTS_ORIGINAL`, `BUILD_SPOON_MODEL` (their sources do not model/compile). It is **eligible** once it reaches `EXTRACT_SPECIFICATIONS` or later (our pipeline is now the thing under test). The stage order is the `ProcessingStage` enum mirrored in `stages.py`; this task pins the "their-fault" set explicitly and a test locks it.

- [ ] **Step 1: Write the failing test** (Tester agent)

`analysis/tests/eval/test_rq6_eligibility.py` builds an in-memory `task` table (columns `id, project_id, stage, status, step, test_id, assertion_id, generalization_id`) with a few projects at different furthest stages, then asserts the partition:

```python
import pandas as pd
from sqlalchemy import create_engine, text
from teralizer.eval.reports import rq6_causes_realworld as rq6

_TASK_DDL = """
CREATE TABLE task (
  id INTEGER PRIMARY KEY, project_id INTEGER, stage TEXT, status TEXT, step INTEGER,
  test_id INTEGER, assertion_id INTEGER, generalization_id INTEGER
)
"""


def _seed(rows):
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as conn:
        conn.execute(text(_TASK_DDL))
        for i, (pid, stage, status, step) in enumerate(rows):
            conn.execute(text(
                "INSERT INTO task VALUES (:id,:p,:s,:st,:step,NULL,NULL,NULL)"
            ), {"id": i, "p": pid, "s": stage, "st": status, "step": step})
    return engine


def test_project_ineligible_when_furthest_stage_is_their_fault():
    # p1 stops at SETUP_PROJECT (classpath); p2 reaches EXTRACT_SPECIFICATIONS.
    engine = _seed([
        (1, "SETUP_PROJECT", "FAILED", 1),
        (2, "SETUP_PROJECT", "COMPLETED", 1),
        (2, "EXTRACT_SPECIFICATIONS", "COMPLETED", 5),
    ])
    with engine.connect() as conn:
        elig = rq6.project_eligibility(conn)
    engine.dispose()
    by_project = dict(zip(elig["project_id"], elig["eligible"]))
    assert by_project[1] is False
    assert by_project[2] is True


def test_eligible_count_is_reported_and_total_preserved():
    engine = _seed([
        (1, "SETUP_PROJECT", "FAILED", 1),
        (2, "BUILD_PROJECT_ORIGINAL", "FAILED", 2),
        (3, "EXTRACT_SPECIFICATIONS", "COMPLETED", 5),
        (4, "GENERATE_TESTS", "COMPLETED", 7),
    ])
    with engine.connect() as conn:
        elig = rq6.project_eligibility(conn)
    engine.dispose()
    assert len(elig) == 4  # every project preserved
    assert int(elig["eligible"].sum()) == 2  # only p3, p4 eligible
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_eligibility.py -q`
Expected: FAIL — `AttributeError: module ... has no attribute 'project_eligibility'`.

- [ ] **Step 3: Implement `project_eligibility`**

```python
"""RQ6: real-world unsuccessful-generalization causes on postgres_reporeapers.

Eligibility partitions projects by whether they failed on their own build,
dependency, compile, or test-execution problems (ineligible) versus reaching the
point where Teralizer itself is under test (eligible). All rows are preserved in
the data; eligibility only shapes the presented denominator.
"""

from __future__ import annotations

import pandas as pd
from sqlalchemy.engine import Connection

from teralizer.eval.data import read_sql

# Stages that fail on the project's own build/deps/tests, before Teralizer is
# meaningfully exercised. A project whose furthest-reached stage is one of these
# is ineligible. Kept in lockstep with the ProcessingStage enum / stages.py.
THEIR_FAULT_STAGES = frozenset({
    "SETUP_PROJECT",
    "BUILD_PROJECT_ORIGINAL",
    "EXECUTE_TESTS_ORIGINAL",
    "BUILD_SPOON_MODEL",
})


def project_eligibility(conn: Connection) -> pd.DataFrame:
    """One row per project: project_id, furthest_stage, furthest_step, eligible."""
    df = read_sql(
        conn,
        """
        SELECT project_id, stage, step
        FROM task
        WHERE test_id IS NULL AND assertion_id IS NULL AND generalization_id IS NULL
        """,
    )
    if df.empty:
        return pd.DataFrame(columns=["project_id", "furthest_stage", "furthest_step", "eligible"])
    idx = df.groupby("project_id")["step"].idxmax()
    furthest = df.loc[idx, ["project_id", "stage", "step"]].reset_index(drop=True)
    furthest = furthest.rename(columns={"stage": "furthest_stage", "step": "furthest_step"})
    furthest["eligible"] = ~furthest["furthest_stage"].isin(THEIR_FAULT_STAGES)
    return furthest
```

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_eligibility.py -q`
Expected: PASS.

- [ ] **Step 5: Validate against the real corpus (evidence, not a gate)**

Run:
```bash
docker exec -i postgres-teralizer psql -U postgres -d postgres_reporeapers -c \
"SELECT stage, count(*) FROM task WHERE test_id IS NULL AND assertion_id IS NULL \
AND generalization_id IS NULL GROUP BY stage ORDER BY 2 DESC;"
```
Confirm the `THEIR_FAULT_STAGES` names exist as `task.stage` values and the eligible count is in the expected range (the 632-vs-1161 reconciliation from the spec: total projects ~1161, eligible near ~632). If a stage name differs from the enum spelling, fix `THEIR_FAULT_STAGES` and re-run Step 4. Record the observed eligible/total in the commit body.

- [ ] **Step 6: Gate and commit** (subject `feat(eval): derive rq6 project eligibility from diagnostics`, body: the observed eligible/total counts and the their-fault stage set).

---

## Task 7: RQ6 funnel and exclusion tables

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes_realworld.py`
- Test: `analysis/tests/eval/test_rq6_queries.py`

Port the funnel queries from `reporeapers_rerun_report.py`, restricting to eligible projects. Reuse verbatim where correct, adapting only the eligible-project filter:
- `get_filter_summary` (assertion scope) — `reporeapers_rerun_report.py:424-473`.
- `get_project_stage_summary` — `:408-421`.
- generalization exclusions by typed `exclusion_info` — new query below.

- [ ] **Step 1: Write the failing test** (Tester agent)

`analysis/tests/eval/test_rq6_queries.py` seeds a `generalization` table (`id, project_id, is_included, exclusion_info`) across eligible and ineligible projects and asserts `generalization_exclusions` counts only eligible-project rows and groups by `exclusion_info`:

```python
import pandas as pd
from sqlalchemy import create_engine, text
from teralizer.eval.reports import rq6_causes_realworld as rq6

_GEN_DDL = """
CREATE TABLE generalization (
  id INTEGER PRIMARY KEY, project_id INTEGER, is_included BOOLEAN, exclusion_info TEXT
)
"""


def test_generalization_exclusions_group_by_typed_label():
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as conn:
        conn.execute(text(_GEN_DDL))
        conn.execute(text(
            "INSERT INTO generalization VALUES "
            "(1, 10, 0, 'ORACLE_NOT_WIDENABLE'),"
            "(2, 10, 0, 'ORACLE_NOT_WIDENABLE'),"
            "(3, 10, 1, NULL),"
            "(4, 99, 0, 'NO_INPUT_SPEC')"  # project 99 is ineligible
        ))
    eligible = pd.DataFrame({"project_id": [10], "eligible": [True]})
    with engine.connect() as conn:
        df = rq6.generalization_exclusions(conn, eligible)
    engine.dispose()
    counts = dict(zip(df["exclusion_info"], df["count"]))
    assert counts["ORACLE_NOT_WIDENABLE"] == 2
    assert "NO_INPUT_SPEC" not in counts  # ineligible project excluded from denominator
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_queries.py -q`
Expected: FAIL — `AttributeError: ... 'generalization_exclusions'`.

- [ ] **Step 3: Implement the eligible-scoped queries**

Add to `rq6_causes_realworld.py`:

```python
def _eligible_ids(eligibility: pd.DataFrame) -> list[int]:
    return eligibility.loc[eligibility["eligible"], "project_id"].astype(int).tolist()


def generalization_exclusions(conn: Connection, eligibility: pd.DataFrame) -> pd.DataFrame:
    """Excluded generalizations by typed exclusion_info, eligible projects only."""
    ids = _eligible_ids(eligibility)
    if not ids:
        return pd.DataFrame(columns=["exclusion_info", "count"])
    placeholders = ",".join(str(i) for i in ids)
    return read_sql(
        conn,
        f"""
        SELECT coalesce(exclusion_info, '<unlabeled>') AS exclusion_info,
               count(*) AS count
        FROM generalization
        WHERE NOT is_included
          AND project_id IN ({placeholders})
        GROUP BY exclusion_info
        ORDER BY count DESC
        """,
    )
```

(Integer ids are formatted directly into the `IN (...)` list; they come from the DB, never user input. Do not use this pattern for text values.)

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_queries.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): add eligible-scoped rq6 exclusion query`).

---

## Task 8: build_report, registry, CLI

**Files:**
- Modify: `analysis/src/teralizer/eval/reports/rq6_causes_realworld.py` (add `build_report`)
- Create: `analysis/src/teralizer/eval/registry.py`, `analysis/src/teralizer/eval/cli.py`
- Test: `analysis/tests/eval/test_rq6_report.py`, `analysis/tests/eval/test_cli.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

`test_rq6_report.py` asserts on the RQReport, not rendered strings — seed eligibility + generalization tables, call `build_report(conn)`, and check the metric and a table:

```python
from teralizer.eval.reports import rq6_causes_realworld as rq6
from teralizer.eval.model import RQReport, Table


def test_build_report_exposes_eligible_metric_and_exclusion_table(rq6_conn):
    report = rq6.build_report(rq6_conn)
    assert isinstance(report, RQReport) and report.rq == "rq6"
    assert report.metric("realworld.eligible_projects").value == 1  # from fixture
    excl = next(t for t in report.tables() if t.key == "generalization_exclusions")
    assert isinstance(excl, Table) and "ORACLE_NOT_WIDENABLE" in excl.df["exclusion_info"].tolist()
```

`rq6_conn` is a fixture in `analysis/tests/eval/conftest.py` building the `task` + `generalization` tables used above (Tester authors it; reuse the DDL from Tasks 6–7).

`test_cli.py` asserts provenance never touches the environment:

```python
import inspect
from teralizer.eval import provenance


def test_provenance_module_never_reads_environment():
    src = inspect.getsource(provenance)
    assert "environ" not in src and "getenv" not in src and ".env" not in src
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_report.py tests/eval/test_cli.py -q`
Expected: FAIL — `AttributeError: ... 'build_report'`.

- [ ] **Step 3: Implement `build_report`, `registry.py`, `cli.py`**

`build_report` in `rq6_causes_realworld.py`:

```python
from teralizer.eval.model import ColumnSpec, Metric, Prose, RQReport, Section, Table
from teralizer.eval.provenance import capture

DEFAULT_DB = "postgres_reporeapers"


def build_report(conn: Connection) -> RQReport:
    eligibility = project_eligibility(conn)
    eligible = int(eligibility["eligible"].sum())
    total = int(len(eligibility))
    exclusions = generalization_exclusions(conn, eligibility)

    metrics = [
        Metric("realworld.eligible_projects", eligible, "int",
               provenance=capture(project_eligibility)),
        Metric("realworld.total_projects", total, "int",
               provenance=capture(project_eligibility)),
        Metric("realworld.eligible_projects_pct",
               (eligible / total) if total else 0.0, "pct1",
               provenance=capture(project_eligibility)),
    ]
    exclusion_table = Table(
        key="generalization_exclusions", df=exclusions,
        columns=[
            ColumnSpec("Exclusion", "exclusion_info", "str"),
            ColumnSpec("Count", "count", "count", align="r"),
        ],
        caption="Generalizations excluded by typed reason, eligible projects.",
        label="tab:rq6-exclusions",
        provenance=capture(generalization_exclusions),
    )
    overview = Section("Overview", [
        Prose(
            "Of {realworld.total_projects} real-world projects, "
            "{realworld.eligible_projects} ({realworld.eligible_projects_pct}) are "
            "eligible: they reach specification extraction rather than failing on "
            "their own build or tests."
        ),
    ])
    causes = Section("Unsuccessful-generalization causes", [exclusion_table])
    return RQReport(rq="rq6", title="RQ6: Real-World Unsuccessful-Generalization Causes",
                    db=DEFAULT_DB, sections=[overview, causes], metrics=metrics)
```

`registry.py`:

```python
"""rq id -> how to build and where to read it."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy.engine import Connection

from teralizer.eval.model import RQReport
from teralizer.eval.reports import rq6_causes_realworld


@dataclass(frozen=True)
class ReportSpec:
    build: Callable[[Connection], RQReport]
    default_db: str
    schema: str  # "new" | "old"


REPORTS: dict[str, ReportSpec] = {
    "rq6": ReportSpec(rq6_causes_realworld.build_report,
                      rq6_causes_realworld.DEFAULT_DB, "new"),
}


def get(rq: str) -> ReportSpec:
    if rq not in REPORTS:
        raise KeyError(f"unknown report: {rq} (known: {sorted(REPORTS)})")
    return REPORTS[rq]
```

`cli.py`:

```python
"""python -m teralizer.eval <rq|all> [--db NAME] [--targets md,figures]"""

from __future__ import annotations

import argparse
from pathlib import Path

from teralizer.eval import registry
from teralizer.eval.data import connect
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import markdown as markdown_renderer

REPO_URL = "https://github.com/glockyco/Teralizer"
REPORTS_DIR = Path(__file__).resolve().parents[3] / "reports"


def _build_and_render(rq: str, db: str | None, targets: set[str]) -> None:
    spec = registry.get(rq)
    with connect(db or spec.default_db, validate_schema=(spec.schema == "old")) as conn:
        report = spec.build(conn)
    if "figures" in targets:
        figures_renderer.materialize(report, REPORTS_DIR / "figures" / rq)
    if "md" in targets:
        markdown_renderer.render(report, REPORTS_DIR, repo_url=REPO_URL)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.eval")
    parser.add_argument("rq", help="report id or 'all'")
    parser.add_argument("--db", default=None, help="override the report's default DB")
    parser.add_argument("--targets", default="md,figures",
                        help="comma-separated: md,figures")
    args = parser.parse_args(argv)
    targets = {t.strip() for t in args.targets.split(",") if t.strip()}
    rqs = sorted(registry.REPORTS) if args.rq == "all" else [args.rq]
    for rq in rqs:
        _build_and_render(rq, args.db, targets)


if __name__ == "__main__":
    main()
```

Create `analysis/src/teralizer/eval/__main__.py` with `from teralizer.eval.cli import main; main()`.

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_rq6_report.py tests/eval/test_cli.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): assemble rq6 report with registry and cli`).

---

## Task 9: Markdown renderer

**Files:**
- Create: `analysis/src/teralizer/eval/render/markdown.py`
- Test: `analysis/tests/eval/test_render_markdown.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

Golden-style: build a small RQReport fixture, render to a string, assert structure (heading, metric substitution, GitHub table, provenance link with commit):

```python
import pandas as pd
from teralizer.eval.model import (ColumnSpec, Metric, Prose, RQReport, Section, Table)
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.markdown import render_str


def _report():
    prov = Provenance("teralizer.eval.reports.rq6_causes_realworld",
                      "generalization_exclusions", 50, None, "abc1234")
    t = Table(key="x", df=pd.DataFrame({"exclusion_info": ["A"], "count": [3598]}),
              columns=[ColumnSpec("Reason", "exclusion_info", "str"),
                       ColumnSpec("Count", "count", "count", align="r")],
              caption="Cap", label="tab:x", provenance=prov)
    return RQReport("rq6", "RQ6 Title", "postgres_reporeapers",
                    [Section("Overview", [Prose("Total {m.total}."), t])],
                    metrics=[Metric("m.total", 3598, "count")])


def test_render_substitutes_metrics_and_renders_table_and_provenance():
    out = render_str(_report(), repo_url="https://github.com/glockyco/Teralizer")
    assert "# RQ6 Title" in out
    assert "Total 3,598." in out                       # metric substituted + formatted
    assert "| Reason | Count |" in out                 # GitHub table header
    assert "| A | 3,598 |" in out
    assert "blob/abc1234/analysis/src/teralizer/eval/reports/rq6_causes_realworld.py#L50" in out
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_render_markdown.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `markdown.py`**

```python
"""Render an RQReport to a human-facing markdown report."""

from __future__ import annotations

import re
from pathlib import Path

from teralizer.eval.format import render_value
from teralizer.eval.model import Figure, Prose, RQReport, Section, Table

_PLACEHOLDER = re.compile(r"\{([a-zA-Z0-9_.]+)\}")


def _substitute(text: str, metrics: dict) -> str:
    def repl(match: re.Match[str]) -> str:
        key = match.group(1)
        if key not in metrics:
            return match.group(0)
        m = metrics[key]
        return render_value(m.value, m.fmt)
    return _PLACEHOLDER.sub(repl, text)


def _table_md(table: Table, repo_url: str) -> str:
    headers = [c.header for c in table.columns]
    lines = ["| " + " | ".join(headers) + " |",
             "| " + " | ".join("---" for _ in headers) + " |"]
    for _, row in table.df.iterrows():
        cells = [render_value(row[c.source], c.fmt) for c in table.columns]
        lines.append("| " + " | ".join(cells) + " |")
    block = f"**{table.caption}**\n\n" + "\n".join(lines)
    if table.note:
        block += f"\n\n_{table.note}_"
    if table.provenance:
        block += f"\n\nsource: [`{table.provenance.qualname}`]({table.provenance.source_url(repo_url)})"
    return block


def _figure_md(fig: Figure, rq: str, repo_url: str) -> str:
    block = f"![{fig.caption}](figures/{rq}/{fig.key}.png)\n\n**{fig.caption}**"
    if fig.provenance:
        block += f"\n\nsource: [`{fig.provenance.qualname}`]({fig.provenance.source_url(repo_url)})"
    return block


def render_str(report: RQReport, *, repo_url: str) -> str:
    metrics = report.metric_map()
    parts = [f"# {report.title}", f"_Source database: `{report.db}`._"]
    for section in report.sections:
        parts.append(f"## {section.title}")
        for block in section.blocks:
            if isinstance(block, Prose):
                parts.append(_substitute(block.text, metrics))
            elif isinstance(block, Table):
                parts.append(_table_md(block, repo_url))
            elif isinstance(block, Figure):
                parts.append(_figure_md(block, report.rq, repo_url))
    return "\n\n".join(parts) + "\n"


def render(report: RQReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    out = reports_dir / f"{report.rq}.md"
    out.write_text(render_str(report, repo_url=repo_url), encoding="utf-8")
    return out
```

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_render_markdown.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): render reports to markdown`).

---

## Task 10: Figures renderer

**Files:**
- Create: `analysis/src/teralizer/eval/render/figures.py`
- Test: `analysis/tests/eval/test_render_figures.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
from teralizer.eval.model import Figure, RQReport, Section
from teralizer.eval.render.figures import materialize


def test_materialize_writes_png_per_figure(tmp_path: Path):
    def draw(ax):
        ax.bar(["a", "b"], [1, 2])
    report = RQReport("rq6", "T", "db",
                      [Section("S", [Figure("bar", draw, "cap", "fig:bar")])])
    materialize(report, tmp_path)
    assert (tmp_path / "bar.png").exists()
    assert (tmp_path / "bar.png").stat().st_size > 0
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_render_figures.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `figures.py`**

Uses the ACM style from `plotting.py` (`setup_paper_style`) and embeds the build commit in PNG metadata (the FAIR code-to-figure trick, spec provenance section):

```python
"""Materialize each Figure once to a committed PNG."""

from __future__ import annotations

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from teralizer.eval.model import RQReport
from teralizer.eval.provenance import git_commit
from teralizer.plotting import setup_paper_style


def materialize(report: RQReport, fig_dir: Path) -> list[Path]:
    setup_paper_style()
    fig_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    commit = git_commit()
    for figure in report.figures():
        fig, ax = plt.subplots()
        try:
            figure.build(ax)
            out = fig_dir / f"{figure.key}.png"
            fig.savefig(out, dpi=200, bbox_inches="tight",
                        metadata={"Comment": f"teralizer.eval {report.rq} @ {commit}"})
            written.append(out)
        finally:
            plt.close(fig)
    return written
```

If `setup_paper_style` requires arguments or is named differently, check `plotting.py` and adapt the import; the contract is "apply the shared ACM style before drawing."

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_render_figures.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): materialize figures with commit metadata`).

---

## Task 11: Provenance manifest

**Files:**
- Create: `analysis/src/teralizer/eval/render/manifest.py`
- Modify: `analysis/src/teralizer/eval/cli.py` (write the manifest)
- Test: `analysis/tests/eval/test_manifest.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
import json
from pathlib import Path
from teralizer.eval.model import Metric, RQReport, Section
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.manifest import write_manifest


def test_manifest_maps_metric_to_source(tmp_path: Path):
    prov = Provenance("teralizer.eval.reports.rq6_causes_realworld",
                      "project_eligibility", 30, "SELECT 1", "abc1234")
    report = RQReport("rq6", "T", "postgres_reporeapers", [Section("S", [])],
                      metrics=[Metric("realworld.eligible_projects", 632, "int", provenance=prov)])
    path = write_manifest(report, tmp_path, repo_url="https://github.com/glockyco/Teralizer")
    data = json.loads(Path(path).read_text())
    entry = data["metrics"]["realworld.eligible_projects"]
    assert entry["value"] == 632
    assert entry["commit"] == "abc1234"
    assert entry["qualname"] == "project_eligibility"
    assert entry["source_url"].endswith("rq6_causes_realworld.py#L30")
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_manifest.py -q`
Expected: FAIL — `ModuleNotFoundError`.

- [ ] **Step 3: Implement `manifest.py`** and wire it into `cli._build_and_render` (write `reports/provenance.json`, merging per-rq under a top-level `{rq: {...}}` so `all` accumulates):

```python
"""Machine-readable provenance sidecar: every metric/table/figure -> its source."""

from __future__ import annotations

import json
from pathlib import Path

from teralizer.eval.model import RQReport


def _entry(value, prov, repo_url: str) -> dict:
    return {
        "value": value,
        "module": prov.module,
        "qualname": prov.qualname,
        "query": prov.query,
        "commit": prov.commit,
        "source_url": prov.source_url(repo_url),
    }


def build_manifest(report: RQReport, *, repo_url: str) -> dict:
    metrics = {m.key: _entry(m.value, m.provenance, repo_url)
               for m in report.metrics if m.provenance}
    tables = {t.key: _entry(t.caption, t.provenance, repo_url)
              for t in report.tables() if t.provenance}
    figures = {f.key: _entry(f.caption, f.provenance, repo_url)
               for f in report.figures() if f.provenance}
    return {"db": report.db, "metrics": metrics, "tables": tables, "figures": figures}


def write_manifest(report: RQReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / "provenance.json"
    existing = json.loads(path.read_text()) if path.exists() else {}
    existing[report.rq] = build_manifest(report, repo_url=repo_url)
    path.write_text(json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path
```

In `cli._build_and_render`, after building `report`, add `from teralizer.eval.render.manifest import write_manifest` and call `write_manifest(report, REPORTS_DIR, repo_url=REPO_URL)` when `"md" in targets`.

- [ ] **Step 4: Run to verify it passes**

Run: `uv run --directory analysis pytest tests/eval/test_manifest.py -q`
Expected: PASS.

- [ ] **Step 5: Gate and commit** (subject `feat(eval): emit provenance manifest`).

---

## Task 12: End-to-end smoke and validate.py hook

**Files:**
- Modify: `analysis/validate.py`
- Test: manual smoke against `postgres_reporeapers`; add `analysis/tests/eval/test_smoke.py`

- [ ] **Step 1: Live smoke run**

Run: `uv run --directory analysis python -m teralizer.eval rq6`
Expected: writes `analysis/reports/rq6.md`, `analysis/reports/figures/rq6/` (any figures), and `analysis/reports/provenance.json`. Open `rq6.md` and confirm the eligible metric, the exclusion table, and the source links render. Confirm the eligible count matches Task 6 Step 5.

- [ ] **Step 2: Write a build-smoke test** (Tester agent)

`analysis/tests/eval/test_smoke.py` — skip when the DB is unreachable, else assert `build_report` returns a non-empty RQReport:

```python
import pytest
from sqlalchemy.exc import OperationalError
from teralizer.eval.data import connect
from teralizer.eval.reports import rq6_causes_realworld as rq6


def test_rq6_builds_against_live_db():
    try:
        with connect("postgres_reporeapers") as conn:
            report = rq6.build_report(conn)
    except OperationalError:
        pytest.skip("postgres_reporeapers unreachable")
    assert report.rq == "rq6"
    assert report.metric("realworld.total_projects").value > 0
```

- [ ] **Step 3: Hook the report build into `validate.py`**

Read `analysis/validate.py` to see how its checks are registered, then add this self-contained smoke check and call it alongside the existing lint/type/test steps. Do NOT remove notebook execution here -- that is Plan 5, when the last notebook is ported.

```python
def _check_eval_reports() -> None:
    import tempfile
    from pathlib import Path

    from sqlalchemy.exc import OperationalError

    from teralizer.eval import registry
    from teralizer.eval.data import connect
    from teralizer.eval.render import markdown as md

    with tempfile.TemporaryDirectory() as tmp:
        for _rq, spec in registry.REPORTS.items():
            try:
                with connect(spec.default_db, validate_schema=(spec.schema == "old")) as conn:
                    report = spec.build(conn)
            except OperationalError:
                continue  # DB unreachable in this environment; skip, do not fail
            md.render(report, Path(tmp), repo_url="https://github.com/glockyco/Teralizer")
```

- [ ] **Step 4: Run the gate**

Run: `uv run --directory analysis python validate.py --changed`
Expected: PASS including the new eval smoke.

- [ ] **Step 5: Commit** the committed report + validate hook (subject `feat(eval): wire rq6 report build into validate`, body: note that `analysis/reports/rq6.md` + figures + `provenance.json` are committed, browsable artifacts).

---

## Task 13: gitignore and README pointer

**Files:**
- Modify: `analysis/.gitignore` (ensure `build/` is ignored, `reports/` is NOT)
- Modify: `docs/plans/INDEX.md` via `omp-plans index`

- [ ] **Step 1:** Ensure `analysis/build/` is gitignored and `analysis/reports/**` is committed (add an explicit `!reports/` allow if a broad ignore would catch it). Verify with `git status --short analysis/reports`.
- [ ] **Step 2:** Run `omp-plans index && omp-plans check` from the repo root; expect `ok`.
- [ ] **Step 3: Commit** (subject `chore(eval): commit report outputs, ignore build dir`).

---

## Acceptance

Plan 1 is done when `uv run --directory analysis python -m teralizer.eval rq6` regenerates a committed `analysis/reports/rq6.md` (eligible metric + typed exclusion table + per-table source links), `analysis/reports/figures/rq6/` rasters, and `analysis/reports/provenance.json`, with every new unit tested, `validate.py --changed` green, and the eligibility count validated against `postgres_reporeapers`. No old module, notebook, or `reporeapers_rerun_report.py` is modified. The LaTeX renderer, `macros.tex`, and paper export are explicitly deferred to Plan 2.
