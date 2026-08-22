## Why

The generator publishes under the filenames the thesis reads but does not reproduce the thesis's table
format, so syncing regenerated tables silently degrades the chapter. The funnel table loses its
numbered rows, its per-stage band rows carrying projects, inclusions, exclusions, and rate, and its
overall row. The breakdown and filtering tables lose their centred composite headers, the thin space
between a count and its share, and the internal phantom padding that aligns both components across
rows. One sync of correct v7 numbers had to be reverted for exactly this reason.

The format cannot be restored where the values are. Composite padding needs the widest count and share
in a column, and the current model hands the renderer a finished string such as `73,780 (89.5%)` built in
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

- **LaTeX tables match the document's format**: plain leaf headers inherit their column alignment;
  spanning and composite headers remain centred. A count and its share use a thin space and computed
  internal padding so both parts align; plain numeric cells rely on their column alignment without
  phantom markup.
- **A table may carry band rows**: a row spanning every column that summarises the group beneath it,
  which is how the funnel states each stage's projects, inclusions, exclusions, and rate.
- **A table may number its rows and label them by semantic key.** The funnel uses visible ordinals for
  readability and a stable table-key-plus-row-key label for citations. Reordering rows may change the
  displayed number without changing which cause a citation denotes.
- A table column declares the **kind of value** it holds — count, share, percentage point,
  percentage-point delta, decimal, delta, runtime, identifier, text, or a named entity — instead of a
  display format string. These numeric kinds retain their significant precision in the value. Human
  targets suffix a percentage-point value such as `47` with `%`. They render a percentage-point delta
  such as `574.5` as `+574.5%`. CSV keeps each bare numeric value. Delta kinds use an explicit sign in
  human targets.
- Each render target owns its presentation:
  - **CSV** emits machine-readable values: `85368`, `0.526`, an empty field for a missing value, and a
    plain identifier. No thousands separators, no percent signs, no em dashes, no macros.
  - **Markdown** emits readable plain text: `85,368`, `52.6%`, `—`, and an identifier in backticks.
  - **LaTeX** emits what the thesis needs today, unchanged: `85,368`, `52.6\%`, `—`, `\texttt{...}`,
    and entities as their thesis macros.
- **Named entities replace baked-in macros.** A variant, a tool, or a dataset is stored as an entity
  reference and rendered per target, so markdown says `PIT` where LaTeX says `\ToolPit{}`. Captions
  reference entities through the placeholder syntax `Prose` already uses for metrics.
- **Maintained table semantics survive regeneration.** Dataset-family group boundaries and
  parenthesised paired deltas remain visible. One reviewed numeric value has one rounded presentation
  across its table, prose, and macro uses; rendering `51 / 80` consistently as `63.8%` changes no
  underlying measurement.
- **BREAKING** for CSV consumers: numeric columns become raw numbers and missing values become empty
  fields. The thesis currently retains selected CSV files as evidence but does not read them during its
  build; the published CSV diff is still reviewed as a machine-readable interface change.
- **Deletions**, not additions: `fmt="tex"`, the `csv_source` redirect, and the `*_display` DataFrame
  columns all disappear, because nothing pre-renders a cell any more.
- **Consumes the explicit report-run architecture.** Renderers receive staged output roots and return
  report-owned artifacts through `ArtifactSet`. This change owns only value kinds, entity rendering,
  target formatting, and table layout. It does not own artifact identity, output paths, staging,
  promotion, manifests, or consumer delivery.

## Capabilities

### New Capabilities

- `report-output/table-rendering`: what a table model may hold, and what each render target must
  produce from it.

### Modified Capabilities

None.

## Impact

- `analysis/src/teralizer/eval/model.py` (`ColumnSpec`, `Prose`), `format.py`, and the target-specific
  value-to-text logic in `render/{csv,markdown,latex}.py`. Renderer output identity and return types
  come from `make-report-runs-explicit` and are not redefined here.
- A neutral shared entity registry under `teralizer.eval`, plus the report-local mappings and display
  columns it replaces in `reports/_causes_common.py`, `reports/_funnel.py`, and `reports/rq0_jarvis.py`.
- Regenerated output: all 8 markdown reports and the complete staged LaTeX and CSV artifact sets. No
  file is copied into the thesis by hand; consumer publication belongs to `declare-published-artifacts`
  and the final thesis reconciliation change.
- **Two staged acceptance criteria.** The value/presentation separation is compared against a clean
  full-run baseline from the same reviewed inputs, never whatever happens to be in `analysis/build/`.
  The deliberate LaTeX format work then targets the thesis's committed generated source and its
  rendered pages: source structure is reviewable, while the page is authoritative for layout.
