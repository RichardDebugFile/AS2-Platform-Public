# Guía de contribución

## Flujo de ramas (GitFlow)

| Rama | Sale de | Vuelve a | Uso |
|---|---|---|---|
| `main` | — | — | Versiones liberadas. Cada merge lleva un *tag* `vX.Y.Z`. |
| `develop` | `main` | — | Integración del sprint en curso. |
| `feature/HU-XX-descripcion` | `develop` | `develop` (PR) | Una historia de usuario. El nombre lleva el ID de la HU (y, si se quiere, su clave de Jira `AS2-NN`) para la trazabilidad. |
| `release/X.Y.Z` | `develop` | `main` (PR) y luego `develop` | Cierre de sprint: versión y fecha en el CHANGELOG. |
| `hotfix/X.Y.Z` | `main` | `main` y `develop` | Corrección urgente de una versión liberada. |
| `chore/AS2-NN-…`, `docs/…`, `fix/AS2-NN-…` | `develop` | `develop` (PR) | Tareas menores sin HU, con su clave de Jira. |

Reglas:

* No se hace *push* directo a `main` ni a `develop` (lo impiden el *hook* `pre-push` y la protección
  de ramas en GitHub): todo entra por Pull Request con el CI en verde.
* Los PR se integran con **merge commit** (sin *squash*), para conservar en la historia los commits
  de cada HU.
* Cada historia de usuario vive en Jira con su historia, criterios de aceptación y subtareas
  (HU-XX = AS2-XX; en los sprints 0 y 1 fue un *issue* de GitHub). El título del PR lleva la HU y su
  clave (`HU-11 Gestión de certificados X.509 (AS2-11)`) y la descripción marca la Definición de
  Terminado. Las tareas que no son historias (`chore/AS2-NN-…`, `fix/AS2-NN-…`) llevan su clave.
* Cada sprint cierra con una `release/X.Y.Z` → `main`, un *tag* y una *release* de GitHub que lista
  las HU terminadas. Mientras no haya versión 1.0, la versión es `0.<sprint + 1>.0` (Sprint 0 → `0.1.0`).

## Commits — Conventional Commits

Formato `tipo(ámbito): resumen` en imperativo y en español; el cuerpo termina con la referencia a
la HU o a la tarea de Jira (`Refs: HU-11` o `Refs: AS2-75`) para poder rastrear cada cambio hasta su
historia. Tipos: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `ci`, `build`, `perf`.

```
feat(partners): carga de certificados X.509

Refs: HU-11 (AS2-64)
```

La referencia es obligatoria: el *hook* `commit-msg` rechaza el commit sin `HU-XX` ni `AS2-NN`, y el
CI aplica la misma regla a los commits de cada PR (`.github/scripts/check_commit_refs.sh`). No se
exige en los commits de fusión ni en las ramas `release/*` y `hotfix/*`.

## Trazabilidad con Jira y SonarQube Cloud

* El tablero Scrum vive en Jira (proyecto `AS2`). Cada HU tiene la misma numeración que su clave:
  HU-01 = AS2-1 … HU-23 = AS2-23.
* El workflow `Sonar` analiza cada PR en SonarQube Cloud y publica en la historia referenciada
  (`HU-XX` o `AS2-NN` en la rama, el título del PR o los commits) un comentario con el Quality Gate
  y las métricas, que se actualiza en cada nuevo análisis del mismo PR.
* Con el app *GitHub for Jira*, las ramas, commits y PR que llevan la clave `AS2-NN` aparecen en la
  historia.

## Primer uso del repositorio

```bash
git config core.hooksPath .githooks      # activa los hooks pre-commit, commit-msg y pre-push
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
