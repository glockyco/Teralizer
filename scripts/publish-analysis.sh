#!/usr/bin/env bash
set -euo pipefail

# Build every registered report in temporary staging and deliver the artifacts
# declared by a consuming repository. Publication does not promote generated
# reports, provenance, or build output into this repository. Use
# scripts/run-rq6-analysis.sh to regenerate one report during local iteration.
#
# PAPER_OUT points at the directory that contains publish.toml. For the thesis,
# this is chapters/05-teralizer.

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

# The command rejects a dirty generator before report construction and refuses
# to overwrite any uncommitted consumer path declared by publish.toml.

exec uv run --directory "$ROOT_DIR/analysis" python -m teralizer.eval all \
  --targets md,figures,latex,csv \
  --paper-out "$PAPER_OUT" \
  "$@"
