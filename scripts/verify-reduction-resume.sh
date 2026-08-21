#!/usr/bin/env bash
# Verify reduction resume and multi-variant reduction isolation on a scratch DB.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME="${REDUCTION_RESUME_SCRATCH_DB:-scratch_reduction_resume}"
DATA_DIR="${REDUCTION_RESUME_DATA_DIR:-data/reduction-resume-verify}"
PROFILE="project-configs/verification.conf"
SINGLE_CONFIG="project-configs/verification/fixture-parse-predicate.conf"
TWO_VARIANT_CONFIG="project-configs/verification-resume/fixture-parse-predicate-two-variants.conf"
FIXTURE_ROOT_REL="verification/fixtures/parse-predicate"
FIXTURE_ROOT="$ROOT_DIR/$FIXTURE_ROOT_REL"
RUN_TIMEOUT="${REDUCTION_RESUME_RUN_TIMEOUT:-600}"
LOG_DIR="$ROOT_DIR/$DATA_DIR/run-logs"

source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"
source "$ROOT_DIR/scripts/lib/run-supervisor.sh"
source "$ROOT_DIR/scripts/lib/db-lifecycle.sh"

PASSED=0
STARTED_AT=$SECONDS
CURRENT_PART="setup"

cleanup_fixture_generated() {
  find "$FIXTURE_ROOT" -type f \( \
    -name '_*_Generalized_*_Test.java' -o \
    -name '_*_Driver_*.java' -o \
    -name '_*_Instrumented_*.java' \
  \) -delete 2>/dev/null || true
  find "$FIXTURE_ROOT" -type d -path '*/data/reduction-resume-verify' -prune -exec rm -rf {} + 2>/dev/null || true
}

cleanup() {
  local rc=$?
  cleanup_active_project_processes
  drop_scratch_db "$DB_NAME"
  cleanup_fixture_generated
  if [[ "$rc" -eq 0 ]]; then
    echo "==> PASS reduction-resume verification completed in $((SECONDS - STARTED_AT))s with $PASSED assertions"
  else
    echo "==> FAIL during $CURRENT_PART after $((SECONDS - STARTED_AT))s" >&2
  fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

pass() {
  echo "PASS: $1"
  PASSED=$((PASSED + 1))
}

trim() {
  local value="$1"
  value="${value#${value%%[![:space:]]*}}"
  value="${value%${value##*[![:space:]]}}"
  printf '%s' "$value"
}

psql_scalar() {
  local sql="$1"
  local out
  out=$(teralizer_psql -d "$DB_NAME" -t -A -c "$sql") || fail "SQL failed: $sql"
  trim "$out"
}

psql_rows() {
  local sql="$1"
  teralizer_psql -d "$DB_NAME" -t -A -c "$sql" || fail "SQL failed: $sql"
}

assert_eq() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [[ "$actual" == "$expected" ]] || fail "$label expected '$expected' but got '$actual'"
  pass "$label = $expected"
}

assert_gt_zero() {
  local actual="$1"
  local label="$2"
  [[ "$actual" =~ ^[0-9]+$ ]] || fail "$label expected an integer but got '$actual'"
  (( actual > 0 )) || fail "$label expected > 0 but got $actual"
  pass "$label = $actual"
}

assert_dir_nonempty() {
  local dir="$1"
  local label="$2"
  [[ -d "$dir" ]] || fail "$label missing directory $dir"
  [[ -n "$(find "$dir" -type f -print -quit)" ]] || fail "$label has no files under $dir"
  pass "$label exists and is non-empty"
}

