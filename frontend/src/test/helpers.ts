import { vi } from "vitest";
import { resetSessionStorageForTests } from "../session/sessionSync";
import { useAuthStore, type AuthUser } from "../store/authStore";

export const API = { VITE_API_HOST: "https://as2.example.com/api", VITE_API_AUTH: "https://as2.example.com" };

/** Respuesta del servidor para fetch (cada llamada necesita su propio Response). */
export const json = (status: number, body: unknown = {}) =>
    new Response(status === 204 ? null : typeof body === "string" ? body : JSON.stringify(body), { status });

/** Cuerpo de /auth/login con la configuracion por defecto (5/15/60 minutos). */
export function sessionBody(user: AuthUser, now: number = Date.now()) {
    return {
        ...user,
        expiresIn: 300,
        sessionExpiresAt: now + 60 * 60_000,
        refreshExpiresAt: now + 15 * 60_000,
        idleTimeoutSeconds: 900,
        sessionMaxSeconds: 3600,
    };
}

export const ADMIN: AuthUser = { username: "admin", email: "admin@example.com", roles: ["ADMIN"] };
export const AUDITOR: AuthUser = { username: "auditor1", email: "auditor1@example.com", roles: ["AUDITOR"] };

export function resetAuth() {
    resetSessionStorageForTests();
    localStorage.clear();
    useAuthStore.setState({
        user: null,
        isAuthenticated: false,
        loading: false,
        errorCode: null,
        mfaToken: null,
        lastEnd: null,
        sessionEnding: null,
    });
}

export function stubFetch() {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    window.APP_CONFIG = { ...API };
    // Cookie CSRF ya presente, como tras la primera visita: las pruebas no ven la peticion previa.
    document.cookie = "XSRF-TOKEN=prueba; path=/";
    return fetchMock;
}
