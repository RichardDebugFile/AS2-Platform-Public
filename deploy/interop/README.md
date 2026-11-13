# Prueba de interoperabilidad AS2

Spike **AS2-94**: comprueba que el motor AS2 de la plataforma (`as2-adapter`) se entiende con un
software AS2 que no es nuestro. Se usa [OpenAS2](https://github.com/OpenAS2/OpenAs2App), un servidor
AS2 de código abierto, como socio.

```
as2-adapter (PLATAFORMA)                          OpenAS2 (OPENAS2)
  850 → MIME → firma SHA-256 → cifrado AES-256 ──HTTP──▶ descifra, verifica la firma,
                                                         guarda el 850 y calcula el MIC
  verifica la firma del MDN y compara el MIC  ◀──MDN firmado (síncrono)──┘
```

Todo es de prueba:
- los certificados son autofirmados, con nombres `*.example`, y se generan la primera vez en el
  volumen `certs`;
- las claves de los almacenes son aleatorias y no se guardan en el repositorio;
- el entorno no usa la base de datos ni los volúmenes de `deploy/docker`.

## Requisitos

Docker Desktop con acceso a Docker Hub, GitHub y Maven Central. La primera vez se compila OpenAS2
desde su repositorio (versión fija `v4.15.0`) y la plataforma desde este; tarda varios minutos. La
imagen de OpenAS2 se arma con un Dockerfile propio (en `docker-compose.yml`) que no usa `apt-get`,
para no depender de los repositorios de Ubuntu.

## Uso

Desde esta carpeta (`deploy/interop`), en Git Bash:

```sh
docker compose up -d --build     # certificados, OpenAS2 y as2-adapter
docker compose ps                # openas2 y as2-adapter en "healthy"
./enviar.sh                      # envía el 850 de ejemplo
./enviar.sh ruta/archivo.edi     # o cualquier otro documento
```

`enviar.sh` muestra el resultado del MDN. El envío salió bien si:

| Campo | Valor esperado |
|---|---|
| `delivered` | `true` |
| `disposition` | termina en `processed` |
| `mdnSigned` y `mdnSignatureValid` | `true`: OpenAS2 firmó el MDN con su clave |
| `micMatches` | `true`: `micSent` y `micReceived` son iguales (OpenAS2 recibió exactamente lo enviado) |
| `originalMessageId` | igual a `messageId` |

El documento recibido queda en `salida/openas2/recibidos/PLATAFORMA/`, y los registros de OpenAS2, en
`docker compose logs openas2`.

Para detener: `docker compose down`. No uses `-v`, porque borraría los certificados de prueba, que se
reutilizan en el siguiente arranque.

## Alcance

Es una prueba de concepto. Hoy solo cubre el envío con MDN síncrono, desde un endpoint
(`POST /api/as2/test-send`) que existe solo con `AS2_SPIKE_ENABLED=true` (este entorno y el perfil
`demo` de `deploy/docker`). Lo que llega después:

| Historia | Qué agrega |
|---|---|
| HU-15 | Envío desde la consola y el repositorio de documentos, con los socios y certificados reales |
| HU-16 | Recepción |
| HU-18 | Gestión de MDN |
