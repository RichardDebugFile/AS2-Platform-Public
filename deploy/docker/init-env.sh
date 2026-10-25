#!/usr/bin/env bash
# Prepara el entorno Docker una sola vez (Linux, macOS o Git Bash en Windows):
#   - .env con contrasenas aleatorias, el par RSA de los JWT y la clave AES del MFA;
#   - certs/tls.crt y certs/tls.key: certificado autofirmado para SERVER_NAME (en un servidor se
#     reemplazan por los de una CA publica, p. ej. Let's Encrypt, con los mismos nombres).
# No sobrescribe nada que ya exista. Ninguno de estos archivos se versiona.
set -euo pipefail
cd "$(dirname "$0")"
SERVER_NAME="${SERVER_NAME:-localhost}"
export MSYS_NO_PATHCONV=1   # Git Bash: no convertir "/CN=..." en una ruta de Windows

rand() { openssl rand -base64 32 | tr -d '/+=\n' | cut -c1-24; }
pem_body() { grep -v -- '-----' | tr -d '\r\n'; }

if [ ! -f .env ]; then
    tmp="$(mktemp -d)"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/jwt.pem" 2>/dev/null
    openssl pkey -in "$tmp/jwt.pem" -pubout -out "$tmp/jwt.pub" 2>/dev/null
    admin_password="$(rand)"
    ( umask 077; : > .env )
    cat >> .env <<ENV
# Generado por init-env.sh el $(date -u +%F). NO versionar.
SERVER_NAME=$SERVER_NAME
HTTP_PORT=80
HTTPS_PORT=443
POSTGRES_PASSWORD=$(rand)
AUTH_DB_PASSWORD=$(rand)
PARTNERS_DB_PASSWORD=$(rand)
DOCUMENTS_DB_PASSWORD=$(rand)
ALERTS_DB_PASSWORD=$(rand)
AUTH_JWT_PRIVATE_KEY_PEM=$(pem_body < "$tmp/jwt.pem")
AUTH_JWT_PUBLIC_KEY_PEM=$(pem_body < "$tmp/jwt.pub")
AUTH_MFA_SECRET_KEY=$(openssl rand -base64 32)
PARTNERS_SECRET_KEY=$(openssl rand -base64 32)
ADMIN_USER=admin
ADMIN_PASSWORD=$admin_password
ADMIN_EMAIL=admin@example.com
ENV
    rm -rf "$tmp"
    echo ".env creado. Usuario inicial: admin / $admin_password (cambiela tras el primer ingreso)."
else
    echo ".env ya existe: se conserva."
fi

# Claves que agregan historias posteriores: se anaden a un .env existente sin tocar lo demas.
grep -q '^PARTNERS_SECRET_KEY=' .env || echo "PARTNERS_SECRET_KEY=$(openssl rand -base64 32)" >> .env

mkdir -p certs backups
if [ ! -f certs/tls.crt ] || [ ! -f certs/tls.key ]; then
    openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
        -keyout certs/tls.key -out certs/tls.crt \
        -subj "/CN=$SERVER_NAME" \
        -addext "subjectAltName=DNS:$SERVER_NAME,DNS:localhost,IP:127.0.0.1" 2>/dev/null
    chmod 644 certs/tls.crt
    chmod 600 certs/tls.key   # lo lee el proceso maestro de nginx (root) al arrancar
    echo "Certificado autofirmado creado para $SERVER_NAME (certs/tls.crt)."
else
    echo "certs/ ya tiene certificado: se conserva."
fi
