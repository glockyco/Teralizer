## 1. Baseline and guards

- [ ] 1.1 After `make-report-runs-explicit` lands, run the complete report set from one reviewed commit
      and declared corpus set into a clean temporary root. Record its manifest and artifact checksums as
      the comparison baseline; do not use `analysis/build/` or introduce another renderer-return shape.
- [ ] 1.2 Add a test that fails when any rendered markdown contains a backslash
- [ ] 1.3 Add a test that fails when any CSV numeric field does not parse as a number, covering digit
      grouping, percent suffixes, and placeholder dashes
- [ ] 1.4 Record the completed thesis consumer audit: no chapter, figure, preamble file, or build entry
      reads `chapters/05-teralizer/data/*.csv`; selected CSVs are retained review evidence. Re-run the
      positive-control search before implementation in case a consumer has since been added.

## 2. Value kinds

- [ ] 2.1 Replace `ColumnSpec.fmt` display formats with value kinds: `count`, `share`, `runtime`,
      `identifier`, `text`, `entity`
- [ ] 2.2 Split `format.py` into one kind-to-text map per target, each inside the renderer contract
      provided by `make-report-runs-explicit`
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

- [ ] 4.1 Create one neutral entity registry under `teralizer.eval` with a stable key, plain name, and
      LaTeX rendering for each shared variant, tool, dataset, stage, and cause. Move report-local entity
      maps into it; do not make `_causes_common.py` the global vocabulary owner.
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
- [ ] 6.7 Add a stable semantic row key to numbered data-row metadata and reject duplicate keys within
      a table. Do not add the key or visible ordinal as a CSV data column.
- [ ] 6.8 Emit visible ordinals from a LaTeX counter and labels of the form
      `tabrow:<table-key>:<row-key>`; band rows consume neither. Test reorder stability, duplicate-key
      failure, and label output.
- [ ] 6.9 Declare the funnel's band summaries and row keys from the funnel result so stage figures and
      cause identities come from the same typed source as the macros.
- [ ] 6.10 Compare every deliberate LaTeX source change with the corresponding committed thesis table,
      publish through the declared consumer mapping into a clean scratch thesis checkout, run its strict
      full build, and inspect each affected page with `scripts/pdf-page.swift`: references resolve,
      numbers align, headers cover their columns, groups state totals, and nothing overflows.

## 7. Verify the cleaned producer output

- [ ] 7.1 Run a second complete report set into a clean temporary root and compare its artifact manifest
      with task 1.1. Account for every deliberate LaTeX diff and require every pre-format separation
      artifact to remain byte-identical.
- [ ] 7.2 Review all 8 markdown reports and confirm each reads as plain text with no LaTeX residue.
- [ ] 7.3 Review every CSV and confirm each numeric field is bare, each absence is empty, and no synthetic
      row ordinal appears.
- [ ] 7.4 Run the full analysis suite, lint, format, type, file-hygiene, and positive-control guards.
- [ ] 7.5 Exercise declaration-driven publication into a clean scratch thesis checkout and confirm the
      complete declared set lands transactionally. Leave the real thesis untouched; its
      `reconcile-reporeapers-claims` change owns the final publication and prose migration.
