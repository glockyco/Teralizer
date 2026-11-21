#!/usr/bin/env bash
# Restore, prepare and verify corpora of an extracted release.
#
# Usage:
#   ./import-databases.sh [--force] CORPUS_ID...
#
# Options:
#   --force  Replace existing corpus databases without prompting.
#   --help   Show this help text.
#
# Each corpus comes from its component, components/corpus-<id>/, and is checked
# against replication/corpus-manifest.json before any database changes. Every
# step runs in the release's Compose services, so the host needs only Docker.
# Set COMPOSE_PROJECT_NAME to keep this workspace's services and volumes apart
# from other installations.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
RELEASE_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
COMPOSE=(docker compose -f "$RELEASE_ROOT/replication/docker-compose.yml")
FORCE=false
CORPUS_IDS=()
REPLICATION_DB_USER="${REPLICATION_DB_USER:-teralizer}"
REPLICATION_DB_PASSWORD="${REPLICATION_DB_PASSWORD:-teralizer}"
# The analysis service writes as the invoking user, so that its output and
# state directories stay removable without root.
export TERALIZER_UID="${TERALIZER_UID:-$(id -u)}"
export TERALIZER_GID="${TERALIZER_GID:-$(id -g)}"

usage() {
    sed -n '2,15p' "$0"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
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
            CORPUS_IDS+=("$1")
            shift
            ;;
    esac
done

if [[ ${#CORPUS_IDS[@]} -eq 0 ]]; then
    echo "Name at least one corpus to restore" >&2
    usage >&2
    exit 2
fi

if [[ "$("${COMPOSE[@]}" ps --status running --services postgres)" != "postgres" ]]; then
    echo "The replication PostgreSQL service is not running" >&2
    echo "Start it with: ${COMPOSE[*]} up -d postgres" >&2
    exit 1
fi

# Compose creates missing bind-mount sources as root, so the default output and
# state directories exist before the analysis service first runs.
[[ -n "${TERALIZER_OUTPUT:-}" ]] || mkdir -p "$RELEASE_ROOT/output"
[[ -n "${TERALIZER_STATE:-}" ]] || mkdir -p "$RELEASE_ROOT/state"

# The analysis service holds only the report role. Preparation creates views and
# grants, so it alone receives the owner role.
analysis() {
    "${COMPOSE[@]}" run --rm -T analysis "$@"
}

owner_analysis() {
    "${COMPOSE[@]}" run --rm -T \
        -e DB_USER="$REPLICATION_DB_USER" \
        -e DB_PASSWORD="$REPLICATION_DB_PASSWORD" \
        analysis "$@"
}

postgres_exec() {
    "${COMPOSE[@]}" exec -T postgres "$@"
}

remove_restored_corpus() {
    local database="$1"
    postgres_exec dropdb -U "$REPLICATION_DB_USER" --force "$database" >/dev/null 2>&1
}

cleanup_failed_restore() {
    local database="$1"
    local container_dump="$2"
    postgres_exec rm -f "$container_dump" >/dev/null 2>&1 || true
    if ! remove_restored_corpus "$database"; then
        echo "Import cleanup could not remove database '$database'" >&2
        return 1
    fi
}

restore_corpus() {
    local corpus_id="$1"
    local fields database dump expected _revision container_dump existing observed
    if ! fields=$(analysis -m teralizer.corpus_publish --resolve-release-corpus "$corpus_id"); then
        echo "Corpus '$corpus_id' does not match the release manifest" >&2
        return 1
    fi
    IFS=$'\t' read -r database dump expected _revision <<<"$fields"
    container_dump="/tmp/teralizer-${corpus_id}.dump"

    existing=$(postgres_exec psql -U "$REPLICATION_DB_USER" -d postgres -tA \
        -v name="$database" -f - 2>/dev/null \
        <<<"SELECT 1 FROM pg_database WHERE datname = :'name'" || true)
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

    postgres_exec createdb -U "$REPLICATION_DB_USER" "$database"
    if ! "${COMPOSE[@]}" cp "$RELEASE_ROOT/$dump" "postgres:$container_dump"; then
        echo "Could not copy the verified dump for corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    if ! postgres_exec pg_restore -U "$REPLICATION_DB_USER" -d "$database" \
        --no-owner --no-privileges "$container_dump"; then
        echo "Could not restore corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    if ! postgres_exec rm -f "$container_dump"; then
        echo "Could not remove the temporary dump for corpus '$corpus_id'" >&2
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi

    if ! observed=$(postgres_exec psql -U "$REPLICATION_DB_USER" -d "$database" -tA \
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

    if ! owner_analysis -m teralizer.corpora prepare-corpus "$corpus_id"; then
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    # Verification reads the prepared corpus through the report role, the only
    # access the reports have.
    if ! analysis -m teralizer.corpora verify-corpus "$corpus_id"; then
        cleanup_failed_restore "$database" "$container_dump" || true
        return 1
    fi
    echo "Restored, prepared, and verified $corpus_id in $database"
}

for corpus_id in "${CORPUS_IDS[@]}"; do
    restore_corpus "$corpus_id"
done
