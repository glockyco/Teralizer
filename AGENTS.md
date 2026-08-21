# Teralizer agent guidance

Teralizer converts existing JUnit tests into property-based jqwik tests. The Java pipeline uses
single-path symbolic analysis. The Python project under `analysis/` builds evaluation reports.

## Keep guidance authoritative

- Keep only repository-wide rules in this file. Put area-specific rules in the nearest scoped
  `AGENTS.md` or `.omp/rules/` file.
- Trust executable declarations over prose. Update the declaration and its focused test together.
- Do not copy directory inventories, schema listings, corpus counts, or report values into guidance.
  These facts change and have stronger owners.

| Fact | Authority |
|---|---|
| Pipeline order and scheduling | `ProcessingStage`, `PipelinePlanner`, and `src/main/resources/db/create-views.sql` |
| Database schema | `src/main/resources/db/create-tables.sql` and generated jOOQ bindings |
| Registered reports | `analysis/src/teralizer/eval/registry.py` |
| Empirical values | Registered reports and `analysis/reports/provenance.json` |
| Current work and accepted requirements | `openspec/changes/` and `openspec/specs/` |
| Historical rationale | Tests and Git history |

## Preserve measurement integrity

- Keep first-run results. A timeout, exclusion, or stage failure is a measured outcome.
- Never rerun a project to select a cleaner result. Investigate nondeterminism as a defect.
- Treat a measurement database and its run root as one record. Never delete one without the other.
- Do not start a corpus, sentinel, hotspot, or JARVIS run without explicit operator approval.

## Use the pinned environment

Enter the Nix devshell before running project commands. It pins Java 8 because the build and
jpf-core bytecode model require it. Use `direnv allow`, `nix develop`, or
`nix develop --command <command>`.

Run commands from the repository root.

| Change | Required check |
|---|---|
| Java implementation | Focused `./gradlew test --tests '<Class>'`, then `./gradlew build` |
| `jpf-symbc/**` | `cd jpf-symbc && ./gradlew :jpf-symbc:test` in addition to the root build |
| Analysis code | `uv run --directory analysis pytest`, `uv run --directory analysis ruff check .`, and `uv run --directory analysis ty check .` |
| Pipeline behavior | `scripts/run-verification-corpus.sh --only <fixture>` while iterating, then one `scripts/verify-pipeline.sh` at the end of the change wave |
| Repository hygiene | `lefthook run pre-commit --all-files` |

Do not bypass hooks. If `jpf-symbc` is absent, run `git submodule update --init --recursive`.

## Protect data and generated state

- `projects/` contains read-only submodules. Do not modify them as Teralizer source.
- `src/main/resources/db/corpora.toml` owns protected corpus identity. Never use a registered
  corpus for an experiment or destructive command.
- Runner scripts own `scratch_*` databases. Create, reset, and drop them only through those runners.
- Never edit or commit PostgreSQL storage, generated datasets, run logs, or fixture residue.
- The verification driver regenerates its run root and removes generated fixture sources. Delete
  residue only when no verification run is active.
- `scripts/detached-run.sh stop` and `sweep` own detached processes. Delete their tracking records
  only after the process stops.

## Keep evidence traceable

- Comments and docstrings explain mechanism and intent. They are not evidence for corpus outcomes.
- Quote each empirical value with its measure and denominator. RQ6 definitions live in
  `analysis/src/teralizer/eval/reports/_funnel.py` and its report builders.
- Before comparing databases, run
  `uv run --directory analysis python -m teralizer.comparability <db_a> <db_b>`.
- When observed behavior conflicts with prose, trust the run, test, query, or report. Correct the
  prose in the same change.

## Follow scoped rules

- `.omp/rules/pipeline.md` applies to pipeline source, tests, configuration, and DDL.
- `.omp/rules/db.md` applies to analysis queries and SQL.
- `project-configs/AGENTS.md` applies to run profiles and project configurations.
- `verification/fixtures/` plus `verification/golden/` own reproducible pipeline regressions.

Write explicit code. Fail fast. Let comments explain why. Do not put paper section numbers or
marketing terms such as "modern", "new", or "enhanced" in code comments.
