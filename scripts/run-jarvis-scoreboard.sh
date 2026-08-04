#!/usr/bin/env bash
set -euo pipefail

# Runs the JARVIS Table-2 scorecard on the dedicated scratch DB (postgres_jarvis_scoreboard) and
# data root (data/jarvis-scoreboard). Shared run logic, flags (--reset-db, --prepare-fixtures),
# the container preflight, stale-test cleanup, and the post-run failure check live in
# scripts/lib/jarvis-run.sh.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
JARVIS_DB_NAME="${JARVIS_DB:-postgres_jarvis_scoreboard}"
JARVIS_DATA_DIR="${JARVIS_DATA_DIR:-data/jarvis-scoreboard}"
JARVIS_LABEL=scoreboard
JARVIS_PREPARE_FLAG=
JARVIS_DEFAULT_CONFIGS=(
  project-configs/jarvis-scoreboard/commons-lang-2017-02-01.conf
  project-configs/jarvis-scoreboard/commons-math-2017-02-01.conf
)

source "$ROOT_DIR/scripts/lib/jarvis-run.sh"
jarvis_run "$@"
