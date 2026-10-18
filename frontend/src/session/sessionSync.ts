/**
 * Estado de sesion compartido entre pestanas del mismo origen, via localStorage y el evento
 * `storage` (que solo se dispara en las OTRAS pestanas, justo lo necesario).
 *
 * Sin esto cada pestana llevaria su propio contador de inactividad: una pestana olvidada cerraria
 * la sesion de la pestana en la que se esta trabajando.
 *
 * Todo va en try/catch: localStorage puede no existir o lanzar (modo privado, cuota). Entonces se
 * degrada a memoria de la pestana: la sesion sigue vigilada, solo que sin sincronizar.
 */
import type { SessionLimits } from "./sessionPolicy";

export type SessionEndReason = "idle" | "max" | "server" | "logout";

const KEY = {
    lastActivity: "as2.session.lastActivity",
    limits: "as2.session.limits",
    ended: "as2.session.ended",
    keepalive: "as2.session.keepalive",
} as const;

const memory = new Map<string, string>();

function read(key: string): string | null {
    try {
        return window.localStorage.getItem(key);
    } catch {
        return memory.get(key) ?? null;
    }
}

function write(key: string, value: string): void {
    memory.set(key, value);
    try {
        window.localStorage.setItem(key, value);
    } catch {
        /* sin localStorage: queda en memoria */
    }
}

function remove(key: string): void {
    memory.delete(key);
    try {
        window.localStorage.removeItem(key);
    } catch {
        /* idem */
    }
}

// ---- actividad ---------------------------------------------------------------------------------

/**
 * @param fallback valor si no hay registro (p. ej. se limpio el almacenamiento). Sin el, un 0 se
 *                 leeria como "inactivo desde 1970" y cerraria la sesion al instante.
 *
 * Renovar el token NO cuenta como actividad: solo la cuentan el usuario (touchActivity) y el inicio
 * de sesion. Si contara, la renovacion en segundo plano mantendria viva una sesion desatendida.
 */
export function readLastActivity(fallback: number = 0): number {
    const raw = read(KEY.lastActivity);
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) && n > 0 ? n : fallback;
}

/** Marca actividad. Solo avanza: un reloj atrasado en otra pestana no reduce el contador. */
export function touchActivity(now: number = Date.now()): void {
    if (now > readLastActivity()) write(KEY.lastActivity, String(now));
}

// ---- limites (vienen del servidor) -------------------------------------------------------------

export function readLimits(): SessionLimits | null {
    const raw = read(KEY.limits);
    if (!raw) return null;
    try {
        const l = JSON.parse(raw) as Partial<SessionLimits>;
        if (
            typeof l.sessionExpiresAt !== "number" ||
            typeof l.refreshExpiresAt !== "number" ||
            typeof l.idleTimeoutMs !== "number" ||
            typeof l.accessTtlMs !== "number" ||
            typeof l.sessionMaxMs !== "number"
        ) {
            return null;
        }
        return {
            sessionExpiresAt: l.sessionExpiresAt,
            refreshExpiresAt: l.refreshExpiresAt,
            idleTimeoutMs: l.idleTimeoutMs,
            accessTtlMs: l.accessTtlMs,
            sessionMaxMs: l.sessionMaxMs,
            issuedAt: typeof l.issuedAt === "number" ? l.issuedAt : 0,
        };
    } catch {
        return null;
    }
}

export function writeLimits(limits: SessionLimits): void {
    write(KEY.limits, JSON.stringify(limits));
}

/** Campos de sesion del JSON de /auth/login, /auth/mfa/verify y /auth/refresh. */
export interface SessionLimitsPayload {
    expiresIn?: number;
    sessionExpiresAt?: number;
    refreshExpiresAt?: number;
    idleTimeoutSeconds?: number;
    sessionMaxSeconds?: number;
}

