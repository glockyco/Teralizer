#!/bin/sh
set -eu

# Git for Windows runs lefthook commands in sh. Other hosts use the pinned
# devshell even when an editor does not put Nix or uv on PATH.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    python_version=$(cat "$(dirname "$0")/../.python-version")
    exec uv run --frozen --python "$python_version" "$@"
    ;;
  *) exec /nix/var/nix/profiles/default/bin/nix develop --command uv run --frozen "$@" ;;
esac
