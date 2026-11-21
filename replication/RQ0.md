# RQ0 evidence

RQ0 compares Teralizer with JARVIS as published in Hila Peleg, Dan Rasin and Eran Yahav, "Generating Tests by Example", VMCAI 2018, <https://doi.org/10.1007/978-3-319-73721-8_19>. JARVIS has no public implementation, so the comparison uses the values its paper reports. This page lists every input of the comparison and what each command establishes about it.

## Inputs

| Input | Location | Role |
|---|---|---|
| JARVIS Table 1 projects and Table 2 rows | `TABLE1_PROJECTS` in `analysis/src/teralizer/eval/reports/rq0_jarvis.py` and `JARVIS_TABLE2` in `analysis/src/teralizer/jarvis_scoreboard.py` | Published values, carried verbatim. Reproduction reads them and does not regenerate them. |
| Scorecard fixtures | Commons Lang and Commons Math at the pins in `scripts/lib/jarvis-pins.sh`, described in `data/jarvis-scoreboard/PROVENANCE.md` and shipped in `projects-jarvis-scoreboard` | Projects of the scorecard collection, which covers the Table 2 scenarios |
| Census fixtures | The twelve Commons projects of Table 1 at the pins in `data/jarvis-census/PROVENANCE.md`, shipped in `projects-jarvis-census` | Projects of the census collection |
| Corpus `jarvis-scenarios` | `corpus-jarvis-scenarios` | Scorecard runs at 100, 200 and 1,000 tries, their generated tests, value-log paths and mutation results |
| Corpus `jarvis-benchmark` | `corpus-jarvis-benchmark` | Census runs and the task status of every project, from which the report derives each project's completion |
| Value logs | `values.tsv` files below `data/jarvis-scoreboard/fixtures/` and `data/jarvis-census/fixtures/` in core | The parameter values each generated test tried |
| Value facts | `analysis/data/report-inputs/jarvis-value-facts.json` | PVC per scenario and per method under test, built from the value logs. It records the count, size and checksum of the logs it read. |
| CUT-PVC captures | `data/cut-pvc-capture/raw/` in core | Parameter values that the original Commons test cases of Table 2 pass to their methods under test |
| CUT-PVC values | `analysis/data/jarvis-cut-values/cut_values.tsv` | Distinct values per Table 2 row, aggregated from the captures |
| Census run ledger and logs | `data/jarvis-census/status.tsv` and `data/jarvis-census/run-logs/` | Exit code and pipeline log of every census run attempt. The report takes each project's completion from the task records in `jarvis-benchmark`, not from the ledger. |

The RQ0 outputs are `reports/rq0.md`, the CSV files `rq0-breadth-summary`, `rq0-table2-comparison` and `rq0-pvc-budget` below `build/rq0/`, their LaTeX tables below `build/`, the macros in `build/macros/rq0.tex`, and the RQ0 entry of `reports/provenance.json`.

## What each command establishes

`./artifact smoke` and `./artifact reproduce-results rq0` restore the two JARVIS corpora and regenerate every RQ0 output from them, the value facts and the CUT-PVC values, and compare each output with the reference. They also check that the value logs in core have the count, size and checksum the value facts record, and that aggregating the CUT-PVC captures again yields `cut_values.tsv`. They repeat no collection.

`./artifact collect-reduced jarvis-scenarios` runs Teralizer on both scorecard fixtures at all three budgets into the scratch database `scratch_jarvis_scoreboard`. It then captures the CUT-PVC values again from Commons Lang and Commons Math checked out at their pins, and compares the aggregate with `cut_values.tsv`. The reduced and the full scorecard collection are the same. jqwik chooses the generated inputs at random, so a rerun yields other value logs and PVC counts than the published corpus.

`./artifact collect-reduced jarvis-benchmark` runs the census on the commons-cli and commons-csv fixtures into the scratch database `scratch_jarvis_census`, and `./artifact collect-full jarvis-benchmark` runs all twelve.

A collection writes only its scratch database and its working copy below `state/`. It does not rebuild the value facts, and the reports keep reading the published corpora.
