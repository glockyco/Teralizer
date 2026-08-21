# Requirements

This document specifies the hardware and software requirements for running the Teralizer replication package.

## Evaluation Hardware

The results in the paper were produced on:

- **Machine**: MacBook Air (M2, 2022)
- **CPU**: Apple M2 (8-core)
- **RAM**: 24 GB

## Verification

Run the preflight check to verify requirements:

```bash
./replication/scripts/preflight-check.sh
```

All checks should pass before proceeding with installation.

## Software

| Software | Required Version |
|----------|------------------|
| Docker | 20.10 or later |
| Docker Compose | V2 (integrated) |
| Bash | Any |
| uv | 0.11 or later |
| Python | 3.11 or later (managed by uv for `analysis/`) |

Docker Compose V2 is included with Docker Desktop and recent Docker Engine installations. Verify with `docker compose version`.

## Hardware

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| RAM | 8 GB | 16 GB |
| Disk | Package preflight result | Preflight result plus pipeline workspace |

The corpus manifest records each dump and restored database size. Read the verified requirement instead
of applying a fixed database count or threshold. In a source checkout, set `CORPUS_PACKAGE_DIR` to an
installed or downloaded corpus component:

```bash
CORPUS_PACKAGE_DIR=/path/to/replication/datasets
uv run --frozen --directory analysis python -m teralizer.corpus_publish \
  --preflight-package "$CORPUS_PACKAGE_DIR"
```

### Disk Space by Workflow

| Workflow | Required Space |
|----------|----------------|
| Verify analysis results | `required free disk` from the verified manifest |
| Run data collection pipeline (extended sample) | ~25 GB |
| Run data collection pipeline (extended full) | ~45 GB |
| Run data collection pipeline (primary full) | ~55 GB |

### Disk Space Breakdown

Run `./scripts/packaging/collect-disk-metrics.sh` for the exact compressed and unpacked
sizes of a built archive set. The core archive includes only dumps and inputs declared by the
verified corpus manifest.

**Runtime components:**

| Component | Size source |
|-----------|-------------|
| Docker images | Installed image inventory |
| PostgreSQL volume after import | `database_bytes` for each selected manifest entry |

**Built projects and pipeline output:**

| Dataset | Built Projects | Pipeline Output |
|---------|----------------|-----------------|
| Primary | 3 GB | 31 GB |
| Extended | 16 GB | 6 GB |

## Network

| Task | Internet Required |
|------|-------------------|
| Initial setup | Yes (Docker image downloads) |
| Analysis reproduction | No |
| Pipeline execution | Yes (project building / dependency downloads) |

## Time

| Activity | Time |
|----------|------|
| Initial setup (download + quick-start) | ~10 minutes |
| Workflow 1: Inspect results | ~5 minutes |
| Workflow 2: Verify analysis | ~10 minutes |
| Workflow 3: Verify pipeline (5 projects) | ~15 minutes |
| Full reproduction of data collection for the real-world corpus | ~12 hours |
| Full reproduction of data collection for the controlled corpus | ~100 hours |

Time estimates are based on evaluation hardware. Actual times may vary based on machine specifications and resources allocated to Docker.

### Versions Provided by Docker Containers

| Component | Version | Container |
|-----------|---------|-----------|
| PostgreSQL | 17.1 | postgres |
| Python | 3.11 | analysis |
| Adminer | 4.8.1 | adminer |
| JDK | 8 | teralizer (pipeline only) |
| Maven | 3.9.8 | teralizer (pipeline only) |
| Gradle | 6.9.1 | teralizer (pipeline only) |
