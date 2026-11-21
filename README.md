# Teralizer

Teralizer turns conventional Java JUnit tests into property-based jqwik tests through semantics-based test generalization. It is a research prototype for studying test generalization and reproducing the evaluation in [Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to Property-Based Tests](https://arxiv.org/abs/2512.14475), not a drop-in test-library replacement.

The pipeline extracts specifications from implementations through single-path symbolic analysis. This repository holds the Java tool, the Python analysis, and the replication scripts. The [Zenodo replication package](https://doi.org/10.5281/zenodo.17950380) supplies production corpus data that is not in a source checkout. Its DOI resolves to the latest version.

## Inspect the results

You can read the rendered [analysis reports](analysis/reports) without running the tool. Use the published replication package if you want the accompanying data or want to reproduce the results.

## Try it on an example

You need Bash, Git LFS and Docker. In a source checkout, build the image and run the example:

```sh
git lfs pull
git submodule update --init --recursive jpf-symbc
docker build -t teralizer .
scripts/try-example.sh
```

The script runs the pipeline on a copy of [projects/example-loan](projects/example-loan), a method with three JUnit tests, and prints:

- the outcome of each assertion: two become properties, and one is refused with a code that says why,
- the PIT mutants that the original tests and the generalized tests kill,
- the paths of the generalized test classes under `build/try-example`,
- the test results after a refactoring of `approvedAmount` that tests `score > 80` where the method tests `score >= 80`: the original tests pass, and one generalized test fails and reports the violating input.

The run starts its own PostgreSQL container and leaves it running, so that you can query the results. `scripts/try-example.sh --clean` removes everything the script created.

To run a project of your own, pass its directory:

```sh
scripts/try-example.sh path/to/project
```

The project has to build with Java 8, use Maven or Gradle with a `build.gradle` file, and test with JUnit 3, 4 or 5. The script works on a copy under `build/try-example` and never changes your project. It uses [project-configs/example-loan.conf](project-configs/example-loan.conf) with the path of your project, and it prints the same outcome, mutant and path lists.

## Package contents

The Zenodo release holds `release-manifest.json`, `checksums.sha256`, a release summary and one ZIP archive per component. The `core` archive holds this repository's source, the reference results and the reviewer commands. Each `corpus-*` archive holds one evaluation database, and each `projects-*` archive holds the projects of one collection. The release summary lists the archives that each workflow needs, with their download and disk sizes. See [REQUIREMENTS.md](REQUIREMENTS.md) for prerequisites and [replication/README.md](replication/README.md) for the corpus-package contract.

[INSTALL.md](INSTALL.md) describes how to download, check and extract the archives and how to check the installation with the smoke test.

## Reproduce the results

Run every command from the extracted `teralizer/` directory. The smoke test restores the two JARVIS corpora, rebuilds the RQ0 report and compares it with the reference:

```sh
./artifact smoke
```

With all four `corpus-*` archives extracted, rebuild every report, compare every output with the reference and check the paper's claims. [replication/CLAIMS.md](replication/CLAIMS.md) lists each claim with its inputs, outputs and expected values:

```sh
./artifact reproduce-results
```

`./artifact reproduce-results rq0` does the same for RQ0 alone. Both also check RQ0's file inputs against the raw evidence they were built from, as [replication/RQ0.md](replication/RQ0.md) describes. The reports land in `output/`, and `output/evidence.json` records the verdict on every output, claim and JARVIS input. The services need the network only while Docker pulls and builds their images. A corpus that is already restored and verified is reused by the next command.

To browse the databases, start Adminer and open <http://localhost:18080>. The connection details are in [replication/docker-compose.yml](replication/docker-compose.yml). [replication/DATA-DICTIONARY.md](replication/DATA-DICTIONARY.md) describes every table, the views that the reports read, and every result CSV file.

```sh
docker compose -f replication/docker-compose.yml up -d adminer
```

`./artifact clean` removes the services, their volumes and the `output/` and `state/` directories. The extracted archives stay. Set `COMPOSE_PROJECT_NAME` to keep the services of two workspaces apart.

In a source checkout, `analysis/reports` holds the rendered reports. To build one report there, use its name from the [report registry](analysis/src/teralizer/eval/registry.py):

```sh
uv run --directory analysis python -m teralizer.eval dataset --targets md
```

## Run test generalization

Extract the project-source component for the collection you want, then run:

```sh
./artifact collect-reduced <corpus>
./artifact collect-full <corpus>
```

The reduced scopes are:

- `controlled` generalizes the Commons Utils developer tests without generating tests first.
- `real-world` runs the projects listed in the [released sample](project-configs/replication/real-world-sample.txt).
- `jarvis-scenarios` runs both scorecard fixtures.
- `jarvis-benchmark` runs the Commons CLI and Commons CSV census fixtures.

Full collection runs all configurations. For `controlled`, it generates tests before generalizing them. For `real-world`, it needs the full project-source component instead of the sample component.

Collection builds and runs third-party code in Docker and needs network access to resolve its dependencies. The pipeline container works on copies below `state/`, sees the configurations read-only, has no Docker socket and no more than 8 GB of memory, and connects to PostgreSQL as a role that owns only the scratch databases, so the code it runs cannot change the restored corpora. Controlled collection has no per-config wall cap by default. Real-world collection uses the cap given in [the replication runner's help](replication/scripts/run.sh).

Working copies and pipeline data go under `state/collect/` by default. Reduced and full real-world runs have separate directories and scratch databases. Each collection prints the location of its ledger and logs, or writes the pipeline log to the terminal. It reports the scratch database name and leaves the published corpus databases unchanged.

To pause a controlled or real-world run, `touch <state>/STOP` at the exact stop path printed by the runner. The active configuration finishes before the runner stops. Run the same command again to resume from the ledger. JARVIS runs start over with an empty scratch database and ledger each time.

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

Use [CITATION.cff](CITATION.cff) for citation metadata. Source code is licensed under [MIT](LICENSE-MIT). Data and documentation are licensed under [CC BY 4.0](LICENSE-CC-BY-4.0). The bundled EvoSuite jar is under the LGPL 3.0, with its license files inside the jar, and the Symbolic PathFinder and JPF sources in `jpf-symbc/` are under the Apache License 2.0. Analyzed projects keep their own licenses, which the `third-party.json` of each project archive records.
