#!/usr/bin/env bash
# Build the registered analysis reports with teralizer.eval.
#
# USAGE:
#   ./run-analysis.sh [verify|replicate]
#
# The run rewrites the rendered reports under analysis/reports/ and collects
# the publication outputs below analysis/output/<variant>/ in the tables/,
# data/, and figures/ layout of the published reference output.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ANALYSIS_DIR="$REPO_ROOT/analysis"
BUILD_DIR="$ANALYSIS_DIR/build"
OUTPUT_ROOT="$ANALYSIS_DIR/output"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

usage() {
    sed -n '2,9p' "$0"
    exit 0
}

VARIANT="verify"
while [[ $# -gt 0 ]]; do
    case "$1" in
        verify|replicate)
            VARIANT="$1"
            shift
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}Error: unknown argument: $1${NC}" >&2
            exit 2
            ;;
    esac
done

OUTPUT_DIR="$OUTPUT_ROOT/$VARIANT"

printf 'Running teralizer.eval for %s\n' "$VARIANT"

uv run --frozen --directory "$ANALYSIS_DIR" \
    python -m teralizer.eval all --targets md,figures,latex,csv

# Copy every file that find prints into one flat directory. Two reports that
# emit the same file name would silently hide one of them, so that is an error.
collect() {
    local target="$1"
    shift
    mkdir -p "$target"
    local file
    while IFS= read -r -d '' file; do
        if [[ -e "$target/$(basename "$file")" ]]; then
            echo -e "${RED}Error: two outputs are named $(basename "$file")${NC}" >&2
            exit 1
        fi
        cp "$file" "$target/"
    done < <(find "$@" -type f -print0)
}

# teralizer.eval writes tables and CSV files to analysis/build/, figures and their
# canonical data sources to analysis/build/figures/ (PDF, JSON), and figure PNGs
# to analysis/reports/figures/.
rm -rf "$OUTPUT_DIR"
collect "$OUTPUT_DIR/tables" "$BUILD_DIR" -maxdepth 1 -name '*.tex'
collect "$OUTPUT_DIR/data" "$BUILD_DIR" -mindepth 2 -name '*.csv'
collect "$OUTPUT_DIR/figures" "$BUILD_DIR/figures" "$ANALYSIS_DIR/reports/figures" \
    \( -name '*.pdf' -o -name '*.png' -o -name '*.source.json' \)

printf '%bAnalysis output written to %s%b\n' "$GREEN" "$OUTPUT_DIR" "$NC"
