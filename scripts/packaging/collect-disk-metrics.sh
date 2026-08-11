#!/usr/bin/env bash
# Collect disk space metrics and version info for REQUIREMENTS.md documentation.
#
# Measures actual disk usage and extracts pinned versions. All numbers are
# computed automatically - no hardcoded values.
#
# Usage:
#   ./scripts/packaging/collect-disk-metrics.sh [ARCHIVE_DIR]

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)

ARCHIVE_DIR="${1:-$HOME/zenodo-upload-test}"
DEV_DIR="${DEV_DIR:-${ROOT_DIR}-dev}"
PROD_DIR="${PROD_DIR:-$ROOT_DIR}"

# Format bytes to human readable (GB/MB)
format_size() {
    local bytes=$1
    if [[ $bytes -ge 1073741824 ]]; then
        echo "$(echo "scale=1; $bytes / 1073741824" | bc) GB"
    elif [[ $bytes -ge 524288 ]]; then
        # Show MB for anything >= 0.5 MB (rounds to nearest MB)
        echo "$(echo "scale=0; ($bytes + 524288) / 1048576" | bc) MB"
    elif [[ $bytes -ge 1024 ]]; then
        echo "$(echo "scale=0; ($bytes + 512) / 1024" | bc) KB"
    else
        echo "$bytes bytes"
    fi
}

# Get size in bytes from du output
get_dir_bytes() {
    du -sk "$1" 2>/dev/null | cut -f1 | awk '{print $1 * 1024}'
}

echo "# Disk Space Metrics"
echo ""
echo "Collected: $(date)"
echo ""

# All archives - compressed and unpacked
echo "## Archive Sizes"
echo ""
echo "| Archive | Compressed | Unpacked | Files |"
echo "|---------|------------|----------|-------|"

for archive in "$ARCHIVE_DIR"/teralizer-*.zip; do
    [[ -f "$archive" ]] || continue
    name=$(basename "$archive" .zip | sed 's/-v[0-9.]*$//')

    # Compressed size in bytes
    compressed_bytes=$(stat -f%z "$archive" 2>/dev/null || stat -c%s "$archive" 2>/dev/null)
    compressed=$(format_size "$compressed_bytes")

    # Unpacked size from zip listing
    info=$(unzip -l "$archive" 2>/dev/null | tail -1)
    unpacked_bytes=$(echo "$info" | awk '{print $1}')
    unpacked=$(format_size "$unpacked_bytes")
    files=$(echo "$info" | awk '{print $2}')

    echo "| $name | $compressed | $unpacked | $files |"
done

echo ""

# Built project directories (after pipeline runs)
echo "## Built Project Directories (After Pipeline)"
echo ""
echo "| Directory | Size |"
echo "|-----------|------|"

for dir in "$PROD_DIR/projects" "$DEV_DIR/projects"; do
    if [[ -d "$dir" ]]; then
        # Use shorter path for display
        display_dir=$(echo "$dir" | sed "s|$HOME|~|")
        bytes=$(get_dir_bytes "$dir")
        size=$(format_size "$bytes")
        echo "| $display_dir | $size |"
    fi
done

echo ""

# Data directories (pipeline output)
echo "## Data Directories (Pipeline Output)"
echo ""
echo "| Directory | Size |"
echo "|-----------|------|"

for dir in "$PROD_DIR/data" "$DEV_DIR/data"; do
    if [[ -d "$dir" ]]; then
        display_dir=$(echo "$dir" | sed "s|$HOME|~|")
        bytes=$(get_dir_bytes "$dir")
        size=$(format_size "$bytes")
        echo "| $display_dir | $size |"
    fi
done

echo ""

# Docker image sizes
echo "## Docker Image Sizes"
echo ""
echo "| Image | Size |"
echo "|-------|------|"

docker images --format "{{.Repository}}:{{.Tag}}\t{{.Size}}" 2>/dev/null | while read -r line; do
    image=$(echo "$line" | cut -f1)
    size=$(echo "$line" | cut -f2)
    case "$image" in
        postgres:17.1|adminer:4.8.1|gradle:6.9.1-jdk8)
            echo "| $image | $size |"
            ;;
        *teralizer*|*analysis*|*replication*)
            echo "| $image | $size |"
            ;;
    esac
