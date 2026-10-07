# Plataforma AS2/EDI

[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=RichardDebugFile_TESIS-as2-platform&metric=alert_status)](https://sonarcloud.io/summary/overall?id=RichardDebugFile_TESIS-as2-platform)
[![Cobertura](https://sonarcloud.io/api/project_badges/measure?project=RichardDebugFile_TESIS-as2-platform&metric=coverage)](https://sonarcloud.io/component_measures?id=RichardDebugFile_TESIS-as2-platform&metric=coverage)

Plataforma de mensajería B2B para intercambiar documentos EDI **ANSI X12** (órdenes de compra 850,
acuses 997, avisos de despacho 856, facturas 810…) con socios comerciales mediante el protocolo
**AS2** (RFC 4130): mensajes firmados y cifrados con S/MIME y acuse de recibo firmado (MDN).

Arquitectura de microservicios Spring Boot con una consola web React.

## Módulos

| Carpeta | Responsabilidad | Puerto |
|---|---|---|
| `gateway/` | Punto único de entrada: enrutamiento, seguridad en el borde | 8765 |
| `auth-service/` | Usuarios, roles, JWT, sesiones y MFA ([API y configuración](auth-service/README.md)) | 8083 |
| `partners-service/` | Socios comerciales, configuración AS2, certificados, carpetas, auditoría | 8081 |
| `documents-service/` | Repositorio de documentos EDI y MDN | 8084 |
| `alerts-service/` | Reglas de alerta y notificaciones | 8082 |
| `as2-adapter/` | Motor AS2 (S/MIME, MDN), X12 y envío automático desde carpetas | 8085 |
| `frontend/` | Consola web (React + TypeScript + Vite) | 5173 (dev) |
| `db/init/` | Creación de las bases de datos (una por servicio) | — |

## Tecnología

Java 25 · Spring Boot 4.1 · Spring Cloud Gateway · PostgreSQL 16 + Flyway · React 19 · TypeScript ·
Vite · Vitest · GitHub Actions (CI, gitleaks, Trivy, CodeQL) · SonarQube Cloud.

## Puesta en marcha (desarrollo)

Requisitos: JDK 25, Maven 3.9, Node 22, Docker. Maven selecciona el JDK 25 instalado aunque
`JAVA_HOME` apunte a otra versión (*toolchains*); los scripts `.bat` usan `JAVA_HOME` si está definido.

```bash
git config core.hooksPath .githooks   # hooks de calidad (una vez por clon)
docker compose up -d db               # PostgreSQL con las bases auth, partners, documents, alerts
mvn -B verify                         # compila, prueba y genera cobertura (target/site/jacoco)
cd frontend && npm ci && npm run lint && npm test && npm run build
```

Windows: `build.bat` → `start.bat` (base de datos + 6 servicios, cada uno en su ventana) → `stop.bat`.
Salud de cada servicio: `http://localhost:<puerto>/actuator/health`.

## Calidad y trazabilidad

* **SonarQube Cloud** (`.github/workflows/sonar.yml`, `sonar-project.properties`): cada PR y cada push a
  `develop`/`main` construye y prueba todo, sube la cobertura (JaCoCo y Vitest) y espera el Quality Gate.
  Cobertura local del frontend: `cd frontend && npm run test:coverage`.
* **Jira** (proyecto `SCRUM`): el mismo workflow comenta el resultado en la historia cuya clave
  (`SCRUM-XX`) o identificador (`HU-XX`) aparece en la rama, el título del PR o los commits.
  Con el app *GitHub for Jira*, las ramas, commits, PR y ejecuciones aparecen en cada historia.

## Contribuir

Flujo GitFlow, convenciones de commits, hooks y revisión de secretos: [CONTRIBUTING.md](CONTRIBUTING.md).
Las historias de usuario se siguen como *issues* (`HU-XX`) y cada PR enlaza la suya.
Cambios por versión: [CHANGELOG.md](CHANGELOG.md).
