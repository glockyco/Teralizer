---
title: Evaluation Analysis and Reporting Redesign
type: spec
status: draft
created: 2026-07-08
parent: 2026-06-26-teralizer-overview
---

# Evaluation Analysis and Reporting Redesign

A clean, from-scratch replacement for the analysis code under `analysis/` that
produces the paper's RQ evaluations. One pure-Python system computes each
research question once and renders it independently to a human-facing markdown
report (committed, browsable) and to paper artifacts (LaTeX tables, PDF figures,
CSV data, and a numbers-macros file).

## Motivation

The analysis code is mid-migration between two generations and is brittle:

- **Old generation** -- Jupyter notebooks (`analysis/notebooks/rq*.ipynb`)
  orchestrating `rqN_*.py` modules (`get_* -> compute_* -> generate_*_table` /
  `generate_*_csv`) into LaTeX + CSV + matplotlib figures via `exports.py`. It
  classifies failures by **regex over free-text** (`v_project_failures.info`
  exception strings) and remaps pipeline stages with hardcoded,
  detected-late corrections. Reads the old DB schema.
- **New generation** -- pure-Python CLIs (`reporeapers_rerun_report.py`,
  `jarvis_scoreboard.py`, `jarvis_census.py`, `generation_coverage.py`,
  `applicability_priorities.py`, ...) built on `report_basis.py`, reading
  **structured reason codes** from the new schema. But they emit only terminal
  text + CSV: no markdown, no LaTeX, no plots.

Further problems:

- The notebook filenames are offset from the paper's RQ numbers (the "naming
  trap": the `rq4-limitations` notebook is paper **RQ5 + RQ6**; its title even
  says "RQ4"). Documented in `2026-07-07-evaluation-run-map.md`.
- The RQ6 project-funnel table functions in `rq4_limitations.py`
  (`get_processing_failures_by_cause_data`,
  `compute_processing_failures_by_stage_and_cause`,
  `generate_processing_failures_table`) are **orphaned** -- no notebook, test,
  or script calls them, yet the paper `\input`s `tab-processing-failures`. It
  was produced by a since-stripped notebook version.
- `rq4_limitations.py` (the whole RQ5/RQ6 analysis) has **no tests**.

The user's proposal -- "pure Python that emits markdown+raster for humans and
LaTeX+PDF for the paper" -- is the natural convergence of the two generations
plus a presentation layer neither has cleanly.

## Goals

- One pure-Python system hosting **all** RQ analyses (RQ0-RQ6 +
  dataset-characteristics).
- **Compute once per RQ; render independently.** Markdown and LaTeX are sibling
  renderers of a single computed result -- never `latex <- markdown`. Adding a
  renderer or an RQ is local and does not touch the others.
- Robust logic on **structured diagnostics** (`task_diagnostic.reason_code`,
  `filter_result.reason_code`, typed `generalization.exclusion_info`) -- no
  regex on free text, no stage-remapping hacks.
- Match the phase-decoupled pipeline order (GENERATION / GENERALIZATION /
  REDUCTION, reduction last).
- Human-facing per-RQ markdown committed to the repo, browsable on GitHub,
  diffable across pipeline changes (a fix's effect on the funnel is visible in
  the diff).
- Paper artifacts regenerable and self-contained -- the paper builds with
  `latexmk` and no live database.
- Fully testable, cleanly bounded units. No hacks or shortcuts.

## Non-goals

- **No dual-schema adapter.** Each RQ reads exactly one schema: RQ1-5 old,
  RQ0/RQ6 new. RQ5 and RQ6 share presentation, not schema.
- **No replication-variant machinery.** The `original/verify/replicate/jarvis`
  variant selection, DB-name suffixing, and variant-aware output dirs are
  dropped. Plain per-RQ default DB + a debugging override. Replication packaging
  is layered back on the clean core when the artifact is rebuilt for
  resubmission.
- **Not auto-generating the paper's prose.** The narrative is a human-authored
  argument; the analysis feeds it tables, figures, and number-macros.
- **Not re-running RQ1-5 on the new schema.** Their data stays on the old-schema
  `postgres_dev`.

## Key decisions

- **Architecture A -- declarative result object + pluggable renderers.**
  Rejected alternatives: (B) emit-as-you-go builder -- entangles compute with
  presentation order and is hard to unit-test the numbers in isolation;
  (C) compute-to-data + Jinja templates -- scatters intricate funnel/exclusion
  logic and inline-number prose into a stringly-typed template DSL.
