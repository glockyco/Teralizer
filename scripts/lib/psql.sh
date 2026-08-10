#!/usr/bin/env bash
# Shared PostgreSQL client resolution. Source this, then call teralizer_psql.
#
# The JVM half of the pipeline reads DB_HOST, DB_PORT, DB_USER and DB_PASSWORD through
# dotenv and falls back to localhost:5432 as `postgres` (see Configuration.java). These
# helpers read the same variables, so the shell steps of a run and the JVM steps of the
# same run cannot end up pointed at different servers.
#
# Transport is decided once, at source time. A psql on PATH talks to the server directly.
# Without one, the client inside the container is borrowed, which is the only option on a
# host whose PostgreSQL lives in Docker and has no client installed alongside it. Set
# TERALIZER_PSQL_TRANSPORT to `native` or `docker` to decide instead of being detected.

TERALIZER_PG_CONTAINER="${TERALIZER_PG_CONTAINER:-postgres-teralizer}"

if [[ -z "${TERALIZER_PSQL_TRANSPORT:-}" ]]; then
  if command -v psql >/dev/null 2>&1; then
    TERALIZER_PSQL_TRANSPORT=native
  else
    TERALIZER_PSQL_TRANSPORT=docker
  fi
fi

teralizer_psql() {
  if [[ "$TERALIZER_PSQL_TRANSPORT" == native ]]; then
    PGPASSWORD="${DB_PASSWORD:-postgres}" psql \
      -h "${DB_HOST:-localhost}" \
      -p "${DB_PORT:-5432}" \
      -U "${DB_USER:-postgres}" \
      "$@"
  else
    docker exec -i "$TERALIZER_PG_CONTAINER" psql -U "${DB_USER:-postgres}" "$@"
  fi
}

# Describes the reachable server for an error message, so a failure names the target that
# was actually tried rather than a container that may not be this host's transport.
teralizer_psql_target() {
  if [[ "$TERALIZER_PSQL_TRANSPORT" == native ]]; then
    printf '%s:%s' "${DB_HOST:-localhost}" "${DB_PORT:-5432}"
  else
    printf 'container %s' "$TERALIZER_PG_CONTAINER"
  fi
}
