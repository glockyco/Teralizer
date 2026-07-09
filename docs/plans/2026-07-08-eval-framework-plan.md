---
title: Eval Framework Foundation
type: plan
status: active
created: 2026-07-08
parent: 2026-07-08-evaluation-analysis-redesign
---

# Eval Framework Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: this plan is executed via sequential subagents under `executing-plans` cadence with review checkpoints. Steps use checkbox (`- [ ]`) syntax. Test steps are authored by the Tester agent per the repo rule; the test code shown is the contract, not a verbatim mandate.

**Goal:** Build the `teralizer.eval` reporting engine: the render-agnostic result model plus every renderer (markdown, figures, LaTeX), provenance capture and manifest, and the registry/CLI/paper-export plumbing. Prove it end to end with golden tests on fixture `RQReport`s and a CLI integration test, so that each research-question report is a thin, real plan built on top.

**Architecture:** One `build_report(conn, cfg) -> RQReport` per research question produces a render-agnostic result object (typed sections of prose, tables, figures, metrics, each carrying `Provenance`). Independent renderers consume that one object: `render/markdown.py` (committed human report), `render/figures.py` (committed rasters), `render/latex.py` (paper `.tex` tables + `macros.tex`), `render/manifest.py` (machine-readable `provenance.json`). The CLI computes each report once and fans out to the requested renderers.

**Tech Stack:** Python 3.13, uv, pandas, SQLAlchemy, matplotlib, ruff/ty/pytest.

**Plan sequence (redesign `2026-07-08-evaluation-analysis-redesign`).** This is the foundation plan: the engine, no research-question reports. Every RQ is a full multi-table (and often multi-figure) report and gets its own plan on top of this engine, in this order:
1. **This plan** -- the engine.
2. **RQ5 + RQ6 (causes).** They share the test/assertion/generalization exclusion breakdowns (`_causes_common.py`); RQ6 additionally carries the project-level exclusion funnel (`tab-processing-failures`, Table 5.12) that RQ5 has no equivalent of, because RQ1-RQ5 only cover pipeline-completing projects by design.
3. **RQ0 (JARVIS)** -- `postgres_jarvis_scoreboard` + `postgres_jarvis_census`, the four axes from `docs/plans/2026-06-30-jarvis-comparison.md`.
4. **RQ1-RQ4 + dataset-characteristics** -- `postgres_dev` (old schema) reports.
5. **Documentation and packaging migration + notebook retirement.**

**Conventions (every task):** run from repo root; Python via `uv run --directory analysis <cmd>`. Commit with `bun skill://commit/commit-helper.ts` (env `COMMIT_ACTION`/`COMMIT_SUBJECT`/`COMMIT_BODY`); commit prose is plain (no semicolon-chained clauses, never "operator", no marketing/temporal words). Gate each task: `uv run --directory analysis ruff check --fix <files> && ruff format <files> && ty check <files>` then the task's pytest.

---

## Status: framework core is DONE

Committed (Phase 1, 5 commits `8b1c7b2b`..`10c23691`), 19 tests green:

- [x] **`model.py`** -- `RQReport`, `Section`, `Prose`, `Table`, `ColumnSpec`, `Figure`, `Metric` (frozen render-agnostic dataclasses). `RQReport.metric_map()`, `.metric()`, `.tables()`, `.figures()`.
- [x] **`format.py`** -- `render_value(value, fmt)` with `str`/`int`/`count`/`pct1`/`pct2`/`float2`/`runtime`.
- [x] **`macros.py`** -- `macro_name(key)` mapping a semantic `Metric.key` to a `\newcommand`-legal, `Tz`-prefixed CamelCase name (digits spelled out).
- [x] **`provenance.py`** -- `Provenance` (module, qualname, lineno, query, commit), `capture(fn, query=)`, `git_commit()` (with `-dirty`), `Provenance.source_url(repo_url)`. Reads no environment or DSN (secret hygiene).
- [x] **`data.py`** -- `connect(db, *, validate_schema)` (new-schema `open_report_connection` path implemented; old-schema validated path raises `NotImplementedError`, implemented by the RQ5 + RQ6 plan as the first old-schema report -- it must validate `postgres_dev` against the paper-tagged `10.5281/zenodo.18242626` schema, not the current one) and `read_sql(conn, sql, params)`.

Remaining work below builds the renderers and the CLI/registry/paper-export plumbing on this core.

---

## Task 1: Markdown renderer

