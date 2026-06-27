#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME_VALUE=${DB_NAME:-postgres_jarvis_scoreboard}
DATA_DIR_VALUE=${DATA_DIR:-data/jarvis-scoreboard}

if [[ "$DB_NAME_VALUE" == "postgres_dev" || "$DB_NAME_VALUE" == "postgres_test" || "$DB_NAME_VALUE" == *_replication ]]; then
  echo "Refusing unsafe DB_NAME=$DB_NAME_VALUE" >&2
  exit 1
fi
if [[ "$DB_NAME_VALUE" != "postgres_jarvis_scoreboard" ]]; then
  echo "Expected DB_NAME=postgres_jarvis_scoreboard, got $DB_NAME_VALUE" >&2
  exit 1
fi
if [[ "$DATA_DIR_VALUE" != "data/jarvis-scoreboard" ]]; then
  echo "Expected DATA_DIR=data/jarvis-scoreboard, got $DATA_DIR_VALUE" >&2
  exit 1
fi

configs=("$@")
if [[ ${#configs[@]} -eq 0 ]]; then
  configs=(
    project-configs/jarvis-scoreboard/commons-lang-3.5.conf
    project-configs/jarvis-scoreboard/commons-math-3.5.conf
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
