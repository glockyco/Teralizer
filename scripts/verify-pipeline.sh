#!/usr/bin/env bash
#
# Developer entry point for Tier 1 verification: build the tool, run every synthetic fixture through
# the full pipeline on the scratch DB, then compare the observed DB shape to checked-in goldens.
set -uo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)

"$ROOT_DIR/gradlew" build --no-daemon || exit $?
"$ROOT_DIR/scripts/run-verification-corpus.sh" || exit $?
"$ROOT_DIR/scripts/check-verification-corpus.sh"