install_analysis_views() {
  teralizer_psql -v ON_ERROR_STOP=1 -d "$DB_NAME" >/dev/null <<'SQL' \
    || fail "could not install analysis views in scratch database $DB_NAME"
SET client_min_messages TO warning;
DROP VIEW IF EXISTS v_projects_generalized;
DROP FUNCTION IF EXISTS project_name(BIGINT);
CREATE FUNCTION project_name(project_id BIGINT)
RETURNS TEXT AS $$
  SELECT regexp_replace(root_path, '^.*/', '') FROM project WHERE id = project_id;
$$ LANGUAGE SQL STABLE;
CREATE VIEW v_projects_generalized AS
SELECT
    project_name(t.project_id),
    t.project_id
FROM
    task t
WHERE
    t.project_id IS NOT NULL
    AND t.test_id IS NULL
    AND t.assertion_id IS NULL
    AND t.generalization_id IS NULL
GROUP BY
    t.project_id
HAVING
    BOOL_AND(t.status = 'SUCCEEDED')
    AND SUM(CASE WHEN t.stage = 'FILTER_GENERALIZATIONS' THEN 1 ELSE 0 END) > 0;
SQL
  pass "analysis views installed in scratch database"
}

reset_workspace() {
  CURRENT_PART="$1 reset"
  rm -rf "$ROOT_DIR/$DATA_DIR"
  mkdir -p "$LOG_DIR"
  cleanup_fixture_generated
  echo "==> Resetting database $DB_NAME for $1"
  recreate_scratch_db "$DB_NAME" >/dev/null || fail "could not recreate scratch database $DB_NAME"
}

run_pipeline() {
  local label="$1"
  local config="$2"
  shift 2
  local log_slug
  log_slug=$(printf '%s' "$label" | tr ' /' '__')
  local log_abs="$LOG_DIR/$log_slug.log"
  local started=$SECONDS
  CURRENT_PART="$label"
  echo "==> $label"
  SUPERVISOR_ACTIVE_PATH="$FIXTURE_ROOT"
  supervised_run "$log_abs" "$RUN_TIMEOUT" \
    "$ROOT_DIR/gradlew" run \
    -Dteralizer.config="$PROFILE,$config" \
    -Dteralizer.database.name="$DB_NAME" \
    -Dteralizer.data-dir="$DATA_DIR" \
    "$@" \
    --no-daemon
  local rc=$SUPERVISED_RC
  cleanup_leftover_project_processes "$FIXTURE_ROOT" "$log_abs"
  SUPERVISOR_ACTIVE_PATH=""
  [[ "$rc" -eq 0 ]] || fail "$label exited $rc, see $log_abs"
  pass "$label completed in $((SECONDS - started))s"
}

load_project_id() {
  local count
  count=$(psql_scalar "SELECT COUNT(*) FROM project WHERE root_path = '$FIXTURE_ROOT_REL';")
  assert_eq "$count" "1" "$CURRENT_PART project row count for $FIXTURE_ROOT_REL"
  PROJECT_ID=$(psql_scalar "SELECT id FROM project WHERE root_path = '$FIXTURE_ROOT_REL';")
}

archive_dir_for() {
  local project_id="$1"
  local variant="$2"
  printf '%s/%s/%s/project-id-%s/generalized-sources/%s' "$ROOT_DIR" "$DATA_DIR" "parse-predicate" "$project_id" "$variant"
}

