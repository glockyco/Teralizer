#!/usr/bin/env bash
# Preflight checks for Teralizer replication package.
#
# Verifies:
#   - Docker is installed and running
#   - Docker Compose is available
#   - Free disk space required by the verified corpus manifest
#   - Ports are available
#
# Usage:
#   ./preflight-check.sh
#
# Exit codes:
#   0 - All checks passed
#   1 - One or more checks failed

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    ERRORS=$((ERRORS + 1))
}

check_warn() {
    echo -e "${YELLOW}!${NC} $1"
}

echo "Running preflight checks..."
echo ""

# Check Docker
echo "Docker:"
if command -v docker &> /dev/null; then
    check_pass "Docker is installed"

    if docker info &> /dev/null; then
        check_pass "Docker daemon is running"

        # Check Docker version
        docker_version=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo "unknown")
        check_pass "Docker version: $docker_version"
    else
        check_fail "Docker daemon is not running (start Docker Desktop or dockerd)"
    fi
else
    check_fail "Docker is not installed"
fi
echo ""

# Check Docker Compose
echo "Docker Compose:"
if docker compose version &> /dev/null; then
    compose_version=$(docker compose version --short 2>/dev/null || echo "unknown")
    check_pass "Docker Compose is available (v$compose_version)"
else
    check_fail "Docker Compose is not available"
fi
echo ""

# Verify the package once. Reuse its inventory for disk and dump checks.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
DUMPS_DIR="$SCRIPT_DIR/../datasets"
package_facts=""
package_summary=""
required_bytes=0
if package_facts=$(uv run --frozen --directory "$REPO_ROOT/analysis" python -m teralizer.corpus_publish \
    --preflight-package "$DUMPS_DIR"); then
    required_line=${package_facts%%$'\n'*}
    required_bytes=${required_line#required_disk_bytes=}
    package_summary=${package_facts#*$'\n'}
else
    check_fail "corpus package manifest or declared files failed verification"
fi

# Check disk space against the manifest's dump plus restored-database requirement.
echo "Disk Space:"
if command -v df &> /dev/null && [[ "$required_bytes" -gt 0 ]]; then
    available_kb=$(df -Pk "$SCRIPT_DIR" 2>/dev/null | awk 'NR==2 {print $4}')
    available_bytes=$((available_kb * 1024))
    available_gb=$((available_kb / 1024 / 1024))
    required_gb=$(((required_bytes + 1024 * 1024 * 1024 - 1) / 1024 / 1024 / 1024))
    if [[ $available_bytes -ge $required_bytes ]]; then
        check_pass "Available disk space: ${available_gb}GB (manifest requires ${required_gb}GB)"
    else
        check_fail "Available disk space: ${available_gb}GB (manifest requires ${required_gb}GB)"
    fi
elif [[ "$required_bytes" -gt 0 ]]; then
    check_warn "Could not check disk space"
fi
echo ""

# Check ports
echo "Port Availability:"
port_in_use() {
    local port=$1
    if command -v lsof &>/dev/null && lsof -i ":$port" &>/dev/null; then
        return 0
    elif command -v nc &>/dev/null && nc -z localhost "$port" 2>/dev/null; then
        return 0
    elif command -v ss &>/dev/null && ss -tln 2>/dev/null | grep -q ":$port "; then
        return 0
    fi
    return 1
}

check_port() {
    local port=$1
    local service=$2
    if ! port_in_use "$port"; then
        check_pass "Port $port is available ($service)"
    else
        check_fail "Port $port is in use ($service needs this port)"
    fi
}

check_port "${REPLICATION_DB_PORT:-5432}" "PostgreSQL"
check_port "${REPLICATION_ADMINER_PORT:-18080}" "Adminer"
echo ""

# Verify the manifest-bound corpus dumps and report their declared byte sizes.
echo "Database Dumps:"
if [[ -n "$package_summary" ]]; then
    while IFS= read -r line; do
        check_pass "$line"
    done <<< "$package_summary"
fi
echo ""

# Summary
echo "=========================================="
if [[ $ERRORS -eq 0 ]]; then
    echo -e "${GREEN}All preflight checks passed!${NC}"
    exit 0
else
    echo -e "${RED}$ERRORS check(s) failed${NC}"
    echo "Please fix the issues above before continuing."
    exit 1
fi
