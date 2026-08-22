## 1. Baseline and guards

- [x] 1.1 After `make-report-runs-explicit` lands, run the complete report set from one reviewed commit
      and declared corpus set into a clean temporary root. Record its manifest and artifact checksums as
      the comparison baseline; do not use `analysis/build/` or introduce another renderer-return shape.
- [x] 1.2 Add a test that fails when any rendered markdown contains a backslash
- [x] 1.3 Add a test that fails when any CSV numeric field does not parse as a number, covering digit
      grouping, percent suffixes, and placeholder dashes
- [x] 1.4 Record the completed thesis consumer audit: no chapter, figure, preamble file, or build entry
      reads `chapters/05-teralizer/data/*.csv`; selected CSVs are retained review evidence. Re-run the
      positive-control search before implementation in case a consumer has since been added.

## 2. Value kinds

- [x] 2.1 Replace `ColumnSpec.fmt` display formats with value kinds: `count`, `share`, `percent`,
      `percent_delta`, `decimal`, `delta`, `runtime`, `identifier`, `text`, `entity`. Store
      percentage-point, percentage-point-delta, decimal, and delta values as `Decimal` at their
      significant precision; do not add renderer format metadata.
- [x] 2.2 Split `format.py` into one kind-to-text map per target, each inside the renderer contract
      provided by `make-report-runs-explicit`. Human targets append `%` to `percent` without scaling
      and render `percent_delta` with both a sign and `%`; CSV emits each bare numeric magnitude.
- [x] 2.3 Remove the `pvc` kind and make an absent `count` render as empty in CSV and as a dash in the
      other targets
- [x] 2.4 Update every `ColumnSpec` declaration across the report modules
- [x] 2.5 Regenerate and confirm the LaTeX files are byte-identical to the baseline

## 3. Combined cells move into the LaTeX renderer

- [x] 3.1 Compose the count-with-share cell in the LaTeX renderer from the count and the share columns
- [x] 3.2 Delete the `*_display` DataFrame columns from `_causes_common.py` and the report modules that
      build them
- [x] 3.3 Delete `ColumnSpec.csv_source`
- [x] 3.4 Regenerate and confirm the LaTeX files are byte-identical to the baseline

## 4. Entities

- [x] 4.1 Create one neutral entity registry under `teralizer.eval` with a stable key, plain name, and
      LaTeX rendering for each shared variant, tool, and dataset. Keep target-invariant stage names as
      text, and use entity placeholders inside composite cause text. Move report-local entity maps into
      the registry; do not make `_causes_common.py` the global vocabulary owner.
- [x] 4.2 Store entity references in cells, and render them per target
- [x] 4.3 Replace the `\texttt{}` mapping and the `\#` header in `rq0_jarvis.py` with an identifier
      column and a plain header
- [x] 4.4 Delete `fmt="tex"`
- [x] 4.5 Regenerate and confirm the LaTeX files are byte-identical to the baseline

## 5. Captions and notes

- [x] 5.1 Extend the `Prose` placeholder mechanism to entity references
- [x] 5.2 Convert every caption and note that names a tool, dataset, or variant to placeholders
- [x] 5.3 Regenerate and confirm the LaTeX files are byte-identical, and that no markdown caption
      contains a macro

## 6. Compute alignment in the LaTeX renderer

- [x] 6.1 Compose a count with its share as one cell, `count\; (share)`, in the renderer rather than in
      a report's DataFrame step
- [x] 6.2 Compute internal padding for composite count/share cells from the widest count and share in
      their column. Keep plain numeric cells on ordinary column alignment and assert that they emit no
      phantom padding.
- [x] 6.3 Assert that no padding, thin space, or parenthesis pairing reaches markdown or CSV
- [x] 6.4 Let plain leaf headers inherit their column alignment. Centre only spanning headers and
      headers that describe composite cells.
- [x] 6.5 Keep the percent sign inline in every human-readable share, percentage-point, and percentage-point-delta cell
- [x] 6.6 Add band rows: a spanned row carrying a fixed-width label and the group's summary, plus a
      closing overall band, reusing the existing spanned-cell and label-row machinery
- [x] 6.7 Add a stable semantic row key to numbered data-row metadata and reject duplicate keys within
      a table. Do not add the key or visible ordinal as a CSV data column.
- [x] 6.8 Emit visible ordinals from a LaTeX counter and labels of the form
      `tabrow:<table-key>:<row-key>`; band rows consume neither. Test reorder stability, duplicate-key
      failure, and label output.
- [x] 6.9 Declare the funnel's band summaries and row keys from the funnel result so stage figures and
      cause identities come from the same typed source as the macros.
- [x] 6.10 Add renderer tests for plain and composite header alignment, composite component widths,
      plain numeric cells without phantom markup, and target-specific percent rendering.

## 7. Restore maintained table semantics

- [x] 7.1 Model RQ3 percentage-point deltas as `percent_delta` values so all three `Delta %` columns
      keep their explicit sign and `%` suffix in LaTeX and markdown while CSV remains numeric.
- [x] 7.2 Render RQ1 deltas paired with absolute detection percentages as signed parenthesised values in
      human targets, driven by the shared-header structure rather than preformatted strings.
- [x] 7.3 Construct the RQ0 `51 / 80` share once at reviewed significant precision and use its `63.8%`
      rendering in the budget table, prose, and generated macros.
- [x] 7.4 Supply RQ2's dataset-family grouping independently of project display names so LaTeX separates
      EqBench, Commons-ES, and Commons-dev groups without adding rules between projects.
- [x] 7.5 Restore the same dataset-family boundaries in the maintained dormant RQ1
      mutants-per-project table source, even though the thesis does not currently include it.
- [x] 7.6 Replace literal project dataset strings in affected report frames with entity references while
      preserving their target-specific visible names.
- [x] 7.7 Add focused producer and renderer tests for every repaired suffix, parenthesis, rounding,
      grouping, and entity-reference contract.

## 8. Verify the cleaned producer output

- [x] 8.1 Run a second complete report set into a clean temporary root and compare its artifact manifest
      with task 1.1. Account for every deliberate LaTeX diff and require every pre-format separation
      artifact to remain byte-identical.
- [x] 8.2 Review all 8 markdown reports and confirm each reads as plain text with no LaTeX residue.
- [x] 8.3 Review every CSV and confirm each numeric field is bare, each absence is empty, and no synthetic
      row ordinal appears.
- [x] 8.4 Run the full analysis suite, lint, format, type, file-hygiene, and positive-control guards.
- [x] 8.5 Exercise declaration-driven publication into a clean scratch thesis checkout and confirm the
      complete declared set lands transactionally. Leave the real thesis untouched; its
      `reconcile-reporeapers-claims` change owns the final publication and prose migration.
- [x] 8.6 Compare every deliberate LaTeX source change with the corresponding committed thesis table,
      including the dormant mutants-per-project source. Run the strict scratch thesis build and inspect
      the RQ0 through RQ3 affected pages with `scripts/pdf-page.swift`: percentage suffixes, rounding,
      paired deltas, dataset-family boundaries, entity labels, references, numeric alignment, and
      header alignment are correct, and nothing overflows.
