#!/bin/sh
# Se ejecuta una vez, al crear el volumen de la base (docker-entrypoint-initdb.d), despues de
# db/init/01-create-databases.sql: cambia la contrasena de cada usuario de servicio por la del .env
# (el script SQL comun los crea con contrasenas de desarrollo).
set -eu
for svc in auth partners documents alerts; do
    var="$(echo "$svc" | tr '[:lower:]' '[:upper:]')_DB_PASSWORD"
    pass="$(printenv "$var" || true)"
    if [ -n "$pass" ]; then
        psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
             -v svc="$svc" -v pass="$pass" <<'SQL'
ALTER ROLE :"svc" PASSWORD :'pass';
SQL
        echo "contrasena de $svc actualizada"
    fi
done
