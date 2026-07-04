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
| Verify sentinel spike subset | `REPOREAPERS_DB=postgres_sentinel_verify REPOREAPERS_DATA_DIR=data/sentinel-verify REPOREAPERS_CONFIG_DIR=project-configs/sentinel scripts/run-reporeapers-rerun.sh --reset-db` |
| Run one config | `./gradlew run -Dteralizer.config=<config-file>` |
| Start / stop DB | `./gradlew startPostgres` / `./gradlew stopPostgres` |
| DB UI | `docker compose up adminer` → http://localhost:18080 (password: `teralizer`) |
| Analysis deps | `uv sync --directory analysis` |
| Notebooks | `uv run --directory analysis jupyter lab` |
| Validate (pre-commit gate) | `uv run --directory analysis python validate.py --changed` |
| Lint / format / types / tests | `uv run --directory analysis ruff check --fix .` · `ruff format .` · `ty check .` · `pytest` |

Run everything from the project root. Validate before committing (`validate.py --changed`, or
`--notebook <NAME.ipynb>` for one notebook; no flag = full run).

## Verification tiers
Match the gate to the change; goldens are observed truth (on a mismatch, investigate — never
edit a golden to match broken output).

| Change | Gate |
|---|---|
| Analysis code (`analysis/`) | `validate.py --changed` |
| Java unit-level | `./gradlew test --tests '<Class>'` while iterating; one full `./gradlew build` before commit |
| Pipeline behavior (codegen, SPF, filters, licenses, build files) | `scripts/verify-pipeline.sh` (~5 min, 9 fixtures, deterministic) |
| One fixture while iterating | reset + `DB_NAME=postgres_verification ./gradlew run -Dteralizer.config=project-configs/verification/fixture-<name>.conf` (~45 s) |
| Real-world seams (surefire versions, reports, big suites) | sentinel subset (~10 min; five stable projects, expected census in the config headers) |
| Full spike / corpus | evaluation events only — never a debugging loop |

New pipeline defect ⇒ add a fixture reproducing it under `verification/fixtures/` with the fix
(golden pins it). kouchat, gedcom4j, xenqtt, uaicriteria, sparkey are excluded from verification
subsets (60s-ceiling jitter / native flakes); they stay evaluation-corpus members.

## Tests (Java)
- jqwik `@Example` + `org.junit.Assert` — JUnit 4 `@Test` is NOT discovered (no vintage engine
  in Teralizer's own suite).
- Spoon-model tests build models via `VirtualFile` + `Launcher`; virtual files have no
  `SourcePosition.getFile()`, so path-derived logic needs real files or the fixture corpus.
- JPF listener tests: `JpfListenerHarness` + target classes in `src/test/java/teralizer/jpf/targets/`.
- `./gradlew spotlessApply` before committing (build gate enforces formatting).

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
Dockerized Postgres, container `postgres-teralizer`, `localhost:5432`. Protected DBs (never drop,
never use for experiments): `postgres_dev` (eqbench + commons-utils), `postgres_test`
(RepoReapers), `postgres_timeout_retry`, `postgres_reporeapers_rerun` (pre-fusion baseline).
Experiments use scratch DBs (`postgres_verification`, `postgres_<purpose>_verify`) created and
dropped by their runner scripts. Direct query:
`docker exec -i postgres-teralizer psql -U postgres -d <db> -c "..."`.
- Key tables: `project`, `test`, `assertion` (incl. `output_spec_class`,
  `concretization_events`, `generalization_recipe`), `generalization`
  (`is_included`/`exclusion_info` — typed labels like `ORACLE_NOT_WIDENABLE`), `task`,
  `filter_result`, `jqwik_property_execution` (`tries`, `distinct_tuples`, `diagnostic_kind`).
- Cross-DB comparisons join on `root_path`, never on `id` (registration-order drift).
- For read-only analytical queries, use the read-only `teralizer-db` MCP.

## Pitfalls
- **Raw-SQL `LIKE` escaping:** in SQLAlchemy raw strings, double the percent signs —
  `LIKE '%%_TRIES'`, not `LIKE '%_TRIES'` (a single `%` causes a parameter error).
- **SPF submodule:** if `jpf-symbc` is missing or the build hits classpath errors, run
  `git submodule update --init --recursive`, then rebuild.
- **HOCON merge:** profile configs MERGE with `reference.conf` — a block like
  `teralizer.generalizations` accumulates keys across files instead of replacing them. Define
  variants only in profile configs; check merged effects after editing either side.
- **`Configuration` freezes at class load:** tests never `System.setProperty` for config keys
  (works only under lucky class-load order) — put test defaults in
  `src/test/resources/reference.conf` instead.
- **`CREATE DATABASE` fails with a template1 collation mismatch** (after OS lib upgrades):
  `ALTER DATABASE template1 REFRESH COLLATION VERSION;` then retry (runner scripts guard this).

## Style & commits
- Explicit over implicit; minimal comments (explain *why*, not *what*); fail fast.
- No marketing/temporal language in code or comments ("modern", "new", "enhanced").
- Never reference paper section numbers (e.g. "Section 4.1") or legacy notebooks in code/notebooks.
- Commits: follow `skill://commit`.

## Boundaries
- `projects/` holds git submodules (target programs) — **read-only**; don't edit them as
  Teralizer work. Exception: deleting Teralizer-generated `_*_Generalized_*_Test.java` litter
  there is fine (runner scripts do it automatically).
- Never commit build artifacts or generated datasets.
