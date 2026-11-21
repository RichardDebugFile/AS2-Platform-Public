#!/usr/bin/env bash
# Detiene la plataforma conservando los datos (bases, carpetas de los socios, respaldos).
#   ./stop.sh          detiene y quita los contenedores
#   ./stop.sh --purge  ademas BORRA los volumenes (bases de datos y carpetas de los socios)
# Detiene tambien los servicios de perfiles opcionales (OpenAS2 y Mailpit del perfil demo) aunque
# COMPOSE_PROFILES no este puesto: Compose ignora los de perfiles inactivos y la red quedaba en uso (AS2-103).
set -euo pipefail
cd "$(dirname "$0")"
export COMPOSE_PROFILES="*"
if [ "${1:-}" = "--purge" ]; then
    read -r -p "Se borraran las bases de datos y las carpetas de los socios. Escriba BORRAR para seguir: " ok
    [ "$ok" = "BORRAR" ] || { echo "Cancelado."; exit 1; }
    docker compose down -v
else
    docker compose down
    echo "Detenido. Los datos se conservan; para arrancar de nuevo: ./start.sh --no-build"
fi