done

echo ""

# Docker volumes
echo "## Docker Volume Sizes"
echo ""

if docker volume ls -q 2>/dev/null | grep -qE "postgres|teralizer|replication"; then
    echo "| Volume | Size |"
    echo "|--------|------|"

    for vol in $(docker volume ls -q 2>/dev/null | grep -E "postgres|teralizer|replication"); do
        size=$(docker run --rm -v "$vol":/data alpine sh -c "du -sk /data 2>/dev/null | cut -f1" 2>/dev/null || echo "0")
        size_bytes=$((size * 1024))
        size_fmt=$(format_size "$size_bytes")
        echo "| $vol | $size_fmt |"
    done
else
    echo "No relevant Docker volumes found."
fi

echo ""

# Database sizes
echo "## Database Sizes"
echo ""

if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "postgres"; then
    container=$(docker ps --format '{{.Names}}' | grep postgres | head -1)
    echo "| Database | Size |"
    echo "|----------|------|"

    for db in postgres_dev postgres_test; do
        size_bytes=$(docker exec "$container" psql -U teralizer -d "$db" -t -c "SELECT pg_database_size('$db');" 2>/dev/null | tr -d ' ' || echo "0")
        size=$(format_size "$size_bytes")
        echo "| $db | $size |"
    done
else
    echo "PostgreSQL container not running."
fi

echo ""

# Pinned versions from Docker configs
echo "## Pinned Versions (from Docker configs)"
echo ""
echo "| Component | Version | Source |"
echo "|-----------|---------|--------|"

# PostgreSQL from docker-compose.yml
pg_version=$(grep 'image: postgres:' "$ROOT_DIR/replication/docker-compose.yml" 2>/dev/null | sed 's/.*postgres://' | tr -d ' ' || echo "N/A")
echo "| PostgreSQL | $pg_version | docker-compose.yml |"

# Adminer from docker-compose.yml
adminer_version=$(grep 'image: adminer:' "$ROOT_DIR/replication/docker-compose.yml" 2>/dev/null | sed 's/.*adminer://' | tr -d ' ' || echo "N/A")
echo "| Adminer | $adminer_version | docker-compose.yml |"

# Python from Dockerfile.analysis
python_version=$(grep '^FROM python:' "$ROOT_DIR/replication/Dockerfile.analysis" 2>/dev/null | sed 's/FROM python://' | sed 's/-.*//' || echo "N/A")
echo "| Python | $python_version | Dockerfile.analysis |"

# JupyterLab from uv.lock
jupyter_version=$(grep -A1 'name = "jupyterlab"' "$ROOT_DIR/analysis/uv.lock" 2>/dev/null | grep 'version' | sed 's/.*= "//' | sed 's/"//' || echo "N/A")
echo "| JupyterLab | $jupyter_version | uv.lock |"

# Gradle and JDK from Dockerfile
gradle_line=$(grep '^FROM gradle:' "$ROOT_DIR/Dockerfile" 2>/dev/null | sed 's/FROM gradle://' || echo "")
if [[ -n "$gradle_line" ]]; then
    gradle_version=$(echo "$gradle_line" | sed 's/-.*//')
    jdk_version=$(echo "$gradle_line" | sed 's/.*-jdk//' | sed 's/[^0-9].*//')
    echo "| Gradle | $gradle_version | Dockerfile |"
    echo "| JDK | $jdk_version | Dockerfile (gradle base) |"
else
    echo "| Gradle | N/A | Dockerfile |"
    echo "| JDK | N/A | Dockerfile |"
fi

# Maven from Dockerfile
maven_version=$(grep 'MAVEN_VERSION=' "$ROOT_DIR/Dockerfile" 2>/dev/null | sed 's/.*MAVEN_VERSION=//' | tr -d ' ' || echo "N/A")
echo "| Maven | $maven_version | Dockerfile |"
