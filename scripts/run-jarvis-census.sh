#!/usr/bin/env bash
set -euo pipefail

# Runs the beyond-JARVIS census on the dedicated scratch DB (postgres_jarvis_census) and data
# root (data/jarvis-census). Shared run logic, flags (--reset-db, --prepare-fixtures), the
# container preflight, stale-test cleanup, and the post-run failure check live in
# scripts/lib/jarvis-run.sh.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
JARVIS_DB_NAME="${JARVIS_DB:-postgres_jarvis_census}"
JARVIS_DATA_DIR="${JARVIS_DATA_DIR:-data/jarvis-census}"
JARVIS_LABEL=census
JARVIS_PREPARE_FLAG=--census
JARVIS_DEFAULT_CONFIGS=(
  project-configs/jarvis-scoreboard/commons-lang-3.5-census.conf
  project-configs/jarvis-scoreboard/commons-math-3.5-census.conf
  project-configs/jarvis-scoreboard/commons-cli-1.3.1-census.conf
  project-configs/jarvis-scoreboard/commons-codec-1.10-census.conf
  project-configs/jarvis-scoreboard/commons-collections-4.1-census.conf
  project-configs/jarvis-scoreboard/commons-configuration-2.1-census.conf
  project-configs/jarvis-scoreboard/commons-csv-1.4-census.conf
  project-configs/jarvis-scoreboard/commons-email-1.4-census.conf
  project-configs/jarvis-scoreboard/commons-io-2.5-census.conf
  project-configs/jarvis-scoreboard/commons-jexl-3.0-census.conf
  project-configs/jarvis-scoreboard/commons-pool-2.4.2-census.conf
  project-configs/jarvis-scoreboard/commons-text-1.0-census.conf
)

source "$ROOT_DIR/scripts/lib/jarvis-run.sh"
jarvis_run "$@"
