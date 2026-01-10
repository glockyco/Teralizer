# Requirements

## Minimum Hardware

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| RAM | 8 GB | 16 GB |
| Disk | 25 GB | 50 GB+ |
| CPU | Any modern x86_64 or ARM64 | - |

## Evaluation Hardware

The results in the paper were produced on:
- **Machine**: MacBook Air (M2, 2022)
- **CPU**: Apple M2 (8-core)
- **RAM**: 24 GB
- **OS**: macOS Sequoia 15.x

Performance may vary on different hardware. Runtime estimates in the paper and documentation are based on this configuration.

## Software

| Software | Version | Purpose |
|----------|---------|---------|
| Docker | 20.10+ | Container runtime |
| Docker Compose | V2 (integrated) | Service orchestration |
| Git | 2.x | Required only for pipeline re-execution |

Check your versions:
```bash
docker --version
docker compose version
```

## Operating System

| OS | Support | Notes |
|----|---------|-------|
| Linux (x86_64) | Full | Native Docker performance |
| macOS Intel | Full | Docker Desktop required |
| macOS Apple Silicon | Full | Rosetta emulation for JDK 8 |
| Windows | Full | WSL2 + Docker Desktop required |

## Network

- **Initial setup**: Internet required to pull Docker images (~2 GB download)
- **Analysis reproduction**: No internet required after setup
- **Pipeline re-execution**: Internet required for GitHub access

## Time Estimates

| Task | Time |
|------|------|
| Quick start (analysis) | 15-30 minutes |
| Run all notebooks | ~5 minutes |
| Pipeline verification (5 projects) | 5-10 minutes |
| Extended verification (50 projects) | ~1 hour |
| Full reproduction | Days to weeks |

## Verification

Run the preflight check to verify your system meets all requirements:
```bash
./replication/scripts/preflight-check.sh
```
