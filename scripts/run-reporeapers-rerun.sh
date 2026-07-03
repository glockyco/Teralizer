#!/usr/bin/env bash
#
# Rerun the RepoReapers corpus through the current pipeline to collect fresh applicability
# evidence -- filter causes and generalization failures -- on a dedicated scratch DB. Each
# project-configs/replication/extended/project-N.conf runs with the reporeapers-rerun profile
# (IMPROVED_100_TRIES, PIT skipped: mutation scores are not relevant to the funnel). Per-project
# failures ARE the data, so the loop records them and continues.
#
# Every attempt is logged to DATA_DIR/status.tsv (n, root_path, gradle exit code, log path) so
# early crashes that never reach the DB still join cleanly with the DB funnel. Resumable via
# per-project done-markers under DATA_DIR/done/: a marker is written only after a project's run
# RETURNS, so a project interrupted mid-run has no marker and is re-run next time. Markers and the
# status ledger are reset whenever the DB is created fresh (--reset-db or a missing DB).
#
# Usage: scripts/run-reporeapers-rerun.sh [--reset-db] [--limit N] [--start N]
#   --reset-db   drop + recreate the scratch DB (clears markers + status) first; else resume/append
#   --limit N    process at most N not-yet-done projects (spike)
#   --start N    skip project configs numbered below N
#
# The target DB/data dir are dedicated (env overrides: REPOREAPERS_DB, REPOREAPERS_DATA_DIR).
# The ambient DB_NAME/.env pin is deliberately NOT used, so this can never hit dev/test/timeout_retry.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME="${REPOREAPERS_DB:-postgres_reporeapers_rerun}"
DATA_DIR="${REPOREAPERS_DATA_DIR:-data/reporeapers-rerun}"
PROFILE="project-configs/reporeapers-rerun.conf"
CONFIG_DIR="${REPOREAPERS_CONFIG_DIR:-project-configs/replication/extended}"
DONE_DIR="$ROOT_DIR/$DATA_DIR/done"
LOG_DIR="$ROOT_DIR/$DATA_DIR/run-logs"
STATUS_TSV="$ROOT_DIR/$DATA_DIR/status.tsv"

# Never touch the core corpora.
case "$DB_NAME" in
  postgres_dev|postgres_test|postgres_timeout_retry|*_replication)
    echo "Refusing unsafe target DB_NAME=$DB_NAME" >&2; exit 1 ;;
esac

reset_db=false; limit=0; start=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --reset-db) reset_db=true; shift ;;
    --limit) limit="${2:?--limit needs a number}"; shift 2 ;;
    --start) start="${2:?--start needs a number}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
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

# Bring Postgres up only if it isn't already reachable (starting it restarts the container),
# then wait for readiness.
if ! _psql -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
  echo "==> Starting Postgres"
  "$ROOT_DIR/gradlew" startPostgres >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    _psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 && break
    sleep 1
  done
fi
_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 || { echo "Postgres (postgres-teralizer) not reachable" >&2; exit 1; }

if [[ "$reset_db" == true ]]; then
  echo "==> Resetting database $DB_NAME"
  _psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$DB_NAME' AND pid<>pg_backend_pid();" >/dev/null
  _psql -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" || { echo "DROP DATABASE failed" >&2; exit 1; }
fi
if [[ "$(_psql -tA -d postgres -c "SELECT 1 FROM pg_database WHERE datname='$DB_NAME';" 2>/dev/null)" != "1" ]]; then
  echo "==> Creating database $DB_NAME"
  if ! _psql -d postgres -c "CREATE DATABASE $DB_NAME;" 2>/dev/null; then
    # A glibc upgrade under the container leaves template1 with a stale collation version.
    _psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
    _psql -d postgres -c "CREATE DATABASE $DB_NAME;" || { echo "CREATE DATABASE failed" >&2; exit 1; }
  fi
  # Fresh DB has no recorded projects -> leftover markers/status are stale.
  rm -rf "$DONE_DIR" "$STATUS_TSV"
fi

mkdir -p "$DONE_DIR" "$LOG_DIR"
[[ -f "$STATUS_TSV" ]] || printf 'n\troot_path\texit_code\tlog\n' > "$STATUS_TSV"

mapfile -t configs < <(find "$ROOT_DIR/$CONFIG_DIR" -maxdepth 1 -name 'project-*.conf' | sort -V)
[[ ${#configs[@]} -gt 0 ]] || { echo "No project configs under $CONFIG_DIR" >&2; exit 1; }

attempted=0; skipped=0; nonzero=0
for config_abs in "${configs[@]}"; do
  n=$(basename "$config_abs" .conf); n="${n#project-}"
  [[ "$n" -lt "$start" ]] && continue
  [[ "$limit" -gt 0 && "$attempted" -ge "$limit" ]] && break
  [[ -f "$DONE_DIR/project-$n" ]] && { skipped=$((skipped + 1)); continue; }
  config="${config_abs#"$ROOT_DIR"/}"
  log="$DATA_DIR/run-logs/project-$n.log"
  log_abs="$ROOT_DIR/$log"
  root_path=$(sed -n 's/[[:space:]]*root-path[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' "$config_abs" | head -1)
  project_abs="$ROOT_DIR/$root_path"
  attempted=$((attempted + 1))
  echo "==> [$n] $root_path"
  active_project_abs="$project_abs"
  active_project_log="$log_abs"
  DB_NAME="$DB_NAME" DATA_DIR="$DATA_DIR" \
    "$ROOT_DIR/gradlew" run -Dteralizer.config="$PROFILE,$config" --no-daemon \
    > "$log_abs" 2>&1
  rc=$?
  cleanup_leftover_project_processes "$project_abs" "$log_abs"
  active_project_abs=""
  active_project_log=""
  printf '%s\t%s\t%s\t%s\n' "$n" "$root_path" "$rc" "$log" >> "$STATUS_TSV"
  if [[ "$rc" -ne 0 ]]; then
    nonzero=$((nonzero + 1))
    echo "    gradle exited $rc (funnel data still recorded where reached; see $log)"
  fi
  touch "$DONE_DIR/project-$n"
done

echo "reporeapers-rerun: attempted=$attempted skipped(already done)=$skipped gradle-nonzero=$nonzero"
echo "Attempt ledger: $DATA_DIR/status.tsv   Funnel DB: '$DB_NAME' (project, test, assertion, filter_result, task, generalization)."
