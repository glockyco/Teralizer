#!/usr/bin/env bash
# Unified script for running the Teralizer pipeline.
#
# Supports both the primary dataset (EqBench + Commons Utils) and extended
# dataset (RepoReapers) with flexible selection options.
#
# Runs are resumable. State lives in REPLICATION_RUN_STATE_DIR when set, or in
# replication/run-state/<dataset> for extended runs and
# replication/run-state/primary-<phase> for primary runs. Each attempted config
# writes a done-marker and a status.tsv row. The ledger records "-" for log path
# because the runner leaves Gradle output visible in the terminal rather than
# capturing it to a per-config file.
#
# Graceful pause: touch <state-dir>/STOP. The in-flight config finishes, the file
# is consumed, and relaunching resumes from the next missing done-marker.
#
# Per-project wall cap: REPLICATION_PROJECT_TIMEOUT seconds (default 1800).
# Set it to 0 to disable the cap. A capped config is ledgered as exit 124.
# Docker mode uses a deterministic container name per config, so the watchdog
# and signal traps can stop the container instead of only the compose-run client.
#
# DATASETS & EXPECTED RUNTIMES:
#
#   Primary Dataset (EqBench + Commons Utils):
#     - Generation phase (EvoSuite): ~1.5-3 hours per config
#     - Generalization phase: ~8-31 hours per config
#     - Total for all configs: ~100+ hours
#
#   Extended Dataset (RepoReapers):
#     - 1161 projects, ~12 hours with the default per-project cap
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
#   # Extended dataset subset (~31 min, project-dependent)
#   ./run.sh --dataset extended --count 50
#
#   # All extended dataset (~12 hours with the default cap)
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


ROOT_DIR="$REPO_ROOT"
# shellcheck source=scripts/lib/run-supervisor.sh
source "$REPO_ROOT/scripts/lib/run-supervisor.sh"
supervisor_install_traps
# Default configuration
DATASET=""
PHASE=""
TIME_VARIANT=""
PROJECT_FILTER=""
START=1
COUNT=""
USE_DOCKER="auto"
DRY_RUN=false
PROJECT_TIMEOUT="${REPLICATION_PROJECT_TIMEOUT:-1800}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

usage() {
    sed -n '2,/^set -euo pipefail$/p' "$0" | sed '$d'
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

if ! [[ "$PROJECT_TIMEOUT" =~ ^[0-9]+$ ]]; then
    echo -e "${RED}Error: REPLICATION_PROJECT_TIMEOUT must be a non-negative integer${NC}"
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

# Runner state and helpers
state_scope="$DATASET"
if [[ "$DATASET" == "primary" ]]; then
    state_scope="primary-$PHASE"
fi
RUN_STATE_DIR="${REPLICATION_RUN_STATE_DIR:-$REPO_ROOT/replication/run-state/$state_scope}"
DONE_DIR="$RUN_STATE_DIR/done"
STATUS_TSV="$RUN_STATE_DIR/status.tsv"
STOP_FILE="$RUN_STATE_DIR/STOP"

get_project_path() {
    local conf="$1"
    sed -n 's/[[:space:]]*root-path[[:space:]]*=[[:space:]]*"\(projects\/[^"]*\)".*/\1/p' "$conf" | head -1
}

project_abs_for_config() {
    local conf="$1"
    local project_path
    project_path=$(get_project_path "$conf")
    [[ -n "$project_path" ]] || return 0
    printf '%s/%s\n' "$REPO_ROOT" "$project_path"
}

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
        # Scale the ~12 h full capped run linearly by config count.
        total_minutes=$(((${#configs[@]} * 720 + 1160) / 1161))
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
echo -e "State dir:  ${CYAN}$RUN_STATE_DIR${NC}"
if [[ "$PROJECT_TIMEOUT" -eq 0 ]]; then
    echo -e "Wall cap:   ${CYAN}disabled${NC}"
else
    echo -e "Wall cap:   ${CYAN}${PROJECT_TIMEOUT}s per config${NC}"
fi
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
    local name="$2"

    if [[ "$USE_DOCKER" == "yes" ]]; then
        # Translate host path to container path
        # Host: /path/to/repo/project-configs/... -> Container: /app/project-configs/...
        local container_conf="${conf/$REPO_ROOT\/project-configs//app/project-configs}"
        local container_name="teralizer-replication-${DATASET}-${name}"
        container_name="${container_name//[^a-zA-Z0-9_.-]/-}"
        supervised_container_run "$container_name" "$PROJECT_TIMEOUT" \
            docker compose -f "$SCRIPT_DIR/../docker-compose.yml" run --rm \
            --name "$container_name" teralizer \
            ./gradlew run -Dteralizer.config="$container_conf" --no-daemon
        return "$SUPERVISED_RC"
    else
        supervised_run "-" "$PROJECT_TIMEOUT" \
            ./gradlew run -Dteralizer.config="$conf" --no-daemon
        return "$SUPERVISED_RC"
    fi
}

cd "$REPO_ROOT"

# Ensure PostgreSQL is running for Docker mode
if [[ "$USE_DOCKER" == "yes" ]]; then
    echo "Ensuring PostgreSQL is running..."
    docker compose -f "$SCRIPT_DIR/../docker-compose.yml" up -d postgres
    sleep 3
fi

# Process configs
mkdir -p "$DONE_DIR"
[[ -f "$STATUS_TSV" ]] || printf 'config-name\texit_code\tlog\n' > "$STATUS_TSV"
rm -f "$STOP_FILE"

succeeded=0
failed=0
skipped=0
capped=0
stopped=false

for ((i=0; i<${#configs[@]}; i++)); do
    if supervisor_stop_requested "$STOP_FILE"; then
        stopped=true
        break
    fi

    conf="${configs[$i]}"
    name=$(basename "$conf" .conf)
    done_marker="$DONE_DIR/$name"
    progress="[$((i+1))/${#configs[@]}]"

    if [[ -f "$done_marker" ]]; then
        echo -e "${YELLOW}$progress Skipping${NC} $name (already done)"
        skipped=$((skipped + 1))
        continue
    fi

    echo -e "${YELLOW}$progress Processing${NC} $name"

    project_abs=$(project_abs_for_config "$conf")
    SUPERVISOR_ACTIVE_PATH="$project_abs"
    if run_config "$conf" "$name"; then
        rc=0
    else
        rc=$?
    fi
    cleanup_leftover_project_processes "$project_abs" "-"
    SUPERVISOR_ACTIVE_PATH=""

    printf '%s\t%s\t%s\n' "$name" "$rc" "-" >> "$STATUS_TSV"
    touch "$done_marker"

    if [[ "$rc" -eq 0 ]]; then
        echo -e "${GREEN}$progress complete${NC} $name"
        succeeded=$((succeeded + 1))
    elif [[ "$rc" -eq 124 ]]; then
        echo -e "${RED}$progress capped${NC} $name (exit 124 in $STATUS_TSV)"
        capped=$((capped + 1))
        failed=$((failed + 1))
    else
        echo -e "${RED}$progress failed${NC} $name (exit $rc in $STATUS_TSV)"
        failed=$((failed + 1))
    fi
    echo ""
done

[[ "$stopped" == true ]] && echo "STOP file honored at a config boundary. Relaunch resumes from $RUN_STATE_DIR."

# Summary
echo "=========================================="
echo -e "${GREEN}Complete${NC}"
echo "=========================================="
echo "Succeeded: $succeeded"
echo "Failed:    $failed"
echo "Capped:    $capped"
echo "Skipped:   $skipped"
echo "Total:     ${#configs[@]}"
echo "Ledger:    $STATUS_TSV"
