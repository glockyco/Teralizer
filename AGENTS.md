# Repository Guidelines — Teralizer

Teralizer transforms JUnit tests into property-based jqwik tests. It runs Symbolic PathFinder (SPF)
in constraint-collection mode along a test's concrete path to extract path-exact specifications
(input partitions + symbolic outputs), then generates property-based tests that explore more inputs
within the same execution paths. Java/Gradle pipeline + PostgreSQL + a Python analysis project.

## Measurement integrity
No check can enforce this section. Every other rule in this file has a test, a command, or a
loud failure behind it. This one depends on you.

First-run numbers stand. A runtime limit is a real filter and a part of the measured system.
A stage that times out is a result, not noise. Never delete a project or a fixture and run it
again to get a cleaner number. Never run anything again to pick the better of two numbers.
Every gate runs once. If you suspect nondeterminism, investigate it as a defect. Do not average
it away with repeat runs.

## Dev environment
Nix provides the toolchain. The devshell pins Java 8, because `build.gradle` targets 1.8 and
jpf-core reads class files through its own bytecode model and rejects anything newer. The shell
also provides Maven, PostgreSQL 17, uv and git-lfs, and it sets `JAVA_HOME`.

Enter the shell in one of two ways:
- Run `direnv allow` once. The shell then loads whenever you enter the repository. `.envrc`
  holds `use flake`.
- Run `nix develop` for an interactive shell, or `nix develop --command <cmd>` for one command.

Every command in this file assumes that you are inside the devshell. Docker is the exception,
because the devshell does not provide it. Docker must come from your own installation.

## Commands
| Task | Command |
|---|---|
| Build (incl. SPF submodules) | `./gradlew build` |
| Verify pipeline fixture corpus — 5-10 min. Run at a wave end, never per change | `scripts/verify-pipeline.sh` |
| Verify sentinel spike subset — 10 min. A measurement event. Ask the operator first | `REPOREAPERS_DB=postgres_sentinel_verify REPOREAPERS_DATA_DIR=data/sentinel-verify REPOREAPERS_CONFIG_DIR=project-configs/sentinel scripts/run-reporeapers-rerun.sh --reset-db` |
| Run one config — a full pipeline for one project. Minutes to hours | `./gradlew run -Dteralizer.config=<config-file>` |
| Start / stop DB | `./gradlew startPostgres` / `./gradlew stopPostgres` |
| DB UI | `docker compose up adminer` → http://localhost:18080. The password is `DB_PASSWORD` from `.env`, and it defaults to `postgres` |
| Analysis deps | `uv sync --directory analysis` |
| Build eval reports | `uv run --directory analysis python -m teralizer.eval all` |
| Lint / format / types / tests | `uv run --directory analysis ruff check --fix .` · `ruff format .` · `ty check .` · `pytest` |

Run everything from the project root. CI is the gate that always runs.
`.github/workflows/build.yml` executes `./gradlew build`, the commit hooks, and `pytest -m "not db"`.
It enforces no check that needs a database, so the `pre-push` hook is the last point those run.
`lefthook.yml` holds the hooks and entering the devshell installs them. Each job reaches its tool
through `nix develop`, so a commit works from an editor or a GUI client, and `uv.lock` decides every
tool version. Use `scripts/publish-analysis.sh` to render the complete report set and to copy
citable tables and CSV data into the paper repository.

## Verification tiers
Match the gate to the change. Goldens are observed truth: on a mismatch, investigate instead of
editing the golden to match broken output.

