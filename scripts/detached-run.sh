#!/usr/bin/env bash
#
# Detached run manager for long-running evaluation jobs (JARVIS census, RepoReapers
# rerun, and any other gradle-looping runner). Launch a command fully detached, then
# check status, stop it cleanly, or sweep orphans from a prior hard-killed run.
#
# Why this exists: these runs outlive an interactive session, so they must survive the
# launching shell. macOS has no `setsid`, so the portable detach is `nohup ... & disown`
# with the launcher re-parenting to init. Stop is the load-bearing part: gradle forks
# the application JVM into its own process group, so a naive single group-kill misses it
# (and a `kill -9` of the runner bypasses its own cleanup traps, orphaning the JVM and
# its PIT minions -- the failure this tool prevents). Stop therefore signals the runner
# so its supervisor traps tear down the active gradle group, then backstops with the
# supervisor's path-based sweep, which reaches every fixture JVM because each carries the
# fixture path on its command line.
#
# Usage:
#   scripts/detached-run.sh launch <name> [--sweep-path <dir>] -- <cmd> [args...]
#   scripts/detached-run.sh status [<name>]
#   scripts/detached-run.sh stop <name>
#   scripts/detached-run.sh sweep <dir>
#   scripts/detached-run.sh list
#
# State lives under data/detached/<name>.{pid,meta,log}.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
STATE_DIR="$ROOT_DIR/data/detached"

# Reuse the supervisor's tested path-based tree sweep rather than reimplementing it.
source "$ROOT_DIR/scripts/lib/run-supervisor.sh"

_pidfile() { echo "$STATE_DIR/$1.pid"; }
_metafile() { echo "$STATE_DIR/$1.meta"; }
_logfile() { echo "$STATE_DIR/$1.log"; }

# Read one key=value field from a run's meta file.
_meta_get() {
  local name="$1" key="$2" metafile
  metafile=$(_metafile "$name")
  [[ -f "$metafile" ]] || return 0
  sed -n "s/^${key}=//p" "$metafile" | head -1
}

