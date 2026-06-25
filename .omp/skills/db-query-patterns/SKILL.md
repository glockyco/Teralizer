---
name: db-query-patterns
description: Query Teralizer's PostgreSQL for analysis. Use when inspecting the schema, writing analytical SQL, computing RQ statistics, or debugging pipeline/filter results across the dev/test/timeout_retry datasets.
---

# Teralizer DB query patterns

Full schema: `docs/database.md`. Container `postgres-teralizer`, `localhost:5432`.
Datasets: `postgres_dev` (eqbench + commons-utils), `postgres_test` (RepoReapers),
`postgres_timeout_retry`.

## Access
Prefer the read-only MCP (`teralizer-db`, role `teralizer_ro`) for analytical queries. Ad-hoc CLI:
```bash
docker exec -i postgres-teralizer psql -U postgres -d postgres_dev -c "SELECT ..."
```

## Core tables / views
- `project`, `test`, `assertion`, `generalization`, `task` (stage/status/info), `filter_result`.
- `v_project_failures` (failed projects + reasons); `mv_exclusions_*` (materialized exclusion stats).

## Gotcha
Raw-SQL `LIKE` in SQLAlchemy needs doubled `%`: `WHERE ec.teralizer_variant LIKE '%%_TRIES'`.

## Inspect a table's columns before querying it
```sql
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'filter_result' ORDER BY ordinal_position;
```
