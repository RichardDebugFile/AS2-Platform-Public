# gateway

Punto único de entrada de la plataforma (Spring Cloud Gateway, reactivo). Es lo único que se publica
detrás del proxy TLS: enruta cada petición a su servicio, exige una sesión válida salvo en las rutas
públicas y registra una traza JSON de cada petición. Puerto **8765**.

## Rutas

| Prefijo | Destino |
|---|---|
| `/api/partners/**`, `/api/audit-logs/**`, `/api/catalogs/**` | partners-service :8081 |
| `/api/alerts/**`, `/api/smtp-config/**` | alerts-service :8082 |
| `/auth/**`, `/users/**`, `/.well-known/**` | auth-service :8083 |
| `/api/documents/**` | documents-service :8084 |
| `/api/as2/**`, `/api/mdn/**`, `/as2/receive` | as2-adapter :8085 |
| `POST /internal/as2/mdn-callback` → `/internal/as2/mdn` | documents-service :8084 |

## Seguridad en el borde

Dos cadenas de Spring Security:

* **Pública** (no inspecciona el token): `/auth/login`, `/auth/refresh`, `/auth/logout`,
  `/auth/mfa/**`, `/.well-known/**`, `/actuator/health`, `/as2/receive/**`,
  `/internal/as2/mdn-callback` y todo `OPTIONS` (preflight CORS). La lista es cerrada: añadir una
  ruta exige justificarla en el PR. `/auth/refresh` no valida el access token a propósito: un token
  caducado impediría justamente renovarlo. `POST /auth/register` **no** es público.
* **Protegida**: todo lo demás exige un JWT válido (firma RS256 contra el JWKS de auth-service y
  caducidad). El token se toma de la cabecera `Authorization: Bearer` o, si no viene, de la cookie
  `access_token`.

| Respuesta | Cuándo |
|---|---|
| `401` | Sin token, token caducado o firma inválida |
| `403` | Un `AUDITOR` intenta `POST`/`PUT`/`PATCH`/`DELETE` en `/api/**` (solo consulta), o alguien que no es `ADMIN` usa `/api/smtp-config/**` |
| `400` | Ruta sin normalizar (`//api/...`, segmentos `..`): no puede esquivar las reglas por rol |
| `500` | El JWKS de auth-service no responde (no es culpa del token, no se manda al login) |

Antes de reenviar, `CookieToHeaderFilter` pasa la cookie `access_token` a `Authorization: Bearer`
para los servicios (salvo en `/auth/**` y `/.well-known/**`). CORS es central (orígenes en
`APP_CORS_ALLOWED_ORIGINS`, con credenciales) y se respetan las cabeceras `X-Forwarded-*` del proxy,
necesarias para que auth-service marque las cookies como `Secure` bajo HTTPS.

## Traza de acceso

Cada petición, también las rechazadas, deja una línea JSON en la salida estándar:

```json
{"@timestamp":"…","logger_name":"access","message":"access","service":"gateway","traceId":"f988…",
 "method":"POST","path":"/as2/receive","status":204,"latencyMs":95,"clientIp":"203.0.113.7"}
```

El `X-Trace-Id` (el recibido si es válido, o uno nuevo) se propaga a los servicios y se devuelve en la
respuesta. Nunca se registran tokens, cookies, la consulta (`?…`) ni el cuerpo de los mensajes AS2.

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `GATEWAY_PORT` | `8765` | Puerto |
| `AUTH_JWKS_URI` | `http://localhost:8083/.well-known/jwks.json` | JWKS para validar el JWT |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes de la consola (patrones, separados por coma) |
| `PARTNERS_SERVICE_URL`, `ALERTS_SERVICE_URL`, `AUTH_SERVICE_URL`, `DOCUMENTS_SERVICE_URL`, `AS2_ADAPTER_URL` | `http://localhost:808x` | Destino de cada ruta (en Docker, el nombre del contenedor) |
| `ALERTS_INGEST_ENABLED`, `ALERTS_INGEST_TOKEN` | `false`, vacío | Envío de cada llamada a alerts-service (se activa con HU-21) |
| `LOG_FORMAT` | `logstash` | Formato del log; vacío = texto plano para depurar |

El perfil opcional `tls` (`SPRING_PROFILES_ACTIVE=tls`, `GATEWAY_KEYSTORE_PATH`,
`GATEWAY_KEYSTORE_PASSWORD`) termina TLS en el propio gateway cuando no hay proxy delante.

## Pruebas

```bash
mvn -pl gateway verify
```

`GatewaySecurityTest` recorre los criterios de aceptación de HU-05 (rutas públicas y protegidas,
roles, rutas sin normalizar) contra un destino simulado, sin necesitar los demás servicios.
