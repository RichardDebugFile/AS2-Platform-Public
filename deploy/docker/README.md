# Entorno de ejecución contenerizado

La plataforma corre en contenedores con Docker Compose. Se valida primero en local y el mismo
`docker-compose.yml` se usa luego en el servidor.

| Servicio | Qué es | Puertos publicados |
|---|---|---|
| `proxy` | nginx: TLS, redirección HTTP→HTTPS, consola web y proxy hacia el gateway | 80, 443 |
| `gateway` | Punto único de entrada (JWT, rutas, traza de acceso) | ninguno |
| `auth-service` | Identidad y sesiones | ninguno |
| `partners-service` | Socios comerciales y su configuración AS2 | ninguno |
| `documents-service` | Repositorio de documentos EDI y validación X12 | ninguno |
| `as2-adapter` | Motor AS2 (envío y recepción con firma, cifrado y MDN) | ninguno |
| `as2-certs` | Generación de certificados de prueba en el volumen `as2certs` | ninguno |
| `openas2` | Servidor socio AS2 de prueba (solo con perfil `demo`) | ninguno |
| `db` | PostgreSQL 16 con una base y un usuario por servicio | ninguno |
| `backup` | `pg_dump` diario de cada base, conserva 7 días | ninguno |

Alertas se incorpora al stack con su historia.

## Puesta en marcha

Requisito: Docker (Docker Desktop en Windows). En Windows, los scripts se ejecutan con Git Bash.

Todo en orden con un solo comando (secretos, imágenes, almacenamiento y arranque esperando a que
cada servicio esté sano):

```bash
cd deploy/docker
./start.sh              # ./start.sh --no-build si las imágenes ya están construidas
./stop.sh               # detiene conservando los datos (./stop.sh --purge los borra)
```

### Perfil demo (OpenAS2)

Para probar el envío AS2 de punta a punta con un servidor socio real de código abierto (OpenAS2):

```bash
COMPOSE_PROFILES=demo ./start.sh
```

Con este perfil se levanta el servicio `openas2` con certificados de prueba y los documentos recibidos
quedan en `salida/openas2/recibidos/PLATAFORMA/`.

Sin el perfil `demo`, la plataforma arranca normalmente; el servicio `as2-adapter` está activo pero
un intento de envío responderá 502 Bad Gateway ("No se pudo conectar…").

O paso a paso:

```bash
cd deploy/docker
./init-env.sh                  # una vez: .env con secretos aleatorios y certificado TLS (certs/)
docker compose build           # construye las imágenes
./prepare-storage.sh           # una vez: carpetas de los socios y recurso compartido del ERP
docker compose up -d           # arranca
./smoke-test.sh                # comprueba los criterios de aceptación
```

Consola: `https://localhost` (usuario `admin`; la contraseña la muestra `init-env.sh` y queda en
`.env`). En local el certificado es autofirmado: el navegador pide confirmarlo una vez.

`init-env.sh` no sobrescribe nada que exista y `prepare-storage.sh` se puede repetir sin riesgo.
`.env`, `certs/`, `backups/` y `shared/` no se versionan. El `.env` va unido al volumen de la base:
si se pierde, `init-env.sh` se detiene en lugar de generar contraseñas que la base no aceptaría.

En Git Bash, los comandos manuales que pasan rutas del contenedor a `docker` (por ejemplo
`/bin/sh`) llevan delante `MSYS_NO_PATHCONV=1`, para que no se conviertan en rutas de Windows.

## Comportamiento

* **Solo HTTPS**: el puerto 80 redirige al 443. TLS 1.2/1.3 y cabeceras HSTS, `nosniff`,
  `X-Frame-Options` y `Referrer-Policy`.
* **Cookies `Secure`**: nginx informa `X-Forwarded-Proto: https` y el gateway lo hace llegar a
  auth-service, que marca así las cookies de sesión.
* **Reinicio automático**: todos los contenedores tienen `restart: unless-stopped` y comprobación de
  salud; si un servicio termina por un fallo, Docker lo vuelve a levantar.
* **Logs**: rotación por contenedor (10 MB × 14 archivos).
* **Respaldo**: el contenedor `backup` deja `backups/<fecha>/<base>.dump` al arrancar y cada 24 h,
  y borra los de más de 7 días. Respaldo manual y prueba de restauración en una base temporal:

  ```bash
  docker compose exec backup /bin/sh /usr/local/bin/backup.sh --once
  docker compose exec backup /bin/sh /usr/local/bin/restore-check.sh auth
  ```