# True when the named run's recorded pid is still alive.
_running() {
  local pidfile pid
  pidfile=$(_pidfile "$1")
  [[ -f "$pidfile" ]] || return 1
  pid=$(cat "$pidfile" 2>/dev/null)
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

usage() {
  # Print the leading comment block (skip the shebang, stop at the first non-comment line).
  awk 'NR==1 && /^#!/ {next} /^#/ {sub(/^# ?/,""); print; next} {exit}' "$0"
  exit "${1:-0}"
}

cmd_launch() {
  [[ $# -ge 1 ]] || { echo "launch: missing <name>" >&2; exit 1; }
  local name="$1"; shift
  local sweep_path=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --sweep-path) sweep_path="$2"; shift 2 ;;
      --) shift; break ;;
      *) echo "launch: unexpected argument before '--': $1" >&2; exit 1 ;;
    esac
  done
  [[ $# -ge 1 ]] || { echo "launch: missing command after '--'" >&2; exit 1; }

  if _running "$name"; then
    echo "launch: '$name' is already running (pid $(cat "$(_pidfile "$name")"))" >&2
    echo "  stop it first: $0 stop $name" >&2
    exit 1
  fi

  # Resolve the sweep path to an absolute directory so stop's backstop can match it.
  if [[ -n "$sweep_path" ]]; then
    case "$sweep_path" in
      /*) : ;;
      *) sweep_path="$ROOT_DIR/$sweep_path" ;;
    esac
  fi

  mkdir -p "$STATE_DIR"
  local pidfile metafile logfile
  pidfile=$(_pidfile "$name"); metafile=$(_metafile "$name"); logfile=$(_logfile "$name")

  # Detach: nohup ignores SIGHUP, the launcher exits so the child re-parents to init,
  # and disown drops it from this shell's job table. stdin from /dev/null so it never
  # blocks on a read. This is the portable equivalent of setsid on macOS.
  nohup "$@" > "$logfile" 2>&1 < /dev/null &
  local pid=$!
  disown 2>/dev/null || true

  echo "$pid" > "$pidfile"
  {
    echo "name=$name"
    echo "pid=$pid"
    echo "started=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "sweep_path=$sweep_path"
    echo "log=$logfile"
    echo "cmd=$*"
  } > "$metafile"

  echo "launched '$name' (pid $pid)"
  echo "  log:    $logfile"
  echo "  status: $0 status $name"
  echo "  stop:   $0 stop $name"
}

cmd_status() {
  mkdir -p "$STATE_DIR"
  local name="${1:-}"
  if [[ -z "$name" ]]; then
    cmd_list
    return 0
  fi
  local metafile logfile pid
  metafile=$(_metafile "$name"); logfile=$(_logfile "$name")
  [[ -f "$metafile" ]] || { echo "no such run: '$name'" >&2; exit 1; }
  pid=$(_meta_get "$name" pid)
  if _running "$name"; then
    echo "RUNNING  '$name'  pid $pid  started $(_meta_get "$name" started)"
  else
    echo "STOPPED  '$name'  (pid $pid no longer alive)  started $(_meta_get "$name" started)"
  fi
  echo "  cmd: $(_meta_get "$name" cmd)"
  echo "  log: $logfile"
  if [[ -f "$logfile" ]]; then
    echo "  --- last 15 log lines ---"
    tail -n 15 "$logfile" | sed 's/^/  /'
  fi
}

cmd_stop() {
  [[ $# -ge 1 ]] || { echo "stop: missing <name>" >&2; exit 1; }
  local name="$1"
  local pidfile metafile pid sweep_path
  pidfile=$(_pidfile "$name"); metafile=$(_metafile "$name")
  [[ -f "$metafile" ]] || { echo "no such run: '$name'" >&2; exit 1; }
  pid=$(_meta_get "$name" pid)
  sweep_path=$(_meta_get "$name" sweep_path)

  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    # TERM the runner so its own supervisor traps tear down the active gradle group.
    echo "stopping '$name' (pid $pid): sending TERM to the runner"
    kill -TERM "$pid" 2>/dev/null || true
    local waited=0
    while kill -0 "$pid" 2>/dev/null && [[ $waited -lt 15 ]]; do
      sleep 1; waited=$((waited + 1))
    done
    if kill -0 "$pid" 2>/dev/null; then
      echo "  runner still alive after ${waited}s, sending KILL"
      kill -KILL "$pid" 2>/dev/null || true
    fi
  else
    echo "'$name' runner not alive; sweeping any leftovers"
  fi

  # Backstop: kill any fixture JVM that escaped the runner's group teardown (or that was
  # orphaned because a prior run was hard-killed before its traps could fire).
  if [[ -n "$sweep_path" && -d "$sweep_path" ]]; then
    cleanup_leftover_project_processes "$sweep_path"
  fi
  rm -f "$pidfile"
  echo "stopped '$name'"
}

cmd_sweep() {
  [[ $# -ge 1 ]] || { echo "sweep: missing <dir>" >&2; exit 1; }
  local dir="$1"
  case "$dir" in
    /*) : ;;
    *) dir="$ROOT_DIR/$dir" ;;
  esac
  [[ -d "$dir" ]] || { echo "sweep: not a directory: $dir" >&2; exit 1; }
  echo "sweeping leftover processes referencing $dir"
  cleanup_leftover_project_processes "$dir"
  echo "sweep complete"
}

cmd_list() {
  mkdir -p "$STATE_DIR"
  local found=false metafile name
  for metafile in "$STATE_DIR"/*.meta; do
    [[ -e "$metafile" ]] || continue
    found=true
    name=$(basename "$metafile" .meta)
    if _running "$name"; then
      printf 'RUNNING  %-24s pid %-8s started %s\n' "$name" "$(_meta_get "$name" pid)" "$(_meta_get "$name" started)"
    else
      printf 'STOPPED  %-24s pid %-8s started %s\n' "$name" "$(_meta_get "$name" pid)" "$(_meta_get "$name" started)"
    fi
  done
  [[ "$found" == true ]] || echo "no detached runs recorded under $STATE_DIR"
}

[[ $# -ge 1 ]] || usage 1
subcommand="$1"; shift
case "$subcommand" in
  launch) cmd_launch "$@" ;;
  status) cmd_status "$@" ;;
  stop)   cmd_stop "$@" ;;
  sweep)  cmd_sweep "$@" ;;
  list)   cmd_list "$@" ;;
  -h|--help|help) usage 0 ;;
  *) echo "unknown subcommand: $subcommand" >&2; usage 1 ;;
esac