- **Databases** (per-RQ default, `--db` override for debugging):
  - RQ1-RQ5 -> `postgres_dev` (old schema, eqbench + commons-utils, validated).
  - RQ6 -> `postgres_reporeapers` (new schema).
  - RQ0 -> `postgres_jarvis_scoreboard` + `postgres_jarvis_census` (new schema).
  - `postgres_test` (the published v1 RepoReapers corpus) is no longer an
    analysis input, superseded for RQ6 by `postgres_reporeapers`. The corpus
    itself is kept, not dropped.
- **Notebooks retired.** Pure Python + a thin CLI.
- **Outputs and git:**
  - Analysis repo commits `analysis/reports/rqN.md`,
    `analysis/reports/figures/rqN/*.png`, and `analysis/reports/index.md`.
  - `.tex`, `.pdf`, `.csv` are gitignored build output under `analysis/build/`.
  - The tool exports `.tex`, `.pdf`, `.csv`, and `macros.tex` into the paper
    repo (`--paper-out` / `PAPER_REPO_PATH`), committed there so the paper is
    self-contained.
- **Numbers macros.** The LaTeX track emits a `macros.tex` with one
  `\newcommand` per `Metric`. The paper prose cites macros instead of
  hand-typed numbers that drift when the pipeline changes, which also enables
  provenance (see below). Macros are named by **meaning, never by RQ number**:
  a metric carries a stable semantic key (`realworld.eligible_projects_pct`)
  that maps to a `\newcommand`-legal, prefixed CamelCase name
  (`\TzRealworldEligiblePct`). RQ indices are section ordering, the least stable
  attribute, so they stay out of the identity and renumbering never rewrites a
  macro. (LaTeX command names are letters-only, so a literal `\RQ6...` is not
  even definable without `\csname`.)
- **RQ6 eligibility filter.** Re-derive the "exclude projects that fail on their
  own build / dependency / compile issues rather than our pipeline" filter from
  the reporeapers corpus's structured diagnostics (the 632-vs-1161 reconciliation: 632 was the
  old RepoReapers eligible count). Preserve all rows in the DB; exclude those
  projects only from the presented denominator.

## Architecture

### Package layout -- new `teralizer.eval`, old modules retired

```
teralizer/eval/
  model.py        # render-agnostic result types: RQReport, Section, Table,
                  #   ColumnSpec, Figure, Metric
  data.py         # connection resolution (old-schema validated engine vs
                  #   new-schema open connection); read_sql helper
  format.py       # column/number formatters (pct, count, float, runtime) --
                  #   single source, used by every renderer
  macros.py       # dataset/variant/tool LaTeX macro map + name replacement
                  #   (from exports.py + formatting.py) + plain markdown names
  plots.py        # reusable plot builders over the shared style (wraps
                  #   plotting.py)
  registry.py     # rq id -> (build_report callable, default DB, schema kind)
  cli.py          # python -m teralizer.eval <rq|all> [--db] [--targets]
                  #   [--paper-out]
  render/
    markdown.py   # RQReport -> .md (+ references committed raster figures)
    latex.py      # RQReport -> per-table .tex (booktabs) + macros.tex
    figures.py    # materialize each Figure once -> .png (committed) + .pdf
  reports/        # one module per PAPER RQ (ends the naming trap)
    dataset_characteristics.py
    rq0_jarvis.py
    rq1_mutation_score.py
    rq2_constraint_complexity.py
    rq3_suite_size_runtime.py
    rq4_efficiency_evosuite.py
    rq5_causes_controlled.py       # old schema (postgres_dev)
    rq6_causes_realworld.py        # new schema (postgres_reporeapers)
    _causes_common.py              # shared RQ5/RQ6 presentation
```

Each `reports/rqN.py` holds `get_*(conn)` (one SQL query each), `compute_*(df)`
(logic/classification), plot builders, and `build_report(conn, cfg) -> RQReport`
that assembles them. `plotting.py` (ACM plot style) and `report_basis.py`
(connection base) are kept and adapted. `formatting.py` is decomposed and
retired: its number formatters move to `format.py`, its project and variant
macro replacement to `macros.py`, and its LaTeX table building to
`render/latex.py`. Its variant-ordering helpers are dropped with the variant
machinery. The old `rqN_*.py`, `exclusions.py`, `stages.py`, and all notebooks
are deleted as each RQ is ported.

RQ-to-module mapping (resolves the naming trap; paper numbering is
authoritative):

