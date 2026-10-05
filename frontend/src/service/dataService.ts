// Cliente HTTP de la consola. Las cookies HttpOnly de sesion viajan con credentials: "include";
// el token nunca pasa por JavaScript.
//
// La renovacion automatica de la sesion ante un 401 llega con la consola base (HU-06), cuando
// exista el store de autenticacion.

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

/** /auth/* y /users/* los atiende auth-service; el resto, la API de negocio. */
export function resolveHost(endpoint: string): string | undefined {
    const { api, auth } = hosts();
    return endpoint.startsWith("/auth") || endpoint.startsWith("/users") ? auth : api;
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

async function request<T>(endpoint: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
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
        const token = SAFE_METHODS.has(method) ? undefined : xsrfToken();
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
    patch: <T = unknown>(endpoint: string, body?: unknown) =>
        request<T>(endpoint, { method: "PATCH", body: JSON.stringify(body) }),
};
