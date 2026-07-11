#!/usr/bin/env bash
# One-off CUT-PVC capture for the RQ0 JARVIS comparison.
#
# Runs the JARVIS Table-2 test cases from the ORIGINAL Commons suites on the
# pinned source checkouts (data/jarvis-scoreboard/source-cache, tags LANG_3_5 /
# MATH_3_5) with the cut-pvc-capture javaagent attached, recording the
# parameter values each case passes to its methods under test. The capture is
# standalone by design: it never touches the Teralizer processing pipeline.
#
# Outputs raw per-case TSVs under data/cut-pvc-capture/raw/<slug>/ and then
# aggregates them into analysis/data/jarvis-cut-values/cut_values.tsv via
#   uv run --directory analysis python -m teralizer.cut_pvc aggregate ...
#
# The source cache stays pristine: checkouts are copied to a scratch work dir
# before building.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_CACHE="$ROOT/data/jarvis-scoreboard/source-cache"
WORK="$ROOT/data/cut-pvc-capture"
RAW="$WORK/raw"
AGENT_JAR="$ROOT/tools/cut-pvc-capture/target/cut-pvc-capture-1.0.jar"

echo "[cut-pvc] building capture agent"
mvn -q -f "$ROOT/tools/cut-pvc-capture/pom.xml" -DskipTests package

for project in commons-lang commons-math; do
    if [[ ! -d "$SOURCE_CACHE/$project" ]]; then
        echo "[cut-pvc] missing source checkout: $SOURCE_CACHE/$project" >&2
        echo "[cut-pvc] run 'bash scripts/run-jarvis-scoreboard.sh --prepare-fixtures' first" >&2
        exit 1
    fi
    if [[ ! -d "$WORK/$project" ]]; then
        echo "[cut-pvc] copying $project checkout to scratch work dir"
        mkdir -p "$WORK"
        rsync -a --exclude '.git' "$SOURCE_CACHE/$project/" "$WORK/$project/"
    fi
    echo "[cut-pvc] test-compiling $project (once)"
    (cd "$WORK/$project" && mvn -q test-compile -Drat.skip=true -Danimal.sniffer.skip=true)
done

mkdir -p "$RAW"

# Plan lines: slug <TAB> project <TAB> caller <TAB> tests(;) <TAB> agent-targets(;)
uv run --directory "$ROOT/analysis" python -m teralizer.cut_pvc plan | \
while IFS=$'\t' read -r slug project caller tests targets; do
    out_dir="$RAW/$slug"
    if [[ -d "$out_dir" ]] && compgen -G "$out_dir/*.tsv" > /dev/null; then
        echo "[cut-pvc] $slug: raw capture already present, keeping first-run numbers"
        continue
    fi
    mkdir -p "$out_dir"
    methods_file="$WORK/methods-$slug.txt"
    printf '%s\n' "${targets//;/$'\n'}" > "$methods_file"
    IFS=';' read -ra test_specs <<< "$tests"
    for test_spec in "${test_specs[@]}"; do
        echo "[cut-pvc] $slug: mvn test -Dtest=$test_spec"
        (
            cd "$WORK/$project"
            # -DargLine overrides the pom argLine (commons-lang sets -Xmx512m,
            # kept here); jacoco/coverage is irrelevant for this measurement.
            mvn -q test \
                -Dtest="$test_spec" \
                -DfailIfNoTests=false \
                -Drat.skip=true \
                -Danimal.sniffer.skip=true \
                -Dmaven.javadoc.skip=true \
                -DargLine="-Xmx512m -javaagent:$AGENT_JAR=methods=$methods_file,out=$out_dir,callers=$caller" \
                || echo "[cut-pvc] $slug: mvn exited nonzero for $test_spec (recorded; capture may still be complete)"
        )
    done
done

echo "[cut-pvc] aggregating"
uv run --directory "$ROOT/analysis" python -m teralizer.cut_pvc aggregate "$RAW"
