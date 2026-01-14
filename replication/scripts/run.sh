#!/usr/bin/env bash
# Unified script for running the Teralizer pipeline.
#
# Supports both the primary dataset (EqBench + Commons Utils) and extended
# dataset (RepoReapers) with flexible selection options.
#
# DATASETS & EXPECTED RUNTIMES:
#
#   Primary Dataset (EqBench + Commons Utils):
#     - Generation phase (EvoSuite): ~1.5-3 hours per config
#     - Generalization phase: ~8-31 hours per config
#     - Total for all configs: ~100+ hours
#
#   Extended Dataset (RepoReapers):
#     - 1161 projects, ~1 minute average per project
#     - Total: ~15 hours
#
# USAGE:
#   ./run.sh --dataset <primary|extended> [options]
#
# PRIMARY DATASET OPTIONS:
#   --phase <generation|generalization>  Pipeline phase (required for primary)
#   --time <1s|10s|60s|dev>              Time variant (default: all)
#   --project <eqbench|commons-utils>    Specific project (default: both)
#
# EXTENDED DATASET OPTIONS:
#   --start N       Start from project N (default: 1)
#   --count N       Number of projects (default: all)
#
# GENERAL OPTIONS:
#   --docker        Run via Docker (default if in replication/)
#   --local         Run locally (requires Java, Gradle, PostgreSQL)
#   --dry-run       Show what would be run without executing
#   --help          Show this help message
#
# EXAMPLES:
#   # Quick verification (~5 min)
#   ./run.sh --dataset extended --count 5
#
#   # Extended dataset subset (~40 min)
#   ./run.sh --dataset extended --count 50
#
#   # All extended dataset (~15 hours)
#   ./run.sh --dataset extended
#
#   # Primary dataset - shortest config (~8 hours total)
#   ./run.sh --dataset primary --phase generalization --time 1s
#
#   # Primary dataset - single project generation (~1.5 hours)
#   ./run.sh --dataset primary --phase generation --time 1s --project commons-utils
#
#   # All primary dataset (~100+ hours)
#   ./run.sh --dataset primary --phase generalization

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Default configuration
DATASET=""
PHASE=""
TIME_VARIANT=""
PROJECT_FILTER=""
START=1
COUNT=""
USE_DOCKER="auto"
DRY_RUN=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

usage() {
    head -50 "$0" | tail -48
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dataset)
            DATASET="$2"
            shift 2
            ;;
        --phase)
            PHASE="$2"
            shift 2
            ;;
        --time)
            TIME_VARIANT="$2"
            shift 2
            ;;
        --project)
            PROJECT_FILTER="$2"
            shift 2
            ;;
        --start)
            START="$2"
            shift 2
            ;;
        --count)
            COUNT="$2"
            shift 2
            ;;
        --docker)
            USE_DOCKER="yes"
            shift
            ;;
        --local)
            USE_DOCKER="no"
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Validate dataset
if [[ -z "$DATASET" ]]; then
    echo -e "${RED}Error: --dataset is required${NC}"
    echo "Use --help for usage information"
    exit 1
fi

if [[ "$DATASET" != "primary" && "$DATASET" != "extended" ]]; then
    echo -e "${RED}Error: --dataset must be 'primary' or 'extended'${NC}"
    exit 1
fi

# Validate primary dataset options
if [[ "$DATASET" == "primary" && -z "$PHASE" ]]; then
    echo -e "${RED}Error: --phase is required for primary dataset${NC}"
    echo "Use: --phase generation  OR  --phase generalization"
    exit 1
fi

if [[ -n "$PHASE" && "$PHASE" != "generation" && "$PHASE" != "generalization" ]]; then
    echo -e "${RED}Error: --phase must be 'generation' or 'generalization'${NC}"
    exit 1
fi

