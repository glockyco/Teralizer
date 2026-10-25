# Teralizer

Teralizer turns conventional Java JUnit tests into property-based jqwik tests through semantics-based test generalization. It is a research prototype for studying test generalization and reproducing the evaluation in [Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to Property-Based Tests](https://arxiv.org/abs/2512.14475), not a drop-in test-library replacement.

The pipeline extracts specifications from implementations through single-path symbolic analysis. This repository holds the Java tool, the Python analysis, and the replication scripts. The [Zenodo replication package](https://doi.org/10.5281/zenodo.18242626) supplies production corpus data that is not in a source checkout.

## Inspect the results

You can read the rendered [analysis reports](analysis/reports) without running the tool. Use the published replication package if you want the accompanying data or want to reproduce the results.

## Package contents

Choose the archives for the work you want to do:

- Browse the published results with `teralizer-results`.
- Rebuild the analysis with `teralizer-core`.
- Exercise the pipeline with `teralizer-core` and `teralizer-projects-extended-sample`.
- Reproduce both corpora with `teralizer-core`, `teralizer-projects-primary`, and `teralizer-projects-extended`.

The optional `teralizer-data-primary` and `teralizer-data-extended` archives hold pipeline logs, reports, and generalized tests. See [REQUIREMENTS.md](REQUIREMENTS.md) for prerequisites and disk planning, [INSTALL.md](INSTALL.md) for installation, and [replication/README.md](replication/README.md) for the corpus-package storage contract.

## Set up replication

You need Bash, Docker with Compose, and `uv`. Run commands from the repository or installed core package root.

In an installed core release:

```sh
./replication/quick-start.sh
```

In a source checkout, first set `CORPUS_PACKAGE_DIR` to the corpus component from an installed or downloaded package, then run the same command:

```sh
export CORPUS_PACKAGE_DIR
./replication/quick-start.sh
```

The quick start verifies the selected package, starts PostgreSQL and Adminer, and restores the published databases. It can also extract sibling project and data archives. Do not point it at databases you need to preserve: the restore uses the importer's `--force` option.

Open the database browser at <http://localhost:18080>. The connection details are in [replication/docker-compose.yml](replication/docker-compose.yml). List published corpus IDs and resolve a database name with:

```sh
./scripts/corpus-registry list --published
./scripts/corpus-registry get controlled database
```

Stop replication services without deleting their data:

```sh
docker compose -f replication/docker-compose.yml down
```

## Rebuild the analysis

After setup, regenerate the reports and compare the publication outputs with the shipped reference:

```sh
./replication/scripts/run-analysis.sh verify
./replication/scripts/verify-outputs.sh original verify
```

The analysis writes rendered reports under [analysis/reports](analysis/reports) and publication outputs under [analysis/output](analysis/output). Each run replaces the selected output variant's tables, data, and figures.

To build an individual report, use its name from the [report registry](analysis/src/teralizer/eval/registry.py):

```sh
uv run --directory analysis python -m teralizer.eval dataset --targets md
```

## Run test generalization

Install the project-source archives for the selected corpus before running the pipeline. Pipeline runs build and execute external project code, download dependencies, and write generalized tests, logs, run state, and scratch-database results. Keep these runs separate from the published measurement databases.

Start with a subset of the real-world corpus:

```sh
./replication/scripts/run.sh --corpus real-world --count 5
```

To run the full real-world corpus, omit `--count`. The controlled corpus needs test generation followed by generalization:

```sh
./replication/scripts/run.sh --corpus controlled --phase generation
./replication/scripts/run.sh --corpus controlled --phase generalization
```

Runs resume from saved progress. Resource limits and external project dependencies can change the results. See the runner's help for selection and execution options:

```sh
./replication/scripts/run.sh --help
```

## Development

Read [AGENTS.md](AGENTS.md) for the pinned environment, focused checks, and measurement-safety rules. [openspec/specs](openspec/specs) holds the accepted behavior contracts, and [project-configs/AGENTS.md](project-configs/AGENTS.md) explains run profiles. [dataset/README.md](dataset/README.md) covers project selection.

On macOS or Linux, enter the pinned Nix environment before building:

```sh
nix develop
git submodule update --init --recursive jpf-symbc
./gradlew build
```

For native Windows development, use Git for Windows, Git LFS, Docker Desktop with Linux containers, `uv`, and `lefthook`. The interpreter selection lives in [.python-version](.python-version). From the repository root:

```powershell
git lfs pull
git submodule update --init --recursive jpf-symbc
lefthook install
docker build -t teralizer .
docker run --rm teralizer timeout 600 ./gradlew test --tests teralizer.util.ConfigurationTest --tests teralizer.processing.PipelinePlannerTest --tests teralizer.transformer.GoldenRenderingTest --tests teralizer.jpf.TestGeneralizationListenerSymbolicTest --no-daemon
```

The container supplies the Java and Linux runtime needed by Symbolic Pathfinder. This test selection does not start a measurement corpus. Profiles that depend on symlinks need a checkout that preserves symlinks.

## Citation and license

Use [CITATION.cff](CITATION.cff) for citation metadata. Source code is licensed under [MIT](LICENSE-MIT). Data and documentation are licensed under [CC BY 4.0](LICENSE-CC-BY-4.0). Analyzed projects retain their own licenses.
