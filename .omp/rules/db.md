---
description: Database access conventions for Teralizer's PostgreSQL
globs:
  - "analysis/**/*.py"
  - "**/*.sql"
---

# Database conventions

- Container `postgres-teralizer`, `localhost:5432`. Protected DBs are the published-paper corpora
  (never drop, never use for experiments): `postgres_dev`, `postgres_test`. In-flight corpora for
  the next version stay unprotected. Experiments use scratch DBs (`postgres_<purpose>_verify`,
  `postgres_verification`) created/dropped by runner scripts. Schema reference:
  `docs/database.md`. Source of truth: `src/main/resources/db/create-tables.sql`.
- Exclusion semantics: `docs/exclusion-model.md`. Read it before writing a query that counts
  excluded tests, assertions, or generalizations. `filter_result` is not only filters,
  `is_included` is not a success signal, and generation-time gates leave no row anywhere except
  `exclusion_info`. Invariants are enforced by `analysis/tests/eval/test_rq6_invariants.py`.
- Cross-DB comparisons join on `root_path`, never on `id`.
- Raw-SQL `LIKE`: double percent signs in SQLAlchemy strings (`LIKE '%%_TRIES'`).
- Prefer read-only access for analysis; use the read-only `teralizer-db` MCP over ad-hoc
  superuser `psql`.
- Never DROP/TRUNCATE or write to the analysis databases from analysis code.