# Determine Docker usage
if [[ "$USE_DOCKER" == "auto" ]]; then
    if [[ -f "$SCRIPT_DIR/../docker-compose.yml" ]]; then
        USE_DOCKER="yes"
    else
        USE_DOCKER="no"
    fi
fi

# Build list of configs
configs=()

if [[ "$DATASET" == "primary" ]]; then
    CONFIG_DIR="$REPO_ROOT/project-configs/primary/$PHASE"

    for conf in "$CONFIG_DIR"/*.conf; do
        [[ -f "$conf" ]] || continue
        name=$(basename "$conf")

        # Filter by time variant
        if [[ -n "$TIME_VARIANT" ]]; then
            case "$TIME_VARIANT" in
                dev)
                    [[ "$name" == *-dev.conf ]] || continue
                    ;;
                1s|10s|60s)
                    [[ "$name" == *-${TIME_VARIANT}.conf ]] || continue
                    ;;
                *)
                    echo -e "${RED}Error: --time must be 1s, 10s, 60s, or dev${NC}"
                    exit 1
                    ;;
            esac
        fi

        # Filter by project
        if [[ -n "$PROJECT_FILTER" ]]; then
            [[ "$name" == ${PROJECT_FILTER}* ]] || continue
        fi

        configs+=("$conf")
    done
else
    # Extended dataset
    # Use replication configs (local paths) for Docker mode, original configs for local mode
    if [[ "$USE_DOCKER" == "yes" ]]; then
        CONFIG_DIR="$REPO_ROOT/project-configs/replication/extended"
        if [[ ! -d "$CONFIG_DIR" ]]; then
            echo -e "${RED}Error: Replication configs not found at $CONFIG_DIR${NC}"
            echo "Run: ./scripts/generate-replication-configs.sh"
            exit 1
        fi
    else
        CONFIG_DIR="$REPO_ROOT/project-configs/extended"
    fi

    PROJECTS_DIR="$REPO_ROOT/projects"

    # Helper: extract project path from config file
    get_project_path() {
        local conf="$1"
        grep 'root-path' "$conf" | sed 's/.*"\(projects\/[^"]*\)".*/\1/' | head -1
    }

    # Helper: check if project directory exists
    project_exists() {
        local conf="$1"
        local project_path
        project_path=$(get_project_path "$conf")
        [[ -n "$project_path" && -d "$REPO_ROOT/$project_path" ]]
    }

    # Get sorted list of project configs that have existing project directories
    all_configs=()
    skipped=0
    while IFS= read -r conf; do
        if project_exists "$conf"; then
            all_configs+=("$conf")
        else
            skipped=$((skipped + 1))
        fi
    done < <(find "$CONFIG_DIR" -name "project-*.conf" | sort -V)

    if [[ $skipped -gt 0 ]]; then
        echo -e "${YELLOW}Note: Skipped $skipped configs (project directories not found)${NC}"
    fi

    # Apply start/count
    end=${#all_configs[@]}
    if [[ -n "$COUNT" ]]; then
        end=$((START - 1 + COUNT))
        if [[ $end -gt ${#all_configs[@]} ]]; then
            end=${#all_configs[@]}
        fi
    fi

    for ((i=START-1; i<end; i++)); do
        if [[ $i -lt ${#all_configs[@]} ]]; then
            configs+=("${all_configs[$i]}")
        fi
    done
fi

if [[ ${#configs[@]} -eq 0 ]]; then
    echo -e "${RED}Error: No configs found matching criteria${NC}"
    exit 1
fi

# Estimate runtime
estimate_runtime() {
    local total_minutes=0

    if [[ "$DATASET" == "extended" ]]; then
        # ~1 minute per project
        total_minutes=${#configs[@]}
    else
        # Primary dataset times (approximate)
        for conf in "${configs[@]}"; do
            name=$(basename "$conf")
            if [[ "$PHASE" == "generation" ]]; then
                case "$name" in
                    eqbench*) total_minutes=$((total_minutes + 500)) ;;
                    commons-utils*) total_minutes=$((total_minutes + 130)) ;;
                esac
            else
                case "$name" in
                    eqbench*) total_minutes=$((total_minutes + 1600)) ;;
                    commons-utils-dev*) total_minutes=$((total_minutes + 200)) ;;
                    commons-utils*) total_minutes=$((total_minutes + 540)) ;;
                esac
            fi
        done
    fi

    if [[ $total_minutes -lt 60 ]]; then
        echo "~${total_minutes} minutes"
    else
        local hours=$((total_minutes / 60))
        local mins=$((total_minutes % 60))
        echo "~${hours}h ${mins}m"
    fi
}

# Display summary
echo "=========================================="
echo "  Teralizer Pipeline Runner"
echo "=========================================="
echo ""
echo -e "Dataset:    ${CYAN}$DATASET${NC}"
[[ -n "$PHASE" ]] && echo -e "Phase:      ${CYAN}$PHASE${NC}"
[[ -n "$TIME_VARIANT" ]] && echo -e "Time:       ${CYAN}$TIME_VARIANT${NC}"
[[ -n "$PROJECT_FILTER" ]] && echo -e "Project:    ${CYAN}$PROJECT_FILTER${NC}"
[[ "$DATASET" == "extended" ]] && echo -e "Range:      ${CYAN}$START to $((START + ${#configs[@]} - 1))${NC}"
echo -e "Configs:    ${CYAN}${#configs[@]}${NC}"
echo -e "Est. time:  ${CYAN}$(estimate_runtime)${NC}"
echo -e "Mode:       ${CYAN}$([ "$USE_DOCKER" == "yes" ] && echo "Docker" || echo "Local")${NC}"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo -e "${YELLOW}DRY RUN - configs that would be processed:${NC}"
    for conf in "${configs[@]}"; do
        echo "  $(basename "$conf")"
    done
    exit 0
fi

# Confirm if long-running
runtime_estimate=$(estimate_runtime)
if [[ "$runtime_estimate" == *"h"* ]]; then
    echo -e "${YELLOW}This will take a long time. Continue? (y/N)${NC}"
    read -r -n 1 reply
    echo
    if [[ ! "$reply" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
fi

# Run the pipeline
run_config() {
    local conf="$1"
    local name=$(basename "$conf")

    if [[ "$USE_DOCKER" == "yes" ]]; then
        # Translate host path to container path
        # Host: /path/to/repo/project-configs/... -> Container: /app/project-configs/...
        local container_conf="${conf/$REPO_ROOT\/project-configs//app/project-configs}"
        docker compose -f "$SCRIPT_DIR/../docker-compose.yml" run --rm teralizer \
            ./gradlew run -Dteralizer.config="$container_conf" --no-daemon
    else
        cd "$REPO_ROOT"
        ./gradlew run -Dteralizer.config="$conf" --no-daemon
    fi
}

# Ensure PostgreSQL is running for Docker mode
if [[ "$USE_DOCKER" == "yes" ]]; then
    echo "Ensuring PostgreSQL is running..."
    docker compose -f "$SCRIPT_DIR/../docker-compose.yml" up -d postgres
    sleep 3
fi

# Process configs
succeeded=0
failed=0

for ((i=0; i<${#configs[@]}; i++)); do
    conf="${configs[$i]}"
    name=$(basename "$conf")
    progress="[$((i+1))/${#configs[@]}]"

    echo -e "${YELLOW}$progress Processing${NC} $name"

    if run_config "$conf"; then
        echo -e "${GREEN}$progress ✓${NC} $name"
        ((succeeded++)) || true
    else
        echo -e "${RED}$progress ✗${NC} $name"
        ((failed++)) || true
    fi
    echo ""
done

# Summary
echo "=========================================="
echo -e "${GREEN}Complete${NC}"
echo "=========================================="
echo "Succeeded: $succeeded"
echo "Failed:    $failed"
echo "Total:     ${#configs[@]}"
