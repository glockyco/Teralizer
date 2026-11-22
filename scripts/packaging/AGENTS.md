# Packaging and artifact tooling

These scripts build and validate the Zenodo replication artifact. Nothing here is part of
the artifact evaluation workflow, and no run driver references this directory.

| Script | Job |
|---|---|
| `export-corpus-remote.sh` | Exports one corpus beside PostgreSQL on the data host, for `replication/scripts/export-databases.sh`. |
| `release/release.toml` | Declares the release identity, components, workflows and platforms. `python -m teralizer.release build` builds a candidate from it, as `replication/README.md` describes. |
| `release/inventory.toml` | Gives every release input one payload component or non-payload disposition, every ignored checkout path a disposition, and every report file input a regenerable or frozen status. `uv run --directory analysis python -m teralizer.release inventory` checks it against a checkout. `uv run --directory analysis python -m teralizer.release report-inputs` resolves every registered report input to its component. |

## Boundaries

- None of these scripts are smoke-tested by the fixture gate. Run them deliberately
  when preparing an artifact release.
- A new ignored path, gitlink or LFS file needs an inventory entry before a release. The
  inventory check fails until it has one.
