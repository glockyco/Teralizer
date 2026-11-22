# Requirements

## Host

- Linux on x86-64, or Windows with WSL 2, with Docker Engine or Docker Desktop and Docker Compose 2.3 or later.
- Bash, unzip, and `sha256sum` or `shasum`.

The release was tested on Ubuntu 24.04 in WSL 2 with Docker Desktop, on an AMD Ryzen 5 5600X with 12 threads and 62 GB of memory available to WSL. macOS on Apple Silicon runs the x86-64 images under emulation, which this release does not claim. `./artifact preflight WORKFLOW` checks the host and the extracted archives before a workflow runs.

## Time, disk and memory

Measured on the test host, starting from extracted archives. `RELEASE.md` on Zenodo lists the download and unpacked size of every workflow.

| Workflow | Time | Restored databases | Docker memory |
|---|---|---|---|
| `./artifact smoke` | 1 min | 0.8 GB | 4 GB |
| `./artifact reproduce-results` | 19 min | 19.4 GB | 4 GB |
| `./artifact collect-reduced jarvis-scenarios` | 8 min | | 10 GB |
| `./artifact collect-reduced controlled` | 2.7 h | | 10 GB |
| `./artifact collect-reduced real-world` | 3.3 h | | 10 GB |

The images take another 2.4 GB. A collection adds its scratch database and up to 1 GB of working copies below `state/`. PostgreSQL is limited to 2 GB of memory and the pipeline container to 8 GB, and the largest project of the reduced runs used 7.8 GB.

The full collections took on the evaluation host: controlled 31 to 37 hours per EqBench configuration and 10 to 12 hours per Commons configuration, real-world 25.6 hours.

## Network

Docker pulls the base images and builds the release images on first use. Smoke and results reproduction need no network after that. Collection downloads the dependencies of the analyzed projects.
