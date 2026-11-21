#!/usr/bin/env bash
# Capture core of the CUT-PVC measurement: runs the JARVIS Table-2 test cases from the
# original Commons suites with the cut-pvc-capture javaagent and writes raw per-case TSVs.
#
# Usage: cut-pvc-capture.sh PLAN SOURCE_CACHE WORK RAW
#
#   PLAN          output of `python -m teralizer.cut_pvc plan`
#   SOURCE_CACHE  pinned upstream checkouts; missing ones are cloned at their pins
#   WORK          scratch copies of the checkouts, built there and never in the cache
#   RAW           one directory of TSVs per plan case
#
# It needs Java, Maven and Git, and runs on the host or in the pipeline image alike.
# scripts/run-cut-pvc-capture.sh drives it on the host, `./artifact collect-reduced
# jarvis-scenarios` in the pipeline container.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLAN="$1"
SOURCE_CACHE="$2"
WORK="$3"
RAW="$4"
AGENT_JAR="$ROOT/tools/cut-pvc-capture/target/cut-pvc-capture-1.0.jar"

# shellcheck source=scripts/lib/jarvis-pins.sh
source "$ROOT/scripts/lib/jarvis-pins.sh"

echo "[cut-pvc] building capture agent"
mvn -q -f "$ROOT/tools/cut-pvc-capture/pom.xml" -DskipTests package

for pin in "commons-lang|$LANG_REPO|$LANG_REF|$LANG_SHA" "commons-math|$MATH_REPO|$MATH_REF|$MATH_SHA"; do
    IFS='|' read -r project url ref sha <<< "$pin"
    if [[ ! -d "$SOURCE_CACHE/$project/.git" ]]; then
        echo "[cut-pvc] cloning $project at $sha"
        require_git_pin "$SOURCE_CACHE/$project" "$ref" "$sha" "$url"
    fi
    if [[ ! -d "$WORK/$project" ]]; then
        echo "[cut-pvc] copying $project checkout to scratch work dir"
        mkdir -p "$WORK/$project"
        (cd "$SOURCE_CACHE/$project" && tar --exclude=.git -cf - .) | (cd "$WORK/$project" && tar -xf -)
    fi
    echo "[cut-pvc] test-compiling $project (once)"
    (cd "$WORK/$project" && mvn -q test-compile -Drat.skip=true -Danimal.sniffer.skip=true)
done

mkdir -p "$RAW"

# Plan lines: slug <TAB> project <TAB> caller <TAB> tests(;) <TAB> agent-targets(;)
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
done < "$PLAN"
