#!/bin/bash
#
# Development batch processing script for Teralizer.
#
# For artifact evaluation, use replication/scripts/run.sh instead.
#
# Runs only against an explicitly disposable scratch database.
#
# Environment variables:
#   DATA_DIR      Directory for pipeline output data (default: data)
#   DB_NAME       Reserved scratch database (default: scratch_dev_run)
#   DB_HOST       PostgreSQL host (default: localhost)
#   DB_PORT       PostgreSQL port (default: 5432)
#   DB_USER       PostgreSQL user (default: teralizer)
#   DB_PASSWORD   PostgreSQL password (default: teralizer)
#
# Options:
#   --force       Skip data directory check and run anyway
#
# Usage:
#   ./dev-run.sh project-configs/extended/*.conf           # Run extended dataset
#   ./dev-run.sh project-configs/primary/generalization/*  # Run primary dataset
#   ./dev-run.sh project-configs/foo.conf                  # Run a single config
#   DATA_DIR=data-repro ./dev-run.sh configs/*.conf        # Custom data directory

set -euo pipefail

# Configuration
DATA_DIR="${DATA_DIR:-data}"
DB_NAME="${DB_NAME:-scratch_dev_run}"
FORCE=false

source scripts/lib/db-guard.sh
require_scratch_db "$DB_NAME"

# Parse --force flag
args=()
for arg in "$@"; do
    if [[ "$arg" == "--force" ]]; then
        FORCE=true
    else
        args+=("$arg")
    fi
done
set -- "${args[@]+"${args[@]}"}"

mkdir -p logs
start_time=$(date +%s)
start_time_fmt=$(date)
total_configs=0
completed=0
failed=0

echo "Starting batch processing at $(date)"
echo "Data directory: $DATA_DIR"
echo "----------------------------------------"

run_and_log() {
    config=$1
    logfile="logs/$(basename $config .conf).txt"

    # Progress calculation
    completed=$((completed + 1))
    percent=$((completed * 100 / total_configs))

    echo "[$(date +"%H:%M:%S")] [$completed/$total_configs - $percent%] Running $config (DB: $DB_NAME)"

    # Capture start time for this task
    task_start=$(date +%s)

    # Run the task with the correct database and data directory
    DB_NAME="$DB_NAME" DATA_DIR="$DATA_DIR" ./gradlew run -Dteralizer.config=$config > "$logfile" 2>&1

    # Check result
    if [ $? -eq 0 ]; then
        task_end=$(date +%s)
        duration=$((task_end - task_start))
        echo "[$(date +"%H:%M:%S")] [$completed/$total_configs - $percent%] ✓ SUCCESS: $config (took ${duration}s)" | tee -a "$logfile"
    else
        failed=$((failed + 1))
        task_end=$(date +%s)
        duration=$((task_end - task_start))
        echo "[$(date +"%H:%M:%S")] [$completed/$total_configs - $percent%] ✗ ERROR: Failed to run $config (took ${duration}s)" | tee -a "$logfile"
    fi

    echo "----------------------------------------"
}

# Require explicit config files
if [[ $# -eq 0 ]]; then
    echo "Usage: ./dev-run.sh <config-files...>"
    echo ""
    echo "Examples:"
    echo "  ./dev-run.sh project-configs/extended/*.conf"
    echo "  ./dev-run.sh project-configs/primary/generalization/*.conf"
    echo "  ./dev-run.sh project-configs/extended/project-{1..10}.conf"
    echo ""
    echo "For artifact evaluation, use: replication/scripts/run.sh"
    exit 1
fi
configs=("$@")

# Sort configs in natural (version) order
IFS=$'\n' sorted_configs=($(printf "%s\n" "${configs[@]}" | sort -V))
unset IFS

# Update total_configs to match actual array length
total_configs=${#sorted_configs[@]}

if [[ $total_configs -eq 0 ]]; then
    echo "Error: No config files found"
    exit 1
fi

# Data directory safety check
if [[ -d "$DATA_DIR" ]]; then
    project_count=$(find "$DATA_DIR" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')
    if [[ "$project_count" -gt 0 ]]; then
        data_size=$(du -sh "$DATA_DIR" 2>/dev/null | cut -f1)
        echo ""
        echo "WARNING: Data directory '$DATA_DIR' already contains $project_count project(s) ($data_size)"
        echo ""
        echo "Running additional projects will pollute the data directory, causing"
        echo "duplicate entries that break analysis (which assumes one run per project)."
        echo ""
        echo "Options:"
        echo "  1. Use a separate data directory: DATA_DIR=data-repro ./dev-run.sh ..."
        echo "  2. Remove existing data: rm -rf $DATA_DIR"
        echo "  3. Force execution anyway: ./dev-run.sh --force ..."
        echo ""

        if [[ "$FORCE" == true ]]; then
            echo "Proceeding anyway (--force specified)..."
            echo ""
        else
            read -p "Continue and risk data pollution? (y/N) " -n 1 -r
            echo ""
            if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                echo "Aborted."
                exit 1
            fi
        fi
    fi
fi

# ./gradlew startPostgres

for config in "${sorted_configs[@]}"; do
    run_and_log "$config"
    # Kill any Java (sub-)processes that remain after
    # processing of the target project has already terminated.
    # Only kill child processes of this script, not all Java on system.
    pkill -9 -P $$ java 2>/dev/null || true
done

#./gradlew stopPostgres


end_time=$(date +%s)
end_time_fmt=$(date)
total_duration=$((end_time - start_time))
hours=$((total_duration / 3600))
minutes=$(( (total_duration % 3600) / 60 ))
seconds=$((total_duration % 60))

echo "========== EXECUTION SUMMARY =========="
echo "Started:   $start_time_fmt"
echo "Finished:  $end_time_fmt"
echo "Duration:  ${hours}h ${minutes}m ${seconds}s"
echo "Completed: $completed/$total_configs"
echo "Success:   $((completed - failed))/$total_configs"
echo "Failed:    $failed/$total_configs"
echo "======================================="
echo "All tasks completed. Logs saved to the logs directory."
