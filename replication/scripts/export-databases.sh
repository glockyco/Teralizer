#!/usr/bin/env bash
# Publish every registered corpus dump and its verified manifest.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
OUTPUT_DIR="${1:-$SCRIPT_DIR/../datasets}"

exec uv run --directory "$REPO_ROOT/analysis" \
    python -m teralizer.corpus_publish --output-dir "$OUTPUT_DIR"
