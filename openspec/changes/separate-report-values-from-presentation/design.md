## Context

Observed state, with evidence:

| Fact | Evidence |
|---|---|
| `format.py` is documented as the single source of truth for every renderer, and its formatters are display formatters | `format.py:1-2`, `_FORMATTERS` producing `1,378`, `76.8%`, `—` |
| The CSV renderer calls those same formatters | `render/csv.py`, `render_value(row[source], column.fmt)` |
| A CSV row mixes a formatted string with raw integers | `Test,"85,368",44875,40198,295` |
| Shares reach CSV as percent strings, absences as em dashes | `rq6_jpf_exception_causes.csv`, `rq0-breadth-summary.csv` |
| Markdown carries macros in headers, cells, captions, and notes | `rq0.md:15-26`, `rq5.md:13-21`, `rq1.md:29`, `rq6.md:9,16` |
| `fmt="tex"` exists so a value that is already LaTeX passes through untouched | `format.py`, comment on the `"tex"` entry |
| Presentation is pre-baked into DataFrame columns, with a redirect to recover the value | `ColumnSpec("Strategy", "strategy_display", csv_source="strategy")` |
| Entity-to-macro tables exist, LaTeX-only | `_causes_common.py:67-76` `_VARIANT_MACROS`; `_funnel.py:623-634` `cause_macros` |
| `Prose` already substitutes `{metric.key}` placeholders at render time | `model.py`, `Prose` docstring |
| The problem predates the RQ0 work | `\VariantAll{}` present in `git show HEAD:analysis/reports/rq5.md` |
| The generator took over the thesis's filenames without its format | `fix(eval): publish the exclusion tables under the names the thesis reads` (69b0bfe5) |
| Format gaps, measured feature by feature against the thesis's committed tables | phantom padding, thin space, centred numeric headers, band rows, row numbering, body indentation |
| The funnel band row is a spanned cell with a fixed-width label | `\multicolumn{4}{l}{\textit{\makebox[13.25em][l]{Stage 1 + 2 ...}}}` in the thesis table |
| The renderer already has label rows, group spacing, spanned leaf headers, and full width | `render/latex.py` `group_style`, `_spanned_cells`, `full_width` |
| The thesis already loads `siunitx` and no table uses an `S` column | `preamble/packages.tex:89`; no match for `S[table-format` |
| An `S` column reproduces the thesis's number style once configured | `group-separator={,}`, `group-minimum-digits=4`, `quantity-product={}` verified on the page |
| A split pair is torn apart by `\extracolsep{\fill}` | rendered test: `73,780 (` then a stretched gap then `89.5\%)` |
| A padded composite cell keeps the compact reading | rendered test: `73,780 (89.5\%)` and `199 (\phantom{0}0.2\%)` aligned |

The codebase has already discovered the value/presentation split three times and implemented it three
different ways: `csv_source` plus a `*_display` twin recovers the value for one target, `fmt="tex"`
surrenders to markup in the value, and the macro tables translate an entity for one target only. Each
is a local patch on one missing rule.

## Goals / Non-Goals

**Goals:**

- A generated table drops into the thesis without a hand edit.
- One rule: the model holds values and entity references; renderers hold presentation.
- CSV becomes data a tool can read without preprocessing.
- Markdown becomes readable text.
- LaTeX output does not move.
- Net removal of mechanism, not addition.

**Non-Goals:**

- Changing any measured value, caption wording, or column order.
- Restyling the thesis. The thesis's committed tables define the target format; the generator moves to
  them, not the reverse.
- Adding a render target.
- Changing how metrics become macros. That path already carries values, not markup.

## Decisions

### 1. `ColumnSpec.fmt` becomes a value kind, not a format string

Today `fmt` names a display formatter (`count`, `pct1`, `pvc`, `tex`). It becomes the column's kind:
`count`, `share`, `runtime`, `identifier`, `text`, `entity`. A kind says what the value *is*; each
renderer decides what it *looks like*.

`format.py` stops being one formatter table shared by all targets and becomes three small kind-to-text
maps, one per target, so the display rules sit beside the target that owns them.

*Why not keep `fmt` and add a per-target override:* that keeps display strings in the model and adds a
second axis. The kind is the only thing all three targets agree on.

`pvc` disappears as a kind. It exists only because a count column sometimes has absent values, which is
a property of the data, not a distinct kind: a `count` renders as empty in CSV and as a dash in the
other targets when the value is absent.

### 2. Entities get one definition and one rendering per target

`_VARIANT_MACROS` already maps a variant code to its thesis macro. It gains a plain-text column, and
the tool and dataset macros in `_funnel.py` join it, so one table answers "how does entity X read in
target Y". Cells store the entity reference, not its rendering.

For captions and notes, the placeholder mechanism `Prose` already uses for metrics is extended to
entities, so a caption reads `... for the {variant.improved_c} strategy ...` and each target
substitutes. This reuses a convention the model already has rather than introducing a second one.

