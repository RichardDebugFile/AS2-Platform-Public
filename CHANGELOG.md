# Novedades de la plataforma AS2/EDI

Qué cambia en cada versión y para qué sirve. Escrito para quien administra o usa la plataforma.

---

## Sin publicar

### Novedades
- Registro de socios comerciales desde la consola: en un solo paso se da de alta el socio con su
  identificador AS2, la dirección de su servidor, los algoritmos de firma y cifrado, cómo recibe los
  acuses (MDN) y sus configuraciones de salida, entrada y MDN, sin tocar archivos ni reiniciar
  servicios. Si algo falla, no queda nada a medias. La consola sugiere los algoritmos recomendados
  (SHA-256 y AES-256) y ofrece los antiguos solo por compatibilidad. Un identificador repetido o que
  no sirve como nombre de carpeta se rechaza con un mensaje claro; dar de baja un socio libera su
  identificador, y solo un administrador puede borrarlo definitivamente. El Auditor consulta sin
  modificar, y cada cambio queda con el usuario que lo hizo (HU-08).
- Carpetas por socio e integración con la carpeta compartida del ERP: al registrar un socio se crea
  su árbol estándar de carpetas (y no queda nada si el alta falla). Desde la consola se verifica la
  carpeta de red del ERP antes de enlazarla —el botón "Enlazar" solo se habilita si todo está bien— y
  al enlazar, los archivos que hubiera se trasladan sin perder ninguno. Pasar un socio a producción
  (el ERP empieza a recibir sus documentos) es un paso que autoriza un administrador escribiendo el
  identificador del socio, y se puede revertir. Si el recurso compartido se desconecta, el socio se
  muestra en error con la explicación y la plataforma sigue funcionando (HU-09).
- Bitácora de auditoría consultable desde la consola: cada alta, cambio, baja o borrado de un socio
  y de su configuración queda registrado con quién lo hizo, cuándo y qué datos cambiaron (antes y
  después, sin mostrar nunca claves). Se filtra por socio, tipo de evento, estado, usuario, Message-ID
  y fechas, y se exporta a CSV. Buscar un Message-ID muestra la historia de ese intercambio en orden.
  Los registros no se pueden modificar ni borrar, y se conservan aunque el socio se elimine. El motor
  AS2 registra sus eventos sin detener nunca un envío si la bitácora no responde (HU-10).

### Correcciones
- El comentario de SonarQube Cloud en la historia de Jira muestra ahora las métricas del código
  nuevo de cada pull request (cobertura, incidencias, hotspots y duplicación), que antes salían
  vacías, y el número de pruebas de los servicios en el análisis de `main` (AS2-32).

## 0.3.0 — 2026-10-17

### Novedades
- Calidad y cobertura verificadas en SonarQube Cloud en cada pull request y en `develop`/`main`:
  el análisis incluye los seis servicios (cobertura JaCoCo) y la consola web (cobertura Vitest) y
  espera el Quality Gate. El resultado se publica como comentario en la historia de Jira
  referenciada, con enlace al análisis (AS2-32).
- Un único punto de entrada protege toda la plataforma: sin una sesión válida no se puede consultar
  ni modificar nada, salvo iniciar sesión y la recepción AS2 de los socios. El rol Auditor solo
  puede consultar. Cada petición queda registrada (método, ruta, resultado, tiempo e identificador
  de traza) sin guardar tokens ni contenido de los mensajes (HU-05).
- Consola web en español e inglés: inicio de sesión (con segundo factor si está activo), menú por
  categorías que muestra solo lo que permite cada rol y administración de usuarios. Un minuto antes
  de cerrarse por inactividad la sesión avisa con una cuenta regresiva y la opción "Seguir
  conectado"; trabajar en una pestaña mantiene viva la sesión en las demás y la sesión se renueva
  sola sin interrumpir lo que se está haciendo (HU-06).
- La plataforma se instala y ejecuta en contenedores Docker: un único punto de entrada por HTTPS
  (HTTP redirige), la consola y el API en el mismo sitio, servicios internos sin puertos
  expuestos, reinicio automático si un servicio falla y respaldo diario de las bases que se puede
  restaurar. Se prueba primero en local y se lleva igual al servidor (HU-07).

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
