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
| Build eval reports | `uv run --directory analysis python -m teralizer.eval all` |
| Lint / format / types / tests | `uv run --directory analysis ruff check --fix .` · `ruff format .` · `ty check .` · `pytest` |

Run everything from the project root. The gate that always runs is CI:
`.github/workflows/build.yml` executes `./gradlew build` and `pytest -m "not db"`,
so database-backed checks are NOT enforced there and must be run locally.
`analysis/.pre-commit-config.yaml` adds ruff and ty, and applies only once you run
`pre-commit install`; it is not installed by cloning. Use `scripts/publish-analysis.sh` to render the complete report set
and copy citable tables and CSV data into the paper repository.

## Verification tiers
Match the gate to the change. Goldens are observed truth: on a mismatch, investigate instead of
editing the golden to match broken output.

| Change | Gate |
|---|---|
| Analysis (`teralizer.eval`, its tests) | `pytest` + the ruff/ty pre-commit hooks |
| Java unit-level | `./gradlew test --tests '<Class>'` while iterating, one full `./gradlew build` before commit |
| Pipeline behavior (codegen, SPF, filters, licenses, build files) | `scripts/verify-pipeline.sh` (~5–10 min, full fixture corpus) — ONCE PER WAVE of related changes, at the wave's end or when a golden must flip, NEVER per commit or per small change; iterate with `--only` below |
| SPF submodule (`jpf-symbc/**`) | additionally `cd jpf-symbc && ./gradlew :jpf-symbc:test` (the root build does NOT run this suite, so a red suite survives "build green" without it) |
| One fixture while iterating | `scripts/run-verification-corpus.sh --only <fixture-name>` (~45 s) |
| Real-world seams (surefire versions, reports, big suites) | sentinel subset (~10 min, five stable projects, expected census in the config headers) |
| Full spike / corpus | evaluation events only, never a debugging loop |

First-run numbers stand. Runtime limits (timeouts, memory) are real filters, part of the
measured system — a stage that times out is a result, not noise. Never delete-and-rerun a
project or fixture to get a cleaner number, and never rerun anything to pick the better of
two numbers. Every gate runs ONCE. Suspected nondeterminism is a defect to investigate, not
something to average away with repeat runs.

Sentinel and hotspot runs are measurement events, not per-change gates. A lever's
refusal-to-licensed conversion is verified by its fixture golden and unit tests at commit
time. The corpus-scale measurement BATCHES into the next naturally scheduled run (wave-end
JARVIS refresh, spike, or rerun) instead of triggering its own. Do not start a sentinel,
hotspot, or JARVIS run for a single change without explicit operator sign-off.

New pipeline defect ⇒ add a fixture reproducing it under `verification/fixtures/` with the fix
(golden pins it). kouchat, gedcom4j, xenqtt, uaicriteria, and sparkey are excluded from
verification subsets (60s-ceiling jitter or native flakes) and stay evaluation-corpus members.

