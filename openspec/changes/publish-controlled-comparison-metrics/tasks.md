## 1. Emit controlled comparison metrics

- [ ] 1.1 Fetch the controlled RQ5 breakdown frame once in `build`, pass it to the existing table
      builder, and select exactly the `Improved (200 tries)` / `Generalization` row through the variant
      registry identity. Fail if the semantic selection does not return one row.
- [ ] 1.2 Emit `controlled.improved_200.generalizations_total`,
      `controlled.improved_200.generalizations_included`, and
      `controlled.improved_200.generalizations_included_pct` from that row with count/share kinds,
      `Generalization` populations, controlled input role, operand keys, and captured breakdown-query
      provenance.
- [ ] 1.3 Validate all RQ5 metric metadata and relations before returning the report. Do not add a query,
      output writer, table schema, or metric for any unretained controlled quantity.

## 2. Defend the controlled metric contract

- [ ] 2.1 Extend the focused RQ5 report tests to assert the three exact metric keys, values, kinds,
      populations, numerator/denominator relation, and provenance.
- [ ] 2.2 Prove the metric values equal the existing retained table row and that zero or duplicate
      semantic row selection fails rather than publishing a plausible value.
- [ ] 2.3 Extend focused rendering/manifest coverage to prove the registered metrics produce aggregate
      LaTeX macros and provenance entries with their stable keys.

## 3. Regenerate and verify producer outputs

- [ ] 3.1 Run the focused RQ5 and metric-rendering tests in the pinned Nix environment.
- [ ] 3.2 Run the complete registered report set once through
      `uv run --directory analysis python -m teralizer.eval all`. Inspect the RQ5 table, metric
      inventory, aggregate macros, and provenance; confirm all three outputs agree and no unrelated
      report value changes.
- [ ] 3.3 Update only normally generated, tracked producer artifacts required by the registered report
      run. Do not hand-edit a report, macro, provenance record, database, or corpus input.
- [ ] 3.4 Run `uv run --directory analysis pytest`, `uv run --directory analysis ruff check .`, and
      `uv run --directory analysis ty check .` in the pinned Nix environment.
- [ ] 3.5 Run `openspec validate publish-controlled-comparison-metrics --strict` and
      `lefthook run pre-commit --all-files`.

## 4. Commit and hand off the metric API

- [ ] 4.1 Review the implementation, focused tests, generated-output diff, and provenance together.
      Confirm the change contains no database, query-semantic, table-presentation, or real-world RQ6
      modification.
- [ ] 4.2 Commit the implementation and generated contract as one causal subject using
      `personal_commit`.
      Message: `feat(eval): publish controlled comparison metrics`
- [ ] 4.3 Record the clean producer revision, three metric and macro identities, report/provenance
      verification, and archive readiness for the blocked thesis reconciliation. Do not resume the
      thesis numbers-only cutover from a dirty or unarchived producer state.