| Module | Paper RQ | Question | Schema / DB |
|---|---|---|---|
| `dataset_characteristics` | -- | dataset stats | old / `postgres_dev` |
| `rq0_jarvis` | RQ0 | JARVIS comparison | new / `postgres_jarvis_*` |
| `rq1_mutation_score` | RQ1 | mutation-score improvement | old / `postgres_dev` |
| `rq2_constraint_complexity` | RQ2 | constraint-aware vs random input gen | old / `postgres_dev` |
| `rq3_suite_size_runtime` | RQ3 | suite size and runtime effects | old / `postgres_dev` |
| `rq4_efficiency_evosuite` | RQ4 | efficiency vs EvoSuite | old / `postgres_dev` |
| `rq5_causes_controlled` | RQ5 | unsuccessful-generalization causes (controlled) | old / `postgres_dev` |
| `rq6_causes_realworld` | RQ6 | unsuccessful-generalization causes (real-world) | new / `postgres_reporeapers` |

### Result model (`model.py`) -- the one contract

Render-agnostic dataclasses:

- **`Metric`** -- a semantic key, a scalar value, and a formatter
  (`realworld.eligible_projects_pct = 0.794`). Every number the report cites is
  a `Metric`. Markdown substitutes its formatted value inline. LaTeX emits it as
  a `\newcommand` whose name derives from the key (see Numbers macros).
- **`ColumnSpec`** -- header text, source DataFrame column, formatter (from
  `format.py`), alignment. Formatting is defined **once** here, killing the
  current duplication between `generate_*_table` and `generate_*_csv`.
- **`Table`** -- a DataFrame + `list[ColumnSpec]` + caption + label + optional
  row-group key (drives LaTeX midrules and markdown section splits) + note.
- **`Figure`** -- a `build(ax_or_fig)` callable + name + caption + label +
  source DataFrame (for provenance and CSV export).
- **`Section`** -- title + ordered blocks: prose (markdown-flavored, with
  `Metric` substitutions) | `Table` | `Figure`.
- **`RQReport`** -- rq id + ordered `Section`s + the flat `Metric` set + the
  default DB.

`compute` is pure data and assembles an `RQReport`. All intricacy (funnels,
internal/external/mixed categorization, the eligibility filter) lives in tested
Python compute driven by structured reason codes.

**Prose asymmetry.** Prose lives only in the markdown track (the human report is
a generated narrative). The paper's narrative stays hand-authored; the LaTeX
track emits tables + figures + `macros.tex`, not prose. So there is no
"prose in two formats" problem -- markdown consumes prose blocks; LaTeX consumes
tables, figures, and metrics.

### Data access (`data.py`)

