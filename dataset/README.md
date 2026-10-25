# Teralizer project selection

These scripts select candidate Java projects from RepoReapers and generate Teralizer project configurations. Use them to rebuild the project-selection inputs, not to run test generalization. See the [root README](../README.md) for the pipeline.

## Setup

Use `uv` to install the Python environment declared in [pyproject.toml](pyproject.toml):

```sh
cd dataset
uv sync --frozen
```

Run the remaining commands from this directory. They write CSV files, a checkpoint, logs, and project configurations. Use a separate checkout if you need to preserve the tracked selection inputs unchanged.

## Select and inspect projects

The tracked `dataset_java.csv` is the starting input for enrichment. To rebuild it, download the entire dataset from [RepoReapers](https://reporeapers.github.io/results/1.html) as `dataset.csv` in this directory. With `awk` installed, select the Java candidates:

```sh
awk -F, 'NR==1 || ($2=="Java" && $10 > 5000 && $10 < 50000 && $11 > 0.2 && $11 < 0.8)' dataset.csv > dataset_java.csv
```

Set `GITHUB_TOKEN` in your environment before enrichment. [build-extended-dataset.py](build-extended-dataset.py) queries GitHub for build, Java, and test metadata. Its configuration block controls input, output, checkpoint, and selection limits.

```sh
uv run python build-extended-dataset.py
uv run python build-filtered-dataset.py
uv run python build-project-confs.py
```

[build-filtered-dataset.py](build-filtered-dataset.py) defines the eligibility criteria. [build-project-confs.py](build-project-confs.py) declares its output and log paths and skips configurations that already exist or have a matching run log. Review the generated configurations before using them in a corpus run.
