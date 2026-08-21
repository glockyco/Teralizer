#!/usr/bin/env bash
# Shared run logic for the JARVIS scoreboard and census runners. This file is *sourced*, not
# executed. The sourcing wrapper must define the following before calling jarvis_run:
#
#   ROOT_DIR                repo root (absolute)
#   JARVIS_CORPUS_ID        registered definition for the measurement
#   JARVIS_DB_NAME          dedicated scratch database
#   JARVIS_DATA_DIR         scratch data root (e.g. data/jarvis-scoreboard)
#   JARVIS_LABEL            human label used in messages (e.g. "census", "scoreboard")
#   JARVIS_PREPARE_FLAG     flag passed to the fixture prep script ("" or "--census")
#   JARVIS_DEFAULT_CONFIGS  array of configs to run when none are given on the command line
#
# The DB/data targets are fixed by the wrapper (not read from the ambient environment), because
# .env may pin DB_NAME to another corpus (e.g. postgres_timeout_retry) for unrelated work.

# Measurement configs legitimately run for an hour or more with PIT enabled, so there is no
# default wall cap. JARVIS_PROJECT_TIMEOUT (seconds) opts in once a measured distribution
# justifies a guard. The supervisor still provides group-kill traps, so interrupting a run
# never leaves an orphaned pipeline JVM behind.
JARVIS_PROJECT_TIMEOUT="${JARVIS_PROJECT_TIMEOUT:-0}"

source "$ROOT_DIR/scripts/lib/psql.sh"

