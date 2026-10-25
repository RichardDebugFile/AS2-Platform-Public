# Entorno de ejecución contenerizado

La plataforma corre en contenedores con Docker Compose. Se valida primero en local y el mismo
`docker-compose.yml` se usa luego en el servidor.

| Servicio | Qué es | Puertos publicados |
|---|---|---|
| `proxy` | nginx: TLS, redirección HTTP→HTTPS, consola web y proxy hacia el gateway | 80, 443 |
| `gateway` | Punto único de entrada (JWT, rutas, traza de acceso) | ninguno |
| `auth-service` | Identidad y sesiones | ninguno |
| `partners-service` | Socios comerciales y su configuración AS2 | ninguno |
| `db` | PostgreSQL 16 con una base y un usuario por servicio | ninguno |
| `backup` | `pg_dump` diario de cada base, conserva 7 días | ninguno |

Los servicios de documentos, alertas y AS2 se incorporan al stack con sus historias.

## Puesta en marcha

Requisito: Docker (Docker Desktop en Windows). En Windows, los scripts se ejecutan con Git Bash.

Todo en orden con un solo comando (secretos, imágenes, almacenamiento y arranque esperando a que
cada servicio esté sano):

```bash
cd deploy/docker
./start.sh              # ./start.sh --no-build si las imágenes ya están construidas
./stop.sh               # detiene conservando los datos (./stop.sh --purge los borra)
```

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
`.env`, `certs/`, `backups/` y `shared/` no se versionan.

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

El workflow `Docker` de GitHub Actions levanta el stack completo y ejecuta `smoke-test.sh` en cada
cambio de `deploy/docker/`.
