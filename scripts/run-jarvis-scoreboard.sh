#!/usr/bin/env bash
set -euo pipefail

# Runs the JARVIS Table 2 scorecard for the registered jarvis-scenarios definition. Measurements
# use a dedicated scratch database and data root. Shared run logic lives in scripts/lib/jarvis-run.sh.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
JARVIS_CORPUS_ID=jarvis-scenarios
JARVIS_DB_NAME="${JARVIS_SCRATCH_DB:-scratch_jarvis_scoreboard}"
JARVIS_DATA_DIR="${JARVIS_DATA_DIR:-data/jarvis-scoreboard}"
JARVIS_LABEL=scoreboard
JARVIS_PREPARE_FLAG=
JARVIS_DEFAULT_CONFIGS=(
  project-configs/jarvis-scoreboard/commons-lang-2017-02-01.conf
  project-configs/jarvis-scoreboard/commons-math-2017-02-01.conf
)

source "$ROOT_DIR/scripts/lib/jarvis-run.sh"
jarvis_run "$@"
