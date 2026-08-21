#!/usr/bin/env bash
# Restore and verify registered corpora from one published manifest set.
#
# Usage:
#   ./import-databases.sh [--force] [--corpus ID]... [input_dir]
#
# Options:
#   --corpus ID  Restore one registered corpus. Repeat to restore a subset.
#   --force      Replace existing corpus databases without prompting.
#   --help       Show this help text.
#
# Without --corpus, the script restores every published corpus. The complete
# package is verified before any database is changed.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
COMPOSE_FILE="$REPO_ROOT/replication/docker-compose.yml"
INPUT_DIR=""
FORCE=false
CORPUS_IDS=()
REPLICATION_DB_HOST="${REPLICATION_DB_HOST:-127.0.0.1}"
REPLICATION_DB_PORT="${REPLICATION_DB_PORT:-5432}"
REPLICATION_DB_USER="${REPLICATION_DB_USER:-teralizer}"
REPLICATION_DB_PASSWORD="${REPLICATION_DB_PASSWORD:-teralizer}"
REPLICATION_REPORT_DB_USER="${REPLICATION_REPORT_DB_USER:-teralizer_report}"
REPLICATION_REPORT_DB_PASSWORD="${REPLICATION_REPORT_DB_PASSWORD:-teralizer-report}"

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
        -*)
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
INPUT_DIR=$(cd "$INPUT_DIR" && pwd -P)

uv run --frozen --directory "$REPO_ROOT/analysis" python -m teralizer.corpus_publish \
    --verify-package "$INPUT_DIR"

if [[ ${#CORPUS_IDS[@]} -eq 0 ]]; then
    while IFS= read -r corpus_id; do
        CORPUS_IDS+=("$corpus_id")
    done < <("$REPO_ROOT/scripts/corpus-registry" list --published)
fi

if [[ "$(docker compose -f "$COMPOSE_FILE" ps --status running --services postgres)" != "postgres" ]]; then
    echo "The replication PostgreSQL service is not running" >&2
    echo "Start it with: docker compose -f $COMPOSE_FILE up -d postgres" >&2
    exit 1
fi

compose_exec() {
    docker compose -f "$COMPOSE_FILE" exec -T postgres "$@"
}

corpus_registry() {
    (
        cd "$REPO_ROOT"
        DB_HOST="$REPLICATION_DB_HOST" \
            DB_PORT="$REPLICATION_DB_PORT" \
            DB_USER="$REPLICATION_DB_USER" \
            DB_PASSWORD="$REPLICATION_DB_PASSWORD" \
            REPORT_DB_USER="$REPLICATION_REPORT_DB_USER" \
            REPORT_DB_PASSWORD="$REPLICATION_REPORT_DB_PASSWORD" \
            "$REPO_ROOT/scripts/corpus-registry" "$@"
    )
}

remove_restored_corpus() {
    local database="$1"
    compose_exec dropdb -U "$REPLICATION_DB_USER" --force "$database" >/dev/null 2>&1
}

cleanup_failed_restore() {
    local database="$1"
    local container_dump="$2"
    compose_exec rm -f "$container_dump" >/dev/null 2>&1 || true
    if ! remove_restored_corpus "$database"; then
        echo "Import cleanup could not remove database '$database'" >&2
        return 1
    fi
}

restore_corpus() {
    local corpus_id="$1"
    local database dump_relative expected _manifest_revision dump container_dump existing observed
    IFS=$'\t' read -r database dump_relative expected _manifest_revision < <(
        uv run --frozen --directory "$REPO_ROOT/analysis" \
            python -m teralizer.corpus_publish \
            --output-dir "$INPUT_DIR" \
            --resolve-package-corpus "$corpus_id"
    )
    dump="$INPUT_DIR/$dump_relative"
    container_dump="/tmp/teralizer-${corpus_id}.dump"

    existing=$(compose_exec psql -U "$REPLICATION_DB_USER" -d postgres -tA \
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
        if ! remove_restored_corpus "$database"; then
            echo "Could not replace corpus '$corpus_id' database '$database'" >&2
            return 1
        fi
    fi

    compose_exec createdb -U "$REPLICATION_DB_USER" "$database"
    if ! docker compose -f "$COMPOSE_FILE" cp "$dump" "postgres:$container_dump"; then
        echo "Could not copy the verified dump for corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    if ! compose_exec pg_restore -U "$REPLICATION_DB_USER" -d "$database" \
        --no-owner --no-privileges "$container_dump"; then
        echo "Could not restore corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    if ! compose_exec rm -f "$container_dump"; then
        echo "Could not remove the temporary dump for corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi

    if ! observed=$(compose_exec psql -U "$REPLICATION_DB_USER" -d "$database" -tA \
        -c "SELECT count(*) FROM project"); then
        echo "Could not count projects for restored corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    if [[ "$observed" != "$expected" ]]; then
        echo "Corpus '$corpus_id' expects $expected projects. The restored database has $observed." >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi

    if ! corpus_registry prepare-corpus "$corpus_id"; then
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    # Package verification binds the manifest's revision to the checked-in revision.
    # verify-corpus checks that installed revision through the report-only role.
    if ! corpus_registry verify-corpus "$corpus_id"; then
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    echo "Restored, prepared, and verified $corpus_id in $database"
}

for corpus_id in "${CORPUS_IDS[@]}"; do
    restore_corpus "$corpus_id"
done
