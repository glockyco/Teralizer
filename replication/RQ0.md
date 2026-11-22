# RQ0 evidence

RQ0 compares Teralizer with the values that Peleg, Rasin and Yahav, "Generating Tests by Example", VMCAI 2018, <https://doi.org/10.1007/978-3-319-73721-8_19>, report for JARVIS, which has no public implementation.

| Input | Location |
|---|---|
| JARVIS Table 1 projects and Table 2 rows, as published | `rq0_jarvis.py` and `jarvis_scoreboard.py` in `analysis/src/teralizer/` |
| Scorecard and census fixtures, with their pinned revisions | `PROVENANCE.md` in `data/jarvis-scoreboard/` and `data/jarvis-census/`, sources in the `projects-jarvis-*` archives |
| Teralizer's scorecard and census runs | corpora `jarvis-scenarios` and `jarvis-benchmark` |
| Values each generated test tried, and the PVC facts built from them | `values.tsv` files below `data/jarvis-*/fixtures/`, `analysis/data/report-inputs/jarvis-value-facts.json` |
| Values the original Commons tests pass, and their aggregate | `data/cut-pvc-capture/raw/`, `analysis/data/jarvis-cut-values/cut_values.tsv` |

`./artifact reproduce-results rq0` regenerates the RQ0 outputs from these inputs. It also checks that the value facts and `cut_values.tsv` match the raw files they were built from. It repeats no collection.

`./artifact collect-reduced jarvis-scenarios` reruns the scorecard and the CUT-PVC capture, and `collect-reduced jarvis-benchmark` reruns the census on two of the twelve projects. The CUT-PVC values come out identical. The generated tests draw random inputs, so their values differ from the published corpus.
