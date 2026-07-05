#!/usr/bin/env bash
# Regenerate the tracked jOOQ sources against a throwaway database built from the checked-in DDL,
# so generated code always matches src/main/resources/db/create-tables.sql and never a live corpus.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
CODEGEN_DB=teralizer_codegen
DDL="$ROOT_DIR/src/main/resources/db/create-tables.sql"

_psql() { docker exec -i postgres-teralizer psql -U postgres "$@"; }

_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 || { echo "Postgres (postgres-teralizer) not reachable" >&2; exit 1; }

cleanup() {
  _psql -d postgres -c "DROP DATABASE IF EXISTS $CODEGEN_DB;" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Creating $CODEGEN_DB"
_psql -d postgres -c "DROP DATABASE IF EXISTS $CODEGEN_DB;" >/dev/null
if ! _psql -d postgres -c "CREATE DATABASE $CODEGEN_DB;" 2>/dev/null; then
  _psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
  _psql -d postgres -c "CREATE DATABASE $CODEGEN_DB;" || { echo "CREATE DATABASE failed" >&2; exit 1; }
fi

echo "==> Applying DDL"
_psql -d "$CODEGEN_DB" < "$DDL" >/dev/null

echo "==> Generating jOOQ sources"
"$ROOT_DIR/gradlew" generateJooq --no-daemon || exit $?

echo "==> Done. Review: git diff --stat build/generated-src"
