#!/bin/sh
# Respaldo de las bases: un pg_dump (formato custom) por base en /backups/<fecha>/, al arrancar y
# luego cada BACKUP_INTERVAL_SECONDS (por defecto, cada 24 h). Conserva BACKUP_RETENTION_DAYS dias.
# Con --once hace un unico respaldo y termina.
set -eu
: "${BACKUP_INTERVAL_SECONDS:=86400}"
: "${BACKUP_RETENTION_DAYS:=7}"
: "${BACKUP_DATABASES:=auth partners documents alerts}"

respaldar() {
    dir="/backups/$(date -u +%Y-%m-%d)"
    mkdir -p "$dir"
    for db in $BACKUP_DATABASES; do
        pg_dump -h "$PGHOST" -U "$PGUSER" -Fc -f "$dir/$db.dump.tmp" "$db" && mv "$dir/$db.dump.tmp" "$dir/$db.dump"
        echo "$(date -u +%FT%TZ) respaldo $dir/$db.dump ($(wc -c < "$dir/$db.dump") bytes)"
    done
    find /backups -mindepth 1 -maxdepth 1 -type d -mtime +"$BACKUP_RETENTION_DAYS" -exec rm -rf {} +
}

until pg_isready -h "$PGHOST" -U "$PGUSER" -q; do sleep 2; done
# --once: un solo respaldo (respaldo manual o antes de una prueba de restauracion).
if [ "${1:-}" = "--once" ]; then
    respaldar
    exit 0
fi
while true; do
    respaldar || echo "$(date -u +%FT%TZ) ERROR en el respaldo" >&2
    sleep "$BACKUP_INTERVAL_SECONDS"
done
