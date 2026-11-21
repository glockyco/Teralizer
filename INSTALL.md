# Installation

## Download

Download from Zenodo, [10.5281/zenodo.17950380](https://doi.org/10.5281/zenodo.17950380), into an empty directory:

- `release-manifest.json` and `checksums.sha256`,
- `teralizer-replication-core.zip`,
- the further archives of your workflow, which the release summary lists. The smoke test needs `teralizer-replication-corpus-jarvis-scenarios.zip` and `teralizer-replication-corpus-jarvis-benchmark.zip`.

The DOI resolves to the latest version. [REQUIREMENTS.md](REQUIREMENTS.md) lists the prerequisites.

## Check and extract

```bash
sha256sum --check --ignore-missing checksums.sha256
unzip teralizer-replication-core.zip
unzip teralizer-replication-corpus-jarvis-scenarios.zip
unzip teralizer-replication-corpus-jarvis-benchmark.zip
```

On macOS, `shasum -a 256 --check --ignore-missing checksums.sha256` does the same check. Core extracts to `teralizer/`, and every other archive to its own directory below `teralizer/components/`, so the order of extraction does not matter. Keep `release-manifest.json` next to `teralizer/`, where the reviewer commands look for it.

## Check the installation

```bash
cd teralizer
./artifact preflight smoke
./artifact smoke
```

Preflight checks Docker, Compose and every file of the components that the smoke test needs, and it lists all problems at once. The smoke test builds the analysis image, restores the two JARVIS corpora, rebuilds the RQ0 report and compares it with the reference. It ends with `smoke passed`.

If an extraction was interrupted or preflight reports changed files, delete that component's directory, `teralizer/` for core or `teralizer/components/<component>/` for any other, check the archive against `checksums.sha256` and extract it again.

[README.md](README.md) describes the remaining workflows. `./artifact clean` removes the services, their volumes and the `output/` and `state/` directories, and it leaves the extracted archives in place.
