# Novedades de la plataforma AS2/EDI

Qué cambia en cada versión y para qué sirve. Escrito para quien administra o usa la plataforma.

---

## Sin publicar

### Novedades
- Calidad y cobertura verificadas en SonarQube Cloud en cada pull request y en `develop`/`main`:
  el análisis incluye los seis servicios (cobertura JaCoCo) y la consola web (cobertura Vitest) y
  espera el Quality Gate. El resultado se publica como comentario en la historia de Jira
  referenciada, con enlace al análisis (SCRUM-48).

## 0.2.0 — 2026-10-04

### Plataforma
- Base tecnológica en Java 25 (LTS) y Spring Boot 4.1: versiones con soporte vigente para toda la vida
  del proyecto. Para compilar basta tener un JDK 25 instalado, aunque no sea el predeterminado del equipo.

### Sprint 1 — Identidad y acceso (HU-02, HU-03, HU-04)
- Inicio de sesión con usuario y contraseña. La sesión viaja en cookies que el navegador no deja leer
  a JavaScript y se renueva sola mientras se trabaja.
- La sesión se cierra tras 15 minutos sin actividad y, en cualquier caso, 60 minutos después del
  inicio. Al cerrarse, la consola puede explicar el motivo.
- Segundo factor opcional con una app autenticadora (Google o Microsoft Authenticator): con él
  activo, la contraseña sola no basta para entrar.
- Administración de usuarios con tres roles (Administrador, Operador, Auditor). Desactivar a alguien
  o cambiarle los roles le corta la sesión.

## 0.1.2 — 2026-09-27

### Cambios
- Actualizaciones automáticas de dependencias (Dependabot) en pausa: las versiones se actualizan de
  forma planificada, en una rama propia y con todas las pruebas. Las alertas de vulnerabilidades
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