**Files:**
- Create: `analysis/src/teralizer/eval/render/markdown.py`
- Test: `analysis/tests/eval/test_render_markdown.py`

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
import pandas as pd
from teralizer.eval.model import (ColumnSpec, Metric, Prose, RQReport, Section, Table)
from teralizer.eval.provenance import Provenance
from teralizer.eval.render.markdown import render_str


def _report():
    prov = Provenance("teralizer.eval.reports.example", "build_report", 50, None, "abc1234")
    t = Table(key="x", df=pd.DataFrame({"reason": ["A"], "count": [3598]}),
              columns=[ColumnSpec("Reason", "reason", "str"),
                       ColumnSpec("Count", "count", "count", align="r")],
              caption="Cap", label="tab:x", provenance=prov)
    return RQReport("example", "Example Title", "postgres_dev",
                    [Section("Overview", [Prose("Total {m.total}."), t])],
                    metrics=[Metric("m.total", 3598, "count")])


def test_render_substitutes_metrics_and_renders_table_and_provenance():
    out = render_str(_report(), repo_url="https://github.com/glockyco/Teralizer")
    assert "# Example Title" in out
    assert "Total 3,598." in out
    assert "| Reason | Count |" in out
    assert "| A | 3,598 |" in out
    assert "blob/abc1234/analysis/src/teralizer/eval/reports/example.py#L50" in out
```

- [ ] **Step 2: Run to verify it fails**

Run: `uv run --directory analysis pytest tests/eval/test_render_markdown.py -q`
Expected: FAIL -- `ModuleNotFoundError`.

- [ ] **Step 3: Implement `render/markdown.py`**

```python
"""Render an RQReport to a human-facing markdown report."""

from __future__ import annotations

import re
from pathlib import Path

from teralizer.eval.format import render_value
from teralizer.eval.model import Figure, Prose, RQReport, Table

_PLACEHOLDER = re.compile(r"\{([a-zA-Z0-9_.]+)\}")


def _substitute(text: str, metrics: dict) -> str:
    def repl(match: re.Match[str]) -> str:
        key = match.group(1)
        if key not in metrics:
            return match.group(0)
        m = metrics[key]
        return render_value(m.value, m.fmt)
    return _PLACEHOLDER.sub(repl, text)


def _source_link(provenance, repo_url: str) -> str:
    return f"\n\nsource: [`{provenance.qualname}`]({provenance.source_url(repo_url)})"


def _table_md(table: Table, repo_url: str) -> str:
    headers = [c.header for c in table.columns]
    lines = ["| " + " | ".join(headers) + " |",
             "| " + " | ".join("---" for _ in headers) + " |"]
    for _, row in table.df.iterrows():
        lines.append("| " + " | ".join(render_value(row[c.source], c.fmt) for c in table.columns) + " |")
    block = f"**{table.caption}**\n\n" + "\n".join(lines)
    if table.note:
        block += f"\n\n_{table.note}_"
    if table.provenance:
        block += _source_link(table.provenance, repo_url)
    return block


def _figure_md(fig: Figure, rq: str, repo_url: str) -> str:
    block = f"![{fig.caption}](figures/{rq}/{fig.key}.png)\n\n**{fig.caption}**"
    if fig.provenance:
        block += _source_link(fig.provenance, repo_url)
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

- [ ] **Step 4: Run to verify it passes** -- `uv run --directory analysis pytest tests/eval/test_render_markdown.py -q` -> PASS.
- [ ] **Step 5: Gate and commit** -- subject `feat(eval): render reports to markdown`.

---

## Task 2: Figures renderer

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
    report = RQReport("example", "T", "db",
                      [Section("S", [Figure("bar", lambda ax: ax.bar(["a", "b"], [1, 2]),
                                            "cap", "fig:bar")])])
    written = materialize(report, tmp_path)
    assert (tmp_path / "bar.png").exists() and (tmp_path / "bar.png").stat().st_size > 0
    assert written == [tmp_path / "bar.png"]
```

- [ ] **Step 2: Run to verify it fails** -- `ModuleNotFoundError`.

- [ ] **Step 3: Implement `render/figures.py`**

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

If `setup_paper_style` has a different signature, check `plotting.py` and adapt; the contract is "apply the shared ACM style before drawing."

- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Gate and commit** -- subject `feat(eval): materialize figures with commit metadata`.

---

## Task 3: LaTeX renderer and macros

**Files:**
- Create: `analysis/src/teralizer/eval/render/latex.py`
- Test: `analysis/tests/eval/test_render_latex.py`

Emits one booktabs `.tex` per `Table` and a single `macros.tex` (`\newcommand` per `Metric`, via `macros.macro_name`). Prose is ignored (the paper narrative is hand-authored). The `group_by` column drives `\midrule`s between row groups.

- [ ] **Step 1: Write the failing test** (Tester agent)

```python
import pandas as pd
from teralizer.eval.model import (ColumnSpec, Metric, RQReport, Section, Table)
from teralizer.eval.render.latex import render_macros, render_table


