---
description: Database access conventions for Teralizer's PostgreSQL
globs:
  - "analysis/**/*.py"
  - "**/*.sql"
---

# Database conventions

- `src/main/resources/db/protected-databases.txt` owns the protected corpus list. Never drop,
  truncate, rename, or write to a listed database. Never use one for an experiment.
- Runner scripts own scratch databases through `scripts/lib/db-lifecycle.sh`. Use those runners for
  creation and deletion.
- `src/main/resources/db/create-tables.sql` is the schema authority. Generated jOOQ bindings reflect
  that DDL.
- `database/teralizer/` is PostgreSQL storage. Never edit or commit it.
- Before counting exclusions, read `analysis/src/teralizer/eval/reports/rq6_causes.py`,
  `analysis/tests/eval/test_rq6_invariants.py`, and the accepted
  `reporting/exclusion-accounting` capability. `filter_result` is not only filters.
  `is_included` is not a success signal. Generation-time gates record their decision only in
  `exclusion_info`.
- Join cross-database records on `root_path`, never on surrogate IDs.
- Double percent signs in SQLAlchemy raw SQL, for example `LIKE '%%_TRIES'`.
- Analysis code must use read-only connections. It must not contain destructive or write queries.