part_a_resume() {
  reset_workspace "Part A"
  run_pipeline "Part A invocation 1 generalization only" "$SINGLE_CONFIG" \
    -Dteralizer.project.use-test-generation=false \
    -Dteralizer.project.use-test-generalization=true \
    -Dteralizer.project.use-test-reduction=false \
    -Dteralizer.pitest.enabled=true

  install_analysis_views

  CURRENT_PART="Part A after invocation 1"
  local project_id
  load_project_id
  project_id="$PROJECT_ID"
  assert_dir_nonempty "$(archive_dir_for "$project_id" IMPROVED_100_TRIES)" "Part A generalized-source archive for IMPROVED_100_TRIES"

  local generalized_projects
  generalized_projects=$(psql_scalar "SELECT COUNT(*) FROM v_projects_generalized WHERE project_id = $project_id;")
  assert_eq "$generalized_projects" "1" "Part A v_projects_generalized row"

  local pit_mutations pit_coverage jacoco_generalized generalize_tasks generalization_rows
  pit_mutations=$(psql_scalar "SELECT COUNT(*) FROM pit_mutation_report WHERE project_id = $project_id;")
  pit_coverage=$(psql_scalar "SELECT COUNT(*) FROM pit_coverage_report WHERE project_id = $project_id;")
  assert_eq "$pit_mutations" "0" "Part A mutation rows before reduction"
  assert_eq "$pit_coverage" "0" "Part A PIT coverage rows before reduction"

  generalize_tasks=$(psql_scalar "SELECT COUNT(*) FROM task WHERE project_id = $project_id AND stage = 'GENERALIZE_TESTS';")
  generalization_rows=$(psql_scalar "SELECT COUNT(*) FROM generalization WHERE project_id = $project_id;")
  assert_gt_zero "$generalize_tasks" "Part A GENERALIZE_TESTS task rows after invocation 1"
  assert_gt_zero "$generalization_rows" "Part A generalization rows after invocation 1"

  run_pipeline "Part A invocation 2 reduction only" "$SINGLE_CONFIG" \
    -Dteralizer.project.use-test-generation=false \
    -Dteralizer.project.use-test-generalization=false \
    -Dteralizer.project.use-test-reduction=true \
    -Dteralizer.pitest.enabled=true

  CURRENT_PART="Part A after invocation 2"
  local project_rows project_id_after generalize_tasks_after generalization_rows_after pit_mutations_after pit_coverage_after
  project_rows=$(psql_scalar "SELECT COUNT(*) FROM project WHERE root_path = '$FIXTURE_ROOT_REL';")
  assert_eq "$project_rows" "1" "Part A same-root project row count after resume"
  project_id_after=$(psql_scalar "SELECT id FROM project WHERE root_path = '$FIXTURE_ROOT_REL';")
  assert_eq "$project_id_after" "$project_id" "Part A resumed project id"

  pit_mutations_after=$(psql_scalar "SELECT COUNT(*) FROM pit_mutation_report WHERE project_id = $project_id AND stage = 'COLLECT_PIT_DATA_GENERALIZED';")
  pit_coverage_after=$(psql_scalar "SELECT COUNT(*) FROM pit_coverage_report WHERE project_id = $project_id AND stage = 'COLLECT_PIT_DATA_GENERALIZED';")
  jacoco_generalized=$(psql_scalar "SELECT COUNT(*) FROM jacoco_coverage_report WHERE project_id = $project_id AND stage = 'COLLECT_JACOCO_DATA_GENERALIZED';")
  assert_gt_zero "$pit_mutations_after" "Part A generalized PIT mutation rows after reduction"
  assert_gt_zero "$pit_coverage_after" "Part A generalized PIT coverage rows after reduction"
  assert_gt_zero "$jacoco_generalized" "Part A generalized JaCoCo rows after reduction"

  generalize_tasks_after=$(psql_scalar "SELECT COUNT(*) FROM task WHERE project_id = $project_id AND stage = 'GENERALIZE_TESTS';")
  generalization_rows_after=$(psql_scalar "SELECT COUNT(*) FROM generalization WHERE project_id = $project_id;")
  assert_eq "$generalize_tasks_after" "$generalize_tasks" "Part A GENERALIZE_TESTS task count unchanged by reduction-only resume"
  assert_eq "$generalization_rows_after" "$generalization_rows" "Part A generalization row count unchanged by reduction-only resume"
}

