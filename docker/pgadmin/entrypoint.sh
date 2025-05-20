#!/bin/bash
set -e

# Substitute environment variables in the template
sed \
  -e "s|\${DB_NAME:-postgres}|${DB_NAME:-postgres}|g" \
  -e "s|\${DB_USER:-postgres}|${DB_USER:-postgres}|g" \
  -e "s|\${DB_PASSWORD:-postgres}|${DB_PASSWORD:-postgres}|g" \
  /pgadmin4/servers.json > /var/lib/pgadmin/servers.json

# Run the original entrypoint
/entrypoint.sh "$@"
