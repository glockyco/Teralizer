#!/usr/bin/env bash
# shellcheck disable=SC2034  # SUPERVISED_RC is read by sourcing drivers after supervised_run
# Shared process supervision for every driver that loops gradle over configs
# (reporeapers/sentinel/hotspot lanes, verification corpus, jarvis scoreboard and census).
# This file is *sourced*, not executed. It owns process lifecycle only. Drivers keep their
# lane logic (ledger format, DB reset policy, golden checks).
#
# Contract:
#   supervised_run <log|-> <timeout_secs> <cmd>...
#       Launches <cmd> as a background job in its own process group and watches it with a
#       poll. Sets SUPERVISED_RC to the command's exit code, or 124 when <timeout_secs> > 0
#       and the wall cap fired (the whole group is TERMed, then KILLed). "<log>" of "-"
#       leaves stdout/stderr on the terminal. Always returns 0 so set -e callers can
#       inspect SUPERVISED_RC.
#   supervisor_install_traps
#       EXIT/INT/TERM kill the in-flight group immediately and sweep leftovers. Bash holds
#       traps while a foreground command runs, and the background-job launch in
#       supervised_run is what makes the traps fire without delay.
#   supervisor_stop_requested <stop_file>
#       True once when <stop_file> exists, consuming it. Check at loop boundaries for a
#       zero-loss pause (the in-flight unit finishes, nothing new starts).
#   cleanup_leftover_project_processes <project_abs> [log_abs]
#       TERM-then-KILL sweep of any process whose command line references <project_abs>.
#       Drivers set SUPERVISOR_ACTIVE_PATH/SUPERVISOR_ACTIVE_LOG around each unit so the
#       traps can sweep the right tree.
#   teralizer_psql <args>...   /  ensure_postgres_up
#       Shared Postgres access and readiness wait for the postgres-teralizer container.

SUPERVISOR_ACTIVE_PGID=""
SUPERVISOR_ACTIVE_PATH=""
SUPERVISOR_ACTIVE_LOG=""
SUPERVISED_RC=""

teralizer_psql() { docker exec -i postgres-teralizer psql -U postgres "$@"; }

ensure_postgres_up() {
  if teralizer_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
    return 0
  fi
  echo "==> Postgres (postgres-teralizer) not ready; starting it"
  "$ROOT_DIR/gradlew" startPostgres >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    teralizer_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "Postgres (postgres-teralizer) is not reachable. Is another container holding port 5432?" >&2
  echo "Start it with ./gradlew startPostgres and retry." >&2
  return 1
}

_log_cleanup() {
  local log_abs="$1"
  local message="$2"
  echo "    cleanup: $message"
  [[ -n "$log_abs" && "$log_abs" != "-" ]] && printf 'cleanup: %s\n' "$message" >> "$log_abs"
}

# Kill the in-flight unit's whole process group (job control gives each launch its own
# group, so this reaches the gradle wrapper JVM and the pipeline JVM it re-parents).
# TERM first, grace period, then KILL for survivors.
supervisor_kill_active_group() {
  [[ -n "$SUPERVISOR_ACTIVE_PGID" ]] || return 0
  kill -TERM -- "-$SUPERVISOR_ACTIVE_PGID" 2>/dev/null || true
  for _ in $(seq 1 10); do
    kill -0 -- "-$SUPERVISOR_ACTIVE_PGID" 2>/dev/null || { SUPERVISOR_ACTIVE_PGID=""; return 0; }
    sleep 1
  done
  _log_cleanup "$SUPERVISOR_ACTIVE_LOG" "force-killing process group $SUPERVISOR_ACTIVE_PGID"
  kill -KILL -- "-$SUPERVISOR_ACTIVE_PGID" 2>/dev/null || true
  SUPERVISOR_ACTIVE_PGID=""
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
  supervisor_kill_active_group
  [[ -n "$SUPERVISOR_ACTIVE_PATH" ]] || return 0
  cleanup_leftover_project_processes "$SUPERVISOR_ACTIVE_PATH" "$SUPERVISOR_ACTIVE_LOG"
}

supervisor_install_traps() {
  trap cleanup_active_project_processes EXIT
  trap 'cleanup_active_project_processes; exit 130' INT TERM
}

supervisor_stop_requested() {
  local stop_file="$1"
  [[ -f "$stop_file" ]] || return 1
  rm -f "$stop_file"
  return 0
}

supervised_run() {
  local log_abs="$1"
  local timeout_secs="$2"
  shift 2
  SUPERVISOR_ACTIVE_LOG="$log_abs"
  # Job control (set -m) puts the background command and every process it spawns in a
  # fresh process group, so the watchdog and the signal traps can kill the whole tree
  # with one group signal. macOS has no setsid, so this is the portable equivalent.
  set -m
  if [[ "$log_abs" == "-" ]]; then
    "$@" < /dev/null &
  else
    "$@" < /dev/null > "$log_abs" 2>&1 &
  fi
  local pid=$!
  set +m
  SUPERVISOR_ACTIVE_PGID="$pid"
  # Watchdog: the background launch keeps this shell interruptible (traps fire
  # immediately instead of waiting for the command to return) and enforces the wall cap.
  local started=$SECONDS
  SUPERVISED_RC=""
  while :; do
    if ! kill -0 "$pid" 2>/dev/null; then
      wait "$pid"
      SUPERVISED_RC=$?
      break
    fi
    if [[ "$timeout_secs" -gt 0 ]] && (( SECONDS - started >= timeout_secs )); then
      _log_cleanup "$log_abs" "run exceeded ${timeout_secs}s wall cap, killing process group"
      supervisor_kill_active_group
      wait "$pid" 2>/dev/null
      SUPERVISED_RC=124
      break
    fi
    sleep 15
  done
  SUPERVISOR_ACTIVE_PGID=""
  return 0
}
