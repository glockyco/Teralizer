#!/usr/bin/env bash
# Import PostgreSQL databases from the replication package.
#
# This script imports the postgres_dev and postgres_test databases
# from pg_dump custom format files.
#
# Usage:
#   ./import-databases.sh [options] [input_dir]
#
# Options:
#   --force    Overwrite existing databases without prompting
#   --help     Show this help message
#
# Environment variables:
#   DB_USER      PostgreSQL user (default: teralizer)
#   DB_NAME_DEV  Target database for dev data (default: postgres_dev)
#   DB_NAME_TEST Target database for test data (default: postgres_test)
#   CONTAINER    Docker container name (default: postgres-replication)
#
# Examples:
#   # Import from datasets/ directory
#   ./import-databases.sh
#
#   # Import from specific directory
#   ./import-databases.sh /path/to/dumps
#
#   # Force overwrite without prompting
#   ./import-databases.sh --force

set -euo pipefail

# Configuration
DB_USER="${DB_USER:-teralizer}"
DB_NAME_DEV="${DB_NAME_DEV:-postgres_dev}"
DB_NAME_TEST="${DB_NAME_TEST:-postgres_test}"
CONTAINER="${CONTAINER:-postgres-replication}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORCE=false
INPUT_DIR=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --force)
            FORCE=true
            shift
            ;;
        --help)
            head -28 "$0" | tail -26
            exit 0
            ;;
        *)
            INPUT_DIR="$1"
            shift
            ;;
    esac
done

# Default input directory
INPUT_DIR="${INPUT_DIR:-$SCRIPT_DIR/../datasets}"

echo "=== Teralizer Database Import ==="
echo "Container: $CONTAINER"
echo "Databases: $DB_NAME_DEV, $DB_NAME_TEST"
echo "Input directory: $INPUT_DIR"
echo ""

# Check container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    echo "Error: Container '$CONTAINER' is not running"
    echo "Start it with: docker compose up -d postgres"
    exit 1
fi

# Check if dump files exist
if [[ ! -f "$INPUT_DIR/postgres_dev.dump" ]]; then
    echo "Error: postgres_dev.dump not found in $INPUT_DIR"
    exit 1
fi

if [[ ! -f "$INPUT_DIR/postgres_test.dump" ]]; then
    echo "Error: postgres_test.dump not found in $INPUT_DIR"
    exit 1
fi

# Function to check if database exists and has data
check_database_has_data() {
    local db_name="$1"
    local count

    # Check if database exists
    if ! docker exec "$CONTAINER" psql -U "$DB_USER" -lqt 2>/dev/null | cut -d \| -f 1 | grep -qw "$db_name"; then
        echo "none"  # Database doesn't exist
        return
    fi

    # Check if project table exists and has data
    count=$(docker exec "$CONTAINER" psql -U "$DB_USER" -d "$db_name" -t -c \
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'project';" 2>/dev/null | tr -d ' ' || echo "0")

    if [[ "$count" -eq 0 ]]; then
        echo "empty"  # Database exists but no project table
        return
    fi

    count=$(docker exec "$CONTAINER" psql -U "$DB_USER" -d "$db_name" -t -c \
        "SELECT count(*) FROM project;" 2>/dev/null | tr -d ' ' || echo "0")

    if [[ "$count" -gt 0 ]]; then
        echo "$count"  # Has data
    else
        echo "empty"
    fi
}

# Check for existing data
echo "Checking for existing data..."

dev_status=$(check_database_has_data "$DB_NAME_DEV")
test_status=$(check_database_has_data "$DB_NAME_TEST")

has_existing_data=false

if [[ "$dev_status" != "none" && "$dev_status" != "empty" ]]; then
    echo "  $DB_NAME_DEV: $dev_status projects"
    has_existing_data=true
else
    echo "  $DB_NAME_DEV: ${dev_status:-no data}"
fi

if [[ "$test_status" != "none" && "$test_status" != "empty" ]]; then
    echo "  $DB_NAME_TEST: $test_status projects"
    has_existing_data=true
else
    echo "  $DB_NAME_TEST: ${test_status:-no data}"
fi

# Prompt for confirmation if data exists
if [[ "$has_existing_data" == true && "$FORCE" != true ]]; then
    echo ""
    echo "WARNING: Existing data will be OVERWRITTEN!"
    echo ""
    read -p "Continue? (y/N) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 1
    fi
fi

# Create databases if they don't exist
echo ""
echo "Creating databases if needed..."

for db_name in "$DB_NAME_DEV" "$DB_NAME_TEST"; do
    if ! docker exec "$CONTAINER" psql -U "$DB_USER" -lqt 2>/dev/null | cut -d \| -f 1 | grep -qw "$db_name"; then
        echo "  Creating $db_name..."
        docker exec "$CONTAINER" createdb -U "$DB_USER" "$db_name"
    fi
done

# Import databases (copy dump into container, then restore)
echo ""
echo "Importing $DB_NAME_DEV (this may take a while)..."
docker cp "$INPUT_DIR/postgres_dev.dump" "$CONTAINER:/tmp/postgres_dev.dump"
docker exec "$CONTAINER" pg_restore -U "$DB_USER" -d "$DB_NAME_DEV" \
    --clean --if-exists --no-owner --no-privileges \
    /tmp/postgres_dev.dump
docker exec "$CONTAINER" rm /tmp/postgres_dev.dump
echo "  -> $DB_NAME_DEV imported"

echo "Importing $DB_NAME_TEST (this may take a while)..."
docker cp "$INPUT_DIR/postgres_test.dump" "$CONTAINER:/tmp/postgres_test.dump"
docker exec "$CONTAINER" pg_restore -U "$DB_USER" -d "$DB_NAME_TEST" \
    --clean --if-exists --no-owner --no-privileges \
    /tmp/postgres_test.dump
docker exec "$CONTAINER" rm /tmp/postgres_test.dump
echo "  -> $DB_NAME_TEST imported"

# Verify import
echo ""
echo "Verifying import..."
echo -n "  $DB_NAME_DEV projects: "
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME_DEV" -t -c \
    "SELECT count(*) FROM project;" 2>/dev/null | tr -d ' ' || echo "error"

echo -n "  $DB_NAME_TEST projects: "
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME_TEST" -t -c \
    "SELECT count(*) FROM project;" 2>/dev/null | tr -d ' ' || echo "error"

echo ""
echo "Import complete!"
