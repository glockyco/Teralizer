#!/usr/bin/env bash
#
# Run Teralizer's synthetic verification fixtures through the full pipeline on a scratch DB.
# The corpus is intentionally small, so every invocation resets the DB and reruns every fixture
# instead of carrying resumability markers that would hide stale partial state.
#
# Usage: scripts/run-verification-corpus.sh [--only <fixture-name>]
#   --only  run a single fixture (fast iteration; the DB then holds only that fixture, so use
#           ad-hoc SQL against it — the full golden check expects the whole corpus)
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME="${VERIFICATION_DB:-postgres_verification}"
DATA_DIR="${VERIFICATION_DATA_DIR:-data/verification}"
PROFILE="project-configs/verification.conf"
CONFIG_DIR="project-configs/verification"
LOG_DIR="$ROOT_DIR/$DATA_DIR/run-logs"
STATUS_TSV="$ROOT_DIR/$DATA_DIR/status.tsv"
FIXTURE_ROOT="$ROOT_DIR/verification/fixtures"

source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"

usage() {
  sed -n '2,12p' "$0"
}
only=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    # Iterating on one behavior family: same reset + golden-comparable run, one fixture only.
    --only) only="$2"; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
  shift
done

_psql() { docker exec -i postgres-teralizer psql -U postgres "$@"; }

active_project_abs=""
active_project_log=""

_log_cleanup() {
  local log_abs="$1"
  local message="$2"
  echo "    cleanup: $message"
  [[ -n "$log_abs" ]] && printf 'cleanup: %s\n' "$message" >> "$log_abs"
}

cleanup_leftover_project_processes() {
  local project_abs="$1"
  local log_abs="${2:-}"
  [[ -n "$project_abs" && -d "$project_abs" ]] || return 0

  local pids=()
  local pid
  while IFS= read -r pid; do
    [[ -n "$pid" && "$pid" != "$$" ]] && pids+=("$pid")
  done < <(pgrep -f "$project_abs" 2>/dev/null || true)
  [[ ${#pids[@]} -gt 0 ]] || return 0

  _log_cleanup "$log_abs" "terminating ${#pids[@]} leftover process(es) for $project_abs: ${pids[*]}"
  for pid in "${pids[@]}"; do
    kill -TERM "$pid" 2>/dev/null || true
  done
  sleep 2
  for pid in "${pids[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      _log_cleanup "$log_abs" "force-killing leftover process $pid for $project_abs"
      kill -KILL "$pid" 2>/dev/null || true
    fi
  done
}

cleanup_active_project_processes() {
  [[ -n "$active_project_abs" ]] || return 0
  cleanup_leftover_project_processes "$active_project_abs" "$active_project_log"
}

trap cleanup_active_project_processes EXIT
trap 'cleanup_active_project_processes; exit 130' INT TERM

if ! _psql -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
  echo "==> Starting Postgres"
  "$ROOT_DIR/gradlew" startPostgres >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    _psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 && break
    sleep 1
  done
fi
_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 || { echo "Postgres (postgres-teralizer) not reachable" >&2; exit 1; }

echo "==> Resetting database $DB_NAME"
_psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$DB_NAME' AND pid<>pg_backend_pid();" >/dev/null
_psql -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" || { echo "DROP DATABASE failed" >&2; exit 1; }
if ! _psql -d postgres -c "CREATE DATABASE $DB_NAME;" 2>/dev/null; then
  # A container image or host libc upgrade can leave template1 with a stale collation version.
  # Refresh only the template metadata, then retry the scratch database creation.
  _psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
  _psql -d postgres -c "CREATE DATABASE $DB_NAME;" || { echo "CREATE DATABASE failed" >&2; exit 1; }
fi

mkdir -p "$LOG_DIR"
printf 'fixture\troot_path\texit_code\tlog\n' > "$STATUS_TSV"

# Crashed prior runs can leave generated tests or persisted jqwik sidecars in fixture trees; remove
# them before Spoon sees stale classes and before new executions append UUID-scoped diagnostics.
find "$FIXTURE_ROOT" -type f \( \
  -name '_*_Generalized_*_Test.java' -o \
  -name '_*_Driver_*.java' -o \
  -name '_*_Instrumented_*.java' \
\) -delete
find "$FIXTURE_ROOT" -type d -path '*/data/verification' -prune -exec rm -rf {} +

mapfile -t configs < <(find "$ROOT_DIR/$CONFIG_DIR" -maxdepth 1 -name "fixture-${only:-*}.conf" | sort)
[[ ${#configs[@]} -gt 0 ]] || { echo "No fixture configs matching '${only:-*}' under $CONFIG_DIR" >&2; exit 1; }

attempted=0; nonzero=0
for config_abs in "${configs[@]}"; do
  fixture=$(basename "$config_abs" .conf); fixture="${fixture#fixture-}"
  config="${config_abs#"$ROOT_DIR"/}"
  log="$DATA_DIR/run-logs/$fixture.log"
  log_abs="$ROOT_DIR/$log"
  root_path=$(sed -n 's/[[:space:]]*root-path[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' "$config_abs" | head -1)
  project_abs="$ROOT_DIR/$root_path"
  attempted=$((attempted + 1))
  echo "==> [$fixture] $root_path"
  active_project_abs="$project_abs"
  active_project_log="$log_abs"
  "$ROOT_DIR/gradlew" run \
    -Dteralizer.config="$PROFILE,$config" \
    -Dteralizer.database.name="$DB_NAME" \
    -Dteralizer.data-dir="$DATA_DIR" \
    --no-daemon \
    > "$log_abs" 2>&1
  rc=$?
  cleanup_leftover_project_processes "$project_abs" "$log_abs"
  active_project_abs=""
  active_project_log=""
  printf '%s\t%s\t%s\t%s\n' "$fixture" "$root_path" "$rc" "$log" >> "$STATUS_TSV"
  if [[ "$rc" -ne 0 ]]; then
    nonzero=$((nonzero + 1))
    echo "    gradle exited $rc (see $log)"
  fi
done

echo "verification-corpus: attempted=$attempted gradle-nonzero=$nonzero"
echo "Attempt ledger: $DATA_DIR/status.tsv   Fixture DB: '$DB_NAME'."
[[ "$nonzero" -eq 0 ]] || exit 1