assert_variant_rows() {
  local project_id="$1"
  local variant="$2"
  local pit_mutations pit_coverage jacoco_rows
  pit_mutations=$(psql_scalar "SELECT COUNT(*) FROM pit_mutation_report WHERE project_id = $project_id AND stage = 'COLLECT_PIT_DATA_GENERALIZED' AND variant = '$variant';")
  pit_coverage=$(psql_scalar "SELECT COUNT(*) FROM pit_coverage_report WHERE project_id = $project_id AND stage = 'COLLECT_PIT_DATA_GENERALIZED' AND variant = '$variant';")
  jacoco_rows=$(psql_scalar "SELECT COUNT(*) FROM jacoco_coverage_report WHERE project_id = $project_id AND stage = 'COLLECT_JACOCO_DATA_GENERALIZED' AND variant = '$variant';")
  assert_gt_zero "$pit_mutations" "Part B $variant generalized PIT mutation rows"
  assert_gt_zero "$pit_coverage" "Part B $variant generalized PIT coverage rows"
  assert_gt_zero "$jacoco_rows" "Part B $variant generalized JaCoCo rows"
}

part_b_isolation() {
  reset_workspace "Part B"
  run_pipeline "Part B two-variant full run" "$TWO_VARIANT_CONFIG" \
    -Dteralizer.project.use-test-generation=false \
    -Dteralizer.project.use-test-generalization=true \
    -Dteralizer.project.use-test-reduction=true \
    -Dteralizer.pitest.enabled=true

  CURRENT_PART="Part B assertions"
  local project_id
  load_project_id
  project_id="$PROJECT_ID"
  assert_dir_nonempty "$(archive_dir_for "$project_id" IMPROVED_100_TRIES)" "Part B generalized-source archive for IMPROVED_100_TRIES"
  assert_dir_nonempty "$(archive_dir_for "$project_id" IMPROVED_200_TRIES)" "Part B generalized-source archive for IMPROVED_200_TRIES"

  assert_variant_rows "$project_id" IMPROVED_100_TRIES
  assert_variant_rows "$project_id" IMPROVED_200_TRIES

  local variant_count coverage_own_variants mutation_sibling_refs coverage_sibling_refs jacoco_own_variants jacoco_sibling_refs
  variant_count=$(psql_scalar "SELECT COUNT(DISTINCT variant) FROM generalization WHERE project_id = $project_id AND variant IN ('IMPROVED_100_TRIES', 'IMPROVED_200_TRIES');")
  assert_eq "$variant_count" "2" "Part B generalization variant count"

  coverage_own_variants=$(psql_scalar "SELECT COUNT(DISTINCT pcr.variant) FROM pit_coverage_report pcr JOIN generalization g ON g.id = pcr.generalization_id AND g.variant = pcr.variant WHERE pcr.project_id = $project_id AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED' AND pcr.variant IN ('IMPROVED_100_TRIES', 'IMPROVED_200_TRIES');")
  assert_eq "$coverage_own_variants" "2" "Part B PIT coverage rows joined to same-variant generalized classes"

  mutation_sibling_refs=$(psql_scalar "SELECT COUNT(*) FROM pit_mutation_report pmr JOIN generalization g ON g.id = pmr.killing_generalization_id WHERE pmr.project_id = $project_id AND pmr.stage = 'COLLECT_PIT_DATA_GENERALIZED' AND pmr.variant <> g.variant;")
  assert_eq "$mutation_sibling_refs" "0" "Part B PIT mutation rows with sibling generalized killer"

  coverage_sibling_refs=$(psql_scalar "SELECT COUNT(*) FROM pit_coverage_report pcr JOIN generalization g ON g.id = pcr.generalization_id WHERE pcr.project_id = $project_id AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED' AND pcr.variant <> g.variant;")
  assert_eq "$coverage_sibling_refs" "0" "Part B PIT coverage rows with sibling generalized class"

  jacoco_own_variants=$(psql_scalar "SELECT COUNT(DISTINCT jcr.variant) FROM jacoco_coverage_report jcr JOIN pit_coverage_report pcr ON pcr.project_id = jcr.project_id AND pcr.variant = jcr.variant AND pcr.covered_package_name = jcr.covered_package AND pcr.covered_class_name = jcr.covered_class JOIN generalization g ON g.id = pcr.generalization_id AND g.variant = jcr.variant WHERE jcr.project_id = $project_id AND jcr.stage = 'COLLECT_JACOCO_DATA_GENERALIZED' AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED' AND jcr.variant IN ('IMPROVED_100_TRIES', 'IMPROVED_200_TRIES');")
  assert_eq "$jacoco_own_variants" "2" "Part B JaCoCo rows linked through same-variant PIT coverage"

  jacoco_sibling_refs=$(psql_scalar "SELECT COUNT(*) FROM jacoco_coverage_report jcr JOIN pit_coverage_report pcr ON pcr.project_id = jcr.project_id AND pcr.variant = jcr.variant AND pcr.covered_package_name = jcr.covered_package AND pcr.covered_class_name = jcr.covered_class JOIN generalization g ON g.id = pcr.generalization_id WHERE jcr.project_id = $project_id AND jcr.stage = 'COLLECT_JACOCO_DATA_GENERALIZED' AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED' AND jcr.variant <> g.variant;")
  assert_eq "$jacoco_sibling_refs" "0" "Part B JaCoCo rows linked through sibling PIT generalized class"

  echo "==> Isolation SQL"
  cat <<SQL
PIT coverage same-variant references:
SELECT COUNT(DISTINCT pcr.variant)
FROM pit_coverage_report pcr
JOIN generalization g ON g.id = pcr.generalization_id AND g.variant = pcr.variant
WHERE pcr.project_id = $project_id
  AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED'
  AND pcr.variant IN ('IMPROVED_100_TRIES', 'IMPROVED_200_TRIES');

PIT mutation sibling-reference guard:
SELECT COUNT(*)
FROM pit_mutation_report pmr
JOIN generalization g ON g.id = pmr.killing_generalization_id
WHERE pmr.project_id = $project_id
  AND pmr.stage = 'COLLECT_PIT_DATA_GENERALIZED'
  AND pmr.variant <> g.variant;

PIT coverage sibling-reference guard:
SELECT COUNT(*)
FROM pit_coverage_report pcr
JOIN generalization g ON g.id = pcr.generalization_id
WHERE pcr.project_id = $project_id
  AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED'
  AND pcr.variant <> g.variant;

JaCoCo same-variant references through PIT coverage:
SELECT COUNT(DISTINCT jcr.variant)
FROM jacoco_coverage_report jcr
JOIN pit_coverage_report pcr ON pcr.project_id = jcr.project_id
  AND pcr.variant = jcr.variant
  AND pcr.covered_package_name = jcr.covered_package
  AND pcr.covered_class_name = jcr.covered_class
JOIN generalization g ON g.id = pcr.generalization_id
  AND g.variant = jcr.variant
WHERE jcr.project_id = $project_id
  AND jcr.stage = 'COLLECT_JACOCO_DATA_GENERALIZED'
  AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED'
  AND jcr.variant IN ('IMPROVED_100_TRIES', 'IMPROVED_200_TRIES');

JaCoCo sibling-reference guard through PIT coverage:
SELECT COUNT(*)
FROM jacoco_coverage_report jcr
JOIN pit_coverage_report pcr ON pcr.project_id = jcr.project_id
  AND pcr.variant = jcr.variant
  AND pcr.covered_package_name = jcr.covered_package
  AND pcr.covered_class_name = jcr.covered_class
JOIN generalization g ON g.id = pcr.generalization_id
WHERE jcr.project_id = $project_id
  AND jcr.stage = 'COLLECT_JACOCO_DATA_GENERALIZED'
  AND pcr.stage = 'COLLECT_PIT_DATA_GENERALIZED'
  AND jcr.variant <> g.variant;
SQL
}

ensure_postgres_up || fail "Postgres container postgres-teralizer is not reachable"
[[ -f "$ROOT_DIR/$TWO_VARIANT_CONFIG" ]] || fail "missing $TWO_VARIANT_CONFIG"
part_a_resume
part_b_isolation
