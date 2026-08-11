#!/usr/bin/env bash
# Build the registered analysis reports with teralizer.eval.
#
# USAGE:
#   ./run-analysis.sh [verify|replicate] [--db NAME]
#
# The selected variant is written below analysis/output/<variant>/ using the
# same tables/, data/, and figures/ layout as the published reference output.
# The optional database override is passed to teralizer.eval for single-corpus
# debugging. The default path keeps each report's registered database.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ANALYSIS_DIR="$REPO_ROOT/analysis"
OUTPUT_ROOT="$ANALYSIS_DIR/output"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

usage() {
    sed -n '1,13p' "$0"
    exit 0
}

VARIANT="verify"
DB_OVERRIDE=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        verify|replicate)
            VARIANT="$1"
            shift
            ;;
        --db)
            if [[ $# -lt 2 ]]; then
                echo -e "${RED}Error: --db requires a database name${NC}" >&2
                exit 2
            fi
            DB_OVERRIDE="$2"
            shift 2
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
FIGURES_DIR="$OUTPUT_DIR/figures"

mkdir -p "$OUTPUT_DIR"
rm -rf "$OUTPUT_DIR/tables" "$OUTPUT_DIR/data" "$FIGURES_DIR"

printf 'Running teralizer.eval for %s\n' "$VARIANT"

EVAL_ARGS=(all --targets md,figures,latex,csv --paper-out "$OUTPUT_DIR")
if [[ -n "$DB_OVERRIDE" ]]; then
    EVAL_ARGS+=(--db "$DB_OVERRIDE")
fi
DATASET_VARIANT="$VARIANT" uv run --directory "$ANALYSIS_DIR" \
    python -m teralizer.eval "${EVAL_ARGS[@]}"

# teralizer.eval keeps rendered figures in the repository reports tree so they
# can be consumed by the markdown renderer. Copy them into the variant output
# alongside tables and CSV files for reviewer-facing comparisons.
mkdir -p "$FIGURES_DIR"
if [[ -d "$ANALYSIS_DIR/reports/figures" ]]; then
    while IFS= read -r -d '' figure; do
        cp "$figure" "$FIGURES_DIR/"
    done < <(find "$ANALYSIS_DIR/reports/figures" -type f -print0)
fi

printf '%bAnalysis output written to %s%b\n' "$GREEN" "$OUTPUT_DIR" "$NC"
