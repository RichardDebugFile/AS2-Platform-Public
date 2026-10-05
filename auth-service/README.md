# auth-service

Identidad de la plataforma: usuarios, roles, inicio de sesión con JWT RS256, sesiones con renovación
y segundo factor TOTP. Puerto **8083**.

## API

| Método y ruta | Acceso | Qué hace |
|---|---|---|
| `POST /auth/login` | público | Usuario y contraseña. Abre sesión (cookies) o, con MFA activo, responde `{"status":"MFA_REQUIRED","mfaToken":…}` |
| `POST /auth/mfa/verify` | público | `{mfaToken, code}`: canjea el desafío MFA por la sesión |
| `POST /auth/refresh` | público (cookie) | Rota el par de tokens; el refresh usado queda revocado |
| `POST /auth/logout` | público (cookie) | Revoca el refresh y borra las cookies |
| `GET /.well-known/jwks.json` | público | Clave pública para validar los JWT |
| `GET /users/me` | autenticado | Datos del usuario de la sesión |
| `GET /users/me/mfa/status` · `POST …/setup` · `POST …/verify` · `POST …/disable` | autenticado | Alta y baja del segundo factor (`verify` y `disable` reciben `{code}`) |
| `GET /users?q=&page=&size=` | ADMIN | Listado paginado con búsqueda por usuario o correo |
| `POST /users` | ADMIN | Alta con roles (`ADMIN`, `OPERATOR`, `AUDITOR`; por defecto `OPERATOR`) |
| `PATCH /users/{id}/roles` · `PATCH /users/{id}/active` | ADMIN | Cambiar roles o activar/desactivar; si cambian, se cortan sus sesiones |
| `POST /auth/register` | ADMIN | Alta rápida de un `OPERATOR` |
| `GET /actuator/health` | público | Salud del servicio |

Los tokens viajan **solo** en cookies `HttpOnly; SameSite=Lax` (`access_token`, `refresh_token`), con
`Secure` cuando la petición llega por HTTPS (también detrás de un proxy, vía `X-Forwarded-Proto`). Los
clientes sin cookies pueden mandar el access token como `Authorization: Bearer` y el refresh en el
cuerpo (`{"refreshToken": …}`).

**CSRF**: como las cookies las adjunta el navegador solo, cada `POST`/`PUT`/`PATCH`/`DELETE` que use
cookies debe llevar la cabecera `X-XSRF-TOKEN` con el valor de la cookie legible `XSRF-TOKEN`, que el
servicio entrega en cualquier respuesta (basta un `GET`, p. ej. `/actuator/health`). Sin ella: 403. Las
peticiones con `Authorization: Bearer` no la necesitan: esa cabecera no la puede forjar otro sitio.

Los errores responden `{status, code, message}`. Códigos de 401: `UNAUTHORIZED`, `TOKEN_INVALID`,
`SESSION_EXPIRED`, `IDLE_EXPIRED`, `MFA_CHALLENGE_EXPIRED`, `MFA_INVALID_CODE`, `MFA_ATTEMPTS_EXCEEDED`.

## Sesión

* **Access token**: 5 min. Es el tiempo máximo que conserva acceso un usuario desactivado.
* **Inactividad**: 15 min sin renovar cierran la sesión (`IDLE_EXPIRED`).
* **Techo absoluto**: 60 min desde el login, aunque haya actividad (`SESSION_EXPIRED`).
* En base de datos se guarda el **hash SHA-256** del refresh token, nunca el token.
* Los refresh caducados hace más de un día se purgan cada hora.

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/auth` / `auth` / `auth` | Base de datos |
| `AUTH_ISSUER` | `http://localhost:8083` | Claim `iss` de los JWT |
| `AUTH_JWT_PRIVATE_KEY_PEM` / `AUTH_JWT_PUBLIC_KEY_PEM` | vacío | Par RSA en PEM (PKCS#8 y X.509). **Obligatorio en servidor**: vacío genera un par temporal y cada reinicio cierra todas las sesiones |
| `AUTH_MFA_SECRET_KEY` | vacío | Clave AES de 32 bytes en Base64 para cifrar los secretos TOTP. **Obligatoria en servidor**: vacía usa una clave temporal y los MFA dejan de verificarse al reiniciar |
| `AUTH_ACCESS_TTL_MIN` / `AUTH_IDLE_TIMEOUT_MIN` / `AUTH_SESSION_MAX_MIN` | `5` / `15` / `60` | Política de sesión (el arranque falla si alguno es ≤ 0) |
| `AUTH_MFA_ISSUER_LABEL` | `AS2 Platform` | Nombre de la cuenta en la app autenticadora |
| `AUTH_MFA_CHALLENGE_TTL_SEC` / `AUTH_MFA_MAX_ATTEMPTS` | `300` / `5` | Vigencia del desafío MFA y códigos fallidos permitidos |
| `AUTH_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes de la consola, separados por comas |
| `ADMIN_USER` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | `admin` / `admin123` / `admin@example.com` | Administrador inicial (solo se crea si no existe). **Cambiar la contraseña fuera de desarrollo** |

Generar las claves:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
openssl rand -base64 32        # AUTH_MFA_SECRET_KEY
```

Los `.pem` no se versionan (`.gitignore`).

## Pruebas

```bash
docker compose up -d db              # desde la raíz
mvn -pl auth-service verify
```

`AuthFlowIntegrationTest` recorre los escenarios de aceptación contra PostgreSQL. Sin base de datos
local se salta; en el CI es obligatoria.

Colección de Postman: [`postman/auth-service.postman_collection.json`](postman/auth-service.postman_collection.json)
(variable `baseUrl`, por defecto `http://localhost:8083`).
