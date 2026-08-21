#!/usr/bin/env bash
# Restore registered corpus databases from replication-package dumps.
#
# Usage:
#   ./import-databases.sh [--force] [--corpus ID]... [input_dir]
#
# Options:
#   --corpus ID  Restore one registered corpus. Repeat to restore a subset.
#   --force      Replace existing corpus databases without prompting.
#   --help       Show this help text.
#
# Without --corpus, the script restores every published corpus. Dump files use
# the registry-resolved physical name, for example <input_dir>/<database>.dump.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
INPUT_DIR=""
FORCE=false
CORPUS_IDS=()
DB_USER="${DB_USER:-teralizer}"
CONTAINER="${CONTAINER:-postgres-replication}"

usage() {
    sed -n '2,15p' "$0"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --corpus)
            CORPUS_IDS+=("${2:?--corpus needs an id}")
            shift 2
            ;;
        --force)
            FORCE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -* )
            echo "Unknown option: $1" >&2
            exit 2
            ;;
        *)
            if [[ -n "$INPUT_DIR" ]]; then
                echo "Only one input directory is permitted" >&2
                exit 2
            fi
            INPUT_DIR="$1"
            shift
            ;;
    esac
done

INPUT_DIR="${INPUT_DIR:-$SCRIPT_DIR/../datasets}"
if [[ ${#CORPUS_IDS[@]} -eq 0 ]]; then
    mapfile -t CORPUS_IDS < <("$REPO_ROOT/scripts/corpus-registry" list --published)
fi

if ! docker ps --format '{{.Names}}' | awk -v name="$CONTAINER" '$0 == name { found = 1 } END { exit !found }'; then
    echo "Container '$CONTAINER' is not running" >&2
    echo "Start it with: docker compose up -d postgres" >&2
    exit 1
fi

restore_corpus() {
    local corpus_id="$1"
    local exports database expected dump existing
    exports=$("$REPO_ROOT/scripts/corpus-registry" export "$corpus_id") || return $?
    eval "$exports"
    database="$DB_NAME"
    expected="$EXPECTED_PROJECTS"
    dump="$INPUT_DIR/$database.dump"

    if [[ ! -f "$dump" ]]; then
        echo "Missing dump for corpus '$corpus_id': $dump" >&2
        return 1
    fi

    existing=$(docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -tA \
        -v name="$database" -c "SELECT 1 FROM pg_database WHERE datname = :'name'" 2>/dev/null || true)
    if [[ "$existing" == 1 ]]; then
        if [[ "$FORCE" != true ]]; then
            printf "Replace corpus '%s' database '%s'? [y/N] " "$corpus_id" "$database"
            read -r reply
            if [[ ! "$reply" =~ ^[Yy]$ ]]; then
                echo "Aborted"
                return 1
            fi
        fi
        docker exec "$CONTAINER" dropdb -U "$DB_USER" --force "$database"
    fi

    docker exec "$CONTAINER" createdb -U "$DB_USER" "$database"
    docker cp "$dump" "$CONTAINER:/tmp/corpus.dump"
    if ! docker exec "$CONTAINER" pg_restore -U "$DB_USER" -d "$database" \
        --no-owner --no-privileges /tmp/corpus.dump; then
        docker exec "$CONTAINER" rm -f /tmp/corpus.dump
        docker exec "$CONTAINER" dropdb -U "$DB_USER" --force "$database"
        return 1
    fi
    docker exec "$CONTAINER" rm -f /tmp/corpus.dump

    observed=$(docker exec "$CONTAINER" psql -U "$DB_USER" -d "$database" -tA \
        -c "SELECT count(*) FROM project")
    if [[ "$observed" != "$expected" ]]; then
        echo "Corpus '$corpus_id' expects $expected projects. The restored database has $observed." >&2
        docker exec "$CONTAINER" dropdb -U "$DB_USER" --force "$database"
        return 1
    fi
    echo "Restored $corpus_id to $database with $observed projects"
}

for corpus_id in "${CORPUS_IDS[@]}"; do
    restore_corpus "$corpus_id"
done
