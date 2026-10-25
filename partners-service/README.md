# partners-service

Socios comerciales y su configuración AS2: identidad, conexión, algoritmos de firma y cifrado, acuses
(MDN) y las configuraciones de salida, entrada y MDN de cada socio. Puerto **8081**. Se llega a él a
través del gateway, que valida la sesión y le pasa el usuario en `X-User-Name` (la cabecera que mande
el cliente se descarta).

## API

| Método y ruta | Acceso | Qué hace |
|---|---|---|
| `GET /api/partners` · `GET /api/partners/inactive` | todos los roles | Socios activos o dados de baja, por identificador AS2 |
| `GET /api/partners/{id}` | todos los roles | Detalle (la clave básica nunca sale: solo `hasBasicAuthPassword`) |
| `POST /api/partners/complete` | ADMIN, OPERATOR | Alta en un paso: el socio y sus tres configuraciones (las que no vengan, por defecto). Atómica: si algo falla no queda nada |
| `POST /api/partners` | ADMIN, OPERATOR | Alta solo del socio |
| `PUT /api/partners/{id}` | ADMIN, OPERATOR | Edición; los campos en `null` conservan su valor y la clave vacía conserva la guardada |
| `PATCH /api/partners/{id}/status` | ADMIN, OPERATOR | `{"active": true\|false}` |
| `DELETE /api/partners/{id}` | ADMIN, OPERATOR | Baja lógica: el identificador queda libre para un socio nuevo |
| `DELETE /api/partners/{id}/purge?confirm=<as2Name>` | ADMIN | Borrado definitivo del socio con su configuración; exige escribir su identificador (la consola solo lo ofrece para socios dados de baja) |
| `GET\|PUT /api/partners/{id}/outgoing-config` · `…/incoming-config` · `…/mdn-config` | lectura todos; escritura ADMIN, OPERATOR | Configuración de salida, entrada y MDN |
| `GET /api/partners/{id}/algorithm-suggestion?legacyCompatibility=` | todos los roles | Algoritmos sugeridos (SHA-256/AES-256, o SHA-1/3DES para socios antiguos) |
| `GET /api/catalogs/algorithms` | todos los roles | Algoritmos admitidos con su estado (`recommended`, `ok`, `legacy`) |
| `GET /internal/partners/search?as2Name=` · `GET /internal/partners/filewatcher-enabled` | red interna | Consultas del motor AS2. El gateway no las enruta |
| `GET /actuator/health` | público | Salud del servicio |

Los algoritmos aceptan el nombre del enumerado o sus formas habituales (`sha-256`, `RSA-SHA256`,
`AES-256`, `3des`…). Los errores responden `{status, code, message}`: `PARTNER_NOT_FOUND` y
`CONFIG_NOT_FOUND` (404), `AS2_NAME_TAKEN` y `CONFLICT` (409), `VALIDATION` y `BAD_REQUEST` (400, con
la regla incumplida) y `PARTNER_FOLDERS_FAILED` (500).

## Reglas del socio

* **Identificador AS2** (`as2Name`): único entre los socios activos sin distinguir mayúsculas; un
  socio dado de baja libera el suyo. Como también nombra sus carpetas, se rechazan los caracteres
  `\ / : * ? " < > |`, el punto final y los nombres reservados de Windows (`CON`, `COM1`, `AUX.txt`…).
* **Servidor**: solo el nombre o la IP (IPv6 entre corchetes), sin protocolo; la dirección AS2 se
  arma con protocolo, servidor, puerto y ruta (`endpointUrl`).
* **MDN asíncrono**: exige una URL `https`.
* **Clave básica**: se guarda cifrada con AES-256-GCM (`PARTNERS_SECRET_KEY`).
* **Salida**: el modo `FILENAME_FILTER` exige el filtro; con `MOVE`, la carpeta de archivo por defecto
  es `Archive` del socio.
* Cada cambio queda registrado con su usuario (`created_by`, `updated_by`) y se publica como evento
  de auditoría; la bitácora persistente llega con HU-10.

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/partners` / `partners` / `partners` | Base de datos (Flyway crea el esquema) |
| `PARTNERS_SECRET_KEY` | vacío | Clave AES de 32 bytes en Base64 (`openssl rand -base64 32`). **Obligatoria en servidor**: vacía usa una clave temporal y las claves guardadas dejan de leerse al reiniciar |
| `AS2_STORAGE_ROOT` | `/opt/as2_data/partners` | Raíz de las carpetas: `<raíz>/<as2Name>/Outgoing\|Incoming\|MDN\|Archive` |

## Pruebas

```bash
docker compose up -d db              # desde la raíz (solo para las pruebas de PostgreSQL)
mvn -pl partners-service verify
```

Las pruebas de API recorren los criterios de aceptación de HU-08 sobre H2. Las del esquema real
(índice único parcial, borrado en cascada, restricciones) necesitan PostgreSQL: sin base local se
saltan; en el CI son obligatorias.

Colección de Postman: [`postman/partners-service.postman_collection.json`](postman/partners-service.postman_collection.json).
Contra el stack Docker, `host = https://localhost` y primero la carpeta "Sesión (vía gateway)".
Contra el servicio en local, `host = http://localhost:8081` y solo las carpetas "Socios" y "Motor AS2
(red interna)". Deja la base como estaba. Con Newman:

```bash
npx newman run partners-service/postman/partners-service.postman_collection.json \
  --env-var host=http://localhost:8081 --folder Socios --folder "Motor AS2 (red interna)"
```
