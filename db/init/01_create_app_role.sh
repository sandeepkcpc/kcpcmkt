#!/bin/sh
# DB-001: creates the restricted runtime role (kcpc_app) at container bootstrap, distinct from
# the schema-owning migrator role (kcpc_migrator, POSTGRES_USER - the initial superuser the
# official postgres image creates). Runs automatically via docker-entrypoint-initdb.d on first
# container start only (Postgres skips /docker-entrypoint-initdb.d/* on a pre-existing data
# volume - see docker-compose.yml's named volume). Table-level GRANTs happen later, in
# V13__db_privilege_split_and_truncate_guards.sql, once Flyway (connected as kcpc_migrator) has
# actually created the schema - a role can't be granted privileges on tables that don't exist yet.
set -eu

: "${APP_DB_PASSWORD:?APP_DB_PASSWORD must be set for the restricted runtime role}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app') THEN
            CREATE ROLE kcpc_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT
                PASSWORD '${APP_DB_PASSWORD}';
        END IF;
    END
    \$\$;
EOSQL
