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
| Format gaps, measured feature by feature against the thesis's committed tables | composite-cell padding, thin space, centred spanning and composite headers, band rows, row numbering, body indentation |
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
- The value/presentation refactor is inert before any deliberate format or row-label change.
- Deliberate LaTeX changes reproduce the thesis's maintained structure and rendered layout.
- Net removal of mechanism, not addition.

**Non-Goals:**

- Changing any measured value, caption wording, or column order.
- Restyling the thesis. The thesis's committed tables define the target format; the generator moves to
  them, not the reverse.
- Adding a render target.
- Changing how metrics become macros. That path already carries values, not markup.

## Decisions

### 1. `ColumnSpec.fmt` becomes a value kind, not a format string

Today `fmt` names a display formatter (`count`, `pct1`, `pvc`, `tex`). It becomes the column's kind: `count`, `share`, `percent`, `percent_delta`, `decimal`, `delta`, `runtime`, `identifier`, `text`, `entity`. A kind states what the value *is*. Each renderer decides what it *looks like*.

`share` stores a ratio and human targets scale it by 100. `percent` stores an already scaled percentage-point value, such as `47`; human targets append `%` without scaling it. `percent_delta` stores an already scaled percentage-point difference, such as `574.5`; human targets render it as `+574.5%`. `decimal` covers fixed-precision measurements. `delta` covers signed integer or decimal differences without a unit suffix. Percentage-point, percentage-point-delta, decimal, and delta kinds store `Decimal` values at the intended significant precision. CSV emits their bare numeric form. Markdown and LaTeX preserve that precision. Delta kinds add a positive sign. These kinds support current numeric fields without format strings or a general numeric-style system.

`format.py` stops being one formatter table shared by all targets and becomes three small kind-to-text
maps, one per target, so the display rules sit beside the target that owns them.

*Why not keep `fmt` and add a per-target override:* that keeps display strings in the model and adds a
second axis. The kind is the only thing all three targets agree on.

`pvc` disappears as a kind. It exists only because a count column sometimes has absent values, which is
a property of the data, not a distinct kind: a `count` renders as empty in CSV and as a dash in the
other targets when the value is absent.

### 2. Entities get one definition and one rendering per target

`_VARIANT_MACROS` already maps a variant code to its thesis macro, but a report-specific causes module
is the wrong owner for a repository-wide vocabulary. A neutral registry under `teralizer.eval` defines
each shared variant, tool, and dataset's stable key, plain name, and LaTeX rendering. Cells store the
entity reference, not its rendering. Stage names remain plain semantic text because every target uses
the same name. Composite cause sentences remain text and use explicit entity placeholders for the
tools or variants that differ by target.

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

### 4. The renderer computes alignment only inside composite cells

An earlier draft proposed moving numeric columns to `siunitx` `S` columns and splitting each
count-with-share into two columns, on the grounds that `\phantom` padding is a manual substitute for a
package the thesis already loads. Tested on the page, that design fails.

The thesis's tables are `tabular*` at `\textwidth` with `\extracolsep{\fill}`, which distributes slack
into every column boundary. A split pair is therefore pulled apart: with the parenthesis in an `@{}`
separator, `73,780 (89.5\%)` renders as `73,780 (` then a stretched gap then `89.5\%)`. Gluing with a
thin space instead has the same effect. An `S` column cannot take the composite whole, because it parses
one number per cell.

The composite cell therefore stays, and the renderer pads its two internal components from the widest
count and share in that column. A renderer handed the finished string `73,780 (89.5%)` would have to
parse it to recover those widths, which is why value/presentation separation comes first.

Plain numeric cells need no corresponding mechanism. Their ordinary LaTeX column alignment already
positions them, so adding invisible leading content has no page effect and only makes generated source
harder to inspect. Plain leaf headers inherit their column alignment. A header centres only when it
spans columns or describes the components of a composite cell. This follows the cell structure rather
than special-casing particular value kinds.

`siunitx` remains the better tool for a table of plain numbers, but introducing it is outside this
change. Ordinary columns for plain values and internal padding for composites are not competing table
alignment mechanisms: one aligns cells in a column, while the other aligns components inside one cell.

The funnel's band row is a spanned cell carrying a fixed-width label plus the group's summary. The
renderer already spans cells for group headers and already emits label rows with group spacing, so the
band row extends existing machinery rather than adding a parallel path.

Row numbering and row identity are deliberately different. A numbered data row carries a stable
semantic key as model metadata, separate from its data columns. The LaTeX renderer increments a table
row counter for the visible ordinal and emits a label derived from the table key and row key, such as
`tabrow:<table-key>:<row-key>`. Band rows consume neither. Markdown and CSV receive the data and group
summaries but no synthetic row-number field. Duplicate row keys within a table fail rendering.

This makes reordering safe: the printed ordinal can change while `\cref` still denotes the same cause.
A removed referenced row becomes an undefined LaTeX reference, which the thesis's strict full build
must reject. Exporting the current ordinal as data was rejected because it would turn presentation
order into a false semantic identifier.

