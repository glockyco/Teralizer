---
name: packaging-artifact
description: Reproduce or package the Teralizer replication artifact. Use when building the replication package, importing databases, re-running analysis in Docker, or verifying outputs match.
---

# Replication / artifact packaging

Self-contained Docker flow lives in `replication/` (`docker-compose.yml`, `quick-start.sh`,
`.env.example`, `scripts/`).

## Quick start (inspect data, re-run analysis)
```bash
cd replication
cp .env.example .env            # set DB creds/ports
docker compose up -d postgres adminer
./scripts/import-databases.sh   # load postgres_dev / postgres_test
docker compose up -d analysis   # Jupyter at http://localhost:8888
```

## Verify outputs match after re-running
```bash
docker compose run --rm verify original verify
```

Tear down with `docker compose down -v`. Follow `replication/quick-start.sh` for the full guided path.
