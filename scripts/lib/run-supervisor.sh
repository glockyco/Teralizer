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
#   supervised_container_run <container_name> <timeout_secs> <cmd>...
#       Launches a container-producing command such as `docker compose run --name`
#       in the background and watches both the compose-run process and the named
#       container. Sets SUPERVISED_RC to the command's exit code, or 124 when the
#       wall cap fired. The cap and signal traps stop the container with
#       `docker stop`, then clean up the compose-run process group. Always
#       returns 0 so set -e callers can inspect SUPERVISED_RC.
#   supervisor_install_traps
#       Acquires the repository working-tree lock, then installs EXIT/INT/TERM traps that kill
#       the in-flight group, sweep leftovers, and release the lock. Bash holds traps while a
#       foreground command runs, and the background-job launch in supervised_run is what makes
#       the traps fire without delay.
#
# The lock uses an atomic mkdir at .teralizer-run.lock. A live holder is reported immediately;
# a lock whose recorded PID is no longer alive is taken over. Set TERALIZER_ALLOW_CONCURRENT_RUNS=1
# only when knowingly bypassing this protection.
#   supervisor_stop_requested <stop_file>
#       True once when <stop_file> exists, consuming it. Check at loop boundaries for a
#       zero-loss pause (the in-flight unit finishes, nothing new starts).
#   cleanup_leftover_project_processes <project_abs> [log_abs]
#       TERM-then-KILL sweep of any process whose command line references <project_abs>.
#       Drivers set SUPERVISOR_ACTIVE_PATH/SUPERVISOR_ACTIVE_LOG around each unit so the
#       traps can sweep the right tree.
#   ensure_postgres_up
#       Readiness wait for the server named by scripts/lib/psql.sh, which this file
#       sources for teralizer_psql.

SUPERVISOR_ACTIVE_PGID=""
SUPERVISOR_ACTIVE_PATH=""
SUPERVISOR_ACTIVE_LOG=""
SUPERVISED_RC=""
SUPERVISOR_LOCK_DIR="$ROOT_DIR/.teralizer-run.lock"
SUPERVISOR_LOCK_HELD=false

source "$ROOT_DIR/scripts/lib/psql.sh"

# mkdir is atomic on macOS and avoids the unavailable flock(2) command. Metadata is kept in
# the lock directory so a second run can identify a live holder and safely reclaim dead locks.
supervisor_acquire_lock() {
  [[ "$SUPERVISOR_LOCK_HELD" == true ]] && return 0
  if [[ "${TERALIZER_ALLOW_CONCURRENT_RUNS:-}" == "1" ]]; then
    echo "run-supervisor: bypassing working-tree lock (TERALIZER_ALLOW_CONCURRENT_RUNS=1)." >&2
    return 0
  fi

  local lock_dir="$SUPERVISOR_LOCK_DIR"
  local holder_pid holder_command holder_started stale_dir
  while :; do
    if mkdir "$lock_dir" 2>/dev/null; then
      printf '%s\n' "$$" > "$lock_dir/pid"
      ps -p "$$" -o command= 2>/dev/null | sed -n '1p' > "$lock_dir/command"
      date '+%Y-%m-%d %H:%M:%S %z' > "$lock_dir/started"
      SUPERVISOR_LOCK_HELD=true
      return 0
    fi

    if [[ ! -e "$lock_dir" ]]; then
      echo "run-supervisor: unable to create working-tree lock at $lock_dir." >&2
      return 1
    fi
    holder_pid=$(cat "$lock_dir/pid" 2>/dev/null || true)
    if [[ "$holder_pid" =~ ^[1-9][0-9]*$ ]] && kill -0 "$holder_pid" 2>/dev/null; then
      holder_command=$(cat "$lock_dir/command" 2>/dev/null || true)
      holder_started=$(cat "$lock_dir/started" 2>/dev/null || true)
      echo "run-supervisor: working tree is already locked by PID $holder_pid" \
        "(${holder_command:-command unavailable}) since ${holder_started:-time unavailable}." >&2
      echo "run-supervisor: wait for that run to finish or set TERALIZER_ALLOW_CONCURRENT_RUNS=1" \
        "to bypass deliberately." >&2
      return 1
    fi

    # Rename before removing so concurrent stale-lock reclaimers cannot delete a newly
    # acquired lock. A missing or dead PID makes this lock stale.
    stale_dir="${lock_dir}.stale.$$"
    rm -rf "$stale_dir" 2>/dev/null || true
    if mv "$lock_dir" "$stale_dir" 2>/dev/null; then
      rm -rf "$stale_dir"
      continue
    fi
    # The holder may have released the directory between our checks. Retry that race, but
    # report filesystem errors instead of spinning forever when the path cannot be moved.
    [[ -e "$lock_dir" ]] || continue
    echo "run-supervisor: unable to reclaim stale working-tree lock at $lock_dir." >&2
    return 1
  done
}

