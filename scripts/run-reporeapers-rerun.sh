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
# Usage: scripts/run-reporeapers-rerun.sh [--reset-db] [--limit N] [--start N] [--no-reduction]
#   --reset-db   drop + recreate the scratch DB (clears markers + status) first, else resume/append
#   --limit N    process at most N not-yet-done projects (spike)
#   --start N    skip project configs numbered below N
#   --no-reduction  skip the reduction phase (Stage 5 reduction plus PIT), generalization only. The
#                   profile also disables PIT, but the phase toggle defaults on, so this is needed
#                   to keep a funnel rerun from running the reduction phase under the phase model.
#
# Graceful pause: `touch <DATA_DIR>/STOP` finishes the in-flight project, then exits cleanly.
# The file is consumed and relaunching resumes. Signals (INT/TERM) kill the in-flight project's
# whole process group immediately. It has no done-marker and re-runs on resume.
#
# Per-project wall cap: REPOREAPERS_PROJECT_TIMEOUT seconds (default 1800). A capped project is
# recorded with exit code 124 in status.tsv and done-marked (attempted, not retried). Its partial
# funnel rows stay. The July baseline shows the cap only fires on zero-yield outliers, but capped
# projects are identifiable by exit code for pairwise exclusion in baseline deltas.
#
# The corpus runner itself requires Java 8 because its bundled JPF revision imports JDK 8 internals.
# Override auto-detection with REPOREAPERS_JAVA_HOME.
#
# The target DB/data dir are dedicated (env overrides: REPOREAPERS_DB, REPOREAPERS_DATA_DIR).
# The ambient DB_NAME/.env pin is deliberately NOT used, so this can never hit dev/test/timeout_retry.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME="${REPOREAPERS_DB:-postgres_reporeapers_scratch}"
DATA_DIR="${REPOREAPERS_DATA_DIR:-data/reporeapers-rerun}"
PROFILE="${REPOREAPERS_PROFILE:-project-configs/reporeapers-rerun.conf}"
CONFIG_DIR="${REPOREAPERS_CONFIG_DIR:-project-configs/replication/extended}"
DONE_DIR="$ROOT_DIR/$DATA_DIR/done"
LOG_DIR="$ROOT_DIR/$DATA_DIR/run-logs"
STATUS_TSV="$ROOT_DIR/$DATA_DIR/status.tsv"
STOP_FILE="$ROOT_DIR/$DATA_DIR/STOP"
PROJECT_TIMEOUT="${REPOREAPERS_PROJECT_TIMEOUT:-1800}"

java8_version() {
  local home="$1"
  [[ -x "$home/bin/java" ]] || return 1
  "$home/bin/java" -XshowSettings:properties -version 2>&1 \
    | sed -n 's/^[[:space:]]*java.version = //p' \
    | head -1
}

require_java8() {
  local explicit="${REPOREAPERS_JAVA_HOME:-}"
  local candidate version
  local -a candidates=()

  if [[ -n "$explicit" ]]; then
    candidates+=("$explicit")
  else
    [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME")
    [[ -n "${JAVA_HOME_8_X64:-}" ]] && candidates+=("$JAVA_HOME_8_X64")
    if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
      candidate=$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)
      [[ -n "$candidate" ]] && candidates+=("$candidate")
    fi
  fi

  for candidate in "${candidates[@]}"; do
    version=$(java8_version "$candidate" || true)
    if [[ "$version" == 1.8.* ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "==> Java runtime: $JAVA_HOME ($version)"
      return 0
    fi
  done

  if [[ -n "$explicit" ]]; then
    echo "REPOREAPERS_JAVA_HOME is not a Java 8 home: $explicit" >&2
  else
    echo "RepoReapers requires Java 8; set REPOREAPERS_JAVA_HOME to a JDK 8 home." >&2
  fi
  return 1
}

source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"

reset_db=false; limit=0; start=1; no_reduction=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --reset-db) reset_db=true; shift ;;
    --limit) limit="${2:?--limit needs a number}"; shift 2 ;;
    --start) start="${2:?--start needs a number}"; shift 2 ;;
    --no-reduction) no_reduction=true; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

