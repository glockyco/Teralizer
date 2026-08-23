## 1. Reconstruct the Shared Filtering Boundary

- [x] 1.1 Audit the controlled and RepoReapers source rows that produce retained, excluded, pre-filter failure, and unknown outcomes; record the exact source columns, joins, variants, and entity identities used by each corpus-local query.
- [x] 1.2 Add positive and negative fixture queries that prove `generalization.is_included` alone cannot define the controlled filtering denominator.
- [x] 1.3 Implement separate read-only controlled and RepoReapers query helpers that return one typed row per generalized test with an explicit retained or excluded filtering result.
- [x] 1.4 Fail each query or relation build on duplicate identities, unsupported variants, missing required evidence, or contradictory filtering outcomes.

## 2. Extend the RQ6 Input and Evidence Model

- [x] 2.1 Declare the controlled corpus snapshot as a second RQ6 input while preserving the existing real-world input role and all existing RQ6 outputs.
- [x] 2.2 Build the controlled and RepoReapers filtering populations independently; exclude pre-filter failures and unknown outcomes from both filtering denominators without deleting their existing evidence.
- [x] 2.3 Add conservation checks that require retained plus excluded to equal the corpus-local filtering total and require compatible corpus, input-role, and `Generalization` populations for every operand relation.
- [x] 2.4 Add focused tests for retained, excluded, pre-filter failure, unknown evidence, duplicate identity, contradictory outcome, unsupported variant, and conservation mismatch cases.

## 3. Publish Metrics and Artifacts

- [x] 3.1 Emit the four controlled keys and four RepoReapers keys named in `design.md`, with count or percentage value kinds, typed populations, numerator and denominator relations, and captured source provenance.
- [x] 3.2 Render one compact RQ6 filtering table from those metrics without independently recomputing counts or percentages.
- [x] 3.3 Publish all eight metrics once in aggregate macros and provenance; verify that generated labels use only the established filtering vocabulary.
- [x] 3.4 Keep all eight keys outside the thesis-wide headline key set and add an integration test that fails if any enters headline selection.
- [x] 3.5 Add focused report, table, macro, manifest, and complete-publication tests for stable keys, values, populations, operand relations, provenance, uniqueness, and headline exclusion.

## 4. Verify the Observed Results

- [x] 4.1 Run the focused RQ6, query, rendering, macro, provenance, and headline-publication tests in the pinned analysis environment.
- [x] 4.2 Run `uv run --directory analysis pytest`, `uv run --directory analysis ruff check .`, and `uv run --directory analysis ty check .`.
- [x] 4.3 Run the complete registered report set against the declared controlled and real-world snapshots and verify 11,597 of 13,804 (84.0%) retained for controlled and 1,615 of 2,035 (79.4%) retained for RepoReapers.
- [x] 4.4 Inspect `analysis/build/macros.tex` and `analysis/reports/provenance.json`; verify all eight keys occur once, carry the correct input snapshots and operand relations, and do not alter existing RQ6 funnel values.

## 5. Validate and Hand Off

- [x] 5.1 Run `openspec validate publish-conditional-filtering-comparison --strict`, `lefthook run pre-commit --all-files`, and `git diff --check`.
- [x] 5.2 Review the implementation, tests, generated artifacts, and provenance together; confirm that no combined effect, overall-success rate, project-applicability claim, or new reader-facing filtering term entered the producer.
- [ ] 5.3 Record the stable metric keys, values, populations, denominators, corpus snapshots, producer revision, generated artifact identities, approved RQ6 framing, prohibited interpretations, and required semantic-review gate for the downstream thesis reconciliation change.
- [ ] 5.4 Archive and synchronize this producer change only after every task and verification gate passes; update the thesis planning artifacts in a separate thesis-repository change before editing RQ6 prose.
