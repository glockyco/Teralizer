#!/bin/bash

mkdir -p logs
start_time=$(date +%s)
start_time_fmt=$(date)
total_configs=0
completed=0
failed=0

echo "Starting batch processing at $(date)"
echo "----------------------------------------"

run_and_log() {
    config=$1
    logfile="logs/$(basename $config .conf).txt"

    # Progress calculation
    completed=$((completed + 1))
    percent=$((completed * 100 / total_configs))

    echo "[$(date +"%H:%M:%S")] [$completed/$total_configs - $percent%] Running $config"

    # Capture start time for this task
    task_start=$(date +%s)

    # Run the task
    ./gradlew run -Dteralizer.config=$config > "$logfile" 2>&1

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

configs=(
    "project-configs/example-gradle-junit4.conf"
    "project-configs/example-gradle-junit5.conf"
    "project-configs/example-maven-junit4.conf"
    "project-configs/example-maven-junit5.conf"
    #"project-configs/eqbench.conf"
)

# Update total_configs to match actual array length
total_configs=${#configs[@]}

./gradlew startPostgres

for config in "${configs[@]}"; do
    run_and_log "$config"
    # Kill any Java (sub-)processes that remain after
    # processing of the target project has already terminated.
    killall -9 java
done

./gradlew stopPostgres


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