jarvis_run() {
  local registered_database
  registered_database=$("$ROOT_DIR/scripts/corpus-registry" get "$JARVIS_CORPUS_ID" database) || exit $?
  echo "==> Corpus: $JARVIS_CORPUS_ID (registered endpoint: $registered_database)"

  source "$ROOT_DIR/scripts/lib/db-guard.sh"
  DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$JARVIS_DB_NAME"
  source "$ROOT_DIR/scripts/lib/run-supervisor.sh"
  source "$ROOT_DIR/scripts/lib/db-lifecycle.sh"
  supervisor_install_traps

  local reset_db=false prepare_fixtures=false no_reduction=false reduction_only=false
  local -a configs=()
  local arg config
  for arg in "$@"; do
    case "$arg" in
      --reset-db) reset_db=true ;;
      --prepare-fixtures) prepare_fixtures=true ;;
      --no-reduction) no_reduction=true ;;
      --reduction-only) reduction_only=true ;;
      -h|--help)
        echo "Usage: $(basename "$0") [--reset-db] [--prepare-fixtures] [--no-reduction] [--reduction-only] [config ...]"
        echo "  --reset-db           drop and recreate $JARVIS_DB_NAME before running"
        echo "  --prepare-fixtures   (re)materialize the $JARVIS_LABEL fixtures first"
        echo "  --no-reduction       skip the reduction phase (Stage 5: mutation + coverage)"
        echo "                       and run only a fast Stage 4 applicability pass"
        echo "  --reduction-only     resume the reduction phase (Stage 5) with PIT enabled over the"
        echo "                       persisted generalized workspace and skip earlier phases"
        echo "  config ...           HOCON configs to run (default: the $JARVIS_LABEL configs)"
        exit 0 ;;
      --*) echo "Unknown flag: $arg" >&2; exit 1 ;;
      *) configs+=("$arg") ;;
    esac
  done

  if [[ "$no_reduction" == true && "$reduction_only" == true ]]; then
    echo "--no-reduction and --reduction-only are mutually exclusive" >&2
    exit 1
  fi

  if [[ ${#configs[@]} -eq 0 ]]; then
    configs=("${JARVIS_DEFAULT_CONFIGS[@]}")
  fi
  for config in "${configs[@]}"; do
    if [[ ! -f "$ROOT_DIR/$config" ]]; then
      echo "Configuration file not found: $config" >&2
      exit 1
    fi
  done

  # Keep attempts separate from the pipeline's database evidence so an early process failure is
  # still attributable to its config and a stale marker cannot describe a later failed run.
  local data_dir_abs="$ROOT_DIR/$JARVIS_DATA_DIR"
  local log_dir="$data_dir_abs/run-logs"
  local status_tsv="$data_dir_abs/status.tsv"
  local complete_marker="$data_dir_abs/complete"
  mkdir -p "$log_dir"
  [[ -f "$status_tsv" ]] || printf 'config\texit_code\tlog\n' > "$status_tsv"
  rm -f "$complete_marker"

  ensure_postgres_up || exit 1

  if [[ "$prepare_fixtures" == true ]]; then
    echo "==> Materializing $JARVIS_LABEL fixtures"
    bash "$ROOT_DIR/scripts/prepare-jarvis-scoreboard-fixtures.sh" ${JARVIS_PREPARE_FLAG:+"$JARVIS_PREPARE_FLAG"}
  fi

  # A failed BUILD_PROJECT_GENERALIZED drops its cleanup task, leaving uncompilable generated
  # tests that break the next build. Always clear them. The pipeline regenerates from scratch.
  find "$ROOT_DIR/$JARVIS_DATA_DIR/fixtures" -name '_*Generalized*_Test.java' -delete 2>/dev/null || true

  if [[ "$reset_db" == true ]]; then
    echo "==> Resetting database $JARVIS_DB_NAME"
    recreate_scratch_db "$JARVIS_DB_NAME" || exit 1
    # A new database holds no runs. A row from an earlier run describes data that
    # is not there any more, thus the ledger must start again with the database.
    rm -rf "$log_dir" "$status_tsv"
    mkdir -p "$log_dir"
    printf 'config\texit_code\tlog\n' > "$status_tsv"
  fi

  # High-water task id so the post-run check sees only this invocation's tasks (keeps incremental
  # runs safe without --reset-db). The task table does not exist yet on a freshly reset DB, so
  # default to 0 and only override when the query succeeds -- a failing query must not trip set -e.
  local baseline_task_id=0 _q
  if _q=$(teralizer_psql -tA -d "$JARVIS_DB_NAME" -c "SELECT COALESCE(MAX(id),0) FROM task;" 2>/dev/null | tr -d '[:space:]'); then
    baseline_task_id=${_q:-0}
  fi

  local gradle_failed=false
  local config_name log log_abs rc
  for config in "${configs[@]}"; do
    config_name="${config##*/}"
    config_name="${config_name%.conf}"
    log="$JARVIS_DATA_DIR/run-logs/$config_name.log"
    log_abs="$ROOT_DIR/$log"
    echo "==> Running $config (DB_NAME=$JARVIS_DB_NAME DATA_DIR=$JARVIS_DATA_DIR)"
    # Build the run command as an array so an empty flag set never trips set -u on bash 3.2.
    local -a run_cmd=(
      "$ROOT_DIR/gradlew" run
      -Dteralizer.config="$config"
      -Dteralizer.database.name="$JARVIS_DB_NAME"
      -Dteralizer.data-dir="$JARVIS_DATA_DIR"
    )
    if [[ "$no_reduction" == true ]]; then
      # Generalization-only pass: reduction is a separate later run over the same workspace.
      run_cmd+=(-Dteralizer.project.use-test-reduction=false)
    fi
    if [[ "$reduction_only" == true ]]; then
      # Reduction-only resume: reuse the persisted generalized workspace, skip generation and
      # generalization, run the reduction phase (Stage 5 mutation plus coverage) with PIT enabled.
      run_cmd+=(
        -Dteralizer.project.use-test-generation=false
        -Dteralizer.project.use-test-generalization=false
        -Dteralizer.project.use-test-reduction=true
        -Dteralizer.pitest.enabled=true
      )
    fi
    run_cmd+=(--no-daemon)
    supervised_run "$log_abs" "$JARVIS_PROJECT_TIMEOUT" "${run_cmd[@]}"
    rc="$SUPERVISED_RC"
    printf '%s\t%s\t%s\n' "$config_name" "$rc" "$log" >> "$status_tsv"
    if [[ "$rc" -eq 124 ]]; then
      echo "run capped at ${JARVIS_PROJECT_TIMEOUT}s for $config" >&2
      gradle_failed=true
    elif [[ "$rc" -ne 0 ]]; then
      echo "gradle exited non-zero for $config" >&2
      gradle_failed=true
    fi
  done

  # Per-assertion JPF analysis (ANALYZE_JPF/EXECUTE_JPF) routinely fails for assertions SPF cannot
  # symbolically handle -- raw-bits relations, unmodeled JDK classes, oversized path conditions --
  # and those assertions are simply excluded, not pipeline breakage. Report that count for
  # visibility, but only FAIL on breakage in other stages (build/collect/generalize/execute), since
  # the pipeline swallows per-task errors and the JVM still exits 0.
  echo "==> Checking pipeline outcome"
  local jpf_excluded=0 breakage=0
  if _q=$(teralizer_psql -tA -d "$JARVIS_DB_NAME" -c \
    "SELECT count(*) FROM task WHERE status='FAILED' AND id > ${baseline_task_id} AND stage IN ('ANALYZE_JPF','EXECUTE_JPF');" 2>/dev/null | tr -d '[:space:]'); then
    jpf_excluded=${_q:-0}
  fi
  # Timed-out, no-input-spec, and proactively rejected oversized-generation tasks are measured
  # attrition, not breakage. The latter predates task diagnostics and is identified by its stable
  # exception message until the pipeline records a dedicated reason code.
  if _q=$(teralizer_psql -tA -d "$JARVIS_DB_NAME" -c \
    "SELECT count(*) FROM task t WHERE t.status='FAILED' AND t.id > ${baseline_task_id} AND t.stage NOT IN ('ANALYZE_JPF','EXECUTE_JPF') AND t.info NOT LIKE '%Failing generalization to avoid potential ''code too large'' compilation errors%' AND NOT EXISTS (SELECT 1 FROM task_diagnostic td WHERE td.task_id = t.id AND td.reason_code IN ('SUITE_TIMEOUT','EXECUTION_TIMEOUT','NO_INPUT_SPEC'));" 2>/dev/null | tr -d '[:space:]'); then
    breakage=${_q:-0}
  fi
  echo "  per-assertion JPF exclusions (non-fatal coverage gaps): ${jpf_excluded}"
  if [[ "$jpf_excluded" -gt 0 ]]; then
    teralizer_psql -d "$JARVIS_DB_NAME" -c \
      "SELECT CASE WHEN info LIKE '%PATH_CONDITION_TOO_LARGE%' THEN 'path-condition size limit' WHEN info LIKE '%SEARCH_DEPTH_LIMIT%' THEN 'search depth limit' WHEN info LIKE '%EXECUTION_TIMEOUT%' THEN 'execution timeout' WHEN info LIKE '%NATIVE_MODEL_GAP%' THEN 'incomplete native peers' WHEN info LIKE '%TARGET_NOT_ENTERED%' THEN 'target not entered (unreachable)' WHEN info LIKE '%TARGET_NOT_EXITED%' THEN 'target not exited' WHEN info LIKE '%NonGeneralizableExpressionException%' THEN 'raw-bits non-generalizable' WHEN info LIKE '%class not found%' THEN 'unmodeled JDK/library class' WHEN info LIKE '%Unexpected rounding%' THEN 'symbolic control-arg out of range' ELSE 'other (investigate)' END AS cause, count(*) FROM task WHERE status='FAILED' AND id > ${baseline_task_id} AND stage IN ('ANALYZE_JPF','EXECUTE_JPF') GROUP BY 1 ORDER BY 2 DESC;" || true
  fi
  if [[ "$breakage" -gt 0 ]]; then
    echo "" >&2
    echo "${breakage} pipeline task(s) FAILED outside JPF analysis this run:" >&2
    teralizer_psql -d "$JARVIS_DB_NAME" -c \
      "SELECT p.root_path, t.stage, t.variant, left(t.info, 200) AS info_head FROM task t JOIN project p ON p.id = t.project_id WHERE t.status='FAILED' AND t.id > ${baseline_task_id} AND t.stage NOT IN ('ANALYZE_JPF','EXECUTE_JPF') AND t.info NOT LIKE '%Failing generalization to avoid potential ''code too large'' compilation errors%' AND NOT EXISTS (SELECT 1 FROM task_diagnostic td WHERE td.task_id = t.id AND td.reason_code IN ('SUITE_TIMEOUT','EXECUTION_TIMEOUT','NO_INPUT_SPEC')) ORDER BY t.id;" >&2 || true
    exit 1
  fi
  if [[ "$gradle_failed" == true ]]; then
    echo "A Gradle invocation exited nonzero, but no FAILED task was recorded. Check the run log." >&2
    exit 1
  fi
  touch "$complete_marker"
  echo "$JARVIS_LABEL run complete: no pipeline breakage (${jpf_excluded} per-assertion JPF exclusions)."
  echo "Run ledger: $JARVIS_DATA_DIR/status.tsv   Logs: $JARVIS_DATA_DIR/run-logs/   Completion: $JARVIS_DATA_DIR/complete"
}
