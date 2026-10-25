# Packaging and artifact tooling

These scripts build and validate the Zenodo replication artifact. Nothing here is part of
the artifact evaluation workflow, and no run driver references this directory.

| Script | Job |
|---|---|
| `prepare-zenodo-package.sh` | Stages and zips the archive families (core, projects, data) for a Zenodo upload, with checksums. |
| `setup-eval-environment.sh` | Simulates an ACM artifact evaluator's environment in `/tmp` from a fresh clone. |
| `collect-disk-metrics.sh` | Computes the disk-space and version tables for REQUIREMENTS.md. Scopes are pinned to this workstation's checkout paths. |

## Boundaries

- `setup-eval-environment.sh` is DESTRUCTIVE to local replication Docker state: it removes
  containers, volumes, and images matching replication names before rebuilding. Never run it
  to "check" anything.
- None of these scripts are smoke-tested by the fixture gate. Run them deliberately
  when preparing an artifact release.
