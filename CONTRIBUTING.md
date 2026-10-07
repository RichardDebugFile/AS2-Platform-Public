# Guía de contribución

## Flujo de ramas (GitFlow)

| Rama | Sale de | Vuelve a | Uso |
|---|---|---|---|
| `main` | — | — | Versiones liberadas. Cada merge lleva un *tag* `vX.Y.Z`. |
| `develop` | `main` | — | Integración del sprint en curso. |
| `feature/HU-XX-descripcion` | `develop` | `develop` (PR) | Una historia de usuario. El nombre lleva el ID de la HU (y, si se quiere, su clave de Jira `SCRUM-NN`) para la trazabilidad. |
| `release/X.Y.Z` | `develop` | `main` (PR) y luego `develop` | Cierre de sprint: versión y fecha en el CHANGELOG. |
| `hotfix/X.Y.Z` | `main` | `main` y `develop` | Corrección urgente de una versión liberada. |
| `chore/…`, `docs/…`, `fix/…` | `develop` | `develop` (PR) | Tareas menores sin HU. |

Reglas:

* No se hace *push* directo a `main` ni a `develop` (lo impiden el *hook* `pre-push` y la protección
  de ramas en GitHub): todo entra por Pull Request con el CI en verde.
* Los PR se integran con **merge commit** (sin *squash*), para conservar en la historia los commits
  de cada HU.
* Cada historia de usuario es un *issue* titulado `HU-XX: …` con su historia y criterios de
  aceptación. El título del PR lleva la HU (`HU-01: Fundación del monorepo y CI`) y la descripción
  la cierra (`Closes #N`) y marca la Definición de Terminado.
* Cada sprint cierra con una `release/X.Y.Z` → `main`, un *tag* y una *release* de GitHub que lista
  las HU terminadas. Mientras no haya versión 1.0, la versión es `0.<sprint + 1>.0` (Sprint 0 → `0.1.0`).

## Commits — Conventional Commits

Formato `tipo(ámbito): resumen` en imperativo y en español; el cuerpo termina con la referencia a
la HU (`Refs: HU-01`) para poder rastrear cada cambio hasta su historia. Tipos: `feat`, `fix`, `chore`, `docs`,
`test`, `refactor`, `ci`, `build`, `perf`.

```
feat(auth): login con JWT RS256 y cookies HttpOnly
fix(frontend): renovar la sesión antes de reintentar la petición
ci: ejecutar solo los módulos que cambian
```

## Trazabilidad con Jira y SonarQube Cloud

* El tablero Scrum vive en Jira (proyecto `SCRUM`). Correspondencia HU → clave: HU-01 = SCRUM-13 …
  HU-23 = SCRUM-35 (clave = 12 + n.º de HU).
* El workflow `Sonar` analiza cada PR en SonarQube Cloud y publica en la historia referenciada
  (`HU-XX` o `SCRUM-NN` en la rama, el título del PR o los commits) un comentario con el Quality Gate
  y las métricas, que se actualiza en cada nuevo análisis del mismo PR.
* Con el app *GitHub for Jira*, las ramas, commits y PR que llevan la clave `SCRUM-NN` aparecen en la
  historia.

## Primer uso del repositorio

```bash
git config core.hooksPath .githooks      # activa los hooks pre-commit y pre-push
winget install Gitleaks.Gitleaks         # (Windows) escaneo de secretos en el pre-commit
cp .gitleaks.local.toml.example .gitleaks.local.toml   # y poner los terminos privados reales
docker compose up -d db                  # PostgreSQL con las 4 bases
mvn -B verify                            # compila y prueba el backend
cd frontend && npm ci && npm test        # frontend
```

En Windows: `build.bat` compila, `start.bat` levanta la BD y los 6 servicios, `stop.bat` los detiene.

## Secretos y datos sensibles (gitleaks)

* `.gitleaks.toml` (público): reglas por defecto de gitleaks + IP internas + correos reales.
  Lo usan el *hook* `pre-commit` y el job `secrets` del CI.
* `.gitleaks.local.toml` (privado, ignorado por git): hereda lo anterior y añade los nombres reales
  de la empresa, socios, dominios, IP públicas e identificadores EDI. Nunca se publica, porque
  contiene justamente lo que no debe aparecer.
* Revisión completa bajo demanda: `gitleaks dir . -c .gitleaks.local.toml --redact`.

## Antes de abrir un Pull Request

Recorre la lista de la plantilla de PR (Definición de Terminado). El CI debe quedar en verde.
