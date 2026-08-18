## Why

The generator publishes under the filenames the thesis reads but does not reproduce the thesis's table
format, so syncing regenerated tables silently degrades the chapter. The funnel table loses its
numbered rows, its per-stage band rows carrying projects, inclusions, exclusions, and rate, and its
overall row. The breakdown and filtering tables lose their centred numeric headers, the thin space
between a count and its share, and the phantom padding that aligns digits across rows. One sync of
correct v7 numbers had to be reverted for exactly this reason.

The format cannot be restored where the values are. Phantom padding needs the widest value in a
column, and the current model hands the renderer a finished string such as `73,780 (89.5%)` built in
the report's DataFrame step. A renderer cannot align what it would first have to parse.

The same missing separation sends LaTeX to targets that cannot use it.

Markdown carries raw macros: `reports/rq0.md:15-26` shows `\#` as a column header and
`\texttt{isAscii}` in ten cells; `rq5.md:13-21` shows `\VariantAll{}` in every row; `rq1.md:29` shows
`Total \%`; `rq6.md:16` shows `timeout exceeded (300 seconds per \VariantOriginal{} test suite)`. The
captions carry `\DatasetsCommons{}` and `\ToolPit{}`.

The CSV files are worse, because they are data. `build/rq6/tab-exclusions-breakdown-extended.csv`
records `Test,"85,368",44875,40198,295`: the first number is a quoted locale string and its three
siblings are plain integers, in the same row. `rq6_jpf_exception_causes.csv` records shares as
`76.8%`, and `rq0-breadth-summary.csv` records missing values as `—`. A consumer must strip commas,
parse a percent sign, and interpret an em dash as null before it can read a number, and the thesis
and the replication artifact both ship these files.

The cause is that `format.py` is documented as "one source of truth used by every renderer" while its
formatters are display formatters, and `render/csv.py` calls the same ones. Three half-mechanisms have
grown around that: `fmt` display strings, `*_display` DataFrame columns paired with `csv_source` to
recover the raw twin, and `fmt="tex"` plus macro tables such as `_VARIANT_MACROS` that bake LaTeX into
values. Each one patches a symptom of the same missing separation.

This is long-standing rather than new. `\VariantAll{}` is already in the committed `rq5.md`. The RQ0
case only became visible now because the committed `rq0.md` was stale relative to its own generator,
and regenerating it surfaced what the generator had been producing.

## What Changes

- **LaTeX tables match the document's format**: body indentation, centred headers over numeric
  columns, a thin space between a count and its share, and phantom padding that aligns digits and
  sub-ten-percent shares within a column.
- **A table may carry band rows**: a row spanning every column that summarises the group beneath it,
  which is how the funnel states each stage's projects, inclusions, exclusions, and rate.
- **A table may number its rows**, which the funnel uses to make a cause citable.
- A table column declares the **kind of value** it holds — count, share, identifier, text, or a named
  entity — instead of a display format string.
- Each render target owns its presentation:
  - **CSV** emits machine-readable values: `85368`, `0.526`, an empty field for a missing value, and a
    plain identifier. No thousands separators, no percent signs, no em dashes, no macros.
  - **Markdown** emits readable plain text: `85,368`, `52.6%`, `—`, and an identifier in backticks.
  - **LaTeX** emits what the thesis needs today, unchanged: `85,368`, `52.6\%`, `—`, `\texttt{...}`,
    and entities as their thesis macros.
- **Named entities replace baked-in macros.** A variant, a tool, or a dataset is stored as an entity
  reference and rendered per target, so markdown says `PIT` where LaTeX says `\ToolPit{}`. Captions
  reference entities through the placeholder syntax `Prose` already uses for metrics.
- **BREAKING** for CSV consumers: numeric columns become raw numbers and missing values become empty
  fields. The thesis reads these files through its plotting macros, which must be checked.
- **Deletions**, not additions: `fmt="tex"`, the `csv_source` redirect, and the `*_display` DataFrame
  columns all disappear, because nothing pre-renders a cell any more.

## Capabilities

### New Capabilities

- `report-output/table-rendering`: what a table model may hold, and what each render target must
  produce from it.

### Modified Capabilities

None.

## Impact

- `analysis/src/teralizer/eval/model.py` (`ColumnSpec`, `Prose`), `format.py`,
  `render/{csv,markdown,latex}.py`.
- `reports/_causes_common.py` (`_VARIANT_MACROS`, the `*_display` columns), `reports/_funnel.py`
  (`cause_macros`, `timeout_macros`), `reports/rq0_jarvis.py` (the `\texttt{}` mapping and the `\#`
  header), and every report that declares a column.
- Regenerated output: all 8 markdown reports, all CSV files under `analysis/build/`, and the copies in
  the thesis at `chapters/05-teralizer/data/`.
- **Two staged acceptance criteria.** While values are separated from presentation, LaTeX output must
  stay byte-identical, which proves the refactor is inert. The format work that follows then changes
  LaTeX deliberately, and its criterion is that each generated table matches the thesis's committed
  table apart from the data rows.