def test_render_table_is_booktabs_with_formatted_cells():
    t = Table(key="funnel", df=pd.DataFrame({"reason": ["A", "B"], "count": [3598, 12]}),
              columns=[ColumnSpec("Reason", "reason", "str", align="l"),
                       ColumnSpec("Count", "count", "count", align="r")],
              caption="Cap", label="tab:funnel")
    tex = render_table(t)
    assert "\\begin{tabular}{lr}" in tex
    assert "\\toprule" in tex and "\\bottomrule" in tex
    assert "Reason & Count \\\\" in tex
    assert "A & 3,598 \\\\" in tex
    assert "\\label{tab:funnel}" in tex


def test_render_macros_one_newcommand_per_metric():
    report = RQReport("rq6", "T", "db", [Section("s", [])],
                      metrics=[Metric("realworld.eligible_projects_pct", 0.794, "pct1")])
    tex = render_macros(report)
    assert "\\newcommand{\\TzRealworldEligibleProjectsPct}{79.4\\%}" in tex
```

- [ ] **Step 2: Run to verify it fails** -- `ModuleNotFoundError`.

- [ ] **Step 3: Implement `render/latex.py`**

```python
"""Render an RQReport to paper artifacts: booktabs tables + a macros file."""

from __future__ import annotations

from pathlib import Path

from teralizer.eval.format import render_value
from teralizer.eval.macros import macro_name
from teralizer.eval.model import RQReport, Table

_ALIGN = {"l": "l", "r": "r", "c": "c"}


def _cell(value: object, fmt: str) -> str:
    return render_value(value, fmt).replace("%", "\\%").replace("_", "\\_")


def render_table(table: Table) -> str:
    cols = "".join(_ALIGN[c.align] for c in table.columns)
    lines = [
        "\\begin{table}",
        f"  \\caption{{{table.caption}}}",
        f"  \\label{{{table.label}}}",
        "  \\centering",
        f"  \\begin{{tabular}}{{{cols}}}",
        "  \\toprule",
        "  " + " & ".join(c.header for c in table.columns) + " \\\\",
        "  \\midrule",
    ]
    prev_group = None
    for _, row in table.df.iterrows():
        if table.group_by is not None:
            group = row[table.group_by]
            if prev_group is not None and group != prev_group:
                lines.append("  \\midrule")
            prev_group = group
        lines.append("  " + " & ".join(_cell(row[c.source], c.fmt) for c in table.columns) + " \\\\")
    lines += ["  \\bottomrule", "  \\end{tabular}", "\\end{table}", ""]
    return "\n".join(lines)


def render_macros(report: RQReport) -> str:
    lines = [f"\\newcommand{{\\{macro_name(m.key)}}}{{{_cell(m.value, m.fmt)}}}"
             for m in report.metrics]
    return "\n".join(lines) + ("\n" if lines else "")


def render(report: RQReport, build_dir: Path) -> list[Path]:
    build_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for table in report.tables():
        out = build_dir / f"{table.key}.tex"
        out.write_text(render_table(table), encoding="utf-8")
        written.append(out)
    macros = build_dir / "macros.tex"
    macros.write_text(render_macros(report), encoding="utf-8")
    written.append(macros)
    return written
```

- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Gate and commit** -- subject `feat(eval): render paper tables and macros`.

---

## Task 4: Provenance manifest

**Files:**
- Create: `analysis/src/teralizer/eval/render/manifest.py`
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
                      "project_funnel", 30, "SELECT 1", "abc1234")
    report = RQReport("rq6", "T", "postgres_reporeapers", [Section("S", [])],
                      metrics=[Metric("realworld.eligible_projects", 632, "int", provenance=prov)])
    path = write_manifest(report, tmp_path, repo_url="https://github.com/glockyco/Teralizer")
    data = json.loads(Path(path).read_text())
    entry = data["rq6"]["metrics"]["realworld.eligible_projects"]
    assert entry["value"] == 632
    assert entry["commit"] == "abc1234"
    assert entry["qualname"] == "project_funnel"
    assert entry["source_url"].endswith("rq6_causes_realworld.py#L30")
```

