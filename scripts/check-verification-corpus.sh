#!/usr/bin/env bash
#
# Compare the verification fixture DB rows with checked-in golden observations. The golden files are
# observed truth from real full-pipeline runs; this script reports a unified diff so contract drift is
# reviewable instead of hidden in ad-hoc SQL.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
DB_NAME="${VERIFICATION_DB:-postgres_verification}"
GOLDEN_DIR="$ROOT_DIR/verification/golden"
HEADER=$'fixture\tgen_count\tgen_index\tvariant\tis_included\texclusion_info\toutput_spec_class\tdiagnostic_kind\ttries\tdistinct_tuples'

case "$DB_NAME" in
  postgres|postgres_dev|postgres_test|postgres_timeout_retry|postgres_fusion_spike|postgres_reporeapers_rerun|*_replication)
    echo "Refusing unsafe target DB_NAME=$DB_NAME" >&2; exit 1 ;;
esac
if [[ ! "$DB_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "Refusing invalid DB_NAME=$DB_NAME" >&2; exit 1
fi

_psql() { docker exec -i postgres-teralizer psql -U postgres "$@"; }

_psql -d "$DB_NAME" -c 'SELECT 1' >/dev/null 2>&1 || { echo "Database '$DB_NAME' is not reachable" >&2; exit 1; }

mapfile -t golden_files < <(find "$GOLDEN_DIR" -maxdepth 1 -name '*.tsv' | sort)
[[ ${#golden_files[@]} -gt 0 ]] || { echo "No golden files under $GOLDEN_DIR" >&2; exit 1; }

expected=$(mktemp)
actual=$(mktemp)
actual_rows=$(mktemp)
trap 'rm -f "$expected" "$actual" "$actual_rows"' EXIT

printf '%s\n' "$HEADER" > "$expected"
for golden_file in "${golden_files[@]}"; do
  tail -n +2 "$golden_file" >> "$expected"
done

_psql -d "$DB_NAME" -AtF $'\t' -c "
WITH latest_project AS (
  SELECT DISTINCT ON (regexp_replace(root_path, '^.*/', ''))
      id,
      regexp_replace(root_path, '^.*/', '') AS fixture
  FROM project
  WHERE root_path LIKE '%verification/fixtures/%'
  ORDER BY regexp_replace(root_path, '^.*/', ''), id DESC
), observed AS (
  SELECT
      lp.fixture,
      COUNT(g.id) OVER (PARTITION BY lp.id) AS gen_count,
      ROW_NUMBER() OVER (PARTITION BY lp.id ORDER BY g.id) AS gen_index,
      g.variant,
      CASE WHEN g.is_included THEN 'true' ELSE 'false' END AS is_included,
      COALESCE(g.exclusion_info, '') AS exclusion_info,
      COALESCE(a.output_spec_class, '') AS output_spec_class,
      COALESCE(string_agg(DISTINCT jpe.diagnostic_kind, ',' ORDER BY jpe.diagnostic_kind), '') AS diagnostic_kind,
      COALESCE(MAX(jpe.tries)::text, '') AS tries,
      COALESCE(MAX(jpe.distinct_tuples)::text, '') AS distinct_tuples
  FROM latest_project lp
  JOIN generalization g ON g.project_id = lp.id
  JOIN assertion a ON a.id = g.assertion_id
  LEFT JOIN jqwik_property_execution jpe ON jpe.generalization_id = g.id
  GROUP BY lp.fixture, lp.id, g.id, g.variant, g.is_included, g.exclusion_info, a.output_spec_class
)
SELECT fixture, gen_count, gen_index, variant, is_included, exclusion_info, output_spec_class, diagnostic_kind, tries, distinct_tuples
FROM observed
ORDER BY fixture, gen_index;
" > "$actual_rows" || { echo "Failed to query golden rows" >&2; exit 1; }

printf '%s\n' "$HEADER" > "$actual"
cat "$actual_rows" >> "$actual"


if ! diff -u "$expected" "$actual"; then
  echo "Verification golden mismatch for DB '$DB_NAME'" >&2
  exit 1
fi

echo "Verification golden check passed for DB '$DB_NAME'."
