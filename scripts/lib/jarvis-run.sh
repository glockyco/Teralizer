#!/usr/bin/env bash
# Shared run logic for the JARVIS scoreboard and census runners. This file is *sourced*, not
# executed; the sourcing wrapper must define the following before calling jarvis_run:
#
#   ROOT_DIR                repo root (absolute)
#   JARVIS_DB_NAME          dedicated scratch DB (never a dev/test/replication corpus)
#   JARVIS_DATA_DIR         scratch data root (e.g. data/jarvis-scoreboard)
#   JARVIS_LABEL            human label used in messages (e.g. "census", "scoreboard")
#   JARVIS_PREPARE_FLAG     flag passed to the fixture prep script ("" or "--census")
#   JARVIS_DEFAULT_CONFIGS  array of configs to run when none are given on the command line
#
# The DB/data targets are fixed by the wrapper (not read from the ambient environment), because
# .env may pin DB_NAME to another corpus (e.g. postgres_timeout_retry) for unrelated work.

_jarvis_psql() { docker exec postgres-teralizer psql -U postgres "$@"; }

_jarvis_ensure_db_up() {
  if _jarvis_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
    return 0
  fi
  echo "==> Postgres (postgres-teralizer) not ready; starting it"
  "$ROOT_DIR/gradlew" startPostgres >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    if _jarvis_psql -d postgres -c 'SELECT 1' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Postgres (postgres-teralizer) is not reachable. Is another container holding port 5432?" >&2
  echo "Start it with ./gradlew startPostgres and retry." >&2
  exit 1
}

