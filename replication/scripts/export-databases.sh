#!/usr/bin/env bash
# Export every published corpus beside PostgreSQL, transfer it, and assemble the package.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd -P)
if [[ $# -ne 1 ]]; then
    echo "usage: export-databases.sh OUTPUT_DIR" >&2
    exit 2
fi
OUTPUT_DIR="$1"
DUMP_DIR="${CORPUS_EXPORT_DUMP_DIR:-}"
if [[ "$OUTPUT_DIR" != /* ]]; then
    OUTPUT_DIR="$PWD/$OUTPUT_DIR"
fi
require_setting() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "export-databases: $name is required" >&2
        exit 2
    fi
}

require_setting CORPUS_EXPORT_HOST
require_setting CORPUS_EXPORT_SPOOL
require_setting CORPUS_EXPORT_DOCKER
require_setting CORPUS_EXPORT_CONTAINER
require_setting CORPUS_EXPORT_DUMP_DIR

if [[ "$DUMP_DIR" != /* ]]; then
    DUMP_DIR="$PWD/$DUMP_DIR"
fi

uv run --frozen --directory "$REPO_ROOT/analysis" \
    python -m teralizer.corpus_publish --plan

export_args=(
    --ssh-host "$CORPUS_EXPORT_HOST"
    --remote-spool "$CORPUS_EXPORT_SPOOL"
    --docker "$CORPUS_EXPORT_DOCKER"
    --postgres-container "$CORPUS_EXPORT_CONTAINER"
    --database-user "${CORPUS_EXPORT_DB_USER:-postgres}"
    --output-dir "$DUMP_DIR"
)
if [[ "${CORPUS_EXPORT_REPLACE:-false}" == true ]]; then
    export_args+=(--replace)
fi
uv run --frozen --directory "$REPO_ROOT/analysis" \
    python -m teralizer.corpus_export "${export_args[@]}"
uv run --frozen --directory "$REPO_ROOT/analysis" \
    python -m teralizer.corpus_publish \
    --assemble-from "$DUMP_DIR" --output-dir "$OUTPUT_DIR"
