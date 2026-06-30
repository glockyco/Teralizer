#!/usr/bin/env bash
set -euo pipefail

# Runs the beyond-JARVIS census on the dedicated scratch DB (postgres_jarvis_census) and data
# root (data/jarvis-census). Automates the prep/cleanup that was previously a manual runbook:
#   --reset-db          drop + recreate the census DB first (clean run = clean DB)
#   --prepare-fixtures  (re)materialize the census fixtures first (re-clone is fast)
# It also preflights the Postgres container, always clears stale generated tests, and fails
# loudly if any pipeline task FAILED -- gradle exits 0 even when the pipeline drops a task, so
# the exit code alone is not a clean run.
#
# The DB and data root are fixed (not read from the ambient environment), because .env may pin
# DB_NAME to another corpus (e.g. postgres_timeout_retry) for unrelated work.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME_VALUE=postgres_jarvis_census
DATA_DIR_VALUE=data/jarvis-census

reset_db=false
prepare_fixtures=false
configs=()
for arg in "$@"; do
  case "$arg" in
    --reset-db) reset_db=true ;;
    --prepare-fixtures) prepare_fixtures=true ;;
    -h|--help)
      echo "Usage: $(basename "$0") [--reset-db] [--prepare-fixtures] [config ...]"
      echo "  --reset-db           drop and recreate $DB_NAME_VALUE before running"
      echo "  --prepare-fixtures   (re)materialize the census fixtures first"
      echo "  config ...           HOCON configs to run (default: the two census configs)"
      exit 0 ;;
    --*) echo "Unknown flag: $arg" >&2; exit 1 ;;
    *) configs+=("$arg") ;;
  esac
done

if [[ ${#configs[@]} -eq 0 ]]; then
  configs=(
    project-configs/jarvis-scoreboard/commons-lang-3.5-census.conf
    project-configs/jarvis-scoreboard/commons-math-3.5-census.conf
  )
fi
for config in "${configs[@]}"; do
  if [[ ! -f "$ROOT_DIR/$config" ]]; then
    echo "Configuration file not found: $config" >&2
    exit 1
  fi
done

psql_db() { docker exec postgres-teralizer psql -U postgres "$@"; }

ensure_db_up() {
  if psql_db -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
    return 0
  fi
  echo "==> Postgres (postgres-teralizer) not ready; starting it"
  "$ROOT_DIR/gradlew" startPostgres >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    if psql_db -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Postgres (postgres-teralizer) is not reachable. Is another container holding port 5432?" >&2
  echo "Start it with ./gradlew startPostgres and retry." >&2
  exit 1
}

ensure_db_up

if [[ "$prepare_fixtures" == true ]]; then
  echo "==> Materializing census fixtures"
  bash "$ROOT_DIR/scripts/prepare-jarvis-scoreboard-fixtures.sh" --census
fi

# A failed BUILD_PROJECT_GENERALIZED drops its cleanup task, leaving uncompilable generated tests
# that break the next build. Always clear them; the pipeline regenerates from scratch.
find "$ROOT_DIR/$DATA_DIR_VALUE/fixtures" -name '_*Generalized*_Test.java' -delete 2>/dev/null || true

if [[ "$reset_db" == true ]]; then
  echo "==> Resetting database $DB_NAME_VALUE"
  psql_db -d postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$DB_NAME_VALUE' AND pid<>pg_backend_pid();" >/dev/null
  psql_db -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME_VALUE;"
  if ! psql_db -d postgres -c "CREATE DATABASE $DB_NAME_VALUE;" 2>/dev/null; then
    # A glibc upgrade under the container leaves template1 with a stale collation version.
    psql_db -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
    psql_db -d postgres -c "CREATE DATABASE $DB_NAME_VALUE;"
  fi
fi

# High-water task id so the post-run check sees only this invocation's tasks (keeps incremental
# runs safe without --reset-db). Empty/0 when the schema does not exist yet (fresh DB).
baseline_task_id=$(psql_db -tA -d "$DB_NAME_VALUE" -c "SELECT COALESCE(MAX(id),0) FROM task;" 2>/dev/null | tr -d '[:space:]')
baseline_task_id=${baseline_task_id:-0}

gradle_failed=false
for config in "${configs[@]}"; do
  echo "==> Running $config (DB_NAME=$DB_NAME_VALUE DATA_DIR=$DATA_DIR_VALUE)"
  if ! DB_NAME="$DB_NAME_VALUE" DATA_DIR="$DATA_DIR_VALUE" DATASET_VARIANT="jarvis" \
       "$ROOT_DIR/gradlew" run -Dteralizer.config="$config" --no-daemon; then
    echo "gradle exited non-zero for $config" >&2
    gradle_failed=true
  fi
done

# The pipeline catches per-task errors and the JVM still exits 0, so a green gradle build is NOT a
# clean run. Surface dropped tasks from the DB and fail loudly if any occurred this run.
echo "==> Checking for failed pipeline tasks"
failed_count=$(psql_db -tA -d "$DB_NAME_VALUE" -c \
  "SELECT count(*) FROM task WHERE status='FAILED' AND id > ${baseline_task_id};" 2>/dev/null | tr -d '[:space:]')
failed_count=${failed_count:-0}
if [[ "$failed_count" -gt 0 ]]; then
  echo "" >&2
  echo "${failed_count} pipeline task(s) FAILED this run:" >&2
  psql_db -d "$DB_NAME_VALUE" -c \
    "SELECT p.root_path, t.stage, t.variant, left(t.info, 200) AS info_head FROM task t JOIN project p ON p.id = t.project_id WHERE t.status='FAILED' AND t.id > ${baseline_task_id} ORDER BY t.id;" >&2 || true
  exit 1
fi
if [[ "$gradle_failed" == true ]]; then
  echo "A gradle invocation exited non-zero, though no FAILED task was recorded; check the run log." >&2
  exit 1
fi
echo "Census run complete: no failed pipeline tasks."
