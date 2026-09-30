# Novedades de la plataforma AS2/EDI

Qué cambia en cada versión y para qué sirve. Escrito para quien administra o usa la plataforma.

---

## Sin publicar

### Novedades
- Calidad y cobertura verificadas en SonarQube Cloud en cada pull request y en `develop`/`main`:
  el análisis incluye los seis servicios (cobertura JaCoCo) y la consola web (cobertura Vitest) y
  espera el Quality Gate. El resultado se publica como comentario en la historia de Jira
  referenciada, con enlace al análisis (SCRUM-48).

## 0.1.2 — 2026-09-27

### Cambios
- Actualizaciones automáticas de dependencias (Dependabot) en pausa: las versiones se mantienen
  alineadas con el sistema de origen y se actualizan allí primero. Las alertas de vulnerabilidades
  siguen activas.

## 0.1.1 — 2026-09-27

### Arreglos
- Documentación y entorno local con nombres neutros: la descripción del proyecto se centra en el
  producto y el `docker-compose` usa un nombre de proyecto fijo (`as2-platform`) con contenedores
  `as2platform-db` y `as2platform-pgadmin`. Para aplicarlo en un entorno ya levantado:
  `docker compose -p <nombre-anterior> down` y luego `docker compose up -d db`.

## 0.1.0 — 2026-09-27

### Sprint 0 — Fundaciones (HU-01)
- Repositorio único con los seis servicios (gateway, autenticación, socios, documentos, alertas y
  motor AS2) y la consola web; cada uno arranca y responde a la comprobación de salud.
- Base de datos local con una base por servicio, lista con un solo comando.
- Integración continua: cada cambio se compila, se prueba y se revisa en busca de secretos y
  dependencias vulnerables antes de integrarse.
