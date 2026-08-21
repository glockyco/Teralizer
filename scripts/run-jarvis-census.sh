#!/usr/bin/env bash
set -euo pipefail

# Runs the beyond-JARVIS census for the registered jarvis-benchmark definition. Measurements use
# a dedicated scratch database and data root. Shared run logic lives in scripts/lib/jarvis-run.sh.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
JARVIS_CORPUS_ID=jarvis-benchmark
JARVIS_DB_NAME="${JARVIS_SCRATCH_DB:-scratch_jarvis_census}"
JARVIS_DATA_DIR="${JARVIS_DATA_DIR:-data/jarvis-census}"
JARVIS_LABEL=census
JARVIS_PREPARE_FLAG=--census
JARVIS_DEFAULT_CONFIGS=(
  project-configs/jarvis-scoreboard/commons-lang-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-math-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-cli-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-codec-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-collections-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-configuration-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-csv-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-email-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-io-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-jexl-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-pool-2017-02-01-census.conf
  project-configs/jarvis-scoreboard/commons-text-2017-02-01-census.conf
)

source "$ROOT_DIR/scripts/lib/jarvis-run.sh"
jarvis_run "$@"