jarvis_run() {
  source "$ROOT_DIR/scripts/lib/db-guard.sh"
  DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$JARVIS_DB_NAME"

  local reset_db=false prepare_fixtures=false
  local -a configs=()
  local arg config
  for arg in "$@"; do
    case "$arg" in
      --reset-db) reset_db=true ;;
      --prepare-fixtures) prepare_fixtures=true ;;
      -h|--help)
        echo "Usage: $(basename "$0") [--reset-db] [--prepare-fixtures] [config ...]"
        echo "  --reset-db           drop and recreate $JARVIS_DB_NAME before running"
        echo "  --prepare-fixtures   (re)materialize the $JARVIS_LABEL fixtures first"
        echo "  config ...           HOCON configs to run (default: the $JARVIS_LABEL configs)"
        exit 0 ;;
      --*) echo "Unknown flag: $arg" >&2; exit 1 ;;
      *) configs+=("$arg") ;;
    esac
  done

  if [[ ${#configs[@]} -eq 0 ]]; then
    configs=("${JARVIS_DEFAULT_CONFIGS[@]}")
  fi
  for config in "${configs[@]}"; do
    if [[ ! -f "$ROOT_DIR/$config" ]]; then
      echo "Configuration file not found: $config" >&2
      exit 1
    fi
  done

  _jarvis_ensure_db_up

  if [[ "$prepare_fixtures" == true ]]; then
    echo "==> Materializing $JARVIS_LABEL fixtures"
    bash "$ROOT_DIR/scripts/prepare-jarvis-scoreboard-fixtures.sh" ${JARVIS_PREPARE_FLAG:+"$JARVIS_PREPARE_FLAG"}
  fi

  # A failed BUILD_PROJECT_GENERALIZED drops its cleanup task, leaving uncompilable generated
  # tests that break the next build. Always clear them; the pipeline regenerates from scratch.
  find "$ROOT_DIR/$JARVIS_DATA_DIR/fixtures" -name '_*Generalized*_Test.java' -delete 2>/dev/null || true

  if [[ "$reset_db" == true ]]; then
    echo "==> Resetting database $JARVIS_DB_NAME"
    _jarvis_psql -d postgres -c \
      "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$JARVIS_DB_NAME' AND pid<>pg_backend_pid();" >/dev/null
    _jarvis_psql -d postgres -c "DROP DATABASE IF EXISTS $JARVIS_DB_NAME;"
    if ! _jarvis_psql -d postgres -c "CREATE DATABASE $JARVIS_DB_NAME;" 2>/dev/null; then
      # A glibc upgrade under the container leaves template1 with a stale collation version.
      _jarvis_psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
      _jarvis_psql -d postgres -c "CREATE DATABASE $JARVIS_DB_NAME;"
    fi
  fi

  # High-water task id so the post-run check sees only this invocation's tasks (keeps incremental
  # runs safe without --reset-db). The task table does not exist yet on a freshly reset DB, so
  # default to 0 and only override when the query succeeds -- a failing query must not trip set -e.
  local baseline_task_id=0 _q
  if _q=$(_jarvis_psql -tA -d "$JARVIS_DB_NAME" -c "SELECT COALESCE(MAX(id),0) FROM task;" 2>/dev/null | tr -d '[:space:]'); then
    baseline_task_id=${_q:-0}
  fi

  local gradle_failed=false
  for config in "${configs[@]}"; do
    echo "==> Running $config (DB_NAME=$JARVIS_DB_NAME DATA_DIR=$JARVIS_DATA_DIR)"
    if ! "$ROOT_DIR/gradlew" run \
         -Dteralizer.config="$config" \
         -Dteralizer.database.name="$JARVIS_DB_NAME" \
         -Dteralizer.data-dir="$JARVIS_DATA_DIR" \
         --no-daemon; then
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
  if _q=$(_jarvis_psql -tA -d "$JARVIS_DB_NAME" -c \
    "SELECT count(*) FROM task WHERE status='FAILED' AND id > ${baseline_task_id} AND stage IN ('ANALYZE_JPF','EXECUTE_JPF');" 2>/dev/null | tr -d '[:space:]'); then
    jpf_excluded=${_q:-0}
  fi
  if _q=$(_jarvis_psql -tA -d "$JARVIS_DB_NAME" -c \
    "SELECT count(*) FROM task WHERE status='FAILED' AND id > ${baseline_task_id} AND stage NOT IN ('ANALYZE_JPF','EXECUTE_JPF');" 2>/dev/null | tr -d '[:space:]'); then
    breakage=${_q:-0}
  fi
  echo "  per-assertion JPF exclusions (non-fatal coverage gaps): ${jpf_excluded}"
  if [[ "$jpf_excluded" -gt 0 ]]; then
    _jarvis_psql -d "$JARVIS_DB_NAME" -c \
      "SELECT CASE WHEN info LIKE '%PATH_CONDITION_TOO_LARGE%' THEN 'path-condition size limit' WHEN info LIKE '%SEARCH_DEPTH_LIMIT%' THEN 'search depth limit' WHEN info LIKE '%EXECUTION_TIMEOUT%' THEN 'execution timeout' WHEN info LIKE '%NATIVE_MODEL_GAP%' THEN 'incomplete native peers' WHEN info LIKE '%TARGET_NOT_ENTERED%' THEN 'target not entered (unreachable)' WHEN info LIKE '%TARGET_NOT_EXITED%' THEN 'target not exited' WHEN info LIKE '%NonGeneralizableExpressionException%' THEN 'raw-bits non-generalizable' WHEN info LIKE '%class not found%' THEN 'unmodeled JDK/library class' WHEN info LIKE '%Unexpected rounding%' THEN 'symbolic control-arg out of range' ELSE 'other (investigate)' END AS cause, count(*) FROM task WHERE status='FAILED' AND id > ${baseline_task_id} AND stage IN ('ANALYZE_JPF','EXECUTE_JPF') GROUP BY 1 ORDER BY 2 DESC;" || true
  fi
  if [[ "$breakage" -gt 0 ]]; then
    echo "" >&2
    echo "${breakage} pipeline task(s) FAILED outside JPF analysis this run:" >&2
    _jarvis_psql -d "$JARVIS_DB_NAME" -c \
      "SELECT p.root_path, t.stage, t.variant, left(t.info, 200) AS info_head FROM task t JOIN project p ON p.id = t.project_id WHERE t.status='FAILED' AND t.id > ${baseline_task_id} AND t.stage NOT IN ('ANALYZE_JPF','EXECUTE_JPF') ORDER BY t.id;" >&2 || true
    exit 1
  fi
  if [[ "$gradle_failed" == true ]]; then
    echo "A gradle invocation exited non-zero, though no FAILED task was recorded; check the run log." >&2
    exit 1
  fi
  echo "$JARVIS_LABEL run complete: no pipeline breakage (${jpf_excluded} per-assertion JPF exclusions)."
}
