## 1. Publish Effectiveness Metrics

- [x] 1.1 Define the three RQ1 effectiveness cohorts, expected project/budget/variant matrix, and final semantic metric keys in the report module.
- [x] 1.2 Implement a pure RQ1 headline summary that rejects missing, duplicate, or unexpected cohort rows before calculating absolute-improvement range endpoints and the developer-written baseline.
- [x] 1.3 Attach typed populations, source-query provenance, and aggregate macro selection to every effectiveness metric.
- [x] 1.4 Add focused RQ1 tests for exact ranges, cohort separation, baseline selection, incomplete matrices, duplicate rows, and aggregate macro output.

## 2. Publish Real-World Output Metrics

- [ ] 2.1 Extend the existing RQ6 lifecycle result to count distinct projects containing final-usable `IMPROVED_200_TRIES` generalizations without changing the accepted lifecycle predicate.
- [ ] 2.2 Publish the final-usable generalization and project counts with typed populations, real-world input identity, source-query provenance, and aggregate macros.
- [ ] 2.3 Fail report construction unless the final-usable project set equals the existing end-to-end applicable project set.
- [ ] 2.4 Add focused RQ6 tests for final-usable entity counts, project-population equality, disagreement failures, and aggregate macro output.

## 3. Publish Mechanism Metrics

- [ ] 3.1 Add the overall widening-refusal share using total widening refusals as numerator and all eligible generalization attempts as denominator; retain refusal-total denominators for branch shares.
- [ ] 3.2 Promote the existing assertion-survival count/share and the new widening-refusal count/share to aggregate macros without duplicating their calculations.
- [ ] 3.3 Add focused tests for value kinds, populations, numerator/denominator relations, attempt conservation, branch conservation, and aggregate macro output.

## 4. Verify Headline Publication

- [ ] 4.1 Add an integration test that asserts every approved effectiveness, applicability, real-world output, and mechanism key appears exactly once in aggregate macros and provenance.
- [ ] 4.2 Assert that no controlled-versus-real-world generalization-success comparison or composite applicability score enters the headline key set.
- [ ] 4.3 Run the focused RQ0, RQ1, RQ6, macro, and provenance tests in the pinned analysis environment.
- [ ] 4.4 Run the complete registered report set once against the recorded producer inputs and inspect the emitted headline values and denominators.
- [ ] 4.5 Update the generated tracked report, macro, and provenance artifacts from that single report run.
- [ ] 4.6 Run `uv run --directory analysis pytest`, `uv run --directory analysis ruff check .`, and `uv run --directory analysis ty check .`.
- [ ] 4.7 Run `openspec validate publish-teralizer-headline-evidence --strict` and `lefthook run pre-commit --all-files`.

## 5. Hand Off to Thesis Wording

- [ ] 5.1 Record the four evidence dimensions, stable metric keys, values, populations, denominators, corpus snapshots, and producer commit in the thesis reconciliation change.
- [ ] 5.2 Keep effectiveness and applicability marked primary and demonstrated output and mechanism insight marked supporting in the handoff.
- [ ] 5.3 Block thesis abstract and repeated-summary edits until the separate wording-review session resolves reader-facing language and placement.
- [ ] 5.4 Review implementation, tests, generated artifacts, and the thesis handoff together before archiving this producer change.
