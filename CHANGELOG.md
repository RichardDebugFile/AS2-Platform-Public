# Novedades de la plataforma AS2/EDI

Qué cambia en cada versión y para qué sirve. Escrito para quien administra o usa la plataforma.

---

## Sin publicar

### Novedades
- La primera línea de cada commit lleva la clave de la tarea de Jira (`tipo(ámbito): AS2-NN resumen`)
  y el cuerpo termina con su historia (`Refs: HU-XX (AS2-NN)`), de modo que cada cambio se identifica
  también en el historial resumido y en la lista de commits de GitHub; el equipo del desarrollador y la
  verificación de cada pull request rechazan los que no cumplen el formato (AS2-92).
- Prueba de concepto del motor AS2 (AS2-94). El as2-adapter arma un mensaje AS2 1.2: firma el documento
  con SHA-256, lo cifra con AES-256 para el socio y pide un MDN firmado síncrono. Al recibir el MDN,
  verifica su firma y comprueba que el MIC coincide con el enviado. En `deploy/interop` hay un entorno
  con OpenAS2, un servidor AS2 de código abierto, que hace de socio, con certificados de prueba
  generados al arrancar. Sirve para comprobar la interoperabilidad antes de HU-15; todavía no se
  integra con la consola.
- Inicio muestra el tablero de la operación (AS2-99). Muestra las cifras del rango elegido (por defecto
  los últimos 7 días): documentos, correctos, con error y tasa de éxito. Incluye un gráfico de tráfico
  por día, el reparto de resultados, el tráfico por socio y el estado de cada servicio con su latencia.
  Se filtra por fechas, dirección y socio. Lo sirven `GET /api/documents/dashboard` y
  `GET /api/system/status` (el gateway consulta la salud de cada servicio).
- Pantalla "Estado del sistema" en Auditoría, para todos los roles (AS2-99). Muestra cada
  microservicio con su estado, su latencia y sus verificaciones internas (base de datos, espacio en
  disco, proceso activo…), y se revisa sola cada 30 segundos. Incluye el flujo de documentos: los de
  hoy por dirección y con error, el último entrante y el último saliente, y los 10 más recientes. Los
  servicios publican el estado de esas verificaciones sin sus detalles (`show-components: always`).
- Envío AS2 de prueba desde la consola (AS2-100). Está en el generador X12 y en el detalle de cada
  documento, para ADMIN y OPERATOR. Manda el documento al socio de prueba firmado y cifrado, y muestra
  el MDN: disposición, validez de la firma, coincidencia del MIC, Message-ID y tiempo de ida y vuelta.
  Si el motor AS2 o el socio no responden, lo indica con el motivo. El envío se publica en
  `POST /api/as2/test-send` por el gateway (sesión con ADMIN u OPERATOR). El as2-adapter entra al
  stack de `deploy/docker` con certificados de prueba; con `COMPOSE_PROFILES=demo` arranca además
  OpenAS2 como socio y el envío funciona de punta a punta. Sin el perfil, el envío responde 502.
