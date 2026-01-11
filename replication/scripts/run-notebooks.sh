#!/usr/bin/env bash
# Execute analysis notebooks and export results.
#
# This script runs all analysis notebooks using jupyter nbconvert inside
# the analysis Docker container, exporting tables, data, and figures to
# variant-specific output directories.
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
cd "$SCRIPT_DIR/.."

# Colors (matching verify-results.sh)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

DRY_RUN=false
CONTAINER="analysis-replication"

usage() {
    head -24 "$0" | tail -22
    exit 0
}

# Check if container is running
check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
        echo -e "${RED}Error: Container '$CONTAINER' is not running${NC}"
        echo "Start it with: ./quick-start.sh"
        exit 1
    fi
}

# Execute command in container
docker_exec() {
    docker exec -e DATASET_VARIANT="$DATASET_VARIANT" "$CONTAINER" "$@"
}

# Count files in directory matching pattern (inside container)
count_files() {
    local dir="$1"
    local pattern="$2"
    local count
    count=$(docker_exec find "$dir" -name "$pattern" 2>/dev/null | wc -l | tr -d ' ') || true
    echo "${count:-0}"
}

run_notebooks() {
    local variant=$1
    export DATASET_VARIANT=$variant

    # Notebooks are at /app/analysis/notebooks inside container
    local notebook_dir="/app/analysis/notebooks"
    local notebooks
    notebooks=$(docker_exec find "$notebook_dir" -maxdepth 1 -name "*.ipynb" -type f | sort)

    local total
    total=$(echo "$notebooks" | wc -l | tr -d ' ')
    local current=0
    local failed=0

    local output_base="/app/analysis/output/$variant"
    local executed_dir="/app/analysis/output/executed/$variant"

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
        echo "$notebooks" | while read -r notebook; do
            echo "  - $(basename "$notebook")"
        done
        echo ""
        echo "Output directories:"
        echo "  - $output_base/{tables,data,figures}/"
        echo "  - $executed_dir/"
        return 0
    fi

    # Create output directories
    docker_exec mkdir -p "$executed_dir"

    local start_time
    start_time=$(date +%s)

    local notebook_array=()
    while IFS= read -r line; do
        [[ -n "$line" ]] && notebook_array+=("$line")
    done <<< "$notebooks"

    for notebook in "${notebook_array[@]}"; do
        ((current++)) || true
        local name
        name=$(basename "$notebook")

        echo -e "${YELLOW}[$current/$total]${NC} Executing $name..."

        # Track files before execution
        local tables_before data_before figures_before
        tables_before=$(count_files "$output_base/tables" "*.tex")
        data_before=$(count_files "$output_base/data" "*.csv")
        figures_before=$(count_files "$output_base/figures" "*.pdf")

        # Execute notebook inside container
        local nb_start
        nb_start=$(date +%s)
        local output_notebook="$executed_dir/$name"
        if docker_exec /opt/venv/bin/jupyter nbconvert --execute \
            --to notebook \
            --output "$output_notebook" \
            --ExecutePreprocessor.timeout=600 \
            "$notebook" 2>&1; then

            local nb_elapsed=$(($(date +%s) - nb_start))

            # Track files after execution
            local tables_after data_after figures_after
            tables_after=$(count_files "$output_base/tables" "*.tex")
            data_after=$(count_files "$output_base/data" "*.csv")
            figures_after=$(count_files "$output_base/figures" "*.pdf")

            local new_tables=$((tables_after - tables_before))
            local new_data=$((data_after - data_before))
            local new_figures=$((figures_after - figures_before))

            echo -e "  ${GREEN}✓${NC} Completed (${nb_elapsed}s)"
            if [[ $new_tables -gt 0 ]] || [[ $new_data -gt 0 ]] || [[ $new_figures -gt 0 ]]; then
                echo -e "  ${CYAN}Created:${NC} $new_tables tables, $new_data CSVs, $new_figures figures"
            fi
        else
            echo -e "  ${RED}✗${NC} Failed"
            echo -e "  ${CYAN}Debug:${NC} docker exec -it $CONTAINER bash -c 'DATASET_VARIANT=$variant jupyter lab'"
            ((failed++))
        fi
        echo ""
    done

    local total_elapsed=$(($(date +%s) - start_time))

    # Summary
    echo "=========================================="
    echo -e "  ${CYAN}Output Summary${NC}"
    echo "=========================================="
    echo ""
    echo "  Tables:   $output_base/tables/"
    echo "  Data:     $output_base/data/"
    echo "  Figures:  $output_base/figures/"
    echo "  Executed: $executed_dir/"
    echo "  Duration: ${total_elapsed}s"
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

# Check container is running
check_container

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
