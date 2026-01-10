#!/bin/bash
#
# Setup/reset the artifact evaluation environment.
#
# This script simulates what an ACM artifact evaluator would experience
# by creating a fresh clone from GitHub, resolving LFS files, and
# extracting to a test location.
#
# Usage:
#   ./scripts/setup-eval-environment.sh [OPTIONS]
#
# Options:
#   --with-projects PATH       Symlink projects directory from PATH
#   --merge-projects P1 P2     Merge projects from two directories (P1 takes precedence for duplicates)
#   --copy-projects            Copy files instead of symlinking (required for Docker)
#   --max-projects N           Limit number of projects to copy (default: all)
#   --with-data PATH           Symlink data directory from PATH
#   --merge-data D1 D2         Merge data from two directories
#   --skip-clone               Skip clone step (use existing /tmp/teralizer-clean)
#   --help                     Show this help message
#
# The script creates:
#   /tmp/teralizer-clean/           Fresh clone with LFS resolved
#   /tmp/teralizer-replication.zip  Zenodo-style archive
#   /tmp/teralizer-eval/            Extracted evaluation environment
#
# Examples:
#   # Basic setup (no projects/data)
#   ./scripts/setup-eval-environment.sh
#
#   # With merged projects from both repos
#   ./scripts/setup-eval-environment.sh \
#       --merge-projects ~/Projects/test-generalization/projects \
#                        ~/Projects/test-generalization-dev/projects
#
#   # Full setup with projects and data
#   ./scripts/setup-eval-environment.sh \
#       --merge-projects ~/Projects/test-generalization/projects \
#                        ~/Projects/test-generalization-dev/projects \
#       --merge-data ~/Projects/test-generalization/data \
#                    ~/Projects/test-generalization-dev/data
#

set -euo pipefail

# Configuration
GITHUB_REPO="git@github.com:glockyco/test-generalization.git"
CLONE_DIR="/tmp/teralizer-clean"
ARCHIVE_PATH="/tmp/teralizer-replication.zip"
EVAL_DIR="/tmp/teralizer-eval"

# Options
PROJECTS_SOURCE=""
PROJECTS_MERGE_1=""
PROJECTS_MERGE_2=""
COPY_PROJECTS=false
MAX_PROJECTS=""
DATA_SOURCE=""
DATA_MERGE_1=""
DATA_MERGE_2=""
SKIP_CLONE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_help() {
    head -40 "$0" | tail -38
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --with-projects)
            PROJECTS_SOURCE="$2"
            shift 2
            ;;
        --merge-projects)
            PROJECTS_MERGE_1="$2"
            PROJECTS_MERGE_2="$3"
            shift 3
            ;;
        --copy-projects)
            COPY_PROJECTS=true
            shift
            ;;
        --max-projects)
            MAX_PROJECTS="$2"
            shift 2
            ;;
        --with-data)
            DATA_SOURCE="$2"
            shift 2
            ;;
        --merge-data)
            DATA_MERGE_1="$2"
            DATA_MERGE_2="$3"
            shift 3
            ;;
        --skip-clone)
            SKIP_CLONE=true
            shift
            ;;
        --help|-h)
            show_help
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            ;;
    esac
done

# Validate options
if [[ -n "$PROJECTS_SOURCE" ]] && [[ ! -d "$PROJECTS_SOURCE" ]]; then
    log_error "Projects directory does not exist: $PROJECTS_SOURCE"
    exit 1
fi

if [[ -n "$PROJECTS_MERGE_1" ]]; then
    if [[ ! -d "$PROJECTS_MERGE_1" ]]; then
        log_error "Projects directory does not exist: $PROJECTS_MERGE_1"
        exit 1
    fi
    if [[ ! -d "$PROJECTS_MERGE_2" ]]; then
        log_error "Projects directory does not exist: $PROJECTS_MERGE_2"
        exit 1
    fi
fi

if [[ -n "$DATA_SOURCE" ]] && [[ ! -d "$DATA_SOURCE" ]]; then
    log_error "Data directory does not exist: $DATA_SOURCE"
    exit 1
fi

if [[ -n "$DATA_MERGE_1" ]]; then
    if [[ ! -d "$DATA_MERGE_1" ]]; then
        log_error "Data directory does not exist: $DATA_MERGE_1"
        exit 1
    fi
    if [[ ! -d "$DATA_MERGE_2" ]]; then
        log_error "Data directory does not exist: $DATA_MERGE_2"
        exit 1
    fi
fi

# Track timing
START_TIME=$(date +%s)

# Step 1: Clean up existing directories
log_info "Cleaning up existing evaluation environment..."
rm -rf "$EVAL_DIR"
rm -f "$ARCHIVE_PATH"

