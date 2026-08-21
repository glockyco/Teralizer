#!/usr/bin/env bash
#
# Compare the verification fixture DB rows with checked-in golden observations. The golden files are
# observed truth from real full-pipeline runs. This script reports a unified diff so contract drift is
# reviewable instead of hidden in ad-hoc SQL.
#
# Usage: scripts/check-verification-corpus.sh [--update-goldens]
#   --update-goldens  rewrite verification/golden/<fixture>.tsv from the current DB
#
# Goldens are written by this script and never by hand, so their row shape cannot drift from the
# query that reads them. Updating only ever rewrites a fixture the run actually produced: a fixture
# whose golden exists but whose run failed keeps its file, because a failed run must not be able to
# quietly delete the evidence that proves it used to work.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
UPDATE_GOLDENS=0
case "${1:-}" in
  --update-goldens) UPDATE_GOLDENS=1 ;;
  "") ;;
  *) echo "unknown argument: $1" >&2; exit 2 ;;
esac
DB_NAME="${VERIFICATION_SCRATCH_DB:-scratch_verification}"
GOLDEN_DIR="$ROOT_DIR/verification/golden"
HEADER=$'fixture\tgen_count\tgen_index\tvariant\tis_included\texclusion_info\toutput_spec_class\tdiagnostic_kind\ttries\tdistinct_tuples'

source "$ROOT_DIR/scripts/lib/db-guard.sh"
DB_GUARD_ROOT="$ROOT_DIR" require_scratch_db "$DB_NAME"

source "$ROOT_DIR/scripts/lib/psql.sh"

teralizer_psql -d "$DB_NAME" -c 'SELECT 1' >/dev/null 2>&1 || { echo "Database '$DB_NAME' is not reachable" >&2; exit 1; }

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

teralizer_psql -d "$DB_NAME" -AtF $'\t' -c "
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
      -- Numbering must come from the generalization's identity, never from g.id. A surrogate
      -- key follows insertion order, which follows scheduling, so the same corpus renumbers its
      -- rows on a machine with a different core count and every golden below the swap reports a
      -- mismatch that no code change caused. This tuple is unique across the corpus.
      ROW_NUMBER() OVER (
        PARTITION BY lp.id
        ORDER BY t.test_method_qualified_name, a.assertion_relative_path,
                 a.tested_method_qualified_name, g.variant
      ) AS gen_index,
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
  JOIN test t ON t.id = a.test_id
  LEFT JOIN jqwik_property_execution jpe ON jpe.generalization_id = g.id
  GROUP BY lp.fixture, lp.id, g.id, g.variant, g.is_included, g.exclusion_info, a.output_spec_class,
           t.test_method_qualified_name, a.assertion_relative_path, a.tested_method_qualified_name
)
SELECT fixture, gen_count, gen_index, variant, is_included, exclusion_info, output_spec_class, diagnostic_kind, tries, distinct_tuples
FROM observed
ORDER BY fixture, gen_index;
" > "$actual_rows" || { echo "Failed to query golden rows" >&2; exit 1; }

printf '%s\n' "$HEADER" > "$actual"
cat "$actual_rows" >> "$actual"

if (( UPDATE_GOLDENS )); then
  observed_fixtures=$(cut -f1 "$actual_rows" | sort -u)
  [[ -n "$observed_fixtures" ]] || { echo "Refusing to update: the run produced no rows" >&2; exit 1; }
  while read -r fixture; do
    { printf '%s\n' "$HEADER"; awk -F'\t' -v f="$fixture" '$1 == f' "$actual_rows"; } \
      > "$GOLDEN_DIR/$fixture.tsv"
    echo "updated $fixture"
  done <<< "$observed_fixtures"
  for golden_file in "${golden_files[@]}"; do
    fixture=$(basename "$golden_file" .tsv)
    grep -qx "$fixture" <<< "$observed_fixtures" \
      || echo "kept $fixture because its incomplete run produced no rows" >&2
  done
  exit 0
fi

if ! diff -u "$expected" "$actual"; then
  echo "Verification golden mismatch for DB '$DB_NAME'" >&2
  exit 1
fi

echo "Verification golden check passed for DB '$DB_NAME'."