*Why not strip macros from markdown at render time:* a translation pass would guess at arbitrary LaTeX
and would still leave the value polluted for CSV. The entity is the fact; the macro is one rendering
of it.

### 3. Three deletions follow

- `fmt="tex"` goes: no value is LaTeX any more.
- `csv_source` goes: with no pre-rendered cell, no target needs a redirect to recover the value.
- The `*_display` DataFrame columns go: the LaTeX renderer composes `19,306 (83.1%)` from the count and
  the share, which is where that composition belongs.

The last one is the largest edit and the most valuable: a combined cell is currently built inside a
report's SQL-to-DataFrame step, which is why CSV had to route around it.

### 4. The renderer computes alignment, because siunitx cannot align a composite cell

An earlier draft proposed moving numeric columns to `siunitx` `S` columns and splitting each
count-with-share into two columns, on the grounds that `\phantom` padding is a manual substitute for a
package the thesis already loads. Tested on the page, that design fails.

The thesis's tables are `tabular*` at `\textwidth` with `\extracolsep{\fill}`, which distributes slack
into every column boundary. A split pair is therefore pulled apart: with the parenthesis in an `@{}`
separator, `73,780 (89.5\%)` renders as `73,780 (` then a stretched gap then `89.5\%)`. Gluing with a
thin space instead has the same effect. An `S` column cannot take the composite whole, because it parses
one number per cell.

So the composite cell stays, and the renderer pads it. Padding a composite is the mechanism that fits
the cell shape, not a workaround for a missing tool. Because one mechanism per table beats two, the
renderer pads plain numeric columns the same way, and `siunitx` is not introduced.

This keeps the alignment computation in the renderer, which is exactly why the separation must come
first: padding depends on the widest count and the widest share in a column, and a renderer handed the
finished string `73,780 (89.5%)` would have to parse what it was given to compute either.

`siunitx` remains the better tool for a table of plain numbers, and the RQ0 tables may be worth
revisiting on their own. Mixing both mechanisms inside one table would be worse than either.

The funnel's band row is a spanned cell carrying a fixed-width label plus the group's summary. The
renderer already spans cells for group headers and already emits label rows with group spacing, so the
band row extends existing machinery rather than adding a parallel path. Row numbering is a rendered
ordinal, not a data column, so band rows do not consume one and the CSV keeps the group as data.

### 5. Verification is staged, and each stage has one criterion

Stage one, the separation: snapshot `analysis/build/`, then require every file to be byte-identical.
This proves the refactor is inert.

Stage two, the format: verify the rendered page. `scripts/pdf-page.swift` renders the pages carrying
each table, and the criterion is that numbers align, headers sit over their columns, and each group
states its totals. A source diff against the committed table is explicitly not the criterion, because
that table is what this stage replaces.

Two standing checks then keep the other targets clean: no backslash may appear in any rendered
markdown, and every numeric CSV field must parse as a number.

## Risks / Trade-offs

- **The thesis reads the CSV files through plotting macros that may expect the current formatting.** →
  Grep the thesis for readers of `chapters/05-teralizer/data/*.csv` and rebuild it before committing.
  The change is worthless if a figure breaks.
- **Padding is computed from the data, so a later corpus can change every cell in a column.** → It is
  derived, not stated, so a regeneration recomputes it; the diff is noisier than the numbers alone.
- **A padded composite hides its alignment in invisible boxes**, which is harder to read in source than
  a plain number. → Accepted: it is the only mechanism that survives `\extracolsep{\fill}`, and it is
  confined to one renderer.
- **Source indentation of generated LaTeX is invisible in the PDF.** → Deliberately not a requirement.
  It belongs to a formatter, and specifying it would have made a contract out of whitespace.
- **Entity placeholders make caption strings less literal to read in source.** → Accepted: it is the
  same mechanism metrics already use, and it is what lets one caption serve three targets.
- **A kind vocabulary can grow into a taxonomy.** → It is capped at what the columns actually hold, and
  `pvc` is removed in the same change to show the direction of travel.

## Migration Plan

1. Snapshot `analysis/build/` as the comparison baseline.
2. Introduce value kinds and the three per-target maps, leaving behavior identical.
3. Move combined-cell composition into the LaTeX renderer and delete the `*_display` columns and
   `csv_source`.
4. Move entities into one table with a rendering per target, and delete `fmt="tex"`.
5. Extend caption placeholders to entities.
6. Regenerate: require byte-identical LaTeX, and review the markdown and CSV diffs.
7. Copy regenerated CSVs into the thesis, rebuild it, and confirm no figure changed.

**Rollback:** every step reverts with git, and no measured value is touched at any point.

## Open Questions

- **Which thesis figures read the CSV files, and do any parse the formatted numbers?** This decides
  whether step 7 needs a change on the thesis side. It changes one task, not the approach.