`connect(db, *, validate_schema)`:
- Old-schema RQ1-5 -> `postgres_dev`, `validate_schema=True` (reuses
  `config.py`'s schema-object check).
- New-schema RQ0/RQ6 -> `postgres_reporeapers` /
  `postgres_jarvis_*`, `validate_schema=False` (the `report_basis`
  open-connection path).

Default DB per RQ in `registry.py`; `--db` overrides for debugging. No variant
machinery. Read-only connections.

### Rendering (`render/`)

- **`markdown.py`** -- `RQReport -> analysis/reports/rqN.md`: prose with metrics
  substituted, GitHub-flavored tables, embedded `![](figures/rqN/name.png)`.
- **`latex.py`** -- `RQReport -> ` one booktabs `.tex` per `Table` (caption,
  label, midrules from the row-group key) + a single `macros.tex` with a
  `\newcommand` per `Metric`. Uses the macro map for tool/dataset/variant names.
- **`figures.py`** -- materializes each `Figure` once via matplotlib and
  `savefig`s to `.png` (committed, referenced by markdown) and `.pdf` (build
  output -> paper). One plot code path, two formats, no md<->pdf coupling.

### Figures

`build_report` attaches `Figure`s carrying data + a builder. Plot builders live
in `plots.py` (or the RQ module) over the shared ACM style in `plotting.py`.
Raster format for committed figures: PNG default (universal GitHub rendering);
WebP/SVG are open options (see Open items).

### CLI (`cli.py`)

`python -m teralizer.eval <rq|all> [--db NAME] [--targets md,latex,figures]
[--paper-out PATH]`. Default targets = all. Markdown + committed rasters always
write into `analysis/reports/`; `--paper-out` (or `PAPER_REPO_PATH`) routes the
LaTeX/PDF/CSV/macros export into the paper repo.

### RQ5 <-> RQ6 consolidation

`_causes_common.py` owns the presentation shared by both RQs: the test,
assertion, and generalization exclusion breakdown `Table` builders (filtering
versus failures) and the filter-rejection cause tables. RQ5 (`postgres_dev`, old
schema) and RQ6 (`postgres_reporeapers`, new schema) each supply a thin `get_*`
data layer, so the **schema difference is isolated to the queries** while the
presentation is shared.

RQ6 additionally owns the project-level exclusion funnel (`tab-processing-failures`,
the "Project-level exclusions by stage and cause" table): the eligible
denominator (projects that pass initial setup, dropping dependency-resolution,
missing-sources, and original-build failures), the per-stage inclusion and
exclusion counts, and the per-cause internal/external/mixed categorization. RQ5
has no funnel: by design RQ1-RQ5 cover only projects that complete the pipeline,
so there are no project-level exclusions.

## Numbers macros and provenance

The LaTeX track emits `macros.tex`, one `\newcommand` per `Metric`. The paper
cites macros (e.g. `\RQsixEligiblePct`) instead of hand-typed numbers, so every
figure in the prose is computed by code and recompiling picks up new results.
This is the reproducible-research staple and the foundation for provenance.

Provenance links every generated artifact back to the exact code that produced
it. This is the `showyourwork` model, where a paper figure carries a clickable
link to its generating script at a pinned git commit. Architecture A gives it to
us without a separate build framework, because every artifact is constructed in
a known function. The design:

- A **`Provenance` field on every `Metric`, `Table`, and `Figure`**, captured at
  build time and never hand-maintained. It records the producing function
  (`module:qualname` and source line via `inspect`), the SQL query text or a
  stable query id, and the analysis-repo git commit. The commit gets a `-dirty`
  suffix when the working tree has uncommitted changes, so a number is never
  falsely pinned to a clean commit.
- A **`provenance.json` manifest** emitted next to `macros.tex`, mapping
  `artifact_id` to its value or caption, producing function, source URL, query,
  and commit. It is a machine-readable audit trail, so every number stays
  traceable even where the paper renders no visible link.
- **In-document provenance at table and figure granularity**, per renderer.
  `showyourwork` patches the LaTeX `figure` environment to inject clickable
  **margin icons** whose links resolve to `<repo>/blob/<sha>/<script>` at the
  build commit (its `\GitHubURL` / `\GitHubSHA` macros supply the pin), plus a
  second icon to the dataset. We take the idea and split it by medium:
  - **LaTeX** -- an opt-in `\provenance{id}` in the caption renders a small
    GitHub-linked icon to the producing source at the pinned commit, and we emit
    `\GitHubURL` / `\GitHubSHA`-style macros so the paper builds the permalink.
  - **Markdown** -- no margins, so a short caption text-link
    ("source: `module.function`") to the same permalink. It is greppable and
    unambiguous on GitHub, where a bare icon is not.
  Inline prose numbers stay clean (macro only) and trace through the manifest.
  `showyourwork` likewise annotates figures and tables, not inline numbers.
- **A generated reproducibility appendix** -- one short paragraph naming the
  analysis repo, the build commit, and the rebuild command, following
  `showyourwork`'s reproducibility-paragraph convention. Cheap and journal-facing.
- **Optionally, embed the build commit in each figure's raster metadata**
  (matplotlib `savefig(metadata=...)`), the FAIR code-to-figure trick, so
  provenance travels inside the `.png` / `.pdf` even apart from the manifest.

Provenance falls out of the result model as a field set where each artifact is
built, rather than being bolted on afterward. The manifest doubles as a
reproducibility check: an artifact with no captured source is a build error.

References and alternatives considered, coarse to fine:

- ACM / NISO artifact badges -- page-level trust signals, not links.
- Compute capsules (Code Ocean, Whole Tale) -- DOI-cited, re-run wholesale.
- An explicit "reproduce" mapping doc -- figure or table to script.
- `showyourwork` (Luger et al. 2021) -- in-document figure-to-script-to-commit
  icons, the model we adapt.
- The FAIR code-to-figure chain (arXiv 2604.25944) -- commit id embedded in
  figure metadata.
- The `\reproduce{}` provenance macro (arXiv 1608.06897).

Literate programming (knitr, PythonTeX, Quarto) was rejected because our
analysis feeds the paper rather than living inside the `.tex`. We take the
manifest as the machine-readable spine and `showyourwork`-style visible links as
the human affordance.

## Testing

- **Compute** -- assert on the `RQReport` (numbers and structure), not on
  rendered strings. Follow the existing DB-fixture pattern used by
  `test_reporeapers_rerun_report.py`, `test_jarvis_scoreboard.py`, etc.
- **Renderers** -- golden tests: a fixture `RQReport` -> expected `.md` / `.tex`.
- **Gate** -- `validate.py` drops notebook execution; it runs the report builds
  (smoke) + renderer goldens + `ruff` + `ty`. Match tier to change per
  `AGENTS.md`.

## Migration plan

Build order, each item after the first being its own plan:

1. The `teralizer.eval` engine (this redesign's foundation plan): the result
   model plus all renderers, provenance, manifest, registry, CLI, and paper
   export, proven by golden and CLI integration tests. No RQ report is built
   here.
2. RQ5 + RQ6 (causes): the shared exclusion breakdowns in `_causes_common.py`
   plus RQ6's project-level funnel. RQ6 ports the orphaned `rq4_limitations.py`
   funnel logic (`categorize_failure_type`,
   `compute_processing_failures_by_stage_and_cause`, and friends) from free-text
   regex to structured reason codes.
3. RQ0 (JARVIS): `postgres_jarvis_scoreboard` + `postgres_jarvis_census`, the
   four axes from `2026-06-30-jarvis-comparison`.
4. RQ1-RQ4 + dataset-characteristics: `postgres_dev` (old schema).
5. Retire the notebook machinery from `validate.py` and the notebook/html output
   dirs from `exports.py`, deleting each old `rqN_*.py` and notebook as its
   report lands, and update the reviewer-facing docs.

Keep and adapt `plotting.py`, `report_basis.py`, and the macro maps. Decompose
`formatting.py` into `format.py`, `macros.py`, and `render/latex.py` as described
above.

## Documentation and artifact-review impact

Retiring the notebooks invalidates the notebook-centric parts of the artifact's
reviewer-facing docs. The redesign is a net win for artifact review -- committed
markdown reports make the "inspect" tier zero-setup, one CLI command replaces
"run the notebooks in order," and the provenance manifest traces any number to
its code and commit -- but the following must be updated in lockstep:

- **Reviewer-facing:** `README.md` (bundle contents, workflow table, the
  Analysis Notebooks section and its mapping, Jupyter access, DB line, directory
  tree), `INSTALL.md` (Jupyter checkpoints), `REQUIREMENTS.md` (the JupyterLab
  row), `STATUS.md` (notebook and variant claims throughout).
- **Dev-facing:** `AGENTS.md` / `GEMINI.md` / `CLAUDE.md` (commands table, the
  `analysis/output` export layout), `.omp/rules/python.md`, and the analysis
  `pyproject.toml` / `.pre-commit-config.yaml` / `.gitignore` notebook entries.
- **Automation:** the packaged `replication/` scripts (`run-notebooks.sh`,
  `verify-outputs.sh`, `quick-start.sh`), `Dockerfile.analysis`, and the
  `scripts/packaging/` assembly.
- **Unaffected:** `docs/artifacts.md` (pipeline output, not analysis) and
  `docs/architecture.md` / `docs/database.md`.

Three reviewer-facing corrections the redesign forces:

- **Figure determinism.** The "all outputs match exactly" claim holds for `.tex`
  and `.csv` but not for rasters (matplotlib output is not byte-identical across
  versions or platforms). Output verification diffs figure **data**, not pixels.
- **Database inventory.** The package ships `postgres_dev` and `postgres_test`.
  The redesign reads `postgres_dev` (RQ1-5), `postgres_reporeapers` (RQ6),
  and `postgres_jarvis_{scoreboard,census}` (RQ0). The `teralizer-core` bundle
  must carry the `postgres_reporeapers` and jarvis dumps. The published-v1
  `postgres_test` is kept but is no longer an analysis input.
- **RQ0 mapping.** RQ0 (JARVIS) is new and absent from the current notebook map.

Treat the doc and packaging updates as a first-class migration deliverable, not
an afterthought, and fold the analysis-workflow description (today duplicated
across README, the agent files, and `python.md`) into the CLI as the single
documented entrypoint.

## Open items

- **Paper-side macro migration** -- one-time edit replacing hand-typed prose
  numbers with `\newcommand` macros across `sections/04-evaluation-*.tex`.
- **Committed figure raster format** -- PNG (default, universal) vs WebP
  (smaller) vs SVG (vector, diffable-ish). Decide when the first figure lands.
- **Exact `RQReport`/`Table`/`ColumnSpec` field lists** -- finalize in the
  implementation plan.
