#!/usr/bin/env bash
# Verify test-suite reduction against a throwaway database built from the checked-in DDL.
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
SCRATCH_DB=scratch_generalization_effects

source "$ROOT_DIR/scripts/lib/run-supervisor.sh"
source "$ROOT_DIR/scripts/lib/db-guard.sh"
source "$ROOT_DIR/scripts/lib/db-lifecycle.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$SCRATCH_DB"

teralizer_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 || {
  echo "Postgres ($(teralizer_psql_target)) not reachable" >&2
  exit 1
}

cleanup() {
  drop_scratch_db "$SCRATCH_DB"
}
trap cleanup EXIT

recreate_scratch_db "$SCRATCH_DB"
teralizer_psql -d "$SCRATCH_DB" --set=ON_ERROR_STOP=1 \
  -f "$ROOT_DIR/src/main/resources/db/create-tables.sql" >/dev/null
teralizer_psql -d "$SCRATCH_DB" --set=ON_ERROR_STOP=1 \
  -f "$ROOT_DIR/src/main/resources/db/create-views.sql" >/dev/null
teralizer_psql -d "$SCRATCH_DB" --set=ON_ERROR_STOP=1 \
  -f "$ROOT_DIR/verification/fixtures/generalization-effects.sql" >/dev/null

echo 'Generalization-effects contract passed.'
