#!/usr/bin/env bash
# Levanta la plataforma completa en orden (Linux, macOS o Git Bash en Windows):
#   1. secretos y certificado (.env, certs/) si aun no existen;
#   2. construccion de las imagenes;
#   3. almacenamiento de las carpetas de los socios y del recurso compartido;
#   4. arranque, esperando a que cada servicio este sano (db -> auth -> socios -> gateway -> proxy).
# Se puede repetir sin riesgo: no sobrescribe secretos ni datos.
#   ./start.sh            construye y arranca
#   ./start.sh --no-build arranca con las imagenes ya construidas
set -euo pipefail
cd "$(dirname "$0")"

paso() { printf '\n[%s] %s\n' "$1" "$2"; }

paso 1/4 "Secretos y certificado"
./init-env.sh

if [ "${1:-}" != "--no-build" ]; then
    paso 2/4 "Construyendo las imagenes (la primera vez tarda varios minutos)"
    docker compose build
else
    paso 2/4 "Construccion omitida (--no-build)"
fi

paso 3/4 "Almacenamiento de las carpetas de los socios"
./prepare-storage.sh

paso 4/4 "Arrancando y esperando a que todos los servicios esten sanos"
docker compose up -d --wait --wait-timeout 300
docker compose ps --format 'table {{.Service}}\t{{.Status}}'

set -a; . ./.env; set +a
printf '\nListo. Consola: https://%s:%s  (usuario %s; la contrasena esta en deploy/docker/.env)\n' \
    "${SERVER_NAME:-localhost}" "${HTTPS_PORT:-443}" "${ADMIN_USER:-admin}"
echo "Comprobacion completa: ./smoke-test.sh    Detener: ./stop.sh"
