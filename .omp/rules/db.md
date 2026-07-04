---
description: Database access conventions for Teralizer's PostgreSQL
globs:
  - "analysis/**/*.py"
  - "**/*.sql"
---

# Database conventions

- Container `postgres-teralizer`, `localhost:5432`. Protected DBs (never drop, never use for
  experiments): `postgres_dev`, `postgres_test`, `postgres_timeout_retry`,
  `postgres_reporeapers_rerun`. Experiments use scratch DBs (`postgres_<purpose>_verify`,
  `postgres_verification`) created/dropped by runner scripts. Schema reference:
  `docs/database.md`. Source of truth: `src/main/resources/db/create-tables.sql`.
- Cross-DB comparisons join on `root_path`, never on `id`.
- Raw-SQL `LIKE`: double percent signs in SQLAlchemy strings (`LIKE '%%_TRIES'`).
- Prefer read-only access for analysis; use the read-only `teralizer-db` MCP over ad-hoc
  superuser `psql`.
- Never DROP/TRUNCATE or write to the analysis databases from analysis code.
