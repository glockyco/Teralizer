#!/usr/bin/env bash
# Scratch-database lifecycle shared by the run drivers and the jOOQ regeneration. This file is
# *sourced*, not executed, and expects teralizer_psql from run-supervisor.sh (source that
# first).
#
#   recreate_scratch_db <name>
#       Terminate backends, drop, and create <name>, owned by TERALIZER_SCRATCH_OWNER when
#       that is set. Falls back to a template1 collation refresh when CREATE fails, which a
#       host libc upgrade under the container causes.
#   drop_scratch_db <name>
#       Terminate backends and drop <name>, tolerating absence.
#   ensure_collect_role
#       Create or update the role that the Compose pipeline service connects as. The
#       service builds and runs third-party projects, so the role is no superuser, cannot
#       create databases or roles, and owns nothing but the scratch databases created for
#       it. Run as a superuser.

COLLECT_DB_USER="${REPLICATION_COLLECT_DB_USER:-teralizer_collect}"
COLLECT_DB_PASSWORD="${REPLICATION_COLLECT_DB_PASSWORD:-teralizer-collect}"

recreate_scratch_db() {
  local name="$1"
  local create="CREATE DATABASE $name${TERALIZER_SCRATCH_OWNER:+ OWNER $TERALIZER_SCRATCH_OWNER};"
  teralizer_psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$name' AND pid<>pg_backend_pid();" >/dev/null
  teralizer_psql -d postgres -c "DROP DATABASE IF EXISTS $name;" || { echo "DROP DATABASE $name failed" >&2; return 1; }
  if ! teralizer_psql -d postgres -c "$create" 2>/dev/null; then
    teralizer_psql -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;" || true
    teralizer_psql -d postgres -c "$create" || { echo "CREATE DATABASE $name failed" >&2; return 1; }
  fi
}

ensure_collect_role() {
  teralizer_psql -v ON_ERROR_STOP=1 -q -d postgres \
    -v role="$COLLECT_DB_USER" -v password="$COLLECT_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I', :'role')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION', :'role', :'password') \gexec
SQL
}

drop_scratch_db() {
  local name="$1"
  teralizer_psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$name' AND pid<>pg_backend_pid();" >/dev/null 2>&1 || true
  teralizer_psql -d postgres -c "DROP DATABASE IF EXISTS $name;" >/dev/null 2>&1 || true
}
