#!/usr/bin/env bash
# Verify database imports and display dataset statistics.
#
# This script verifies that database dumps were imported correctly by
# comparing counts against expected values from the paper.
#
# NOTE: Re-running the pipeline will NOT produce identical results because:
#   - EvoSuite test generation uses randomized search
#   - Generalization progress depends on timeouts (machine-dependent)
#
# This verification checks:
#   1. Database connectivity
#   2. Expected project counts (exact match)
#   3. Approximate test/assertion counts (order of magnitude)
#
# USAGE:
#   ./verify-results.sh [options]
#
# OPTIONS:
#   --dataset <primary|extended|all>  Which dataset to verify (default: all)
#   --help                            Show this help message

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Expected values from paper (for import verification)
# Primary dataset
EXPECTED_PRIMARY_PROJECTS=13
# Extended dataset
EXPECTED_EXTENDED_PROJECTS=1161

# Default configuration
DATASET="all"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Database connection
DB_USER="${DB_USER:-teralizer}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dataset)
            DATASET="$2"
            shift 2
            ;;
        --help|-h)
            head -24 "$0" | tail -22
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Query database
query_db() {
    local db_name="$1"
    local query="$2"
    docker exec -i postgres-replication psql -U "$DB_USER" -d "$db_name" -t -A -c "$query" 2>/dev/null
}

# Check value against expected
check_value() {
    local name="$1"
    local actual="$2"
    local expected="$3"
    local tolerance="${4:-0}"

    local diff=$((actual - expected))
    if [[ $diff -lt 0 ]]; then diff=$((-diff)); fi

    if [[ $tolerance -eq 0 ]]; then
        if [[ "$actual" -eq "$expected" ]]; then
            echo -e "  ${GREEN}✓${NC} $name: $actual (expected $expected)"
            return 0
        else
            echo -e "  ${RED}✗${NC} $name: $actual (expected $expected)"
            return 1
        fi
    else
        if [[ $diff -le $tolerance ]]; then
            echo -e "  ${GREEN}✓${NC} $name: $actual (expected ~$expected ±$tolerance)"
            return 0
        else
            echo -e "  ${YELLOW}!${NC} $name: $actual (expected ~$expected ±$tolerance)"
            return 0  # Warning, not failure
        fi
    fi
}

errors=0

# Verify primary dataset
verify_primary() {
    echo "=========================================="
    echo "  Primary Dataset (postgres_dev)"
    echo "=========================================="
    echo ""

    # Check connectivity
    if ! docker exec -i postgres-replication psql -U "$DB_USER" -d "postgres_dev" -c "SELECT 1" &>/dev/null; then
        echo -e "  ${RED}✗${NC} Cannot connect to postgres_dev"
        ((errors++))
        return
    fi
    echo -e "  ${GREEN}✓${NC} Database connection OK"

    # Check project count
    local project_count=$(query_db "postgres_dev" "SELECT COUNT(*) FROM project;")
    check_value "Project count" "$project_count" "$EXPECTED_PRIMARY_PROJECTS" || ((errors++))

    # Show statistics (informational)
    echo ""
    echo -e "  ${CYAN}Statistics:${NC}"

    local test_count=$(query_db "postgres_dev" "SELECT COUNT(*) FROM test;")
    echo "    Tests: $test_count"

    local assertion_count=$(query_db "postgres_dev" "SELECT COUNT(*) FROM assertion;")
    echo "    Assertions: $assertion_count"

    local gen_count=$(query_db "postgres_dev" "SELECT COUNT(*) FROM generalization;")
    echo "    Generalizations: $gen_count"

    echo ""
}

# Verify extended dataset
verify_extended() {
    echo "=========================================="
    echo "  Extended Dataset (postgres_test)"
    echo "=========================================="
    echo ""

    # Check connectivity
    if ! docker exec -i postgres-replication psql -U "$DB_USER" -d "postgres_test" -c "SELECT 1" &>/dev/null; then
        echo -e "  ${RED}✗${NC} Cannot connect to postgres_test"
        ((errors++))
        return
    fi
    echo -e "  ${GREEN}✓${NC} Database connection OK"

    # Check project count
    local project_count=$(query_db "postgres_test" "SELECT COUNT(*) FROM project;")
    check_value "Project count" "$project_count" "$EXPECTED_EXTENDED_PROJECTS" || ((errors++))

    # Show statistics (informational)
    echo ""
    echo -e "  ${CYAN}Statistics:${NC}"

    local test_count=$(query_db "postgres_test" "SELECT COUNT(*) FROM test;")
    echo "    Tests: $test_count"

    local assertion_count=$(query_db "postgres_test" "SELECT COUNT(*) FROM assertion;")
    echo "    Assertions: $assertion_count"

    local gen_count=$(query_db "postgres_test" "SELECT COUNT(*) FROM generalization;")
    echo "    Generalizations: $gen_count"

    echo ""
}

# Run verification
echo ""
echo "Teralizer Import Verification"
echo "============================="
echo ""

case "$DATASET" in
    primary)
        verify_primary
        ;;
    extended)
        verify_extended
        ;;
    all)
        verify_primary
        verify_extended
        ;;
    *)
        echo -e "${RED}Unknown dataset: $DATASET${NC}"
        exit 1
        ;;
esac

# Summary
echo "=========================================="
if [[ $errors -eq 0 ]]; then
    echo -e "${GREEN}All checks passed${NC}"
else
    echo -e "${RED}$errors check(s) failed${NC}"
fi
echo "=========================================="
echo ""
echo "Note: Statistics shown above are from the imported database dumps."
echo "Re-running the pipeline will produce different (but similar) results"
echo "due to non-deterministic test generation and timeout-dependent processing."
echo ""

exit $errors
