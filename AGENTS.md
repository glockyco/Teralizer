# Repository Guidelines — Teralizer

Teralizer transforms JUnit tests into property-based jqwik tests. It runs Symbolic PathFinder (SPF)
in constraint-collection mode along a test's concrete path to extract path-exact specifications
(input partitions + symbolic outputs), then generates property-based tests that explore more inputs
within the same execution paths. Java/Gradle pipeline + PostgreSQL + a Python analysis project.

## Commands
| Task | Command |
|---|---|
| Build (incl. SPF submodules) | `./gradlew build` |
| Verify pipeline fixture corpus | `scripts/verify-pipeline.sh` |
| Run one config | `./gradlew run -Dteralizer.config=<config-file>` |
| Start / stop DB | `./gradlew startPostgres` / `./gradlew stopPostgres` |
| DB UI | `docker compose up adminer` → http://localhost:18080 (password: `teralizer`) |
| Analysis deps | `uv sync --directory analysis` |
| Notebooks | `uv run --directory analysis jupyter lab` |
| Validate (pre-commit gate) | `uv run --directory analysis python validate.py --changed` |
| Lint / format / types / tests | `uv run --directory analysis ruff check --fix .` · `ruff format .` · `ty check .` · `pytest` |

Run everything from the project root. Validate before committing (`validate.py --changed`, or
`--notebook <NAME.ipynb>` for one notebook; no flag = full run).

## Layout & config
- Architecture / DB references: `docs/architecture.md`, `docs/database.md`.
- Planning & roadmap: read `docs/plans/INDEX.md` first; the north-star, strategy sequence, and current focus live in `docs/plans/2026-06-26-teralizer-overview.md`.
- Config: Typesafe Config (HOCON); examples in `project-configs/example-*.conf`.
- Analysis in `analysis/`; legacy notebooks under `notebooks/legacy/` are excluded via `pyproject.toml`.
- Exports: `save_latex_table`, `save_csv_data`, `save_figure` from `teralizer.exports` →
  `analysis/output/{tables,data,figures}`.
- Paper sync (on-demand only): `uv run --directory analysis python sync.py`; `PAPER_REPO_PATH` in
  `.env`. Never sync automatically.

## Database
Dockerized Postgres, container `postgres-teralizer`, `localhost:5432`. Databases: `postgres_dev`
(eqbench + commons-utils), `postgres_test` (RepoReapers), `postgres_timeout_retry`. Direct query:
`docker exec -i postgres-teralizer psql -U postgres -d <db> -c "..."`.
- Key tables: `project`, `test`, `assertion`, `generalization`, `task`, `filter_result`.
- Key views: `v_project_failures`, `mv_exclusions_*`.
- For read-only analytical queries, use the read-only `teralizer-db` MCP.

## Pitfalls
- **Raw-SQL `LIKE` escaping:** in SQLAlchemy raw strings, double the percent signs —
  `LIKE '%%_TRIES'`, not `LIKE '%_TRIES'` (a single `%` causes a parameter error).
- **SPF submodule:** if `jpf-symbc` is missing or the build hits classpath errors, run
  `git submodule update --init --recursive`, then rebuild.

## Style & commits
- Explicit over implicit; minimal comments (explain *why*, not *what*); fail fast.
- No marketing/temporal language in code or comments ("modern", "new", "enhanced").
- Never reference paper section numbers (e.g. "Section 4.1") or legacy notebooks in code/notebooks.
- Commits: follow `skill://commit`.

## Boundaries
- `projects/` holds git submodules (target programs) — **read-only**; don't edit them as Teralizer work.
- Never commit build artifacts or generated datasets.
