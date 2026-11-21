#!/usr/bin/env bash
# Unified script for running the Teralizer pipeline.
#
# Supports both the primary dataset (EqBench + Commons Utils) and extended
# dataset (RepoReapers) with flexible selection options.
#
# Runs are resumable. The ledger lives under REPLICATION_RUN_STATE_DIR when set.
# Otherwise the runner uses replication/run-state/real-world or
# replication/run-state/controlled-<phase>. Packaged controlled runs keep one
# ledger per phase under the shared state directory.
# Each attempted config writes a done marker and a status.tsv row. The ledger
# records "-" for log path because Gradle prints directly to the terminal.
# REPLICATION_PROJECTS_ROOT moves the directory containing projects/ (default:
# the repository root).
#
# Graceful pause: touch the STOP path printed at startup. The in-flight config
# finishes, the file is consumed, and the runner exits 75. Relaunching resumes
# from the next missing done-marker.
#
# Per-project wall cap: REPLICATION_PROJECT_TIMEOUT seconds (default 1800).
# Set it to 0 to disable the cap. A capped config is ledgered as exit 124.
# Docker mode uses a deterministic container name per config, so the watchdog
# and signal traps can stop the container instead of only the compose-run client.
#
# DATASETS & EXPECTED RUNTIMES:
#
#   Controlled corpus (EqBench + Commons Utils):
#     - Generation phase (EvoSuite): ~1.5-3 hours per config
#     - Generalization phase: ~8-31 hours per config
#     - Total for all configs: ~100+ hours
#
#   Real-world corpus (RepoReapers):
#     - Every registered project configuration, ~12 hours with the default cap
#
# USAGE:
#   ./run.sh --corpus <controlled|real-world> [options]
#
# CONTROLLED CORPUS OPTIONS:
#   --phase <generation|generalization>  Pipeline phase (required for controlled)
#   --time <1s|10s|60s|dev>              Time variant (default: all)
#   --project <eqbench|commons-utils>    Specific project (default: both)
#
# REAL-WORLD CORPUS OPTIONS:
#   --start N       Start from project N (default: 1)
#   --count N       Number of projects (default: all)
#   --sample FILE   Use exactly the projects listed in FILE, one name per line.
#                   Cannot be combined with --start or --count.
#
# GENERAL OPTIONS:
#   --docker        Run via Docker (default when the Compose file is available)
#   --local         Run locally (requires Java, Gradle, PostgreSQL)
#   --dry-run       Show what would be run without executing
#   --yes           Skip the confirmation for long runs
#   --help          Show this help message
#
# EXAMPLES:
#   # Quick verification (~5 min)
#   ./run.sh --corpus real-world --count 5
#
#   # Extended dataset subset (~31 min, project-dependent)
#   ./run.sh --corpus real-world --count 50
#
#   # All extended dataset (~12 hours with the default cap)
#   ./run.sh --corpus real-world
#
#   # Primary dataset - shortest config (~8 hours total)
#   ./run.sh --corpus controlled --phase generalization --time 1s
#
#   # Primary dataset - single project generation (~1.5 hours)
#   ./run.sh --corpus controlled --phase generation --time 1s --project commons-utils
#
#   # All primary dataset (~100+ hours)
#   ./run.sh --corpus controlled --phase generalization

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

