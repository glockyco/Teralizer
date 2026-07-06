#!/usr/bin/env bash
# Scratch-database lifecycle shared by the run drivers and the jOOQ regeneration. This file is
# *sourced*, not executed, and expects teralizer_psql from run-supervisor.sh (source that
# first).
#
#   recreate_scratch_db <name>
#       Terminate backends, drop, and create <name>. Falls back to a template1 collation
#       refresh when CREATE fails, which a host libc upgrade under the container causes.
#   drop_scratch_db <name>
#       Terminate backends and drop <name>, tolerating absence.

recreate_scratch_db() {
  local name="$1"
  teralizer_psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$name' AND pid<>pg_backend_pid();" >/dev/null
  teralizer_psql -d postgres -c "DROP DATABASE IF EXISTS $name;" || { echo "DROP DATABASE $name failed" >&2; return 1; }
  if ! teralizer_psql -d postgres -c "CREATE DATABASE $name;" 2>/dev/null; then
    teralizer_psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
    teralizer_psql -d postgres -c "CREATE DATABASE $name;" || { echo "CREATE DATABASE $name failed" >&2; return 1; }
  fi
}

drop_scratch_db() {
  local name="$1"
  teralizer_psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$name' AND pid<>pg_backend_pid();" >/dev/null 2>&1 || true
  teralizer_psql -d postgres -c "DROP DATABASE IF EXISTS $name;" >/dev/null 2>&1 || true
}