supervisor_release_lock() {
  [[ "$SUPERVISOR_LOCK_HELD" == true ]] || return 0
  rm -rf "$SUPERVISOR_LOCK_DIR"
  SUPERVISOR_LOCK_HELD=false
}

ensure_postgres_up() {
  if teralizer_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
    return 0
  fi
  # Only the Docker transport has a server this repository knows how to start. A native
  # server is owned by the host, so guessing at a start command would hide the real fault.
  if [[ "$TERALIZER_PSQL_TRANSPORT" != docker ]]; then
    echo "Postgres at $(teralizer_psql_target) is not reachable." >&2
    return 1
  fi
  echo "==> Postgres ($(teralizer_psql_target)) not ready; starting it"
  "$ROOT_DIR/gradlew" startPostgres >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    teralizer_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "Postgres ($(teralizer_psql_target)) is not reachable. Is another container holding port 5432?" >&2
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
  if [[ -n "$SUPERVISOR_ACTIVE_PATH" ]]; then
    cleanup_leftover_project_processes "$SUPERVISOR_ACTIVE_PATH" "$SUPERVISOR_ACTIVE_LOG"
  fi
  supervisor_release_lock
}

supervisor_install_traps() {
  # Install EXIT first so an interrupt during lock acquisition still runs the cleanup path.
  trap cleanup_active_project_processes EXIT
  trap 'cleanup_active_project_processes; exit 130' INT TERM
  supervisor_acquire_lock || exit 1
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

supervised_container_run() {
  local container_name="$1"
  local timeout_secs="$2"
  shift 2

  SUPERVISOR_ACTIVE_LOG="-"
  docker rm -f "$container_name" >/dev/null 2>&1 || true

  # While this function is active, signals must stop the named container rather
  # than only the compose-run process. Restore the standard sourced-driver traps
  # before returning.
  trap 'docker stop "$container_name" >/dev/null 2>&1 || true; supervisor_kill_active_group; cleanup_active_project_processes' EXIT
  trap 'docker stop "$container_name" >/dev/null 2>&1 || true; supervisor_kill_active_group; exit 130' INT TERM

  set -m
  "$@" < /dev/null &
  local pid=$!
  set +m
  SUPERVISOR_ACTIVE_PGID="$pid"

  local started=$SECONDS
  SUPERVISED_RC=""
  while :; do
    if ! kill -0 "$pid" 2>/dev/null; then
      wait "$pid"
      SUPERVISED_RC=$?
      break
    fi
    if [[ "$timeout_secs" -gt 0 ]] && (( SECONDS - started >= timeout_secs )); then
      _log_cleanup "-" "container $container_name exceeded ${timeout_secs}s wall cap, stopping container"
      docker stop "$container_name" >/dev/null 2>&1 || true
      supervisor_kill_active_group
      wait "$pid" 2>/dev/null || true
      SUPERVISED_RC=124
      break
    fi
    sleep 15
  done

  SUPERVISOR_ACTIVE_PGID=""
  supervisor_install_traps
  return 0
}