/** null si la respuesta no trae los limites completos. */
export function limitsFromResponse(
    data: SessionLimitsPayload | null | undefined,
    now: number = Date.now(),
): SessionLimits | null {
    if (
        !data ||
        typeof data.sessionExpiresAt !== "number" ||
        typeof data.refreshExpiresAt !== "number" ||
        typeof data.idleTimeoutSeconds !== "number" ||
        typeof data.sessionMaxSeconds !== "number" ||
        typeof data.expiresIn !== "number"
    ) {
        return null;
    }
    return {
        sessionExpiresAt: data.sessionExpiresAt,
        refreshExpiresAt: data.refreshExpiresAt,
        idleTimeoutMs: data.idleTimeoutSeconds * 1000,
        accessTtlMs: data.expiresIn * 1000,
        sessionMaxMs: data.sessionMaxSeconds * 1000,
        issuedAt: now,
    };
}

// ---- inicio y fin de sesion --------------------------------------------------------------------

export interface SessionEnded {
    reason: SessionEndReason;
    /** Minutos para el texto traducido; viajan con el aviso porque la otra pestana ya no tiene limites. */
    minutes?: number;
    at: number;
}

/** Al abrir sesion: limites nuevos, actividad ahora y se olvida cualquier cierre anterior. */
export function startSession(limits: SessionLimits, now: number = Date.now()): void {
    remove(KEY.ended);
    writeLimits(limits);
    write(KEY.lastActivity, String(now));
}

/** Al cerrar: se anuncia a las demas pestanas y se borran los limites. */
export function announceEnd(reason: SessionEndReason, minutes?: number, now: number = Date.now()): SessionEnded {
    const ended: SessionEnded = { reason, minutes, at: now };
    remove(KEY.limits);
    remove(KEY.keepalive);
    write(KEY.ended, JSON.stringify(ended));
    return ended;
}

export function readEnded(): SessionEnded | null {
    const raw = read(KEY.ended);
    if (!raw) return null;
    try {
        const e = JSON.parse(raw) as Partial<SessionEnded>;
        if (typeof e.reason !== "string" || typeof e.at !== "number") return null;
        return {
            reason: e.reason as SessionEndReason,
            minutes: typeof e.minutes === "number" ? e.minutes : undefined,
            at: e.at,
        };
    } catch {
        return null;
    }
}

// ---- keepalive: una sola pestana renueva -------------------------------------------------------

/**
 * Cerrojo para que no renueven dos pestanas a la vez: el servidor rota el refresh token y la segunda
 * llegaria con un token ya revocado. localStorage no es atomico, pero la ventana de carrera es de
 * milisegundos y el ciclo de vigilancia es de 1 s.
 */
export function tryAcquireKeepalive(tabId: string, now: number = Date.now(), ttlMs: number = 30_000): boolean {
    const raw = read(KEY.keepalive);
    if (raw) {
        try {
            const lock = JSON.parse(raw) as { tab: string; at: number };
            if (lock.tab !== tabId && now - lock.at < ttlMs) return false;
        } catch {
            /* cerrojo corrupto: se sobrescribe */
        }
    }
    write(KEY.keepalive, JSON.stringify({ tab: tabId, at: now }));
    const check = read(KEY.keepalive);
    return check !== null && check.includes(`"tab":"${tabId}"`);
}

// ---- cambios hechos por otras pestanas ---------------------------------------------------------

export type SessionSyncEvent = "activity" | "limits" | "ended";

/** Avisa cuando OTRA pestana cambia alguna clave. Devuelve la funcion para desuscribirse. */
export function subscribe(cb: (event: SessionSyncEvent) => void): () => void {
    const handler = (e: StorageEvent) => {
        if (e.key === KEY.lastActivity) cb("activity");
        else if (e.key === KEY.limits) cb("limits");
        else if (e.key === KEY.ended && e.newValue) cb("ended");
    };
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
}

/** Solo para pruebas: deja el almacenamiento como al cargar la pagina. */
export function resetSessionStorageForTests(): void {
    memory.clear();
    Object.values(KEY).forEach((k) => remove(k));
}
