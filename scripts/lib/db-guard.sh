#!/usr/bin/env bash
# Shared destructive-target guard. Source this, then call require_scratch_db "<name>".
# The corpus registry classifies protected inputs. Only the reserved scratch namespace is disposable.
#
# Requires DB_GUARD_ROOT to point at the repo root before sourcing, or falls back to the
# directory two levels above this file.

_db_guard_root() {
  if [[ -n "${DB_GUARD_ROOT:-}" ]]; then
    printf '%s' "$DB_GUARD_ROOT"
  else
    cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P
  fi
}

require_scratch_db() {
  local name="$1"
  local root kind
  root="$(_db_guard_root)"
  kind=$("$root/scripts/corpus-registry" classify "$name") || {
    echo "db-guard: cannot classify database '$name'" >&2
    exit 1
  }
  if [[ "$kind" != "scratch" ]]; then
    echo "db-guard: refusing $kind database '$name'. Only scratch_* databases are disposable." >&2
    exit 1
  fi
}
