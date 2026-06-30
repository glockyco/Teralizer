#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME_VALUE=${DB_NAME:-postgres_jarvis_census}
DATA_DIR_VALUE=${DATA_DIR:-data/jarvis-census}

# Guard: the census must never touch the canonical scoreboard DB or the dev/test corpora.
if [[ "$DB_NAME_VALUE" != "postgres_jarvis_census" ]]; then
  echo "Expected DB_NAME=postgres_jarvis_census, got $DB_NAME_VALUE" >&2
  exit 1
fi
if [[ "$DATA_DIR_VALUE" != "data/jarvis-census" ]]; then
  echo "Expected DATA_DIR=data/jarvis-census, got $DATA_DIR_VALUE" >&2
  exit 1
fi

configs=("$@")
if [[ ${#configs[@]} -eq 0 ]]; then
  configs=(
    project-configs/jarvis-scoreboard/commons-lang-3.5-census.conf
    project-configs/jarvis-scoreboard/commons-math-3.5-census.conf
  )
fi

for config in "${configs[@]}"; do
  if [[ ! -f "$ROOT_DIR/$config" ]]; then
    echo "Configuration file not found: $config" >&2
    exit 1
  fi
  echo "Running $config with DB_NAME=$DB_NAME_VALUE DATA_DIR=$DATA_DIR_VALUE"
  DB_NAME="$DB_NAME_VALUE" \
  DATA_DIR="$DATA_DIR_VALUE" \
  DATASET_VARIANT="jarvis" \
  "$ROOT_DIR/gradlew" run -Dteralizer.config="$config" --no-daemon
done
