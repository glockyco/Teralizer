---
description: Database access conventions for Teralizer's PostgreSQL
globs:
  - "analysis/**/*.py"
  - "**/*.sql"
---

# Database conventions

- Container `postgres-teralizer`, `localhost:5432`; DBs `postgres_dev`, `postgres_test`,
  `postgres_timeout_retry`. Schema reference: `docs/database.md`.
- Raw-SQL `LIKE`: double percent signs in SQLAlchemy strings (`LIKE '%%_TRIES'`).
- Prefer read-only access for analysis; use the read-only `teralizer-db` MCP over ad-hoc
  superuser `psql`.
- Never DROP/TRUNCATE or write to the analysis databases from analysis code.
