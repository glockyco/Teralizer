#!/usr/bin/env bash
set -euo pipefail

# Materialize the canonical RQ6 funnel, exclusion, filtering, and root-cause tables.
# Metric definitions live in teralizer.eval reports; this wrapper deliberately contains no SQL.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
RQ6_DB_NAME="${RQ6_DB:-postgres_reporeapers_rq6_v6}"
RQ6_OUTPUTS="${RQ6_TARGETS:-md,latex,csv}"
RQ6_DATA_DIR="${RQ6_DATA_DIR:-data/reporeapers-rerun-v6}"
RQ6_CONFIG_DIR="${RQ6_CONFIG_DIR:-project-configs/replication/extended}"

exec uv run --directory "$ROOT_DIR/analysis" python -m teralizer.eval rq6 \
  --db "$RQ6_DB_NAME" \
  --targets "$RQ6_OUTPUTS" \
  --corpus-data-dir "$RQ6_DATA_DIR" \
  --corpus-config-dir "$RQ6_CONFIG_DIR" \
  "$@"
