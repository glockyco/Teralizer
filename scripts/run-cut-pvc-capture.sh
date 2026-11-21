#!/usr/bin/env bash
# One-off CUT-PVC capture for the RQ0 JARVIS comparison, run on the host.
#
# Runs the JARVIS Table-2 test cases from the ORIGINAL Commons suites on the
# pinned source checkouts (data/jarvis-source-cache, reconstructed at the 2017-02-01 cutoff)
# with the cut-pvc-capture javaagent attached, recording the
# parameter values each case passes to its methods under test. The capture is
# standalone by design: it never touches the Teralizer processing pipeline.
#
# Outputs raw per-case TSVs under data/cut-pvc-capture/raw/<slug>/ and then
# aggregates them into analysis/data/jarvis-cut-values/cut_values.tsv via
#   uv run --directory analysis python -m teralizer.cut_pvc aggregate ...
#
# The capture itself lives in scripts/lib/cut-pvc-capture.sh, which the packaged
# collection runs in the pipeline container.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/data/cut-pvc-capture"
RAW="$WORK/raw"

mkdir -p "$WORK"
"$ROOT/scripts/lib/cut-pvc-capture.sh" \
    <(uv run --directory "$ROOT/analysis" python -m teralizer.cut_pvc plan) \
    "$ROOT/data/jarvis-source-cache" "$WORK" "$RAW"

echo "[cut-pvc] aggregating"
uv run --directory "$ROOT/analysis" python -m teralizer.cut_pvc aggregate "$RAW"
