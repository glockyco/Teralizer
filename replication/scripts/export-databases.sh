#!/usr/bin/env bash
# Export registered corpus databases in PostgreSQL custom format.
#
# Usage:
#   ./export-databases.sh [--corpus ID]... [output_dir]
#
# Without --corpus, the script exports every published corpus. Each dump uses
# the registry-resolved physical database name.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
OUTPUT_DIR=""
CORPUS_IDS=()
DB_USER="${DB_USER:-teralizer}"
CONTAINER="${CONTAINER:-postgres-replication}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --corpus)
            CORPUS_IDS+=("${2:?--corpus needs an id}")
            shift 2
            ;;
        -h|--help)
            sed -n '2,8p' "$0"
            exit 0
            ;;
        -* )
            echo "Unknown option: $1" >&2
            exit 2
            ;;
        *)
            if [[ -n "$OUTPUT_DIR" ]]; then
                echo "Only one output directory is permitted" >&2
                exit 2
            fi
            OUTPUT_DIR="$1"
            shift
            ;;
    esac
done

OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR/../datasets}"
mkdir -p "$OUTPUT_DIR"
if [[ ${#CORPUS_IDS[@]} -eq 0 ]]; then
    mapfile -t CORPUS_IDS < <("$REPO_ROOT/scripts/corpus-registry" list --published)
fi

if ! docker ps --format '{{.Names}}' | awk -v name="$CONTAINER" '$0 == name { found = 1 } END { exit !found }'; then
    echo "Container '$CONTAINER' is not running" >&2
    exit 1
fi

for corpus_id in "${CORPUS_IDS[@]}"; do
    database=$("$REPO_ROOT/scripts/corpus-registry" get "$corpus_id" database)
    dump="$OUTPUT_DIR/$database.dump"
    echo "Exporting $corpus_id from $database"
    docker exec "$CONTAINER" pg_dump -U "$DB_USER" -Fc "$database" > "$dump"
    echo "Wrote $dump"
done

(
    cd "$OUTPUT_DIR"
    sha256sum ./*.dump > checksums.sha256
)
echo "Wrote $OUTPUT_DIR/checksums.sha256"