| Change | Gate |
|---|---|
| Analysis (`teralizer.eval`, its tests) | `pytest`. The devshell's hooks add formatting, lint and types |
| Java unit-level | `./gradlew test --tests '<Class>'` while iterating, one full `./gradlew build` before commit |
| Pipeline behavior (codegen, SPF, filters, licenses, build files) | `scripts/verify-pipeline.sh` (~5–10 min, full fixture corpus) — ONCE PER WAVE of related changes, at the wave's end or when a golden must flip, NEVER per commit or per small change; iterate with `--only` below |
| SPF submodule (`jpf-symbc/**`) | additionally `cd jpf-symbc && ./gradlew :jpf-symbc:test` (the root build does NOT run this suite, so a red suite survives "build green" without it) |
| One fixture while iterating | `scripts/run-verification-corpus.sh --only <fixture-name>` (~45 s) |
| Real-world seams (surefire versions, reports, big suites) | sentinel subset (~10 min, five stable projects, expected census in the config headers) |
| Full spike / corpus | evaluation events only, never a debugging loop |

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
- Classify a failure by its type, never by its message. A message is prose and it changes.
  `TaskDiagnosticClassifierCouplingTest` fails when the classifier matches text that no source
  produces, and `EXTERNAL_MESSAGES` in that test lists the tool that owns each foreign string.
- `./gradlew spotlessApply` before committing (build gate enforces formatting).

## Layout & config
- Architecture / DB references: `docs/architecture.md`, `docs/database.md`.
- Why an entity is excluded, and which column may be trusted to say so:
  `docs/exclusion-model.md`. Required reading before quoting any exclusion figure.
- Planning: current work lives in `openspec/changes/`, and accepted behavior lives in `openspec/specs/`. Use `openspec list --json` before starting or resuming work.
- Config: Typesafe Config (HOCON). Examples are in `project-configs/example-*.conf`.
- Analysis lives in `analysis/`.
- Exports: `save_latex_table`, `save_csv_data`, `save_figure` from `teralizer.exports` →
  `analysis/output/<variant>/{tables,data,figures}`. `_get_output_base` selects the variant.
- Paper sync (on-demand only): `uv run --directory analysis python sync.py`; `PAPER_REPO_PATH` in
  `.env`. Never sync automatically.

## Evaluation analysis
Before you call a behaviour a defect, search the tests and `git log` for it. Decisions here are
recorded as regression tests named after the observable symptom, such as
`leavesVintageInitializationErrorUnlinked` and `ProcessingPipelineCascadeTest`, and as commit
messages that give the reason. Unexpected data is more often a decision you have not read than a
bug. Read accepted OpenSpec contracts and active changes after the tests and history. Historical planning records are available only through Git history.

**Evidence rule:** implementation docstrings describe intent and mechanism, never empirical
distributions or outcomes. A claim about what the corpus contains must come from a query or report,
not from comments such as `WideningLicense`'s explanation of refusal handling or boxed output capture.
Treat boxed output capture as an implemented mechanism, not as evidence that refusals are recoverable.

**Metric rule:** RQ6 definitions live in `analysis/src/teralizer/eval/reports/_funnel.py` and the
report builders. Quote every figure with its measure and denominator. Current Stage-4 success
requires `generated_filter_passed`.

Before you compare figures from two databases, run
`uv run --directory analysis python -m teralizer.comparability <db_a> <db_b>`. It exits non-zero
when the two runs record different tool versions, or when a funnel table has a different set of
columns. A column that one run records and the other does not makes a per-project comparison
meaningless, and a query that never names the column cannot show you that.

## Database
Dockerized Postgres, container `postgres-teralizer`, `localhost:5432`. Protected DBs are the
corpora the published paper depends on. Never drop one and never use one for an experiment.
`src/main/resources/db/protected-databases.txt` is the canonical list. Among them, `postgres_dev`
holds eqbench and commons-utils, and `postgres_test` holds RepoReapers. Corpora still being
iterated for the next paper version stay deliberately unprotected. A run names its target database in its profile (`teralizer.database.name`),
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
- Explicit over implicit. Write few comments, and let them explain *why* and not *what*. Fail fast.
- No marketing/temporal language in code or comments ("modern", "new", "enhanced").
- Never reference paper section numbers (e.g. "Section 4.1") in code.
- Commits: follow `skill://commit`.

## Boundaries
- `projects/` holds git submodules (target programs) and is **read-only**. Do not edit them as
  Teralizer work. You can delete Teralizer-generated `_*_Generalized_*_Test.java` litter there,
  and the runner scripts already do it.
- Never commit build artifacts or generated datasets.
- Local state (gitignored run outputs, scratch DBs, generated litter — what owns it, what is
  safe to delete): `docs/local-state.md`.
