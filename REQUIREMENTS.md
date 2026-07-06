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

Docker Compose V2 is included with Docker Desktop and recent Docker Engine installations. Verify with `docker compose version`.

## Hardware

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| RAM | 8 GB | 16 GB |
| Disk | 25 GB | 50 GB |

### Disk Space by Workflow

| Workflow | Required Space |
|----------|----------------|
| Verify analysis results | ~20 GB |
| Run data collection pipeline (extended sample) | ~25 GB |
| Run data collection pipeline (extended full) | ~45 GB |
| Run data collection pipeline (primary full) | ~55 GB |

### Disk Space Breakdown

**Archive sizes** (run `./scripts/packaging/collect-disk-metrics.sh` to regenerate):

| Archive | Compressed | Unpacked |
|---------|------------|----------|
| teralizer-core | ~250 MB | ~350 MB |
| teralizer-results | ~1 MB | ~3 MB |
| teralizer-projects-primary | ~45 MB | ~150 MB |
| teralizer-projects-extended-sample | ~170 MB | ~275 MB |
| teralizer-projects-extended | ~1.7 GB | ~4.5 GB |
| teralizer-data-primary | ~1.1 GB | ~30 GB |
| teralizer-data-extended | ~260 MB | ~5.5 GB |

**Runtime components:**

| Component | Size |
|-----------|------|
| Docker images (postgres, adminer, analysis) | 2 GB |
| PostgreSQL volume (after import) | 17 GB |

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
| Full reproduction of data collection for the extended dataset (1161 projects) | ~12 hours |
| Full reproduction of data collection for the primary dataset (all variants) | ~100 hours |

Time estimates are based on evaluation hardware. Actual times may vary based on machine specifications and resources allocated to Docker.

### Versions Provided by Docker Containers

| Component | Version | Container |
|-----------|---------|-----------|
| PostgreSQL | 17.1 | postgres |
| Python | 3.11 | analysis |
| JupyterLab | 4.4.4 | analysis |
| Adminer | 4.8.1 | adminer |
| JDK | 8 | teralizer (pipeline only) |
| Maven | 3.9.8 | teralizer (pipeline only) |
| Gradle | 6.9.1 | teralizer (pipeline only) |
