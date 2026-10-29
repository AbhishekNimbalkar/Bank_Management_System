#!/bin/bash
# One database, one schema per service, one login role per service.
# Runs once, on the first start of an empty Postgres volume, as the admin user (POSTGRES_USER).
#
# Each <service>_svc role OWNS only its schema and has no rights on the others, so isolation between
# services is enforced by PostgreSQL itself, not just by convention. Flyway (running as that role)
# creates the tables inside the schema.
set -euo pipefail

create_service_schema() {
    local role="$1" schema="$2" password="$3"
    if [ -z "$password" ]; then
        echo "Password for ${role} is empty - set it in .env" >&2
        exit 1
    fi
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
         -v role="$role" -v schema="$schema" -v password="$password" <<'EOSQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE SCHEMA IF NOT EXISTS :"schema" AUTHORIZATION :"role";
GRANT CONNECT ON DATABASE :"DBNAME" TO :"role";
ALTER ROLE :"role" IN DATABASE :"DBNAME" SET search_path = :"schema";
EOSQL
}

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
-- Nobody gets implicit rights: no connect to the database, no objects in "public".
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
EOSQL

create_service_schema auth_svc         auth         "${AUTH_DB_PASSWORD:-}"
create_service_schema organization_svc organization "${ORGANIZATION_DB_PASSWORD:-}"
create_service_schema attendance_svc   attendance   "${ATTENDANCE_DB_PASSWORD:-}"
create_service_schema incentive_svc    incentive    "${INCENTIVE_DB_PASSWORD:-}"
create_service_schema audit_svc        audit        "${AUDIT_DB_PASSWORD:-}"

echo "Bank Management System schemas and service roles created."
