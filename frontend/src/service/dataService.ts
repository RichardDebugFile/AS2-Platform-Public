// Cliente HTTP de la consola. Las cookies HttpOnly de sesion viajan con credentials: "include";
// el token nunca pasa por JavaScript.
//
// Renovacion transparente (HU-06): ante un 401 fuera de /auth/*, se renueva la sesion UNA vez y se
// reintenta la peticion. Quien sabe renovar es el store de autenticacion, que se registra con
// setSessionHandler (asi este modulo no depende del store y se prueba por separado).

declare global {
    interface Window {
        APP_CONFIG?: {
            VITE_API_HOST?: string;
            VITE_API_AUTH?: string;
        };
    }
}

export interface ApiError {
    code: string;
    message: string;
    status: number;
}

export type ApiResponse<T = unknown> = { ok: true; data: T } | { ok: false; error: ApiError };

interface Hosts {
    api?: string;
    auth?: string;
}

// config.js (en tiempo de ejecucion) manda sobre las variables de build: asi se cambia la URL del
// servidor sin recompilar. La cadena vacia es valida: significa "mismo origen".
function hosts(): Hosts {
    const cfg = typeof window === "undefined" ? undefined : window.APP_CONFIG;
    return {
        api: cfg?.VITE_API_HOST ?? import.meta.env.VITE_API_HOST,
        auth: cfg?.VITE_API_AUTH ?? import.meta.env.VITE_API_AUTH,
    };
}

/** /auth/*, /users/* y /.well-known/* los atiende auth-service; el resto, la API de negocio. */
export function resolveHost(endpoint: string): string | undefined {
    const { api, auth } = hosts();
    return endpoint.startsWith("/auth") || endpoint.startsWith("/users") || endpoint.startsWith("/.well-known")
        ? auth
        : api;
}

function errorFrom(json: unknown, status: number): ApiError {
    const obj = json && typeof json === "object" ? (json as Record<string, unknown>) : {};
    return {
        code: typeof obj.code === "string" ? obj.code : `HTTP_${status}`,
        message: typeof obj.message === "string" ? obj.message : `Error HTTP ${status}`,
        status,
    };
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * Token CSRF: auth-service lo deja en la cookie legible XSRF-TOKEN y exige que vuelva en la cabecera
 * X-XSRF-TOKEN en cada peticion que modifica. Otro sitio no puede leer la cookie, asi que no puede
 * forjar la cabecera aunque el navegador adjunte las cookies de sesion.
 */
export function xsrfToken(): string | undefined {
    if (typeof document === "undefined") return undefined;
    const match = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
    return match ? decodeURIComponent(match.slice("XSRF-TOKEN=".length)) : undefined;
}

/** Lo que el cliente necesita de la sesion para renovarla ante un 401. */
export interface SessionHandler {
    /** Renueva la sesion (POST /auth/refresh). Debe resolver siempre, nunca rechazar. */
    refresh: () => Promise<void>;
    /** Si tras renovar sigue habiendo sesion. */
    isAuthenticated: () => boolean;
}

let sessionHandler: SessionHandler | null = null;

export function setSessionHandler(handler: SessionHandler | null): void {
    sessionHandler = handler;
}

// Un solo refresh aunque fallen varias peticiones a la vez: el servidor rota el refresh token y un
// segundo refresh simultaneo llegaria con un token ya revocado y cerraria una sesion valida.
let refreshing: Promise<void> | null = null;

function renewSession(handler: SessionHandler): Promise<void> {
    if (!refreshing) {
        refreshing = handler.refresh().finally(() => {
            refreshing = null;
        });
    }
    return refreshing;
}

const SESSION_EXPIRED: ApiError = {
    code: "UNAUTHORIZED",
    message: "La sesión expiró. Inicie sesión de nuevo.",
    status: 401,
};

/**
 * En un navegador recien llegado aun no existe la cookie XSRF-TOKEN, y el primer POST (el login)
 * seria rechazado con 403. auth-service la entrega en cualquier respuesta: se pide su JWKS (publico
 * y barato) una vez y se lee la cookie.
 */
let xsrfPriming: Promise<string | undefined> | null = null;

function fetchXsrfToken(): Promise<string | undefined> {
    const base = resolveHost("/.well-known/jwks.json");
    if (typeof base !== "string") return Promise.resolve(undefined);
    if (!xsrfPriming) {
        xsrfPriming = fetch(`${base}/.well-known/jwks.json`, { credentials: "include" })
            .then(() => xsrfToken())
            .catch(() => undefined)
            .finally(() => {
                xsrfPriming = null;
            });
    }
    return xsrfPriming;
}

async function request<T>(endpoint: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
    const first = await send<T>(endpoint, init);
    // /auth/* queda fuera: un 401 del login o del propio refresh no se reintenta (evita bucles).
    if (first.ok || first.error.status !== 401 || endpoint.startsWith("/auth/") || !sessionHandler) {
        return first;
    }
    const handler = sessionHandler;
    await renewSession(handler);
    if (!handler.isAuthenticated()) {
        return { ok: false, error: SESSION_EXPIRED };
    }
    return send<T>(endpoint, init);
}

async function send<T>(endpoint: string, init: RequestInit): Promise<ApiResponse<T>> {
    const base = resolveHost(endpoint);
    if (typeof base !== "string") {
        // Sin esto la URL quedaria "undefined/..." y el error de red no daria ninguna pista.
        return {
            ok: false,
            error: { code: "CONFIG_MISSING", message: "Configuración del API no disponible (config.js).", status: 0 },
        };
    }
    try {
        const method = (init.method ?? "GET").toUpperCase();
        const token = SAFE_METHODS.has(method) ? undefined : (xsrfToken() ?? (await fetchXsrfToken()));
        const res = await fetch(`${base}${endpoint}`, {
            ...init,
            headers: {
                "Content-Type": "application/json",
                ...(token ? { "X-XSRF-TOKEN": token } : {}),
                ...init.headers,
            },
            credentials: "include",
        });
        const raw = await res.text();
        let json: unknown = null;
        try {
            json = raw ? JSON.parse(raw) : null;
        } catch {
            // cuerpo no JSON: se conserva el texto
        }
        if (!res.ok) {
            return { ok: false, error: errorFrom(json, res.status) };
        }
        return { ok: true, data: (json ?? raw) as T };
    } catch {
        return { ok: false, error: { code: "NETWORK", message: "No se pudo conectar con el servidor", status: 0 } };
    }
}

export const dataService = {
    get: <T = unknown>(endpoint: string) => request<T>(endpoint),
    post: <T = unknown>(endpoint: string, body?: unknown) =>
        request<T>(endpoint, { method: "POST", body: JSON.stringify(body) }),
    put: <T = unknown>(endpoint: string, body?: unknown) =>
        request<T>(endpoint, { method: "PUT", body: JSON.stringify(body) }),
    patch: <T = unknown>(endpoint: string, body?: unknown) =>
        request<T>(endpoint, { method: "PATCH", body: JSON.stringify(body) }),
    delete: <T = unknown>(endpoint: string) => request<T>(endpoint, { method: "DELETE" }),
};
