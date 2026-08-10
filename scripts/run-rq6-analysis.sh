#!/usr/bin/env bash
set -euo pipefail

# Materialize the RQ6 tables while iterating. The corpus the database must agree
# with is declared by the report, so override it only for a scratch corpus.
# Publishing to a consuming repository is scripts/publish-analysis.sh.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)

exec uv run --directory "$ROOT_DIR/analysis" python -m teralizer.eval rq6 \
  --db "${RQ6_DB:-postgres_reporeapers_rq6_v6}" \
  --targets "${RQ6_TARGETS:-md,latex,csv}" \
  "$@"