if [[ "$SKIP_CLONE" == false ]]; then
    rm -rf "$CLONE_DIR"

    # Step 2: Fresh clone from GitHub
    log_info "Cloning repository from GitHub..."
    git clone "$GITHUB_REPO" "$CLONE_DIR" 2>&1 | head -5

    # Step 3: Resolve LFS files
    log_info "Resolving Git LFS files..."
    cd "$CLONE_DIR"
    git lfs pull

    # Step 3b: Initialize submodules (jpf-core, jpf-symbc)
    log_info "Initializing Git submodules..."
    git submodule update --init --recursive
else
    log_info "Skipping clone (using existing $CLONE_DIR)"
    if [[ ! -d "$CLONE_DIR" ]]; then
        log_error "Clone directory does not exist: $CLONE_DIR"
        exit 1
    fi
    cd "$CLONE_DIR"
fi

# Step 4: Verify LFS files are resolved (not pointers)
log_info "Verifying LFS files..."
for dump in replication/datasets/*.dump; do
    if [[ -f "$dump" ]]; then
        # LFS pointers start with "version https://git-lfs"
        if head -c 50 "$dump" | grep -q "version https://git-lfs"; then
            log_error "LFS file not resolved: $dump"
            exit 1
        fi
        size=$(stat -f%z "$dump" 2>/dev/null || stat --printf="%s" "$dump" 2>/dev/null)
        log_info "  $(basename "$dump"): $(numfmt --to=iec-i --suffix=B "$size" 2>/dev/null || echo "${size} bytes")"
    fi
done

# Step 5: Create Zenodo-style archive
# Include .git for submodule references (git-version plugin needs it)
log_info "Creating Zenodo-style archive..."
cd "$CLONE_DIR"
zip -rq "$ARCHIVE_PATH" .
archive_size=$(stat -f%z "$ARCHIVE_PATH" 2>/dev/null || stat --printf="%s" "$ARCHIVE_PATH" 2>/dev/null)
log_info "  Archive size: $(numfmt --to=iec-i --suffix=B "$archive_size" 2>/dev/null || echo "${archive_size} bytes")"

# Step 6: Extract to evaluation directory
log_info "Extracting to evaluation directory..."
mkdir -p "$EVAL_DIR"
cd "$EVAL_DIR"
unzip -q "$ARCHIVE_PATH"

# Step 7: Generate replication configs if needed
REPLICATION_CONFIG_DIR="$EVAL_DIR/project-configs/replication/extended"
if [[ ! -d "$REPLICATION_CONFIG_DIR" ]]; then
    log_info "Generating replication configs..."
    /bin/bash "$EVAL_DIR/replication/scripts/generate-replication-configs.sh"
fi

# Step 8: Handle projects directory
# Clean existing projects to avoid stale files from archive
if [[ -d "$EVAL_DIR/projects" ]]; then
    rm -rf "$EVAL_DIR/projects"
fi

if [[ -n "$PROJECTS_SOURCE" ]]; then
    if [[ "$COPY_PROJECTS" == true ]]; then
        log_info "Copying projects directory..."
        mkdir -p "$EVAL_DIR/projects"
        rsync -aL "$PROJECTS_SOURCE/" "$EVAL_DIR/projects/"
        total=$(ls -1 "$EVAL_DIR/projects" | wc -l | tr -d ' ')
        log_info "  Copied $total projects"
    else
        log_info "Symlinking projects directory..."
        ln -s "$PROJECTS_SOURCE" "$EVAL_DIR/projects"
        echo "  $EVAL_DIR/projects -> $PROJECTS_SOURCE"
    fi
elif [[ -n "$PROJECTS_MERGE_1" ]]; then
    log_info "Merging projects directories..."
    mkdir -p "$EVAL_DIR/projects"

    # Primary projects used in the paper (exact list)
    declare -a PRIMARY_PROJECT_NAMES=(
        "commons-utils"
        "commons-utils-es-default-1s"
        "commons-utils-es-default-10s"
        "commons-utils-es-default-60s"
        "eqbench-es-default-1s"
        "eqbench-es-default-10s"
        "eqbench-es-default-60s"
    )

    # Collect primary projects from both directories
    declare -a primary_projects=()
    for source_dir in "$PROJECTS_MERGE_2" "$PROJECTS_MERGE_1"; do
        for dir in "$source_dir"/*/; do
            if [[ -d "$dir" ]]; then
                name=$(basename "$dir")
                if [[ " ${PRIMARY_PROJECT_NAMES[*]} " =~ " $name " ]]; then
                    primary_projects+=("$dir")
                fi
            fi
        done
    done

    # Collect extended projects that match the first N configs
    declare -a extended_projects=()
    EXTENDED_CONFIG_DIR="$EVAL_DIR/project-configs/replication/extended"

    if [[ -n "$MAX_PROJECTS" ]]; then
        log_info "  Primary: ${#primary_projects[@]} projects (all)"
        log_info "  Extended: matching first $MAX_PROJECTS configs"

        # Read project names from the first N replication configs
        for ((i=1; i<=MAX_PROJECTS; i++)); do
            config="$EXTENDED_CONFIG_DIR/project-$i.conf"
            if [[ -f "$config" ]]; then
                project_path=$(grep 'root-path' "$config" | sed 's/.*= "//' | sed 's/".*//')
                project_name=$(basename "$project_path")
                # Find this project in source directories
                for source_dir in "$PROJECTS_MERGE_2" "$PROJECTS_MERGE_1"; do
                    if [[ -d "$source_dir/$project_name" ]]; then
                        extended_projects+=("$source_dir/$project_name")
                        break
                    fi
                done
            fi
        done
    else
        # No limit - collect all extended projects
        for source_dir in "$PROJECTS_MERGE_2" "$PROJECTS_MERGE_1"; do
            for dir in "$source_dir"/*/; do
                if [[ -d "$dir" ]]; then
                    name=$(basename "$dir")
                    if [[ "$name" == github_com_* ]]; then
                        extended_projects+=("$dir")
                    fi
                fi
            done
        done
    fi

    # Combine: all primary + limited extended
    declare -a all_projects=("${primary_projects[@]}" "${extended_projects[@]}")

    # Process projects (copy or symlink)
    count=0
    for dir in "${all_projects[@]}"; do
        name=$(basename "$dir")
        # Remove trailing slash for consistent rsync behavior
        dir="${dir%/}"
        if [[ "$COPY_PROJECTS" == true ]]; then
            # Copy with -L to follow symlinks (no trailing slash = copy dir itself)
            rsync -aL "$dir" "$EVAL_DIR/projects/" 2>/dev/null || true
        else
            # Use ln -sfn: -f to force, -n to not follow existing symlinks
            ln -sfn "$dir" "$EVAL_DIR/projects/$name" 2>/dev/null || true
        fi
        ((count++)) || true

        # Progress indicator every 100 projects
        if (( count % 100 == 0 )); then
            echo "    Processed $count / ${#all_projects[@]} projects..."
        fi
    done

    total=$(ls -1 "$EVAL_DIR/projects" 2>/dev/null | wc -l | tr -d ' ')
    if [[ "$COPY_PROJECTS" == true ]]; then
        log_info "  Copied $total projects"
    else
        log_info "  Linked $total projects"
    fi
fi

# Step 9: Handle data directory
if [[ -n "$DATA_SOURCE" ]]; then
    log_info "Symlinking data directory..."
    ln -s "$DATA_SOURCE" "$EVAL_DIR/data"
    echo "  $EVAL_DIR/data -> $DATA_SOURCE"
elif [[ -n "$DATA_MERGE_1" ]]; then
    log_info "Merging data directories..."
    mkdir -p "$EVAL_DIR/data"

    # First, link all from merge_2 (lower priority)
    for dir in "$DATA_MERGE_2"/*/; do
        if [[ -d "$dir" ]]; then
            name=$(basename "$dir")
            ln -s "$dir" "$EVAL_DIR/data/$name" 2>/dev/null || true
        fi
    done

    # Then, override with merge_1 (higher priority)
    # Use ln -sfn: -f to force, -n to not follow existing symlinks to directories
    for dir in "$DATA_MERGE_1"/*/; do
        if [[ -d "$dir" ]]; then
            name=$(basename "$dir")
            ln -sfn "$dir" "$EVAL_DIR/data/$name"
        fi
    done

    total=$(ls -1 "$EVAL_DIR/data" 2>/dev/null | wc -l | tr -d ' ')
    log_info "  Total: $total data directories"
fi

# Step 10: Report environment status
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
log_info "Evaluation environment ready!"
echo ""
echo "  Location:     $EVAL_DIR"
echo "  Setup time:   ${DURATION}s"
echo "  Archive:      $ARCHIVE_PATH"

if [[ -d "$EVAL_DIR/projects" ]]; then
    proj_count=$(ls -1 "$EVAL_DIR/projects" 2>/dev/null | wc -l | tr -d ' ')
    echo "  Projects:     $proj_count directories"
fi

if [[ -d "$EVAL_DIR/data" ]]; then
    data_count=$(ls -1 "$EVAL_DIR/data" 2>/dev/null | wc -l | tr -d ' ')
    echo "  Data:         $data_count directories"
fi

echo ""
echo "Next steps:"
echo "  cd $EVAL_DIR/replication"
echo "  ./quick-start.sh"
echo ""