- [ ] **Step 2: Run to verify it fails** -- `ModuleNotFoundError`.

- [ ] **Step 3: Implement `render/manifest.py`**

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
    return {
        "db": report.db,
        "metrics": {m.key: _entry(m.value, m.provenance, repo_url)
                    for m in report.metrics if m.provenance},
        "tables": {t.key: _entry(t.caption, t.provenance, repo_url)
                   for t in report.tables() if t.provenance},
        "figures": {f.key: _entry(f.caption, f.provenance, repo_url)
                    for f in report.figures() if f.provenance},
    }


def write_manifest(report: RQReport, reports_dir: Path, *, repo_url: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / "provenance.json"
    existing = json.loads(path.read_text()) if path.exists() else {}
    existing[report.rq] = build_manifest(report, repo_url=repo_url)
    path.write_text(json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path
```

- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Gate and commit** -- subject `feat(eval): emit provenance manifest`.

---

## Task 5: Registry, CLI, and paper export

**Files:**
- Create: `analysis/src/teralizer/eval/registry.py`, `analysis/src/teralizer/eval/cli.py`, `analysis/src/teralizer/eval/__main__.py`
- Test: `analysis/tests/eval/test_registry.py`, `analysis/tests/eval/test_cli.py`

The registry starts empty; each report plan registers its `build_report`. The CLI computes each requested report once and fans out: markdown + figures + manifest into `analysis/reports/` (committed), LaTeX tables + `macros.tex` into `analysis/build/` and, when `--paper-out` (or `PAPER_REPO_PATH`) is set, into the paper repo.

- [ ] **Step 1: Write the failing tests** (Tester agent)

`test_registry.py`:

```python
import pytest
from teralizer.eval import registry
from teralizer.eval.model import RQReport, Section


def test_get_unknown_raises():
    with pytest.raises(KeyError):
        registry.get("nope")


def test_register_and_get(monkeypatch):
    spec = registry.ReportSpec(lambda conn: RQReport("t", "T", "db", [Section("s", [])]),
                               "postgres_dev", "old")
    monkeypatch.setitem(registry.REPORTS, "t", spec)
    assert registry.get("t") is spec
```

`test_cli.py` (fixture report + provenance hygiene):

```python
import inspect
from pathlib import Path
import pandas as pd
from teralizer.eval import cli, provenance, registry
from teralizer.eval.model import ColumnSpec, Metric, RQReport, Section, Table


def _fixture_report(_conn):
    t = Table(key="k", df=pd.DataFrame({"a": [1]}),
              columns=[ColumnSpec("A", "a", "int")], caption="c", label="tab:k")
    return RQReport("smoke", "Smoke", "sqlite", [Section("s", [t])],
                    metrics=[Metric("smoke.n", 1, "int")])


def test_provenance_module_never_reads_environment():
    src = inspect.getsource(provenance)
    assert "environ" not in src and "getenv" not in src and ".env" not in src


def test_cli_fans_out_to_targets(monkeypatch, tmp_path):
    import contextlib

    @contextlib.contextmanager
    def fake_connect(db, *, validate_schema=False):
        yield None

    monkeypatch.setitem(registry.REPORTS, "smoke",
                        registry.ReportSpec(_fixture_report, "sqlite", "new"))
    monkeypatch.setattr(cli, "connect", fake_connect)
    monkeypatch.setattr(cli, "REPORTS_DIR", tmp_path / "reports")
    monkeypatch.setattr(cli, "BUILD_DIR", tmp_path / "build")
    cli.main(["smoke", "--targets", "md,figures,latex"])
    assert (tmp_path / "reports" / "smoke.md").exists()
    assert (tmp_path / "reports" / "provenance.json").exists()
    assert (tmp_path / "build" / "k.tex").exists()
    assert (tmp_path / "build" / "macros.tex").exists()
```

- [ ] **Step 2: Run to verify it fails** -- `ModuleNotFoundError` / `AttributeError`.

- [ ] **Step 3: Implement `registry.py`, `cli.py`, `__main__.py`**

`registry.py`:

```python
"""rq id -> how to build and where to read it. Reports self-register on import."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy.engine import Connection

from teralizer.eval.model import RQReport


@dataclass(frozen=True)
class ReportSpec:
    build: Callable[[Connection], RQReport]
    default_db: str
    schema: str  # "new" | "old"


REPORTS: dict[str, ReportSpec] = {}


def register(rq: str, spec: ReportSpec) -> None:
    REPORTS[rq] = spec


def get(rq: str) -> ReportSpec:
    if rq not in REPORTS:
        raise KeyError(f"unknown report: {rq} (known: {sorted(REPORTS)})")
    return REPORTS[rq]
```

`cli.py`:

```python
"""python -m teralizer.eval <rq|all> [--db NAME] [--targets md,figures,latex] [--paper-out PATH]"""

from __future__ import annotations

import argparse
import os
import shutil
from pathlib import Path

from teralizer.eval import registry
from teralizer.eval.data import connect
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import latex as latex_renderer
from teralizer.eval.render import manifest as manifest_renderer
from teralizer.eval.render import markdown as markdown_renderer

REPO_URL = "https://github.com/glockyco/Teralizer"
_ANALYSIS = Path(__file__).resolve().parents[3]
REPORTS_DIR = _ANALYSIS / "reports"
BUILD_DIR = _ANALYSIS / "build"


def _build_and_render(rq: str, db: str | None, targets: set[str], paper_out: Path | None) -> None:
    spec = registry.get(rq)
    with connect(db or spec.default_db, validate_schema=(spec.schema == "old")) as conn:
        report = spec.build(conn)
    if "figures" in targets:
        figures_renderer.materialize(report, REPORTS_DIR / "figures" / rq)
    if "md" in targets:
        markdown_renderer.render(report, REPORTS_DIR, repo_url=REPO_URL)
        manifest_renderer.write_manifest(report, REPORTS_DIR, repo_url=REPO_URL)
    if "latex" in targets:
        written = latex_renderer.render(report, BUILD_DIR)
        if paper_out is not None:
            paper_out.mkdir(parents=True, exist_ok=True)
            for path in written:
                shutil.copy2(path, paper_out / path.name)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teralizer.eval")
    parser.add_argument("rq", help="report id or 'all'")
    parser.add_argument("--db", default=None)
    parser.add_argument("--targets", default="md,figures,latex")
    parser.add_argument("--paper-out", default=os.environ.get("PAPER_REPO_PATH"))
    args = parser.parse_args(argv)
    targets = {t.strip() for t in args.targets.split(",") if t.strip()}
    paper_out = Path(args.paper_out) / "tables" if args.paper_out else None
    rqs = sorted(registry.REPORTS) if args.rq == "all" else [args.rq]
    if not rqs or (args.rq == "all" and not registry.REPORTS):
        print("no reports registered")
        return
    for rq in rqs:
        _build_and_render(rq, args.db, targets, paper_out)


if __name__ == "__main__":
    main()
```

`__main__.py`: `from teralizer.eval.cli import main\n\nmain()`

Note: `cli.main` reads `PAPER_REPO_PATH` only to choose an OUTPUT directory for generated artifacts. It never passes environment into `Provenance` (guarded by `test_provenance_module_never_reads_environment`).

- [ ] **Step 4: Run to verify it passes** -- `uv run --directory analysis pytest tests/eval/test_registry.py tests/eval/test_cli.py -q` -> PASS.
- [ ] **Step 5: Gate and commit** -- subject `feat(eval): add registry, cli, and paper export`.

---

## Task 6: committed reports layout and report-build smoke

**Files:**
- Modify: `analysis/.gitignore`
- Create: `analysis/tests/eval/test_smoke.py`

- [x] **Step 1: gitignore the build dir, keep reports committed.** `analysis/build/`
  (regenerable LaTeX, CSV, and macros export) is gitignored; `analysis/reports/**`
  (committed markdown, rasters, and manifest) is not.
- [x] **Step 2: report-build smoke as a pytest.** `analysis/tests/eval/test_smoke.py`
  iterates `registry.REPORTS`, builds each report against its default DB, and
  skips on `OperationalError`. Vacuous until reports register, gaining live
  coverage per report. This is a normal test, NOT a `validate.py` hook: the eval
  engine's gate is `pytest tests/eval` plus the ruff and ty pre-commit hooks, and
  `validate.py` stays the legacy notebook gate until the final migration retires
  it.

---

## Acceptance

The engine is done when `uv run --directory analysis pytest tests/eval -q` is fully green (golden coverage of all three renderers and the manifest, the CLI integration test proving compute-once-then-fan-out to `md`/`figures`/`latex` plus paper export on a fixture report, and the report-build smoke), the ruff and ty pre-commit hooks pass, and `analysis/reports/` is committed while `analysis/build/` is gitignored. `validate.py` is not the engine's gate. No research-question report is built here; each RQ is its own plan on top of this engine, per the sequence above.
