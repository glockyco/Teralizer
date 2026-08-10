#!/usr/bin/env bash
set -euo pipefail

# Materialize every registered report and copy the citable artifacts into a
# consuming repository. Reports share macros.tex and the CSV directory, so this
# always builds the whole set. Use scripts/run-rq6-analysis.sh to iterate on one.
#
# PAPER_OUT points at the directory holding `tables/` and `data/`, for the
# thesis that is chapters/05-teralizer.

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
PAPER_OUT="${PAPER_OUT:-${PAPER_REPO_PATH:-}}"

if [[ -z "$PAPER_OUT" ]]; then
  echo "PAPER_OUT is required, e.g. PAPER_OUT=~/Projects/phd-thesis/chapters/05-teralizer" >&2
  exit 2
fi
if [[ ! -d "$PAPER_OUT" ]]; then
  echo "PAPER_OUT does not exist: $PAPER_OUT" >&2
  exit 2
fi

exec uv run --directory "$ROOT_DIR/analysis" python -m teralizer.eval all \
  --targets md,figures,latex,csv \
  --paper-out "$PAPER_OUT" \
  "$@"
