#!/bin/sh
# Export one immutable corpus beside PostgreSQL into a durable checkpoint.

set -eu

if [ "$#" -ne 8 ]; then
    echo "usage: export-corpus-remote.sh SPOOL CORPUS_ID DATABASE EXPECTED_PROJECTS DOCKER CONTAINER DB_USER REPLACE" >&2
    exit 2
fi

spool=$1
corpus_id=$2
database=$3
expected_projects=$4
docker=$5
container=$6
db_user=$7
replace=$8
partial="$spool/$corpus_id.partial"
complete="$spool/$corpus_id.complete"
dump_name="$database.dump"

fail() {
    echo "export-corpus: $*" >&2
    exit 1
}

query() {
    "$docker" exec "$container" psql --username "$db_user" --dbname "$database" \
        --tuples-only --no-align --quiet --command "$1"
}

projects=$(query "SELECT count(*) FROM project")
database_bytes=$(query "SELECT pg_database_size(current_database())")
[ "$projects" = "$expected_projects" ] ||
    fail "corpus '$corpus_id' expects $expected_projects projects in '$database'; observed $projects"

version=$("$docker" exec "$container" pg_dump --version)
case "$version" in
    "pg_dump (PostgreSQL) 17."*) ;;
    *) fail "corpus export requires PostgreSQL 17 pg_dump; observed $version" ;;
esac

verify_complete() {
    [ -d "$complete" ] || return 1
    [ -f "$complete/$dump_name" ] || return 1
    [ -f "$complete/facts.tsv" ] || return 1
    IFS="$(printf '\t')" read -r fact_id fact_database fact_projects \
        fact_database_bytes fact_sha fact_bytes < "$complete/facts.tsv" || return 1
    [ "$fact_id" = "$corpus_id" ] || return 1
    [ "$fact_database" = "$database" ] || return 1
    [ "$fact_projects" = "$projects" ] || return 1
    [ "$fact_database_bytes" = "$database_bytes" ] || return 1
    actual_sha=$(shasum -a 256 "$complete/$dump_name" | cut -d ' ' -f 1)
    actual_bytes=$(wc -c < "$complete/$dump_name" | tr -d ' ')
    [ "$fact_sha" = "$actual_sha" ] || return 1
    [ "$fact_bytes" = "$actual_bytes" ] || return 1
}

if [ -e "$complete" ]; then
    if verify_complete; then
        cat "$complete/facts.tsv"
        exit 0
    fi
    [ "$replace" = true ] ||
        fail "completed export for corpus '$corpus_id' failed verification; rerun with --replace"
    rm -rf "$complete"
fi

rm -rf "$partial"
mkdir -p "$partial"
"$docker" exec "$container" pg_dump \
    --username "$db_user" \
    --format=custom \
    --no-owner \
    --no-privileges \
    "$database" > "$partial/$dump_name"

observed_after=$(query "SELECT count(*) FROM project")
[ "$observed_after" = "$projects" ] ||
    fail "corpus '$corpus_id' changed project count during export: $projects to $observed_after"

sha=$(shasum -a 256 "$partial/$dump_name" | cut -d ' ' -f 1)
bytes=$(wc -c < "$partial/$dump_name" | tr -d ' ')
printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$corpus_id" "$database" "$projects" "$database_bytes" "$sha" "$bytes" \
    > "$partial/facts.tsv"
mv "$partial" "$complete"
cat "$complete/facts.tsv"
