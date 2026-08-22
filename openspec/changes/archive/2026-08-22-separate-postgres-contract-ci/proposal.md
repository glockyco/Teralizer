## Why

The analysis CI job starts PostgreSQL for one synthetic lifecycle contract while its hooks, OpenSpec validation, and marker-free tests do not use a database. This couples every analysis run to service plumbing and recently blocked all useful validation before the database contract itself executed.

## What Changes

- Keep the ordinary analysis validation path database-free and responsible for hooks, OpenSpec validation, and marker-free tests.
- Move the real PostgreSQL lifecycle proof into an isolated contract workflow that uses only the declared synthetic corpus fixture.
- Run the PostgreSQL contract for changes to its database, registry, connection, fixture, or workflow inputs, and permit explicit manual execution.
- Resolve the synthetic corpus through its semantic registry id and route shell database access through the repository connection boundary.
- Preserve the current real-database guarantees: canonical DDL installation, idempotent derived-schema preparation, expected project count, report-role reads, and database-enforced write refusal.
- Do not run registered production corpora, corpus downloads, reports, collection, mutation testing, or release acceptance in ordinary CI.

## Capabilities

### New Capabilities

- `repository/ci-validation`: Defines the database-free default validation path and the isolated, input-scoped PostgreSQL contract path.

### Modified Capabilities

None. The change consumes the accepted corpus registry, database lifecycle, and synthetic-fixture boundaries without changing their requirements.

## Impact

The change affects GitHub Actions workflow structure, the synthetic corpus verification path, PostgreSQL client invocation, and CI documentation or comments. It does not change the evaluation schema, corpus registry contents, report behavior, production corpus lifecycle, reviewer runtime, or release workflow.
