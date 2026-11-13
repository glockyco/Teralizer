#!/usr/bin/env bash
#
# Runs Teralizer on a copy of a project and prints what it produced. Without an argument the
# project is projects/example-loan. The copy, the run data and the logs go to build/try-example,
# so neither the checkout nor the project changes.
#
# For the example, the script afterwards runs the generalized tests against a variant in which
# FULL is lowered from 60 to 50, a regression that the original tests do not detect.
#
# Needs Docker and the `teralizer` image, built with `docker build -t teralizer .`. The run
# uses its own PostgreSQL container and network, so it never touches another Teralizer database.
#
# Usage: scripts/try-example.sh [project-directory | --clean]
#   project-directory  a Maven or Gradle project that builds with Java 8 and has JUnit tests
#   --clean            remove the container, the network, the Maven cache and build/try-example
#   TERALIZER_IMAGE    image to run (default: teralizer)
set -euo pipefail
# Git Bash on Windows would otherwise rewrite the container paths in the docker arguments.
export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
IMAGE="${TERALIZER_IMAGE:-teralizer}"
NETWORK=teralizer-example
POSTGRES=teralizer-example-postgres
MAVEN_CACHE=teralizer-example-m2
DATABASE=scratch_try_example
WORK="$ROOT/build/try-example"
# The regression check below belongs to the example, so it runs only without an argument.
RUNS_EXAMPLE=$([[ $# -eq 0 ]] && echo true || echo false)

if [[ "${1:-}" == "--clean" ]]; then
  docker rm -f "$POSTGRES" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  docker volume rm "$MAVEN_CACHE" >/dev/null 2>&1 || true
  rm -rf "$WORK"
  echo "Removed $POSTGRES, $NETWORK, $MAVEN_CACHE and build/try-example."
  exit 0
fi

SOURCE="${1:-$ROOT/projects/example-loan}"
if [[ ! -d "$SOURCE" ]]; then
  echo "No such project directory: $SOURCE" >&2
  exit 1
fi
SOURCE=$(cd "$SOURCE" && pwd -P)
NAME=$(basename "$SOURCE")

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Image '$IMAGE' not found. Build it first: docker build -t teralizer ." >&2
  exit 1
fi

query() {
  docker exec "$POSTGRES" psql -U teralizer -d "$DATABASE" -P pager=off -c "$1"
}

rm -rf "$WORK"
mkdir -p "$WORK/data"
cp -r "$SOURCE" "$WORK/$NAME"
# Every project runs with the configuration of the example. Only the project root differs.
sed "s|projects/example-loan|projects/$NAME|" "$ROOT/project-configs/example-loan.conf" > "$WORK/$NAME.conf"

docker network create "$NETWORK" >/dev/null 2>&1 || true
docker rm -f "$POSTGRES" >/dev/null 2>&1 || true
docker run -d --name "$POSTGRES" --network "$NETWORK" \
  -e POSTGRES_USER=teralizer -e POSTGRES_PASSWORD=teralizer postgres:17 >/dev/null
until docker exec "$POSTGRES" pg_isready -U teralizer >/dev/null 2>&1; do sleep 1; done
docker exec "$POSTGRES" createdb -U teralizer "$DATABASE"

echo "==> Running Teralizer on $NAME (log: build/try-example/teralizer.log)"
if ! docker run --rm --network "$NETWORK" \
  -e DB_HOST="$POSTGRES" -e DB_PORT=5432 -e DB_USER=teralizer -e DB_PASSWORD=teralizer \
  -v "$WORK/$NAME:/app/projects/$NAME" \
  -v "$WORK/$NAME.conf:/app/project-configs/$NAME.conf:ro" \
  -v "$WORK/data:/app/data" \
  -v "$MAVEN_CACHE:/root/.m2" \
  "$IMAGE" ./gradlew run --no-daemon \
    -Dteralizer.config="project-configs/$NAME.conf" \
    -Dteralizer.database.name="$DATABASE" \
    -Dteralizer.data-dir=data \
  > "$WORK/teralizer.log" 2>&1; then
  echo "The run failed. See build/try-example/teralizer.log." >&2
  exit 1
fi

echo "==> Outcome of each assertion that reached generalization"
query "
  SELECT t.test_method_name AS test,
         a.tested_method_name AS tested_method,
         g.is_included AS generalized,
         coalesce(g.widening_refusal_code, g.exclusion_info) AS reason
  FROM test t
  JOIN assertion a ON a.test_id = t.id
  JOIN generalization g ON g.assertion_id = a.id
  ORDER BY t.test_method_name;"

echo "==> Mutants under the original and the generalized test suite"
query "
  SELECT regexp_replace(i.mutated_class, '.*\.', '') AS class,
         i.line_number AS line,
         regexp_replace(i.mutator, '.*\.', '') AS mutator,
         i.status AS original_suite,
         g.status AS generalized_suite
  FROM pit_mutation_report i
  JOIN pit_mutation_report g
    ON g.stage = 'COLLECT_PIT_DATA_GENERALIZED'
   AND (g.mutated_class, g.mutated_method, g.method_description, g.line_number, g.mutator, g.indexes, g.blocks)
     = (i.mutated_class, i.mutated_method, i.method_description, i.line_number, i.mutator, i.indexes, i.blocks)
  WHERE i.stage = 'COLLECT_PIT_DATA_INITIAL'
  ORDER BY class, i.line_number, mutator;"

echo "==> Generalized tests"
(cd "$WORK" && find "$NAME" -name '_*_Generalized_*_Test.java' | sed 's|^|build/try-example/|')

if [[ "$RUNS_EXAMPLE" == true ]]; then
  echo "==> Running all tests against the example with FULL lowered from 60 to 50"
  cp -r "$WORK/$NAME" "$WORK/$NAME-regression"
  sed 's/FULL = 60/FULL = 50/' "$WORK/$NAME/src/main/java/bank/LoanPolicy.java" \
    > "$WORK/$NAME-regression/src/main/java/bank/LoanPolicy.java"
  docker run --rm \
    -v "$WORK/$NAME-regression:/app/projects/$NAME" \
    -v "$MAVEN_CACHE:/root/.m2" \
    -w "/app/projects/$NAME" \
    "$IMAGE" mvn --batch-mode --file pom.teralizer.generalized.xml -Djacoco.skip=true test \
    > "$WORK/regression.log" 2>&1 || true
  grep -E 'Tests run:|expected:|TestParameters\{|^tries = ' "$WORK/regression.log" \
    | sed -E 's/, Time elapsed: [0-9.]+ s//'
fi

echo "==> Done"
echo "Query the results: docker exec -it $POSTGRES psql -U teralizer -d $DATABASE"
echo "Clean up:          scripts/try-example.sh --clean"
