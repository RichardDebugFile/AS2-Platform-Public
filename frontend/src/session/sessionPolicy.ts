/**
 * Reglas de vida de la sesion en el cliente. Solo aritmetica sobre marcas de tiempo (sin React ni
 * red) para poder probarla con fechas fijas.
 *
 * Los limites llegan del servidor en cada login y refresh (LoginResponse de auth-service): la
 * consola no guarda una copia de la configuracion que pueda desincronizarse.
 */

export interface SessionLimits {
    /** epoch ms: techo absoluto de la sesion. Se hereda en cada refresh, nunca se mueve. */
    sessionExpiresAt: number;
    /** epoch ms: hasta cuando el servidor acepta renovar sin actividad. */
    refreshExpiresAt: number;
    /** ms de inactividad tras los que se cierra la sesion. */
    idleTimeoutMs: number;
    /** ms de vida del access token. */
    accessTtlMs: number;
    /** ms de vida maxima de la sesion desde el login (para los textos). */
    sessionMaxMs: number;
    /** epoch ms en que se recibieron estos limites. */
    issuedAt: number;
}

export type SessionPhase =
    | "active"
    | "warn-idle" // falta poco para cerrar por inactividad: aviso con "Seguir conectado"
    | "warn-max" // falta poco para el techo: aviso, no se puede extender
    | "expired-idle"
    | "expired-max";

export interface SessionSnapshot {
    phase: SessionPhase;
    idleRemainingMs: number;
    sessionRemainingMs: number;
    /** El servidor va a dejar de aceptar el refresh y el usuario sigue activo: renovar ya. */
    keepaliveDue: boolean;
}

/** Antelacion del aviso: con 15 min de inactividad, el aviso aparece al minuto 14. */
export const WARN_BEFORE_MS = 60_000;

/**
 * Cuanto se muestra el aviso "Su sesion ha terminado" antes de ir al login. La sesion ya esta
 * revocada cuando aparece: solo se retrasa la redireccion para que el usuario sepa que paso.
 */
export const ENDED_NOTICE_MS = 30_000;

/** Antelacion del aviso adaptada a inactividades muy cortas (configuraciones de prueba). */
export function warnBeforeMs(idleTimeoutMs: number): number {
    return Math.min(WARN_BEFORE_MS, Math.floor(idleTimeoutMs / 2));
}

/**
 * Margen para renovar en segundo plano. Mayor que la antelacion del aviso: durante el aviso no se
 * renueva (seria "Seguir conectado" sin pulsarlo) y, aun asi, al pulsarlo el servidor debe seguir
 * aceptando el refresh.
 */
export function keepaliveMarginMs(limits: SessionLimits): number {
    return Math.max(limits.accessTtlMs, warnBeforeMs(limits.idleTimeoutMs) + 60_000);
}

export function evaluate(limits: SessionLimits, lastActivity: number, now: number): SessionSnapshot {
    const sessionRemainingMs = limits.sessionExpiresAt - now;
    const idleRemainingMs = limits.idleTimeoutMs - (now - lastActivity);
    const warn = warnBeforeMs(limits.idleTimeoutMs);

    let phase: SessionPhase;
    if (sessionRemainingMs <= 0) phase = "expired-max";
    else if (idleRemainingMs <= 0) phase = "expired-idle";
    else if (idleRemainingMs <= warn) phase = "warn-idle";
    else if (sessionRemainingMs <= warn) phase = "warn-max";
    else phase = "active";

    const keepaliveDue =
        phase === "active" &&
        // Solo si el usuario hizo algo desde la ultima renovacion: una sesion desatendida no se renueva.
        lastActivity > limits.issuedAt &&
        limits.refreshExpiresAt - now <= keepaliveMarginMs(limits) &&
        // Si la ventana ya la recorta el techo, renovar no la alarga: no insistir.
        limits.refreshExpiresAt < limits.sessionExpiresAt;

    return { phase, idleRemainingMs, sessionRemainingMs, keepaliveDue };
}

/** "m:ss" para la cuenta regresiva. Nunca negativo. */
export function formatCountdown(ms: number): string {
    const total = Math.max(0, Math.ceil(ms / 1000));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
}
