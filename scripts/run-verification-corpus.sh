#!/usr/bin/env bash
#
# Run Teralizer's synthetic verification fixtures through the full pipeline on a scratch DB.
# The corpus is intentionally small, so every invocation resets the DB and reruns every fixture
# instead of carrying resumability markers that would hide stale partial state.
#
# Usage: scripts/run-verification-corpus.sh [--only <fixture-name>]
#   --only  run a single fixture (fast iteration; the DB then holds only that fixture, so use
#           ad-hoc SQL against it — the full golden check expects the whole corpus)
#
# Each fixture runs under a wall cap (VERIFICATION_FIXTURE_TIMEOUT, default 300 s, against a
# normal fixture time of ~47 s). A capped fixture is a defect in the fixture or the pipeline,
# never data. It ledgers exit 124 and fails the gate like any nonzero exit.
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

FIXTURE_TIMEOUT="${VERIFICATION_FIXTURE_TIMEOUT:-300}"

source "$ROOT_DIR/scripts/lib/run-supervisor.sh"
source "$ROOT_DIR/scripts/lib/db-lifecycle.sh"
supervisor_install_traps

ensure_postgres_up || exit 1

echo "==> Resetting database $DB_NAME"
recreate_scratch_db "$DB_NAME" || exit 1

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
  SUPERVISOR_ACTIVE_PATH="$project_abs"
  supervised_run "$log_abs" "$FIXTURE_TIMEOUT" \
    "$ROOT_DIR/gradlew" run \
    -Dteralizer.config="$PROFILE,$config" \
    -Dteralizer.database.name="$DB_NAME" \
    -Dteralizer.data-dir="$DATA_DIR" \
    --no-daemon
  rc=$SUPERVISED_RC
  cleanup_leftover_project_processes "$project_abs" "$log_abs"
  SUPERVISOR_ACTIVE_PATH=""
  printf '%s\t%s\t%s\t%s\n' "$fixture" "$root_path" "$rc" "$log" >> "$STATUS_TSV"
  if [[ "$rc" -eq 124 ]]; then
    nonzero=$((nonzero + 1))
    echo "    capped at ${FIXTURE_TIMEOUT}s -- a fixture this slow is a defect, not data (see $log)"
  elif [[ "$rc" -ne 0 ]]; then
    nonzero=$((nonzero + 1))
    echo "    gradle exited $rc (see $log)"
  fi
done

echo "verification-corpus: attempted=$attempted gradle-nonzero=$nonzero"
echo "Attempt ledger: $DATA_DIR/status.tsv   Fixture DB: '$DB_NAME'."
[[ "$nonzero" -eq 0 ]] || exit 1
