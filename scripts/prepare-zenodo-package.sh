#!/usr/bin/env bash
# Prepare Zenodo archives for artifact submission.
#
# This script creates multiple archives for Zenodo submission:
#   1. teralizer-results.zip                   - Results only (HTML, tables, figures, data)
#   2. teralizer-core.zip                      - Verification package (code, database dumps)
#   3. teralizer-projects-primary.zip          - Primary dataset projects
#   4. teralizer-projects-extended-sample.zip  - Sampled extended projects
#   5. teralizer-projects-extended.zip         - Full extended dataset
#   6. teralizer-data-primary.zip              - Primary dataset logs and tool reports
#   7. teralizer-data-extended.zip             - Extended dataset logs and tool reports
#
# USAGE:
#   ./scripts/prepare-zenodo-package.sh [OPTIONS]
#
# OPTIONS:
#   --output-dir DIR         Output directory for archives (default: ~/zenodo-upload)
#   --projects-primary DIR   Primary projects directory
#   --projects-extended DIR  Extended projects directory
#   --data-primary DIR       Primary data directory (logs, tool reports)
#   --data-extended DIR      Extended data directory (logs, tool reports)
#   --sample-size N          Number of projects in extended sample (default: 100)
#   --version VERSION        Version suffix for archive names (default: none)
#   --skip-extended-full     Skip creating the full extended archive
#   --dry-run                Show what would be created without doing it
#   --help                   Show this help message
#
# IMPORTANT:
#   This script creates CLEANED COPIES of project directories for packaging.
#   Original directories are never modified - all build artifacts are preserved locally.
#
# EXAMPLES:
#   # Dry run to preview what would be created
#   ./scripts/prepare-zenodo-package.sh --dry-run \
#       --projects-primary ~/Projects/test-generalization/projects \
#       --projects-extended ~/Projects/test-generalization-dev/projects \
#       --data-primary ~/Projects/test-generalization/data \
#       --data-extended ~/Projects/test-generalization-dev/data
#
#   # Create all archives except full extended (saves time/space)
#   ./scripts/prepare-zenodo-package.sh \
#       --projects-primary ~/Projects/test-generalization/projects \
#       --projects-extended ~/Projects/test-generalization-dev/projects \
#       --data-primary ~/Projects/test-generalization/data \
#       --data-extended ~/Projects/test-generalization-dev/data \
#       --skip-extended-full
#
#   # Create only results and core archives (no projects or data)
#   ./scripts/prepare-zenodo-package.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Defaults
OUTPUT_DIR="$HOME/zenodo-upload"
PROJECTS_PRIMARY=""
PROJECTS_EXTENDED=""
DATA_PRIMARY=""
DATA_EXTENDED=""
SAMPLE_SIZE=100
VERSION=""
SKIP_EXTENDED_FULL=false
DRY_RUN=false

usage() {
    sed -n '2,48p' "$0"
    exit 0
}

log_step() {
    echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $1"
}

log_success() {
    echo -e "  ${GREEN}✓${NC} $1"
}

log_warning() {
    echo -e "  ${YELLOW}!${NC} $1"
}

log_error() {
    echo -e "  ${RED}✗${NC} $1"
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --projects-primary) PROJECTS_PRIMARY="$2"; shift 2 ;;
        --projects-extended) PROJECTS_EXTENDED="$2"; shift 2 ;;
        --data-primary) DATA_PRIMARY="$2"; shift 2 ;;
        --data-extended) DATA_EXTENDED="$2"; shift 2 ;;
        --sample-size) SAMPLE_SIZE="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --skip-extended-full) SKIP_EXTENDED_FULL=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        --help|-h) usage ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; usage ;;
    esac
done

# Validate required paths exist
if [[ ! -d "$REPO_ROOT/analysis/output/original" ]]; then
    log_error "Original outputs not found at $REPO_ROOT/analysis/output/original"
    log_error "Run notebooks first to generate outputs."
    exit 1
fi

