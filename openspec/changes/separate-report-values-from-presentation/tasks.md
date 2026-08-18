## 1. Baseline and guards

- [ ] 1.1 Snapshot every file under `analysis/build/` as the comparison baseline
- [ ] 1.2 Add a test that fails when any rendered markdown contains a backslash
- [ ] 1.3 Add a test that fails when any CSV numeric field does not parse as a number, covering digit
      grouping, percent suffixes, and placeholder dashes
- [ ] 1.4 Find every thesis consumer of `chapters/05-teralizer/data/*.csv` and record whether it parses
      formatted numbers

## 2. Value kinds

- [ ] 2.1 Replace `ColumnSpec.fmt` display formats with value kinds: `count`, `share`, `runtime`,
      `identifier`, `text`, `entity`
- [ ] 2.2 Split `format.py` into one kind-to-text map per target, each beside its renderer
- [ ] 2.3 Remove the `pvc` kind and make an absent `count` render as empty in CSV and as a dash in the
      other targets
- [ ] 2.4 Update every `ColumnSpec` declaration across the report modules
- [ ] 2.5 Regenerate and confirm the LaTeX files are byte-identical to the baseline

## 3. Combined cells move into the LaTeX renderer

- [ ] 3.1 Compose the count-with-share cell in the LaTeX renderer from the count and the share columns
- [ ] 3.2 Delete the `*_display` DataFrame columns from `_causes_common.py` and the report modules that
      build them
- [ ] 3.3 Delete `ColumnSpec.csv_source`
- [ ] 3.4 Regenerate and confirm the LaTeX files are byte-identical to the baseline

## 4. Entities

- [ ] 4.1 Extend the variant table in `_causes_common.py` with a plain-text rendering per entity, and
      move the tool and dataset macros from `_funnel.py` into it
- [ ] 4.2 Store entity references in cells, and render them per target
- [ ] 4.3 Replace the `\texttt{}` mapping and the `\#` header in `rq0_jarvis.py` with an identifier
      column and a plain header
- [ ] 4.4 Delete `fmt="tex"`
- [ ] 4.5 Regenerate and confirm the LaTeX files are byte-identical to the baseline

## 5. Captions and notes

- [ ] 5.1 Extend the `Prose` placeholder mechanism to entity references
- [ ] 5.2 Convert every caption and note that names a tool, dataset, or variant to placeholders
- [ ] 5.3 Regenerate and confirm the LaTeX files are byte-identical, and that no markdown caption
      contains a macro

## 6. Compute alignment in the LaTeX renderer

- [ ] 6.1 Compose a count with its share as one cell, `count\; (share)`, in the renderer rather than in
      a report's DataFrame step
- [ ] 6.2 Compute padding per column from the widest count and the widest share, and apply it to plain
      numeric columns by the same rule so one table uses one mechanism
- [ ] 6.3 Assert that no padding, thin space, or parenthesis pairing reaches markdown or CSV
- [ ] 6.4 Centre a numeric column's header with `\multicolumn{1}{c}{...}`
- [ ] 6.5 Keep the percent sign inline in every share cell
- [ ] 6.6 Add band rows: a spanned row carrying a fixed-width label and the group's summary, plus a
      closing overall band, reusing the existing spanned-cell and label-row machinery
- [ ] 6.7 Add rendered row numbering that band rows do not consume, and keep the group as data in CSV
- [ ] 6.8 Declare the funnel's band summaries from the funnel result so the stage figures come from the
      same source as the macros
- [ ] 6.9 Copy the regenerated tables into the thesis, rebuild, and verify each table on the rendered
      page with `scripts/pdf-page.swift`: numbers aligned, headers over their columns, groups stating
      their totals, nothing overflowing the text width

## 7. Publish the cleaned output

- [ ] 7.1 Review the markdown diff for all 8 reports and confirm each reads as plain text
- [ ] 7.2 Review the CSV diff and confirm every numeric column is bare and every absence is empty
- [ ] 7.3 Copy the regenerated tables and CSVs into the thesis, rebuild it, and inspect the three RQ6
      tables on the rendered page
- [ ] 7.4 Run the full analysis test suite and the two new guards
