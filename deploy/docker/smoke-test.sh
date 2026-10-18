#!/usr/bin/env bash
# Verifica los criterios de aceptacion de HU-07 sobre el entorno levantado con docker compose.
# Uso: ./smoke-test.sh   (desde deploy/docker, con el entorno arriba)
# En GitHub Actions cada resultado queda ademas como anotacion del job.
set -uo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
HTTP_PORT="${HTTP_PORT:-80}"; HTTPS_PORT="${HTTPS_PORT:-443}"
BASE="https://localhost:$HTTPS_PORT"
CURL=(curl -sS --max-time 15 --cacert certs/tls.crt)
fallos=0

ok()   { echo "OK    $1"; [ -n "${GITHUB_ACTIONS:-}" ] && echo "::notice title=HU-07 OK::$1"; return 0; }
fail() { echo "FALLA $1"; [ -n "${GITHUB_ACTIONS:-}" ] && echo "::error title=HU-07 FALLA::$1"; fallos=$((fallos + 1)); }

# 1. HTTP redirige a HTTPS
loc="$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' "http://localhost:$HTTP_PORT/partners")"
[[ "$loc" == "301 https://localhost:$HTTPS_PORT/partners" ]] && ok "HTTP redirige a HTTPS ($loc)" || fail "HTTP no redirige a HTTPS: $loc"

# 2. HTTPS con certificado valido para el nombre (se valida contra certs/tls.crt) y la consola responde
if "${CURL[@]}" "$BASE/" | grep -q 'id="root"'; then ok "HTTPS con certificado valido sirve la consola"; else fail "HTTPS o certificado invalido"; fi
hsts="$("${CURL[@]}" -o /dev/null -D - "$BASE/" | tr -d '\r' | grep -i '^strict-transport-security' || true)"
[ -n "$hsts" ] && ok "Cabecera HSTS presente" || fail "Falta la cabecera HSTS"

# 3. Solo se publican los puertos del proxy
publicados="$(docker compose ps --format '{{.Service}} {{.Publishers}}' | awk '$2 != "[]" {print $1}' | sort -u | tr '\n' ' ')"
[ "$publicados" = "proxy " ] && ok "Solo el proxy publica puertos" || fail "Servicios con puertos publicados: $publicados"
for p in 5432 8083 8765; do
    if (exec 3<>"/dev/tcp/127.0.0.1/$p") 2>/dev/null; then fail "El puerto $p es alcanzable desde fuera"; else ok "Puerto $p cerrado desde fuera"; fi
done

# 4. API protegida por el gateway a traves del proxy
code="$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/api/partners")"
[ "$code" = "401" ] && ok "API sin sesion responde 401" || fail "API sin sesion respondio $code"

# 5. Login por HTTPS: cookies de sesion con Secure
jar="$(mktemp)"
"${CURL[@]}" -c "$jar" -o /dev/null "$BASE/.well-known/jwks.json"
xsrf="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar")"
headers="$("${CURL[@]}" -b "$jar" -c "$jar" -o /dev/null -D - -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" \
    -d "{\"username\":\"${ADMIN_USER:-admin}\",\"password\":\"$ADMIN_PASSWORD\"}" "$BASE/auth/login" | tr -d '\r')"
status="$(echo "$headers" | awk 'NR==1 {print $2}')"
access="$(echo "$headers" | grep -i '^set-cookie: access_token=' || true)"
if [ "$status" = "200" ] && echo "$access" | grep -qi 'secure' && echo "$access" | grep -qi 'httponly'; then
    ok "Login por HTTPS: cookie access_token con Secure y HttpOnly"
else
    fail "Login por HTTPS (estado $status): $access"
fi
code="$("${CURL[@]}" -b "$jar" -o /dev/null -w '%{http_code}' "$BASE/users/me")"
[ "$code" = "200" ] && ok "Con la cookie de sesion el gateway deja pasar (/users/me 200)" || fail "/users/me con sesion respondio $code"
rm -f "$jar"

# 6. Reinicio automatico del gateway tras un fallo (el proceso termina)
antes="$(docker inspect -f '{{.RestartCount}}' "$(docker compose ps -q gateway)")"
docker compose exec -T gateway sh -c 'kill 1' >/dev/null 2>&1
inicio=$(date +%s); sano=""
for _ in $(seq 1 60); do
    sleep 2
    estado="$(docker inspect -f '{{.State.Health.Status}} {{.RestartCount}}' "$(docker compose ps -q gateway)" 2>/dev/null)"
    if [ "${estado%% *}" = "healthy" ] && [ "${estado##* }" -gt "$antes" ]; then sano=1; break; fi
done
if [ -n "$sano" ]; then ok "El gateway se reinicio solo y volvio a UP en $(( $(date +%s) - inicio )) s"; else fail "El gateway no volvio a UP tras el fallo"; fi

# 7. Respaldo: existe el dump del dia y se restaura en una base de prueba
hoy="$(date -u +%F)"
for _ in $(seq 1 15); do [ -f "backups/$hoy/auth.dump" ] && break; sleep 2; done
if [ -f "backups/$hoy/auth.dump" ]; then ok "Respaldo del dia presente (backups/$hoy)"; else fail "No hay respaldo del dia"; fi
if salida="$(docker compose exec -T backup /bin/sh /usr/local/bin/restore-check.sh auth 2>&1)"; then ok "$salida"; else fail "Restauracion de prueba: $salida"; fi

echo
if [ "$fallos" -eq 0 ]; then echo "Todos los criterios de HU-07 se cumplen."; else echo "$fallos comprobaciones fallaron."; fi
exit "$fallos"
