#!/usr/bin/env bash
# Compare analysis outputs between variants.
#
# This script compares outputs from different analysis runs to verify
# that results can be reproduced. It checks file counts and content
# differences between variant directories.
#
# USAGE:
#   ./verify-outputs.sh <base> <target>
#
# ARGUMENTS:
#   base    Base variant to compare against (original, verify, replicate)
#   target  Target variant to compare (original, verify, replicate)
#
# EXAMPLES:
#   ./verify-outputs.sh original verify      # Verify re-run matches original
#   ./verify-outputs.sh original replicate   # Compare replication results
#
# EXPECTED RESULTS:
#   original vs verify:    Should be IDENTICAL (same data, same analysis)
#   original vs replicate: May differ (EvoSuite uses random search)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/analysis/output"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

usage() {
    head -22 "$0" | tail -20
    exit 1
}

# Validate arguments
if [[ $# -lt 2 ]]; then
    echo -e "${RED}Error: Missing arguments${NC}"
    echo ""
    usage
fi

BASE="$1"
TARGET="$2"

BASE_DIR="$OUTPUT_DIR/$BASE"
TARGET_DIR="$OUTPUT_DIR/$TARGET"

# Validate directories exist
if [[ ! -d "$BASE_DIR" ]]; then
    echo -e "${RED}Error: Base directory does not exist: $BASE_DIR${NC}"
    exit 1
fi

if [[ ! -d "$TARGET_DIR" ]]; then
    echo -e "${RED}Error: Target directory does not exist: $TARGET_DIR${NC}"
    exit 1
fi

echo ""
echo "=========================================="
echo "  Output Comparison"
echo "  Base:   $BASE"
echo "  Target: $TARGET"
echo "=========================================="
echo ""

errors=0

# Compare file counts in a subdirectory
compare_subdir() {
    local subdir="$1"
    local pattern="$2"
    local base_path="$BASE_DIR/$subdir"
    local target_path="$TARGET_DIR/$subdir"

    local base_count=0
    local target_count=0

    if [[ -d "$base_path" ]]; then
        base_count=$(find "$base_path" -name "$pattern" -type f 2>/dev/null | wc -l | tr -d ' ')
    fi

    if [[ -d "$target_path" ]]; then
        target_count=$(find "$target_path" -name "$pattern" -type f 2>/dev/null | wc -l | tr -d ' ')
    fi

    if [[ "$base_count" -eq "$target_count" ]]; then
        echo -e "  ${GREEN}✓${NC} $subdir: $base_count files (match)"
        return 0
    else
        echo -e "  ${YELLOW}!${NC} $subdir: $base_count vs $target_count files"
        return 1
    fi
}

# Compare content of text files
compare_content() {
    local subdir="$1"
    local pattern="$2"
    local base_path="$BASE_DIR/$subdir"
    local target_path="$TARGET_DIR/$subdir"

    if [[ ! -d "$base_path" ]] || [[ ! -d "$target_path" ]]; then
        return 0
    fi

    local diff_count=0
    local total=0

    # Compare each file in base directory
    while IFS= read -r -d '' base_file; do
        local rel_path="${base_file#$base_path/}"
        local target_file="$target_path/$rel_path"
        ((total++))

        if [[ ! -f "$target_file" ]]; then
            echo -e "    ${RED}Missing:${NC} $rel_path"
            ((diff_count++))
        elif ! diff -q <(sed 's/[[:space:]]*$//' "$base_file") <(sed 's/[[:space:]]*$//' "$target_file") > /dev/null 2>&1; then
            echo -e "    ${YELLOW}Differs:${NC} $rel_path"
            ((diff_count++))
        fi
    done < <(find "$base_path" -name "$pattern" -type f -print0 2>/dev/null)

    # Check for extra files in target
    while IFS= read -r -d '' target_file; do
        local rel_path="${target_file#$target_path/}"
        local base_file="$base_path/$rel_path"

        if [[ ! -f "$base_file" ]]; then
            echo -e "    ${CYAN}Extra:${NC} $rel_path"
            ((diff_count++))
        fi
    done < <(find "$target_path" -name "$pattern" -type f -print0 2>/dev/null)

    if [[ $diff_count -eq 0 ]]; then
        echo -e "  ${GREEN}✓${NC} $subdir content: all $total files identical"
    else
        echo -e "  ${YELLOW}!${NC} $subdir content: $diff_count differences"
    fi

    return $diff_count
}

echo -e "${CYAN}File Counts:${NC}"
compare_subdir "tables" "*.tex" || ((errors++))
compare_subdir "data" "*.csv" || ((errors++))
compare_subdir "figures" "*.pdf" || ((errors++))
echo ""

echo -e "${CYAN}Content Comparison (tables):${NC}"
compare_content "tables" "*.tex" || ((errors++))
echo ""

echo -e "${CYAN}Content Comparison (data):${NC}"
compare_content "data" "*.csv" || ((errors++))
echo ""

# Summary
echo "=========================================="
if [[ $errors -eq 0 ]]; then
    echo -e "${GREEN}All outputs match${NC}"
else
    echo -e "${YELLOW}$errors category/categories have differences${NC}"

    # Provide context based on comparison type
    if [[ "$BASE" == "original" ]] && [[ "$TARGET" == "verify" ]]; then
        echo ""
        echo "Note: original vs verify should be identical."
        echo "Any differences indicate a problem with the analysis."
    elif [[ "$BASE" == "original" ]] && [[ "$TARGET" == "replicate" ]]; then
        echo ""
        echo "Note: original vs replicate may differ due to:"
        echo "  - EvoSuite uses randomized search algorithms"
        echo "  - Timeout-dependent processing varies by machine"
        echo "  - Only developer-written tests (commons-utils-dev) are deterministic"
    fi
fi
echo "=========================================="
echo ""

# Exit code depends on comparison type
if [[ $errors -gt 0 ]] && [[ "$BASE" == "original" ]] && [[ "$TARGET" == "verify" ]]; then
    # original vs verify should be identical - differences are failures
    exit 1
fi

exit 0
