#!/bin/sh
# Prueba de restauracion: carga el respaldo mas reciente de una base en una base temporal y cuenta
# sus tablas. Uso (desde deploy/docker):
#   docker compose exec backup /usr/local/bin/restore-check.sh [base]
set -eu
db="${1:-auth}"
latest="$(ls -1d /backups/*/ 2>/dev/null | sort | tail -n 1)"
[ -n "$latest" ] && [ -f "$latest$db.dump" ] || { echo "No hay respaldo de $db" >&2; exit 1; }
target="restore_check_$db"
dropdb -h "$PGHOST" -U "$PGUSER" --if-exists "$target"
createdb -h "$PGHOST" -U "$PGUSER" "$target"
pg_restore -h "$PGHOST" -U "$PGUSER" --no-owner -d "$target" "$latest$db.dump"
tables="$(psql -h "$PGHOST" -U "$PGUSER" -d "$target" -tAc "select count(*) from information_schema.tables where table_schema = 'public'")"
dropdb -h "$PGHOST" -U "$PGUSER" "$target"
echo "Restaurado $latest$db.dump: $tables tablas"
[ "$tables" -gt 0 ]
