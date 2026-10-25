#!/usr/bin/env bash
# Detiene la plataforma conservando los datos (bases, carpetas de los socios, respaldos).
#   ./stop.sh          detiene y quita los contenedores
#   ./stop.sh --purge  ademas BORRA los volumenes (bases de datos y carpetas de los socios)
set -euo pipefail
cd "$(dirname "$0")"
if [ "${1:-}" = "--purge" ]; then
    read -r -p "Se borraran las bases de datos y las carpetas de los socios. Escriba BORRAR para seguir: " ok
    [ "$ok" = "BORRAR" ] || { echo "Cancelado."; exit 1; }
    docker compose down -v
else
    docker compose down
    echo "Detenido. Los datos se conservan; para arrancar de nuevo: ./start.sh --no-build"
fi