# Build version suffix (empty if no version specified)
if [[ -n "$VERSION" ]]; then
    VERSION_SUFFIX="-v${VERSION}"
else
    VERSION_SUFFIX=""
fi

echo ""
echo "=========================================="
echo "  Zenodo Package Preparation"
if [[ -n "$VERSION" ]]; then
    echo "  Version: $VERSION"
fi
echo "=========================================="
echo ""

if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "${YELLOW}DRY RUN - No files will be created${NC}"
    echo ""
fi

# Create output directory
if [[ "$DRY_RUN" == "false" ]]; then
    mkdir -p "$OUTPUT_DIR"
fi

# Temporary directory for staging
STAGING_DIR=$(mktemp -d)
trap "rm -rf $STAGING_DIR" EXIT

# ----------------------------------------------------------------------------
# Archive 1: Results Only
# ----------------------------------------------------------------------------
log_step "Creating Archive 1: Results Only"

RESULTS_NAME="teralizer-results${VERSION_SUFFIX}"
RESULTS_DIR="$STAGING_DIR/$RESULTS_NAME"

if [[ "$DRY_RUN" == "false" ]]; then
    mkdir -p "$RESULTS_DIR"

    # Copy tables, figures, data
    cp -r "$REPO_ROOT/analysis/output/original/tables" "$RESULTS_DIR/"
    cp -r "$REPO_ROOT/analysis/output/original/figures" "$RESULTS_DIR/"
    cp -r "$REPO_ROOT/analysis/output/original/data" "$RESULTS_DIR/"

    # Copy HTML notebooks if they exist
    if [[ -d "$REPO_ROOT/analysis/output/original/html" ]]; then
        cp -r "$REPO_ROOT/analysis/output/original/html" "$RESULTS_DIR/"
    else
        log_warning "HTML notebooks not found - run notebook export first"
    fi

    # Create archive
    (cd "$STAGING_DIR" && zip -rq "$OUTPUT_DIR/${RESULTS_NAME}.zip" "$RESULTS_NAME")

    RESULTS_SIZE=$(du -sh "$OUTPUT_DIR/${RESULTS_NAME}.zip" | cut -f1)
    log_success "Created ${RESULTS_NAME}.zip ($RESULTS_SIZE)"
else
    log_success "Would create ${RESULTS_NAME}.zip"
fi

# ----------------------------------------------------------------------------
# Archive 2: Verification Package (Core)
# ----------------------------------------------------------------------------
log_step "Creating Archive 2: Verification Package"

CORE_NAME="teralizer-core${VERSION_SUFFIX}"
CORE_DIR="$STAGING_DIR/$CORE_NAME"

