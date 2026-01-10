#!/usr/bin/env bash
# Export PostgreSQL databases for the replication package.
#
# This script exports the postgres_dev and postgres_test databases
# using pg_dump in custom format (compressed).
#
# Usage:
#   ./export-databases.sh [output_dir]
#
# Environment variables:
#   DB_USER      PostgreSQL user (default: teralizer)
#   DB_NAME_DEV  Source database for dev data (default: postgres_dev)
#   DB_NAME_TEST Source database for test data (default: postgres_test)
#   CONTAINER    Docker container name (default: postgres-replication)
#
# Examples:
#   # Export from default databases
#   ./export-databases.sh
#
#   # Export to specific directory
#   ./export-databases.sh /path/to/output

set -euo pipefail

# Configuration
DB_USER="${DB_USER:-teralizer}"
DB_NAME_DEV="${DB_NAME_DEV:-postgres_dev}"
DB_NAME_TEST="${DB_NAME_TEST:-postgres_test}"
CONTAINER="${CONTAINER:-postgres-replication}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$SCRIPT_DIR/../datasets}"

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

echo "=== Teralizer Database Export ==="
echo "Container: $CONTAINER"
echo "Databases: $DB_NAME_DEV, $DB_NAME_TEST"
echo "Output directory: $OUTPUT_DIR"
echo ""

# Check container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    echo "Error: Container '$CONTAINER' is not running"
    echo "Start it with: docker compose up -d postgres"
    exit 1
fi

# Check database connection
if ! docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME_DEV" -c "SELECT 1" >/dev/null 2>&1; then
    echo "Error: Cannot connect to $DB_NAME_DEV"
    exit 1
fi

# Export postgres_dev
echo "Exporting $DB_NAME_DEV..."
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -Fc "$DB_NAME_DEV" > "$OUTPUT_DIR/postgres_dev.dump"
echo "  -> postgres_dev.dump ($(du -h "$OUTPUT_DIR/postgres_dev.dump" | cut -f1))"

# Export postgres_test
echo "Exporting $DB_NAME_TEST..."
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -Fc "$DB_NAME_TEST" > "$OUTPUT_DIR/postgres_test.dump"
echo "  -> postgres_test.dump ($(du -h "$OUTPUT_DIR/postgres_test.dump" | cut -f1))"

# Generate checksums
echo "Generating checksums..."
(cd "$OUTPUT_DIR" && shasum -a 256 *.dump > checksums.sha256)
echo "  -> checksums.sha256"

echo ""
echo "Export complete!"
echo "Files created:"
ls -lh "$OUTPUT_DIR"/*.dump "$OUTPUT_DIR"/checksums.sha256
