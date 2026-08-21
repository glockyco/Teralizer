#!/usr/bin/env bash
# Verify restored corpus identity and display database statistics.
#
# Usage:
#   ./verify-results.sh [--corpus ID]...
#
# Without --corpus, the script verifies every published corpus.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
REPLICATION_DB_USER="${REPLICATION_DB_USER:-teralizer}"
CORPUS_IDS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --corpus)
            CORPUS_IDS+=("${2:?--corpus needs an id}")
            shift 2
            ;;
        -h|--help)
            sed -n '2,7p' "$0"
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 2
            ;;
    esac
done

if [[ ${#CORPUS_IDS[@]} -eq 0 ]]; then
    mapfile -t CORPUS_IDS < <("$REPO_ROOT/scripts/corpus-registry" list --published)
fi

query_db() {
    local database="$1"
    local sql="$2"
    docker compose -f "$SCRIPT_DIR/../docker-compose.yml" exec -T postgres \
        psql -U "$REPLICATION_DB_USER" -d "$database" -tA -c "$sql" 2>/dev/null
}

errors=0
for corpus_id in "${CORPUS_IDS[@]}"; do
    database=$("$REPO_ROOT/scripts/corpus-registry" get "$corpus_id" database)
    expected=$("$REPO_ROOT/scripts/corpus-registry" get "$corpus_id" expected_projects)
    echo "Corpus: $corpus_id"
    echo "Database: $database"

    if ! query_db "$database" "SELECT 1" >/dev/null; then
        echo "  ERROR: database is not reachable"
        errors=$((errors + 1))
        continue
    fi

    observed=$(query_db "$database" "SELECT count(*) FROM project")
    if [[ "$observed" != "$expected" ]]; then
        echo "  ERROR: expected $expected projects, observed $observed"
        errors=$((errors + 1))
        continue
    fi

    tests=$(query_db "$database" "SELECT count(*) FROM test")
    assertions=$(query_db "$database" "SELECT count(*) FROM assertion")
    generalizations=$(query_db "$database" "SELECT count(*) FROM generalization")
    echo "  Projects: $observed"
    echo "  Tests: $tests"
    echo "  Assertions: $assertions"
    echo "  Generalizations: $generalizations"
done

if [[ "$errors" -ne 0 ]]; then
    echo "$errors corpus verification checks failed" >&2
    exit 1
fi
echo "All requested corpus databases match the registry"