## Carpetas de los socios y recurso compartido del ERP

* **Volumen `as2data`** → `/opt/as2_data/partners` en partners-service: el árbol
  `<socio>/{Archive,Certs,Error,Incoming,MDN,Outgoing}` de cada socio. Sobrevive a recrear los
  contenedores (`docker compose down -v` lo borra).
* **Recurso compartido** → `/mnt/erp`: la carpeta de red donde el ERP deja y recoge archivos. En
  local es `./shared`; en el servidor se monta el recurso Samba/CIFS en el host y se indica su ruta en
  `ERP_SHARED_PATH`. El montaje debe permitir escritura al usuario de los contenedores.
* `prepare-storage.sh` crea lo que falte y deja ambos con dueño ese usuario; avisa si el montaje no
  permite escribir. Desde la consola se verifica y se enlaza cada socio con su carpeta del recurso.

## Variables (`.env`)

| Variable | Uso |
|---|---|
| `SERVER_NAME`, `HTTP_PORT`, `HTTPS_PORT` | Nombre público y puertos del proxy |
| `POSTGRES_PASSWORD`, `*_DB_PASSWORD` | Superusuario y usuario de cada base |
| `AUTH_JWT_PRIVATE_KEY_PEM`, `AUTH_JWT_PUBLIC_KEY_PEM` | Par RSA de los JWT (fijo: reiniciar no cierra sesiones) |
| `AUTH_MFA_SECRET_KEY` | Clave AES de los secretos MFA |
| `PARTNERS_SECRET_KEY` | Clave AES de las claves de autenticación básica de los socios (se añade a un `.env` existente) |
| `ADMIN_USER`, `ADMIN_PASSWORD`, `ADMIN_EMAIL` | Administrador inicial |
| `BACKUP_INTERVAL_SECONDS`, `BACKUP_RETENTION_DAYS` | Frecuencia y retención del respaldo (opcionales) |
| `ERP_SHARED_PATH` | Ruta del host donde está montado el recurso compartido del ERP (opcional; por defecto `./shared`) |

## En un servidor

Mismo procedimiento con `SERVER_NAME` = dominio público. Se reemplazan `certs/tls.crt` y
`certs/tls.key` por los de una CA pública (por ejemplo, Let's Encrypt) y se reinicia `proxy`. El
cortafuegos solo debe abrir 22 (SSH por clave), 80 y 443.

## Verificación automática

El workflow `Docker` de GitHub Actions levanta el stack completo con el perfil `demo`. Ejecuta
`smoke-test.sh` y después las colecciones de Postman de todos los servicios con Newman, en cada
cambio de `deploy/docker/` o de una colección.

## API con Newman

Con la plataforma levantada (`COMPOSE_PROFILES=demo ./start.sh`), desde la raíz del repositorio y
con la contraseña del administrador de `.env`:

```bash
pw="$(grep '^ADMIN_PASSWORD=' deploy/docker/.env | cut -d= -f2-)"
for c in gateway auth-service documents-service as2-adapter; do
  npx -y newman@6.2.1 run "$c/postman/$c.postman_collection.json" --insecure \
    --env-var host=https://localhost --env-var adminUser=admin --env-var "adminPassword=$pw"
done
npx -y newman@6.2.1 run partners-service/postman/partners-service.postman_collection.json --insecure \
  --env-var host=https://localhost --env-var adminUser=admin --env-var "adminPassword=$pw" \
  --folder "Sesión (vía gateway)" --folder Socios --folder Bitácora --folder Certificados
```

| Colección | Qué comprueba |
|---|---|
| `gateway` | 401 sin sesión, salud de cada servicio con sus verificaciones internas, protección CSRF, permisos de AUDITOR y rutas que el proxy no publica |
| `auth-service` | Login, renovación y cierre de sesión, administración de usuarios y el segundo factor completo (la colección calcula el código TOTP como la app autenticadora) |
| `documents-service` | Interpretar, validar y generar X12; registro en el repositorio, reenvío con el mismo Message-ID, detalle, contenido, tablero y consistencia; permisos de AUDITOR |
| `as2-adapter` | Envío AS2 de prueba a OpenAS2 con MDN firmado (necesita el perfil `demo`), 403 con AUDITOR y 401 sin sesión |
| `partners-service` | Socios, bitácora y certificados con doble compuerta ("Carpetas" necesita además la carpeta compartida del ERP) |

Los usuarios, socios y documentos que crean llevan nombres aleatorios y se desactivan o se borran al
final de cada colección; ninguna escribe credenciales en el repositorio.