if [[ "$DRY_RUN" == "false" ]]; then
    mkdir -p "$CORE_DIR"

    # Clone repo fresh to get clean state (include submodules like jpf-symbc)
    log_step "  Cloning repository..."
    git clone --quiet --recurse-submodules "$REPO_ROOT" "$CORE_DIR/repo-temp"

    # Resolve LFS files
    log_step "  Resolving LFS files..."
    (cd "$CORE_DIR/repo-temp" && git lfs pull 2>/dev/null)

    # Move contents up (excluding .git)
    shopt -s dotglob
    mv "$CORE_DIR/repo-temp"/* "$CORE_DIR/" 2>/dev/null || true
    shopt -u dotglob
    rm -rf "$CORE_DIR/repo-temp"
    rm -rf "$CORE_DIR/.git"

    # Remove unnecessary files for verification
    rm -rf "$CORE_DIR/projects" 2>/dev/null || true
    rm -rf "$CORE_DIR/data" 2>/dev/null || true
    rm -rf "$CORE_DIR/.idea" 2>/dev/null || true
    rm -f "$CORE_DIR/.env" 2>/dev/null || true

    # Ensure output/original exists with reference outputs
    mkdir -p "$CORE_DIR/analysis/output/original"
    cp -r "$REPO_ROOT/analysis/output/original/tables" "$CORE_DIR/analysis/output/original/"
    cp -r "$REPO_ROOT/analysis/output/original/figures" "$CORE_DIR/analysis/output/original/"
    cp -r "$REPO_ROOT/analysis/output/original/data" "$CORE_DIR/analysis/output/original/"

    # Create archive
    (cd "$STAGING_DIR" && zip -rq "$OUTPUT_DIR/${CORE_NAME}.zip" "$CORE_NAME")

    CORE_SIZE=$(du -sh "$OUTPUT_DIR/${CORE_NAME}.zip" | cut -f1)
    log_success "Created ${CORE_NAME}.zip ($CORE_SIZE)"
else
    log_success "Would create ${CORE_NAME}.zip"
fi

# ----------------------------------------------------------------------------
# Archive 3: Primary Projects
# Archive extracts directly as projects/ for easy merging with core
# ----------------------------------------------------------------------------
if [[ -n "$PROJECTS_PRIMARY" ]] && [[ -d "$PROJECTS_PRIMARY" ]]; then
    log_step "Creating Archive 3: Primary Projects"

    PRIMARY_NAME="teralizer-projects-primary${VERSION_SUFFIX}"
    PRIMARY_DIR="$STAGING_DIR/primary-staging/projects"

    if [[ "$DRY_RUN" == "false" ]]; then
        mkdir -p "$PRIMARY_DIR"

        # Copy primary projects (commons-utils*, eqbench*)
        for project in "$PROJECTS_PRIMARY"/commons-utils* "$PROJECTS_PRIMARY"/eqbench*; do
            if [[ -d "$project" ]]; then
                project_name=$(basename "$project")
                log_step "  Copying $project_name (cleaned)..."

                # Copy excluding build artifacts
                rsync -a \
                    --exclude='target/' \
                    --exclude='.git/' \
                    --exclude='*.class' \
                    --exclude='*.jar' \
                    "$project/" "$PRIMARY_DIR/$project_name/"
            fi
        done

        # Create archive (extracts as projects/)
        (cd "$STAGING_DIR/primary-staging" && zip -rq "$OUTPUT_DIR/${PRIMARY_NAME}.zip" projects)

        PRIMARY_SIZE=$(du -sh "$OUTPUT_DIR/${PRIMARY_NAME}.zip" | cut -f1)
        log_success "Created ${PRIMARY_NAME}.zip ($PRIMARY_SIZE)"
    else
        log_success "Would create ${PRIMARY_NAME}.zip"
    fi
else
    log_warning "Skipping primary projects (--projects-primary not specified or not found)"
fi

# ----------------------------------------------------------------------------
# Archive 4: Extended Sample
# Archive extracts directly as projects/ for easy merging with core
# ----------------------------------------------------------------------------
if [[ -n "$PROJECTS_EXTENDED" ]] && [[ -d "$PROJECTS_EXTENDED" ]]; then
    log_step "Creating Archive 4: Extended Sample ($SAMPLE_SIZE projects)"

    SAMPLE_NAME="teralizer-projects-extended-sample${VERSION_SUFFIX}"
    SAMPLE_DIR="$STAGING_DIR/sample-staging/projects"

    if [[ "$DRY_RUN" == "false" ]]; then
        mkdir -p "$SAMPLE_DIR"

        # Get list of extended projects and sample
        EXTENDED_PROJECTS=("$PROJECTS_EXTENDED"/github_com_*)
        TOTAL_EXTENDED=${#EXTENDED_PROJECTS[@]}

        if [[ $TOTAL_EXTENDED -gt 0 ]]; then
            # Sample projects (deterministic using sort)
            SAMPLE_COUNT=$((SAMPLE_SIZE < TOTAL_EXTENDED ? SAMPLE_SIZE : TOTAL_EXTENDED))

            # Use a deterministic sampling: every Nth project
            STEP=$((TOTAL_EXTENDED / SAMPLE_COUNT))
            [[ $STEP -lt 1 ]] && STEP=1

            COUNT=0
            for ((i=0; i<TOTAL_EXTENDED && COUNT<SAMPLE_COUNT; i+=STEP)); do
                project="${EXTENDED_PROJECTS[$i]}"
                project_name=$(basename "$project")

                # Copy excluding build artifacts
                rsync -a \
                    --exclude='target/' \
                    --exclude='.git/' \
                    --exclude='*.class' \
                    --exclude='*.jar' \
                    "$project/" "$SAMPLE_DIR/$project_name/"

                ((COUNT++)) || true
            done

            log_step "  Sampled $COUNT of $TOTAL_EXTENDED projects"
        fi

        # Create archive (extracts as projects/)
        (cd "$STAGING_DIR/sample-staging" && zip -rq "$OUTPUT_DIR/${SAMPLE_NAME}.zip" projects)

        SAMPLE_SIZE_DISK=$(du -sh "$OUTPUT_DIR/${SAMPLE_NAME}.zip" | cut -f1)
        log_success "Created ${SAMPLE_NAME}.zip ($SAMPLE_SIZE_DISK)"
    else
        log_success "Would create ${SAMPLE_NAME}.zip"
    fi
else
    log_warning "Skipping extended sample (--projects-extended not specified or not found)"
fi

# ----------------------------------------------------------------------------
# Archive 5: Full Extended
# Archive extracts directly as projects/ for easy merging with core
# ----------------------------------------------------------------------------
if [[ -n "$PROJECTS_EXTENDED" ]] && [[ -d "$PROJECTS_EXTENDED" ]] && [[ "$SKIP_EXTENDED_FULL" == "false" ]]; then
    log_step "Creating Archive 5: Full Extended (this may take a while)"

    FULL_NAME="teralizer-projects-extended${VERSION_SUFFIX}"
    FULL_DIR="$STAGING_DIR/full-staging/projects"

    if [[ "$DRY_RUN" == "false" ]]; then
        mkdir -p "$FULL_DIR"

        # Count total projects for progress reporting
        FULL_PROJECTS=("$PROJECTS_EXTENDED"/github_com_*)
        FULL_TOTAL=${#FULL_PROJECTS[@]}
        FULL_COUNT=0
        FULL_PROGRESS_INTERVAL=100

        # Copy all extended projects with progress
        for project in "${FULL_PROJECTS[@]}"; do
            if [[ -d "$project" ]]; then
                project_name=$(basename "$project")

                # Copy excluding build artifacts
                rsync -a \
                    --exclude='target/' \
                    --exclude='.git/' \
                    --exclude='*.class' \
                    --exclude='*.jar' \
                    "$project/" "$FULL_DIR/$project_name/"

                ((FULL_COUNT++)) || true

                # Report progress every N projects
                if [[ $((FULL_COUNT % FULL_PROGRESS_INTERVAL)) -eq 0 ]]; then
                    echo -e "  ${CYAN}Progress:${NC} $FULL_COUNT / $FULL_TOTAL projects copied"
                fi
            fi
        done
        echo -e "  ${CYAN}Progress:${NC} $FULL_COUNT / $FULL_TOTAL projects copied"

        # Create archive (extracts as projects/)
        log_step "  Compressing archive..."
        (cd "$STAGING_DIR/full-staging" && zip -rq "$OUTPUT_DIR/${FULL_NAME}.zip" projects)

        FULL_SIZE=$(du -sh "$OUTPUT_DIR/${FULL_NAME}.zip" | cut -f1)
        log_success "Created ${FULL_NAME}.zip ($FULL_SIZE)"
    else
        log_success "Would create ${FULL_NAME}.zip"
    fi
elif [[ "$SKIP_EXTENDED_FULL" == "true" ]]; then
    log_warning "Skipping full extended archive (--skip-extended-full)"
else
    log_warning "Skipping full extended (--projects-extended not specified)"
fi

# ----------------------------------------------------------------------------
# Archive 6: Primary Data (logs, tool reports, generalized tests)
# Archive extracts directly as data/ for easy merging with core
# ----------------------------------------------------------------------------
if [[ -n "$DATA_PRIMARY" ]] && [[ -d "$DATA_PRIMARY" ]]; then
    log_step "Creating Archive 6: Primary Data"

    DATA_PRIMARY_NAME="teralizer-data-primary${VERSION_SUFFIX}"
    DATA_PRIMARY_DIR="$STAGING_DIR/data-primary-staging/data"

    if [[ "$DRY_RUN" == "false" ]]; then
        mkdir -p "$DATA_PRIMARY_DIR"

        # Copy data directory contents
        log_step "  Copying primary data (this may take a while)..."
        rsync -a "$DATA_PRIMARY/" "$DATA_PRIMARY_DIR/"

        # Create archive (extracts as data/)
        log_step "  Compressing archive..."
        (cd "$STAGING_DIR/data-primary-staging" && zip -rq "$OUTPUT_DIR/${DATA_PRIMARY_NAME}.zip" data)

        DATA_PRIMARY_SIZE=$(du -sh "$OUTPUT_DIR/${DATA_PRIMARY_NAME}.zip" | cut -f1)
        log_success "Created ${DATA_PRIMARY_NAME}.zip ($DATA_PRIMARY_SIZE)"
    else
        log_success "Would create ${DATA_PRIMARY_NAME}.zip"
    fi
else
    log_warning "Skipping primary data (--data-primary not specified or not found)"
fi

# ----------------------------------------------------------------------------
# Archive 7: Extended Data (logs, tool reports, generalized tests)
# Archive extracts directly as data/ for easy merging with core
# ----------------------------------------------------------------------------
if [[ -n "$DATA_EXTENDED" ]] && [[ -d "$DATA_EXTENDED" ]]; then
    log_step "Creating Archive 7: Extended Data"

    DATA_EXTENDED_NAME="teralizer-data-extended${VERSION_SUFFIX}"
    DATA_EXTENDED_DIR="$STAGING_DIR/data-extended-staging/data"

    if [[ "$DRY_RUN" == "false" ]]; then
        mkdir -p "$DATA_EXTENDED_DIR"

        # Copy data directory contents
        log_step "  Copying extended data (this may take a while)..."
        rsync -a "$DATA_EXTENDED/" "$DATA_EXTENDED_DIR/"

        # Create archive (extracts as data/)
        log_step "  Compressing archive..."
        (cd "$STAGING_DIR/data-extended-staging" && zip -rq "$OUTPUT_DIR/${DATA_EXTENDED_NAME}.zip" data)

        DATA_EXTENDED_SIZE=$(du -sh "$OUTPUT_DIR/${DATA_EXTENDED_NAME}.zip" | cut -f1)
        log_success "Created ${DATA_EXTENDED_NAME}.zip ($DATA_EXTENDED_SIZE)"
    else
        log_success "Would create ${DATA_EXTENDED_NAME}.zip"
    fi
else
    log_warning "Skipping extended data (--data-extended not specified or not found)"
fi

# ----------------------------------------------------------------------------
# Generate checksums
# ----------------------------------------------------------------------------
if [[ "$DRY_RUN" == "false" ]]; then
    log_step "Generating checksums"

    (cd "$OUTPUT_DIR" && shasum -a 256 *.zip > checksums.sha256)
    log_success "Created checksums.sha256"
fi

# ----------------------------------------------------------------------------
# Summary
# ----------------------------------------------------------------------------
echo ""
echo "=========================================="
echo "  Summary"
echo "=========================================="
echo ""

if [[ "$DRY_RUN" == "false" ]]; then
    echo "Archives created in: $OUTPUT_DIR"
    echo ""
    ls -lh "$OUTPUT_DIR"/*.zip 2>/dev/null || true
    echo ""
    echo "Checksums:"
    cat "$OUTPUT_DIR/checksums.sha256" 2>/dev/null || true
else
    echo "Dry run complete. Use without --dry-run to create archives."
fi

echo ""
echo "=========================================="