- Si un servicio está detenido o caído, el gateway responde 503 con su nombre ("El servicio
  as2-adapter no responde") en lugar de un 500 genérico, y la consola lo muestra así.
- Colecciones de Postman de documents-service (X12, repositorio, tablero y consistencia) y del gateway
  (401 sin sesión, salud, CSRF, permisos por rol y rutas no publicadas). La de auth-service pasa a ir
  por el gateway y prueba el segundo factor completo calculando el código TOTP (AS2-101). Ninguna
  escribe credenciales: los usuarios y socios de prueba son aleatorios y se desactivan o borran al
  final. El workflow `Docker` las corre todas con Newman contra el stack con el perfil `demo`.

### Correcciones
- `build.bat` y `start.bat` buscan un Java 25 o superior en vez de usar el primero que encuentran: con
  Java 17 en el PATH, los servicios se detenían con `UnsupportedClassVersionError` (AS2-96).
- La base de datos de `start.bat` es un proyecto de Docker propio (`as2-platform-dev`). Compartía
  nombre de proyecto y volumen con la plataforma de `deploy/docker`: tomaba su base, con otras claves,
  y la dejaba sin base de datos (AS2-97).
- `start.bat` abre también la consola web cuando el gateway ya responde, con el navegador en
  `http://localhost:5173`, y `stop.bat` la detiene junto con los servicios (AS2-98).
- "Abrir en el visor", desde el generador o desde el detalle de un documento, interpreta el documento
  recibido; antes seguía mostrando el resultado del documento interpretado anteriormente. Si se cambia
  el texto del visor, el resultado anterior se oculta hasta volver a pulsar "Interpretar". Desde el
  detalle, el panel se cierra para dejar a la vista el visor (AS2-93).
- La paginación, los calendarios y los diálogos de confirmación salen en el idioma de la consola; en el
  navegador quedaban en inglés (AS2-93).
- Un administrador ya no puede desactivar su propia cuenta: el servidor lo rechaza y la consola no
  ofrece la acción en su fila (AS2-93).
- En la bitácora, el detalle, el tipo de evento, el socio y el usuario largos se parten en varias
  líneas en vez de ensanchar la tabla más allá de la pantalla (AS2-93).
- Las fechas tienen el mismo formato en la lista y en el detalle del repositorio y en la bitácora,
  tanto con el idioma detectado del navegador como con el elegido en el selector (AS2-93).
- Quien abre por la URL una pantalla que su rol no permite vuelve al inicio con un aviso que lo explica
  (AS2-93).

## 0.5.0 — 2026-11-01

### Novedades
- Certificados X.509 de cada socio con doble compuerta: desde la pestaña Certificados se carga el
  certificado (PEM o DER) de cifrado, firma o TLS y se ven su huella y su vigencia, con aviso de
  vencimiento. Al cargar el reemplazo, el nuevo queda como preferente y el anterior sigue activo como
  respaldo, de modo que el intercambio no se corta si el socio cambia su clave antes o después de lo
  anunciado. Cada día se retiran los caducados que ya tienen reemplazo; el único activo nunca se retira.
  Se rechazan los certificados caducados, los de una autoridad de certificación para cifrar y cualquier
  archivo con una clave privada. Solo un administrador elimina certificados, y cada operación queda en
  la bitácora (HU-11).
- Documentos X12 en la consola (Documentos EDI): el visor interpreta un documento tal como llega del
  socio (separadores, tipo, versión, emisor y receptor con sus calificadores, números de control) y lo
  valida contra el esquema de su tipo, indicando qué segmentos faltan o qué sobres no cierran; el
  generador crea órdenes de compra 850, acuses 997 (con emisor y receptor invertidos) y anuncios de
  promoción 889 con el ISA de ancho fijo y los conteos calculados. Un auditor puede usar el visor; solo
  administradores y operadores generan (HU-12).
- Repositorio de documentos (Documentos EDI → Repositorio): cada documento enviado o recibido queda
  registrado en la base de datos y guardado como archivo, ordenado por dirección, resultado, fecha,
  tipo y socio, con un nombre que incluye sus números de control. Una copia va a la carpeta del socio
  (Incoming los recibidos, Archive los enviados, Error los no válidos) para el ERP; si esa carpeta no
  está disponible, el documento no se pierde y la copia se reintenta cada hora. Si el socio reenvía un
  documento con el mismo Message-ID no se crea otro registro. La consola busca por socio, dirección,
  tipo, estado, fechas, Message-ID o control ISA, muestra el contenido segmento a segmento y dónde está
  guardado, y permite registrar documentos o guardar los generados; solo un administrador los elimina.
  Un control de consistencia avisa de archivos borrados a mano o sin registro (HU-14).
- Consola rediseñada con un sistema de diseño propio, documentado en Figma: barra lateral oscura con el
  menú por rol, cabecera con la sección y el título de la pantalla, colores de estado consistentes,
  tipografía Inter incluida en la consola (sin depender de internet) y pantallas de inicio de sesión,
  segundo factor, socios, certificados, bitácora y usuarios con la misma estructura (HU-24).
- Cada cambio del código queda vinculado con su historia: un commit sin la clave de su historia o
  tarea (`HU-XX` o `AS2-NN`) se rechaza en el equipo del desarrollador y en la verificación de cada
  pull request (AS2-75).

### Correcciones
- La bitácora muestra con su nombre los eventos de certificados (cargado, preferente, desactivado,
  retirado y los demás) en lugar del código interno (HU-11), y los del repositorio de documentos
  (creado, repetido, eliminado, copia pendiente y copia restaurada) (HU-14).
- Dependencias de desarrollo de la consola actualizadas por alertas de seguridad (vite, vitest,
  undici y zod); no forman parte de la consola publicada (AS2-76).
- La plataforma vuelve a levantarse en Windows con `start.sh`: la preparación de secretos terminaba
  sin mensaje en Git Bash, y la comprobación automática fallaba en el enlace con la carpeta
  compartida y en el respaldo. Si falta el archivo de secretos pero quedan datos de una instalación
  anterior, el arranque se detiene y explica cómo seguir, en lugar de dejar los servicios sin poder
  conectarse a la base (HU-07).
- El inicio de sesión en la consola desplegada con Docker en el puerto 443 respondía "Invalid CORS
  request": el navegador omite ese puerto al indicar el origen y solo se admitía con él. Ahora se
  admiten ambas formas, y la comprobación automática envía el origen como lo hace el navegador
  (HU-07).

---

## 0.4.0 — 2026-10-25

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
