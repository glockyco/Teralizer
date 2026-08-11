#!/usr/bin/env bash
# Regenerate the tracked jOOQ sources against a throwaway database built from the checked-in DDL,
# so generated code always matches src/main/resources/db/create-tables.sql and never a live corpus.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
CODEGEN_DB=teralizer_codegen
DDL="$ROOT_DIR/src/main/resources/db/create-tables.sql"

source "$ROOT_DIR/scripts/lib/run-supervisor.sh"
source "$ROOT_DIR/scripts/lib/db-guard.sh"
source "$ROOT_DIR/scripts/lib/db-lifecycle.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$CODEGEN_DB"

teralizer_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 || { echo "Postgres ($(teralizer_psql_target)) not reachable" >&2; exit 1; }

cleanup() {
  drop_scratch_db "$CODEGEN_DB"
}
trap cleanup EXIT

echo "==> Creating $CODEGEN_DB"
recreate_scratch_db "$CODEGEN_DB" || exit 1

echo "==> Applying DDL"
teralizer_psql -d "$CODEGEN_DB" < "$DDL" >/dev/null

echo "==> Generating jOOQ sources"
"$ROOT_DIR/gradlew" generateJooq --no-daemon || exit $?

echo "==> Done. Review: git diff --stat build/generated-src"