## Tests (Java)
- jqwik `@Example` + `org.junit.Assert`. JUnit 4 `@Test` is NOT discovered (no vintage engine
  in Teralizer's own suite).
- Spoon-model tests build models via `VirtualFile` + `Launcher`. Virtual files have no
  `SourcePosition.getFile()`, so path-derived logic needs real files or the fixture corpus.
- JPF listener tests: `JpfListenerHarness` + target classes in `src/test/java/teralizer/jpf/targets/`.
- `./gradlew spotlessApply` before committing (build gate enforces formatting).

## Layout & config
- Architecture / DB references: `docs/architecture.md`, `docs/database.md`.
- Why an entity is excluded, and which column may be trusted to say so:
  `docs/exclusion-model.md`. Required reading before quoting any exclusion figure.
- Planning & roadmap: read `docs/plans/INDEX.md` first; the north-star, strategy sequence, and current focus live in `docs/plans/2026-06-26-teralizer-overview.md`.
- Config: Typesafe Config (HOCON); examples in `project-configs/example-*.conf`.
- Analysis lives in `analysis/`.
- Exports: `save_latex_table`, `save_csv_data`, `save_figure` from `teralizer.exports` →
  `analysis/output/{tables,data,figures}`.
- Paper sync (on-demand only): `uv run --directory analysis python sync.py`; `PAPER_REPO_PATH` in
  `.env`. Never sync automatically.

## Evaluation analysis
Search `docs/plans/` first: its audits record measured findings and prevent repeating completed
investigations before starting new analysis.

**Evidence rule:** implementation docstrings describe intent and mechanism, never empirical
distributions or outcomes. A claim about what the corpus contains must come from a query or report,
not from comments such as `WideningLicense`'s explanation of refusal handling or boxed output capture.
Treat boxed output capture as an implemented mechanism, not as evidence that refusals are recoverable.

**Metric rule:** RQ6 definitions live in `analysis/src/teralizer/eval/reports/_funnel.py` and the
report builders. Quote every figure with its measure and denominator. Before comparing figures from
two databases, confirm that both were computed through the same code path and therefore have the same
definition; in particular, current Stage-4 success requires `generated_filter_passed`.

## Database
Dockerized Postgres, container `postgres-teralizer`, `localhost:5432`. Protected DBs are the
corpora the published paper depends on (never drop, never use for experiments): `postgres_dev`
(eqbench + commons-utils) and `postgres_test` (RepoReapers). Corpora still being iterated for the
next paper version stay deliberately unprotected. A run names its target database in its profile (`teralizer.database.name`),
or on the command line with `-Dteralizer.database.name`. Targeting a protected corpus requires
`teralizer.database.allow-protected = true`. The canonical protected list lives in
`src/main/resources/db/protected-databases.txt`.
Experiments use scratch DBs (`postgres_verification`, `postgres_<purpose>_verify`) created and
dropped by their runner scripts. Direct query:
`docker exec -i postgres-teralizer psql -U postgres -d <db> -c "..."`.
- Key tables: `project`, `test`, `assertion` (incl. `output_spec_class`,
  `concretization_events`, `generalization_recipe`), `generalization`
  (`is_included`/`exclusion_info` — typed labels like `ORACLE_NOT_WIDENABLE`), `task`,
  `filter_result`, `jqwik_property_execution` (`tries`, `distinct_tuples`, `diagnostic_kind`).
- Cross-DB comparisons join on `root_path`, never on `id` (registration-order drift).

## Pitfalls
- **Raw-SQL `LIKE` escaping:** in SQLAlchemy raw strings, double the percent signs —
  `LIKE '%%_TRIES'`, not `LIKE '%_TRIES'` (a single `%` causes a parameter error).
- **SPF submodule:** if `jpf-symbc` is missing or the build hits classpath errors, run
  `git submodule update --init --recursive`, then rebuild.
- **HOCON merge:** profile configs MERGE with `reference.conf` — a block like
  `teralizer.generalizations` accumulates keys across files instead of replacing them. Define
  variants only in profile configs and check merged effects after editing either side.
- **`Configuration` freezes at class load:** tests never `System.setProperty` for config keys
  (works only under lucky class-load order). Put test defaults in
  `src/test/resources/reference.conf` instead.
- **`CREATE DATABASE` fails with a template1 collation mismatch** (after OS lib upgrades):
  `ALTER DATABASE template1 REFRESH COLLATION VERSION;` then retry (runner scripts guard this).

## Style & commits
- Explicit over implicit; minimal comments (explain *why*, not *what*); fail fast.
- No marketing/temporal language in code or comments ("modern", "new", "enhanced").
- Never reference paper section numbers (e.g. "Section 4.1") in code.
- Commits: follow `skill://commit`.

## Boundaries
- `projects/` holds git submodules (target programs) — **read-only**; don't edit them as
  Teralizer work. Exception: deleting Teralizer-generated `_*_Generalized_*_Test.java` litter
  there is fine (runner scripts do it automatically).
- Never commit build artifacts or generated datasets.
- Local state (gitignored run outputs, scratch DBs, generated litter — what owns it, what is
  safe to delete): `docs/local-state.md`.
