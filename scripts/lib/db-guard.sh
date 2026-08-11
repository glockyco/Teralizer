#!/usr/bin/env bash
# Shared database-target guard. Source this, then call require_scratch_db "<name>".
# Reads the canonical protected-corpus policy (src/main/resources/db/protected-databases.txt),
# the same file the Java startup guard consumes. A protected target is refused unless
# TERALIZER_ALLOW_PROTECTED=1 is set, mirroring the profile's allow-protected opt-in.
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
  local policy
  policy="$(_db_guard_root)/src/main/resources/db/protected-databases.txt"
  if [[ ! -f "$policy" ]]; then
    echo "db-guard: policy file not found at $policy" >&2
    exit 1
  fi
  if [[ ! "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "db-guard: refusing invalid database name '$name'" >&2
    exit 1
  fi
  local line
  while IFS= read -r line; do
    line="${line%%#*}"
    line="${line// /}"
    [[ -z "$line" ]] && continue
    # shellcheck disable=SC2053  # intentional glob match against the pattern
    if [[ "$name" == $line ]]; then
      if [[ "${TERALIZER_ALLOW_PROTECTED:-}" == "1" ]]; then
        return 0
      fi
      echo "db-guard: refusing protected database '$name' (matches '$line' in $policy)." >&2
      echo "          Set TERALIZER_ALLOW_PROTECTED=1 to override deliberately." >&2
      exit 1
    fi
  done < "$policy"
}
