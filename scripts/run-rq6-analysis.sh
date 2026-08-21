#!/usr/bin/env bash
set -euo pipefail

# Materialize the RQ6 tables while iterating. Select the registered input by
# semantic id. Publishing to a consuming repository is scripts/publish-analysis.sh.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
CORPUS_ID="${RQ6_CORPUS:-real-world}"
if [[ "$CORPUS_ID" != real-world ]]; then
  echo "RQ6 declares the real-world corpus, not '$CORPUS_ID'." >&2
  exit 2
fi

exec uv run --directory "$ROOT_DIR/analysis" python -m teralizer.eval rq6 \
  --targets "${RQ6_TARGETS:-md,latex,csv}" \
  "$@"
