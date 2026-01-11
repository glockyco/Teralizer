#!/usr/bin/env bash
# Execute analysis notebooks and export results.
#
# This script runs all analysis notebooks using jupyter nbconvert,
# exporting tables, data, and figures to variant-specific output directories.
#
# USAGE:
#   ./run-notebooks.sh [variant] [options]
#
# VARIANTS:
#   verify     Re-run analysis on original databases → output to verify/
#   replicate  Run analysis on replication databases → output to replicate/
#   all        Run both variants sequentially
#
# OPTIONS:
#   --dry-run  Show what would be executed without running
#   --help     Show this help message
#
# OUTPUT DIRECTORIES:
#   analysis/output/{variant}/tables/   LaTeX tables
#   analysis/output/{variant}/data/     CSV files
#   analysis/output/{variant}/figures/  PDF figures
#   analysis/output/executed/{variant}/ Executed notebooks with outputs

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors (matching verify-results.sh)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

DRY_RUN=false

usage() {
    head -24 "$0" | tail -22
    exit 0
}

# Count files in directory matching pattern
count_files() {
    local dir="$1"
    local pattern="$2"
    find "$dir" -name "$pattern" 2>/dev/null | wc -l | tr -d ' '
}

run_notebooks() {
    local variant=$1
    export DATASET_VARIANT=$variant

    # Find notebooks
    local notebook_dir="$REPO_ROOT/analysis/notebooks"
    local notebooks=()
    for nb in "$notebook_dir"/*.ipynb; do
        [[ -f "$nb" ]] && notebooks+=("$nb")
    done

    local total=${#notebooks[@]}
    local current=0
    local failed=0

    local output_base="$REPO_ROOT/analysis/output/$variant"
    local executed_dir="$REPO_ROOT/analysis/output/executed/$variant"

    # Determine which database will be used
    local db_name="postgres_dev"
    if [[ "$variant" == "replicate" ]]; then
        db_name="postgres_dev_replication"
    fi

    echo ""
    echo "=========================================="
    echo "  Running Analysis Notebooks"
    echo "  Variant: $variant"
    echo "  Database: $db_name"
    echo "=========================================="
    echo ""

    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${CYAN}Dry run - would execute:${NC}"
        for notebook in "${notebooks[@]}"; do
            echo "  - $(basename "$notebook")"
        done
        echo ""
        echo "Output directories:"
        echo "  - $output_base/{tables,data,figures}/"
        echo "  - $executed_dir/"
        return 0
    fi

    mkdir -p "$executed_dir"

    for notebook in "${notebooks[@]}"; do
        ((current++))
        local name=$(basename "$notebook")

        echo -e "${YELLOW}[$current/$total]${NC} Executing $name..."

        # Track files before execution
        local tables_before=$(count_files "$output_base/tables" "*.tex")
        local data_before=$(count_files "$output_base/data" "*.csv")
        local figures_before=$(count_files "$output_base/figures" "*.pdf")

        # Execute notebook
        local output_notebook="$executed_dir/$name"
        if jupyter nbconvert --execute \
            --to notebook \
            --output "$output_notebook" \
            --ExecutePreprocessor.timeout=600 \
            "$notebook" 2>&1; then

            # Track files after execution
            local tables_after=$(count_files "$output_base/tables" "*.tex")
            local data_after=$(count_files "$output_base/data" "*.csv")
            local figures_after=$(count_files "$output_base/figures" "*.pdf")

            local new_tables=$((tables_after - tables_before))
            local new_data=$((data_after - data_before))
            local new_figures=$((figures_after - figures_before))

            echo -e "  ${GREEN}✓${NC} Completed"
            if [[ $new_tables -gt 0 ]] || [[ $new_data -gt 0 ]] || [[ $new_figures -gt 0 ]]; then
                echo -e "  ${CYAN}Created:${NC} $new_tables tables, $new_data CSVs, $new_figures figures"
            fi
        else
            echo -e "  ${RED}✗${NC} Failed"
            echo -e "  ${CYAN}Debug:${NC} DATASET_VARIANT=$variant jupyter lab $notebook"
            ((failed++))
        fi
        echo ""
    done

    # Summary
    echo "=========================================="
    echo -e "  ${CYAN}Output Summary${NC}"
    echo "=========================================="
    echo ""
    echo "  Tables:   $output_base/tables/"
    echo "  Data:     $output_base/data/"
    echo "  Figures:  $output_base/figures/"
    echo "  Executed: $executed_dir/"
    echo ""

    if [[ $failed -eq 0 ]]; then
        echo -e "${GREEN}All $total notebooks completed successfully${NC}"
    else
        echo -e "${RED}$failed of $total notebooks failed${NC}"
        return 1
    fi
}

# Parse arguments
MODE=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            usage
            ;;
        verify|replicate|all)
            MODE=$1
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            ;;
    esac
done

# Default to verify if not specified
MODE=${MODE:-verify}

echo ""
echo "Teralizer Notebook Execution"
echo "============================"

case $MODE in
    verify)
        run_notebooks "verify"
        ;;
    replicate)
        run_notebooks "replicate"
        ;;
    all)
        run_notebooks "verify"
        echo ""
        run_notebooks "replicate"
        ;;
    *)
        echo -e "${RED}Unknown mode: $MODE${NC}"
        usage
        ;;
esac

echo ""
