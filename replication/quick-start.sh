#!/usr/bin/env bash
# Quick start script for Teralizer replication package.
#
# This script sets up everything needed to inspect data and re-run analysis:
#   1. Runs preflight checks (Docker, resources)
#   2. Starts PostgreSQL and Adminer
#   3. Imports database dumps
#   4. Starts Jupyter Lab
#   5. Opens browser to Jupyter
#
# Usage:
#   ./quick-start.sh
#
# After running, access:
#   - Jupyter Lab: http://localhost:8888
#   - Adminer (DB UI): http://localhost:18080

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
DB_USER="${DB_USER:-teralizer}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "  Teralizer Replication Package Setup"
echo "=========================================="
echo ""

# Step 0: Extract project archives if present as siblings
echo -e "${YELLOW}[0/5]${NC} Checking for project archives..."
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PARENT_DIR="$(cd "$REPO_ROOT/.." && pwd)"
FOUND_ARCHIVES=0

for archive in "$PARENT_DIR"/teralizer-projects-*.zip; do
    [[ -f "$archive" ]] || continue
    archive_name=$(basename "$archive")

    # Check if projects/ already exists with content
    if [[ -d "$REPO_ROOT/projects" ]] && [[ -n "$(ls -A "$REPO_ROOT/projects" 2>/dev/null)" ]]; then
        echo -e "  ${YELLOW}!${NC} projects/ already exists, skipping $archive_name"
        continue
    fi

    echo "  Extracting $archive_name..."
    unzip -q "$archive" -d "$REPO_ROOT"
    FOUND_ARCHIVES=$((FOUND_ARCHIVES + 1))
done

if [[ $FOUND_ARCHIVES -gt 0 ]]; then
    echo -e "${GREEN}Extracted $FOUND_ARCHIVES project archive(s)${NC}"
elif [[ -d "$REPO_ROOT/projects" ]]; then
    echo -e "${GREEN}projects/ directory already present${NC}"
else
    echo -e "${YELLOW}No project archives found (pipeline will not be runnable)${NC}"
fi
echo ""

# Step 1: Preflight checks
echo -e "${YELLOW}[1/5]${NC} Running preflight checks..."
if [[ -x scripts/preflight-check.sh ]]; then
    if ! scripts/preflight-check.sh; then
        echo -e "${RED}Preflight checks failed. Please fix the issues above.${NC}"
        exit 1
    fi
else
    # Minimal inline checks if preflight script doesn't exist
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Error: Docker is not installed${NC}"
        exit 1
    fi
    if ! docker info &> /dev/null; then
        echo -e "${RED}Error: Docker daemon is not running${NC}"
        exit 1
    fi
    echo -e "${GREEN}Docker is available${NC}"
fi
echo ""

# Step 2: Check for database dumps
echo -e "${YELLOW}[2/5]${NC} Checking for database dumps..."
if [[ ! -f datasets/postgres_dev.dump ]] || [[ ! -f datasets/postgres_test.dump ]]; then
    echo -e "${RED}Error: Database dumps not found in datasets/${NC}"
    echo "Expected files:"
    echo "  - datasets/postgres_dev.dump"
    echo "  - datasets/postgres_test.dump"
    echo ""
    echo "Please ensure you have extracted the complete replication package."
    exit 1
fi
echo -e "${GREEN}Database dumps found${NC}"
echo ""

# Step 3: Start PostgreSQL and Adminer
echo -e "${YELLOW}[3/5]${NC} Starting PostgreSQL and Adminer..."
docker compose up -d postgres adminer

# Wait for PostgreSQL to be healthy
echo "Waiting for PostgreSQL to be ready..."
for i in {1..30}; do
    if docker compose exec -T postgres pg_isready -U "$DB_USER" &> /dev/null; then
        echo -e "${GREEN}PostgreSQL is ready${NC}"
        break
    fi
    if [[ $i -eq 30 ]]; then
        echo -e "${RED}Error: PostgreSQL failed to start${NC}"
        exit 1
    fi
    sleep 1
done
echo ""

# Step 4: Import databases
echo -e "${YELLOW}[4/5]${NC} Importing databases (this may take a few minutes)..."
if ! scripts/import-databases.sh --force datasets/; then
    echo -e "${RED}Error: Database import failed${NC}"
    exit 1
fi
echo ""

# Step 5: Start Jupyter
echo -e "${YELLOW}[5/5]${NC} Starting Jupyter Lab..."
docker compose up -d analysis

# Wait for Jupyter to be ready
echo "Waiting for Jupyter to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:8888 &> /dev/null; then
        echo -e "${GREEN}Jupyter is ready${NC}"
        break
    fi
    if [[ $i -eq 30 ]]; then
        echo -e "${YELLOW}Warning: Could not verify Jupyter is running${NC}"
        break
    fi
    sleep 1
done
echo ""

# Done!
echo "=========================================="
echo -e "${GREEN}  Setup Complete!${NC}"
echo "=========================================="
echo ""
echo "Access the following URLs:"
echo "  - Jupyter Lab:  http://localhost:8888"
echo "  - Adminer (DB): http://localhost:18080"
echo ""
echo "Adminer login:"
echo "  System:   PostgreSQL"
echo "  Server:   postgres"
echo "  Username: teralizer"
echo "  Password: teralizer"
echo "  Database: postgres_dev (or postgres_test)"
echo ""
echo "To stop all services: docker compose down"
echo "To remove all data:   docker compose down -v"
echo ""

# Try to open browser
if command -v open &> /dev/null; then
    open http://localhost:8888
elif command -v xdg-open &> /dev/null; then
    xdg-open http://localhost:8888
fi
