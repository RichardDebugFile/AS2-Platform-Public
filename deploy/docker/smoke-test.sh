#!/usr/bin/env bash
# Verifica los criterios de aceptacion de HU-07 (y las carpetas de HU-09) sobre el entorno levantado
# con docker compose.
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
# Publishers incluye tambien los puertos solo expuestos (EXPOSE) con PublishedPort 0: no cuentan.
publicados="$(docker compose ps --format json | jq -rs 'flatten | .[] | select([.Publishers[]? | select(.PublishedPort > 0)] | length > 0) | .Service' | sort -u | tr '\n' ' ')"
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
code="$("${CURL[@]}" -b "$jar" -o /dev/null -w '%{http_code}' "$BASE/api/partners")"
[ "$code" = "200" ] && ok "Servicio de socios alcanzable por el gateway (/api/partners 200)" || fail "/api/partners con sesion respondio $code"

# 5b. Carpetas del socio (HU-09): arbol estandar al registrar, enlace verificado con el recurso compartido
xsrf="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar")"
api() { "${CURL[@]}" -b "$jar" -c "$jar" -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" "$@"; }
socio="SOCIO-SMOKE-$(date +%s)"; compartida="Smoke$(date +%s)"
alta="$(api -d "{\"partner\":{\"as2Name\":\"$socio\",\"serverAddress\":\"as2.socio.example\",\"port\":4443}}" \
    "$BASE/api/partners/complete")"
id="$(echo "$alta" | sed -n 's/.*"partner":{"id":"\([^"]*\)".*/\1/p')"
if [ -n "$id" ] && docker compose exec -T partners-service sh -c "for d in Archive Certs Error Incoming MDN Outgoing; do test -d /opt/as2_data/partners/$socio/\$d || exit 1; done"; then
    ok "Alta de socio crea su arbol de carpetas en el volumen"
else
    fail "Alta de socio o arbol de carpetas: $(echo "$alta" | head -c 200)"
fi
pre="$(api -d "{\"sharedFolderName\":\"$compartida\"}" "$BASE/api/partners/$id/folders/precheck")"
echo "$pre" | grep -q '"ok":false' && ok "Precheck sin la carpeta compartida: negativo" || fail "Precheck esperado negativo: $pre"
docker compose exec -T partners-service mkdir -p "/mnt/erp/$compartida"
enlace="$(api -d "{\"sharedFolderName\":\"$compartida\"}" "$BASE/api/partners/$id/folders/link")"
if echo "$enlace" | grep -q '"folderState":"SAMBA_LINKED"' \
    && docker compose exec -T partners-service test -L "/opt/as2_data/partners/$socio/Incoming"; then
    ok "Enlace con el recurso compartido: subcarpetas como enlaces simbolicos"
else
    fail "Enlace con el recurso compartido: $(echo "$enlace" | head -c 200)"
fi
code="$("${CURL[@]}" -b "$jar" -o /dev/null -w '%{http_code}' "$BASE/api/partners/folder-statuses")"
[ "$code" = "200" ] && ok "Estado de carpetas de todos los socios (folder-statuses 200)" || fail "folder-statuses respondio $code"
# Limpieza del socio de prueba (la bitacora conserva sus eventos).
api -X DELETE -o /dev/null "$BASE/api/partners/$id"
api -X DELETE -o /dev/null "$BASE/api/partners/$id/purge?confirm=$socio"
docker compose exec -T partners-service rm -rf "/opt/as2_data/partners/$socio" "/mnt/erp/$compartida"
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
# Respaldo nuevo antes de restaurar: el del arranque puede ser anterior a las migraciones de los
# servicios (la base aun sin tablas).
if ! salida="$(docker compose exec -T backup /bin/sh /usr/local/bin/backup.sh --once 2>&1)"; then
    fail "Respaldo bajo demanda: $(echo "$salida" | paste -sd '|' -)"
fi
if salida="$(docker compose exec -T backup /bin/sh /usr/local/bin/restore-check.sh auth 2>&1)"; then
    ok "$(echo "$salida" | tail -n 1)"
else
    fail "Restauracion de prueba: $(echo "$salida" | paste -sd '|' -)"
fi

echo
if [ "$fallos" -eq 0 ]; then echo "Todos los criterios de HU-07 se cumplen."; else echo "$fallos comprobaciones fallaron."; fi
exit "$fallos"