### 5. Reports carry the semantic distinctions that renderers must preserve

A renderer cannot infer a dataset-family boundary from a corrected project display name. Reports
therefore store project datasets as entity references and supply the dataset-family grouping that the
maintained table uses. The LaTeX renderer emits a separator only when that semantic group changes; it
does not group by `project_name` or add a rule after every row.

A paired delta is also structural, not a special string. When an absolute value and its delta share a
merged heading, human renderers retain the delta's explicit sign and wrap it in parentheses. The CSV
renderer sees the same numeric delta but emits neither presentation mark.

Finally, rounding belongs where a report constructs the semantic value. A report creates one
significant-precision `Decimal`, and table cells, prose metrics, and generated macros consume that same
value. Renderers do not independently recompute or truncate it. Thus the reviewed ratio `51 / 80`
becomes `63.8%` everywhere without changing either count or the underlying measurement.

*Why not preserve current strings independently:* that is the mechanism this change removes. It allows
a table, prose passage, and macro to disagree while each looks locally plausible.

### 6. Rendering semantics sit inside the common artifact contract

`make-report-runs-explicit` establishes `BuiltReport`, `RenderTarget`, `ArtifactId`,
`RenderedArtifact`, and `ArtifactSet`, and supplies each renderer with a staging root. This change
implements value-to-text and table-layout behavior inside those renderer functions. Each renderer
returns its artifacts through the common contract without choosing final paths or adding another
emitted-output shape.

The ownership boundary is strict:

- this change owns value kinds, entities, target formatting, and LaTeX table layout;
- `make-report-runs-explicit` owns artifact identity, output containment, ownership, merging, staging,
  and promotion;
- `declare-published-artifacts` owns consumer declarations, guards, and delivery.

*Alternative considered:* finish value rendering against the current bare path lists and adapt it to
`ArtifactSet` later. Rejected because that would touch every renderer twice and create an intermediate
return contract with no durable owner.

### 7. Verification is staged, and each stage has one criterion

Stage one, the separation: from one reviewed commit and one declared corpus set, run the complete
report set into two clean temporary roots before and after the refactor. Require every corresponding
artifact to be byte-identical. The mutable `analysis/build/` directory is never a baseline.

Stage two, the deliberate format and label changes: compare every generated LaTeX table with the
thesis's committed generated sources and explain each structural difference. The audit explicitly
covers the RQ0 budget table, RQ1 mutator table, RQ2 complexity table, RQ3 runtime and line-count tables,
and maintained source for the dormant mutants-per-project table. It checks percentage suffixes,
rounding, paired-delta parentheses, dataset-family boundaries, entity-backed labels, and header and cell
alignment. Then publish through the declared consumer mapping into a clean scratch thesis checkout.
`scripts/pdf-page.swift` renders each affected page. Source comparison catches loss of maintained
structure; the page decides whether numbers align, headers sit over their columns, row references
resolve, and each semantic group remains distinct.

Two standing checks keep the other targets clean: no backslash may appear in any rendered markdown,
and every numeric CSV field must parse as a number. A complete manifest comparison proves that the
same artifact identities exist before and after the inert stage.

## Risks / Trade-offs

- **A downstream CSV consumer may expect display-formatted fields.** The thesis was checked and has no
  build-time reader of `chapters/05-teralizer/data/*.csv`; selected files are retained evidence. → Treat
  the CSV change as a declared interface break, review every numeric field, and record any other known
  consumer before release.
- **Composite padding is computed from the data, so a later corpus can change every composite cell in
  a column.** → It is derived, not stated, and confined to count/share cells; plain numeric columns add
  no phantom markup.
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

1. Land `make-report-runs-explicit`, then create a clean full-run baseline from one reviewed commit and
   declared corpus set.
2. Introduce value kinds and per-target maps, move combined-cell composition into the LaTeX renderer,
   and delete `*_display`, `csv_source`, and `fmt="tex"`; prove this stage byte-identical.
3. Move entities into the neutral registry and extend prose placeholders.
4. Add computed composite layout, aligned headers, percentage-point rendering, maintained group
   boundaries, paired deltas, band rows, semantic row keys, and LaTeX counter labels. Review every
   deliberate source diff against the thesis's committed generated tables.
5. Publish the complete declared set into a clean scratch thesis checkout through the common
   publication command. Run the strict thesis build and inspect affected pages. Do not copy any artifact
   by hand.
6. Leave the real thesis untouched. `reconcile-reporeapers-claims` publishes the finalized artifact set
   and updates every consumer claim as one coherent thesis migration.

**Rollback:** revert the responsible producer commit. Consumer publication is transactional, so a
failed verification leaves the thesis checkout unchanged.

## Open Questions

None. The thesis has no build-time CSV reader; the remaining consumer risk is handled as an explicit
interface review rather than an unresolved design choice.
