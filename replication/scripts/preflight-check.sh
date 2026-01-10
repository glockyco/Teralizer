#!/usr/bin/env bash
# Preflight checks for Teralizer replication package.
#
# Verifies:
#   - Docker is installed and running
#   - Docker Compose is available
#   - Sufficient disk space (10GB minimum)
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

# Check disk space
echo "Disk Space:"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if command -v df &> /dev/null; then
    # Get available space in KB, then convert to GB (-P for POSIX output format)
    available_kb=$(df -Pk "$SCRIPT_DIR" 2>/dev/null | awk 'NR==2 {print $4}')
    available_gb=$((available_kb / 1024 / 1024))

    if [[ $available_gb -ge 10 ]]; then
        check_pass "Available disk space: ${available_gb}GB (minimum 10GB)"
    elif [[ $available_gb -ge 5 ]]; then
        check_warn "Available disk space: ${available_gb}GB (10GB recommended, may be tight)"
    else
        check_fail "Available disk space: ${available_gb}GB (minimum 10GB required)"
    fi
else
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

check_port 5432 "PostgreSQL"
check_port 18080 "Adminer"
check_port 8888 "Jupyter"
echo ""

# Check for database dumps
echo "Database Dumps:"
DUMPS_DIR="$(dirname "$SCRIPT_DIR")/datasets"
if [[ -f "$DUMPS_DIR/postgres_dev.dump" ]]; then
    size=$(du -h "$DUMPS_DIR/postgres_dev.dump" | cut -f1)
    check_pass "postgres_dev.dump found ($size)"
else
    check_fail "postgres_dev.dump not found in datasets/"
fi

if [[ -f "$DUMPS_DIR/postgres_test.dump" ]]; then
    size=$(du -h "$DUMPS_DIR/postgres_test.dump" | cut -f1)
    check_pass "postgres_test.dump found ($size)"
else
    check_fail "postgres_test.dump not found in datasets/"
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
