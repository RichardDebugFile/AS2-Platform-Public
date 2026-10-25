# Consola web

Consola de operación de la plataforma: React 19 + TypeScript + Vite, Ant Design 5, Zustand
(un *store* por dominio), React Router 7 e i18next (español e inglés).

## Configuración en tiempo de ejecución

`public/config.js` define a qué servidor llama la consola; se edita en cada entorno sin recompilar:

```js
window.APP_CONFIG = {
  VITE_API_HOST: 'http://localhost:8765/api', // API de negocio (/api/...)
  VITE_API_AUTH: 'http://localhost:8765',     // /auth/* y /users/* (auth-service)
};
```

La cadena vacía significa "mismo origen" (consola y API detrás del mismo proxy, como en Docker).

## Sesión

* La sesión viaja en cookies `HttpOnly`: el token nunca pasa por JavaScript ni se guarda en el
  navegador. Las peticiones que modifican llevan la cabecera CSRF `X-XSRF-TOKEN`.
* **Renovación transparente**: ante un `401`, el cliente renueva la sesión una sola vez (aunque
  fallen varias peticiones a la vez) y reintenta sin que la pantalla lo note.
* **Inactividad**: los límites llegan del servidor en cada login y renovación. Con 15 min de
  inactividad, al minuto 14 aparece un aviso con cuenta regresiva y "Seguir conectado"; si no se
  responde, la sesión se cierra y el login explica el motivo. La duración máxima (60 min) avisa antes
  y no se puede extender.
* **Varias pestañas**: la actividad, los límites y el cierre se comparten por `localStorage`; una
  pestaña olvidada no cierra la sesión de la pestaña en uso, y un cierre se refleja en todas.
* La renovación en segundo plano solo ocurre si el usuario hizo algo desde la última: una sesión
  desatendida no se mantiene viva.

## Navegación

El menú (`src/routes/menu.ts`) se organiza por categorías (Operación, Socios, Documentos, Alertas,
Administración) y se filtra por rol: Administración solo la ve `ADMIN`, y sus rutas están además
protegidas por `RequireRole`. La seguridad real está en el servidor (gateway y auth-service).

## Socios comerciales

`/partners` (`src/pages/partners/`, store `partnersStore`): lista de socios activos o dados de baja
con búsqueda; alta en un paso (`POST /api/partners/complete`, crea también las configuraciones de
salida, entrada y MDN por defecto) y edición en un panel con pestañas Datos, Conexión, Seguridad AS2,
Salida, Entrada y MDN (cada configuración se guarda por separado). Todos los roles consultan;
`ADMIN` y `OPERATOR` registran, editan, dan de baja y reactivan; solo `ADMIN` borra definitivamente,
escribiendo el identificador AS2 para confirmar.

## Idiomas

Textos por espacios de nombres en `src/i18n/locales/{es,en}/` (`common`, `auth`, `session`,
`admin`, `partners`, `documents`, `alerts`). El idioma elegido se recuerda en el navegador. Una
prueba exige que español e inglés tengan exactamente las mismas claves.

## Comandos

```bash
npm ci
npm run dev            # http://localhost:5173
npm run lint
npm test               # Vitest + Testing Library
npm run test:coverage  # cobertura (frontend/coverage/lcov.info, la usa SonarQube)
npm run build
```