require_java8 || exit 1

# shellcheck source=scripts/lib/run-supervisor.sh
source "$ROOT_DIR/scripts/lib/run-supervisor.sh"
# shellcheck source=scripts/lib/db-lifecycle.sh
source "$ROOT_DIR/scripts/lib/db-lifecycle.sh"
supervisor_install_traps

ensure_postgres_up || exit 1

if [[ "$reset_db" == true ]]; then
  echo "==> Resetting database $DB_NAME"
  drop_scratch_db "$DB_NAME"
fi
if [[ "$(teralizer_psql -tA -d postgres -c "SELECT 1 FROM pg_database WHERE datname='$DB_NAME';" 2>/dev/null)" != "1" ]]; then
  echo "==> Creating database $DB_NAME"
  recreate_scratch_db "$DB_NAME" || exit 1
  # Fresh DB has no recorded projects -> leftover markers/status are stale.
  rm -rf "$DONE_DIR" "$STATUS_TSV"
fi

mkdir -p "$DONE_DIR" "$LOG_DIR"
[[ -f "$STATUS_TSV" ]] || printf 'n\troot_path\texit_code\tlog\n' > "$STATUS_TSV"

mapfile -t configs < <(find "$ROOT_DIR/$CONFIG_DIR" -maxdepth 1 -name 'project-*.conf' | sort -V)
[[ ${#configs[@]} -gt 0 ]] || { echo "No project configs under $CONFIG_DIR" >&2; exit 1; }

rm -f "$STOP_FILE"
attempted=0; skipped=0; nonzero=0; capped=0; stopped=false
for config_abs in "${configs[@]}"; do
  if supervisor_stop_requested "$STOP_FILE"; then
    stopped=true
    break
  fi
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
  # A prior run whose BUILD_PROJECT_GENERALIZED failed can drop the cleanup task and leave
  # generated tests behind that break the next BUILD_PROJECT_ORIGINAL, so sweep them first.
  find "$project_abs/src/test" -name '_*_Generalized_*_Test.java' -delete 2>/dev/null
  SUPERVISOR_ACTIVE_PATH="$project_abs"
  run_cmd=(
    "$ROOT_DIR/gradlew" run
    -Dteralizer.config="$PROFILE,$config"
    -Dteralizer.database.name="$DB_NAME"
    -Dteralizer.data-dir="$DATA_DIR"
  )
  [[ "$no_reduction" == true ]] && run_cmd+=(-Dteralizer.project.use-test-reduction=false)
  run_cmd+=(--no-daemon)
  supervised_run "$log_abs" "$PROJECT_TIMEOUT" "${run_cmd[@]}"
  rc=$SUPERVISED_RC
  cleanup_leftover_project_processes "$project_abs" "$log_abs"
  SUPERVISOR_ACTIVE_PATH=""
  printf '%s\t%s\t%s\t%s\n' "$n" "$root_path" "$rc" "$log" >> "$STATUS_TSV"
  if [[ "$rc" -eq 124 ]]; then
    capped=$((capped + 1))
    echo "    capped at ${PROJECT_TIMEOUT}s (exit 124 in the ledger, partial funnel rows recorded)"
  elif [[ "$rc" -ne 0 ]]; then
    nonzero=$((nonzero + 1))
    echo "    gradle exited $rc (funnel data still recorded where reached, see $log)"
  fi
  touch "$DONE_DIR/project-$n"
done

[[ "$stopped" == true ]] && echo "reporeapers-rerun: STOP file honored, exiting at a project boundary (relaunch resumes)."
echo "reporeapers-rerun: attempted=$attempted skipped(already done)=$skipped gradle-nonzero=$nonzero capped=$capped"
echo "Attempt ledger: $DATA_DIR/status.tsv   Funnel DB: '$DB_NAME' (project, test, assertion, filter_result, task, generalization)."