ROOT_DIR="$REPO_ROOT"
# shellcheck source=scripts/lib/run-supervisor.sh
source "$REPO_ROOT/scripts/lib/run-supervisor.sh"
# Default configuration
CORPUS_ID=""
DATASET=""
PHASE=""
TIME_VARIANT=""
PROJECT_FILTER=""
START=1
COUNT=""
SAMPLE_FILE=""
RANGE_SELECTED=false
USE_DOCKER="auto"
DRY_RUN=false
ASSUME_YES=false
PROJECT_TIMEOUT="${REPLICATION_PROJECT_TIMEOUT:-1800}"
PROJECTS_ROOT="${REPLICATION_PROJECTS_ROOT:-$REPO_ROOT}"
[[ "$PROJECTS_ROOT" == /* || "$PROJECTS_ROOT" == ?:/* ]] || PROJECTS_ROOT="$PWD/$PROJECTS_ROOT"
COMPOSE_FILE="${TERALIZER_COMPOSE_FILE:-$SCRIPT_DIR/../docker-compose.yml}"

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
        --corpus)
            CORPUS_ID="$2"
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
            RANGE_SELECTED=true
            shift 2
            ;;
        --count)
            COUNT="$2"
            RANGE_SELECTED=true
            shift 2
            ;;
        --sample)
            SAMPLE_FILE="${2:?--sample requires a file}"
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
        --yes)
            ASSUME_YES=true
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

# Resolve the corpus definition. New measurements always use a scratch database.
if [[ -z "$CORPUS_ID" ]]; then
    echo -e "${RED}Error: --corpus is required${NC}"
    echo "Use --help for usage information"
    exit 1
fi
corpus_exports=$("$REPO_ROOT/scripts/corpus-registry" export "$CORPUS_ID") || exit $?
eval "$corpus_exports"
RESOLVED_CORPUS_DATABASE="$DB_NAME"
CORPUS_CONFIG_DIR="$CONFIG_DIR"
case "$CORPUS_ID" in
    controlled) DATASET=primary ;;
    real-world) DATASET=extended ;;
    *)
        echo -e "${RED}Error: pipeline replication is not defined for corpus '$CORPUS_ID'${NC}"
        exit 1
        ;;
esac
SCRATCH_DB="${REPLICATION_SCRATCH_DB:-scratch_replication_${CORPUS_ID//-/_}}"
RUN_DATA_DIR="${REPLICATION_DATA_DIR:-data/replication-$CORPUS_ID}"
source "$REPO_ROOT/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$REPO_ROOT" require_scratch_db "$SCRATCH_DB"

# Validate controlled-corpus options
if [[ "$DATASET" == "primary" && -z "$PHASE" ]]; then
    echo -e "${RED}Error: --phase is required for the controlled corpus${NC}"
    echo "Use: --phase generation  OR  --phase generalization"
    exit 1
fi

if [[ -n "$PHASE" && "$PHASE" != "generation" && "$PHASE" != "generalization" ]]; then
    echo -e "${RED}Error: --phase must be 'generation' or 'generalization'${NC}"
    exit 1
fi

if [[ -n "$SAMPLE_FILE" && ( "$DATASET" != extended || "$RANGE_SELECTED" == true ) ]]; then
    echo "--sample is for real-world runs and cannot be combined with --start or --count" >&2
    exit 1
fi

if [[ -n "$SAMPLE_FILE" && ! -r "$SAMPLE_FILE" ]]; then
    echo "Cannot read sample list: $SAMPLE_FILE" >&2
    exit 1
fi

if ! [[ "$PROJECT_TIMEOUT" =~ ^[0-9]+$ ]]; then
    echo -e "${RED}Error: REPLICATION_PROJECT_TIMEOUT must be a non-negative integer${NC}"
    exit 1
fi

# Determine Docker usage
if [[ "$USE_DOCKER" == "auto" ]]; then
    if [[ -f "$COMPOSE_FILE" ]]; then
        USE_DOCKER="yes"
    else
        USE_DOCKER="no"
    fi
fi

# Runner state and helpers
state_scope="$CORPUS_ID"
if [[ "$DATASET" == "primary" ]]; then
    state_scope="$CORPUS_ID-$PHASE"
fi
RUN_STATE_DIR="${REPLICATION_RUN_STATE_DIR:-$REPO_ROOT/replication/run-state/$state_scope}"
if [[ -n "${TERALIZER_COLLECT_DATA:-}" && "$DATASET" == primary ]]; then
    # Generation and generalization share config names, not done markers.
    RUN_STATE_DIR="$RUN_STATE_DIR/$PHASE"
fi
DONE_DIR="$RUN_STATE_DIR/done"
STATUS_TSV="$RUN_STATE_DIR/status.tsv"
STOP_FILE="${REPLICATION_RUN_STATE_DIR:-$RUN_STATE_DIR}/STOP"

get_project_path() {
    local conf="$1"
    sed -n 's/[[:space:]]*root-path[[:space:]]*=[[:space:]]*"\(projects\/[^"]*\)".*/\1/p' "$conf" | head -1
}

project_abs_for_config() {
    local conf="$1"
    local project_path
    project_path=$(get_project_path "$conf")
    [[ -n "$project_path" ]] || return 0
    printf '%s/%s\n' "$PROJECTS_ROOT" "$project_path"
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
    # Extended dataset. Both modes run against the projects checked out under
    # projects/, so both read the replication configs that carry local paths.
    # The project-configs/extended set holds remote URLs and is the source that
    # generate-replication-configs.sh transforms, never a runnable config.
    if [[ -z "$CORPUS_CONFIG_DIR" ]]; then
        echo -e "${RED}Error: corpus '$CORPUS_ID' declares no configuration directory${NC}"
        exit 1
    fi
    CONFIG_DIR="$REPO_ROOT/$CORPUS_CONFIG_DIR"
    if [[ ! -d "$CONFIG_DIR" ]]; then
        echo -e "${RED}Error: Replication configs not found at $CONFIG_DIR${NC}"
        echo "Run: ./scripts/generate-replication-configs.sh"
        exit 1
    fi

    # Newline delimiters match whole project names, including on Bash 3.2.
    sample_projects=$'\n'
    seen_projects=$'\n'
    if [[ -n "$SAMPLE_FILE" ]]; then
        while IFS=$' \t\r' read -r project || [[ -n "$project" ]]; do
            if [[ -n "$project" ]]; then
                sample_projects+="$project"$'\n'
            fi
        done < "$SAMPLE_FILE"
    fi

    # Get sorted list of project configs that have existing project directories
    all_configs=()
    skipped=0
    while IFS= read -r conf; do
        project_path=$(get_project_path "$conf")
        project="${project_path#projects/}"
        if [[ -n "$SAMPLE_FILE" ]]; then
            [[ "$sample_projects" == *$'\n'"$project"$'\n'* && -n "$project" ]] || continue
            seen_projects+="$project"$'\n'
        fi
        if [[ -n "$project_path" && -d "$PROJECTS_ROOT/$project_path" ]]; then
            all_configs+=("$conf")
        else
            skipped=$((skipped + 1))
        fi
    done < <(find "$CONFIG_DIR" -name "project-*.conf" | sort -V)

    if [[ -n "$SAMPLE_FILE" ]]; then
        invalid_sample=false
        while IFS= read -r project; do
            [[ -n "$project" ]] || continue
            if [[ "$seen_projects" != *$'\n'"$project"$'\n'* ]]; then
                echo "Sample project '$project' has no config in $CONFIG_DIR" >&2
                invalid_sample=true
            fi
            if [[ ! -d "$PROJECTS_ROOT/projects/$project" ]]; then
                echo "Sample project '$project' has no directory in $PROJECTS_ROOT/projects" >&2
                invalid_sample=true
            fi
        done <<<"$sample_projects"
        [[ "$invalid_sample" == false ]] || exit 1
    fi

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
        # Scale the measured full capped run by the current configuration inventory.
        local full_count=${#all_configs[@]}
        total_minutes=$(((${#configs[@]} * 720 + full_count - 1) / full_count))
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
echo -e "Corpus:     ${CYAN}$CORPUS_ID${NC}"
echo -e "Registered: ${CYAN}$RESOLVED_CORPUS_DATABASE${NC}"
echo -e "Scratch:    ${CYAN}$SCRATCH_DB${NC}"
[[ -n "$PHASE" ]] && echo -e "Phase:      ${CYAN}$PHASE${NC}"
[[ -n "$TIME_VARIANT" ]] && echo -e "Time:       ${CYAN}$TIME_VARIANT${NC}"
[[ -n "$PROJECT_FILTER" ]] && echo -e "Project:    ${CYAN}$PROJECT_FILTER${NC}"
if [[ -n "$SAMPLE_FILE" ]]; then
    echo -e "Sample:     ${CYAN}$SAMPLE_FILE${NC}"
elif [[ "$DATASET" == "extended" ]]; then
    echo -e "Range:      ${CYAN}$START to $((START + ${#configs[@]} - 1))${NC}"
fi
echo -e "Configs:    ${CYAN}${#configs[@]}${NC}"
echo -e "Est. time:  ${CYAN}$(estimate_runtime)${NC}"
echo -e "Mode:       ${CYAN}$([ "$USE_DOCKER" == "yes" ] && echo "Docker" || echo "Local")${NC}"
echo -e "State dir:  ${CYAN}$RUN_STATE_DIR${NC}"
echo -e "Stop file:  ${CYAN}$STOP_FILE${NC}"
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
if [[ "$ASSUME_YES" == false && "$runtime_estimate" == *"h"* ]]; then
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
        local container_name="${COMPOSE_PROJECT_NAME:-teralizer}-collect-${CORPUS_ID}-${name}"
        container_name="${container_name//[^a-zA-Z0-9_.-]/-}"
        supervised_container_run "$container_name" "$PROJECT_TIMEOUT" \
            docker compose -f "$COMPOSE_FILE" run --rm -T \
            --name "$container_name" teralizer \
            ./gradlew run -Dteralizer.config="$container_conf" \
            -Dteralizer.database.name="$SCRATCH_DB" \
            -Dteralizer.data-dir="/app/$RUN_DATA_DIR" --no-daemon
        return "$SUPERVISED_RC"
    else
        local path_overrides=() assignment
        if [[ "$PROJECTS_ROOT" != "$REPO_ROOT" ]]; then
            # EqBench configs explicitly set source and report paths under projects/.
            while IFS= read -r assignment; do
                path_overrides+=("-Dteralizer.project.${assignment%%=*}=$PROJECTS_ROOT/${assignment#*=}")
            done < <(sed -n 's/^[[:space:]]*\([a-z-]*-path\)[[:space:]]*=[[:space:]]*"\(projects\/[^"]*\)".*/\1=\2/p' "$conf")
        fi
        supervised_run "-" "$PROJECT_TIMEOUT" \
            ./gradlew run -Dteralizer.config="$conf" \
            -Dteralizer.database.name="$SCRATCH_DB" \
            -Dteralizer.data-dir="$RUN_DATA_DIR" ${path_overrides[@]+"${path_overrides[@]}"} --no-daemon
        return "$SUPERVISED_RC"
    fi
}

cd "$REPO_ROOT"
if [[ "$PROJECTS_ROOT" != "$REPO_ROOT" ]]; then
    SUPERVISOR_LOCK_DIR="$PROJECTS_ROOT/.teralizer-run.lock"
    if [[ "$USE_DOCKER" == yes ]]; then
        export TERALIZER_COLLECT_PROJECTS="$PROJECTS_ROOT/projects"
    fi
fi
supervisor_install_traps

# Ensure the selected scratch database exists without replacing resumable state.
if [[ "$USE_DOCKER" == "yes" ]]; then
    echo "Ensuring PostgreSQL is running..."
    docker compose -f "$COMPOSE_FILE" up -d --wait postgres
    # The pipeline service connects as the collection role, so that role owns the
    # scratch database. Role and database are set up by the server's superuser.
    export TERALIZER_PSQL_TRANSPORT=compose TERALIZER_COMPOSE_FILE="$COMPOSE_FILE"
    DB_USER="${REPLICATION_DB_USER:-teralizer}"
    source "$REPO_ROOT/scripts/lib/db-lifecycle.sh"
    ensure_collect_role
    if [[ "$(teralizer_psql -d postgres -tA \
        -c "SELECT 1 FROM pg_database WHERE datname = '$SCRATCH_DB'" 2>/dev/null)" != 1 ]]; then
        teralizer_psql -v ON_ERROR_STOP=1 -d postgres \
            -c "CREATE DATABASE $SCRATCH_DB OWNER $COLLECT_DB_USER;"
    fi
else
    source "$REPO_ROOT/scripts/lib/db-lifecycle.sh"
    ensure_postgres_up
    if [[ "$(teralizer_psql -tA -d postgres \
        -c "SELECT 1 FROM pg_database WHERE datname = '$SCRATCH_DB'" 2>/dev/null)" != 1 ]]; then
        recreate_scratch_db "$SCRATCH_DB"
    fi
fi

# Process configs
mkdir -p "$DONE_DIR"
[[ -f "$STATUS_TSV" ]] || printf 'config-name\texit_code\tlog\n' > "$STATUS_TSV"

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

if [[ "$stopped" == true ]] || supervisor_stop_requested "$STOP_FILE"; then
    stopped=true
    echo "STOP file honored at a config boundary. Relaunch resumes from $RUN_STATE_DIR."
fi

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
if [[ "$stopped" == true ]]; then
    exit 75
fi
