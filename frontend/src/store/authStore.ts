import { create } from "zustand";
import { persist } from "zustand/middleware";
import { dataService, setSessionHandler } from "../service/dataService";
import { evaluate } from "../session/sessionPolicy";
import {
    announceEnd,
    limitsFromResponse,
    readLastActivity,
    readLimits,
    startSession,
    writeLimits,
    type SessionEndReason,
    type SessionEnded,
    type SessionLimitsPayload,
} from "../session/sessionSync";

export type Role = "ADMIN" | "OPERATOR" | "AUDITOR";

export interface AuthUser {
    username: string;
    email: string;
    roles: Role[];
}

/** Respuesta de /auth/login, /auth/mfa/verify y /auth/refresh (los tokens van en cookies HttpOnly). */
export type SessionPayload = Partial<AuthUser> & SessionLimitsPayload;

/** Respuesta de /auth/login cuando la contrasena es correcta pero el usuario tiene MFA. */
interface MfaRequired {
    status: "MFA_REQUIRED";
    mfaToken: string;
}

export type LoginResult = { ok: true } | { ok: false; mfaRequired?: boolean };

interface AuthState {
    user: AuthUser | null;
    isAuthenticated: boolean;
    loading: boolean;
    /** Codigo del ultimo error de login (UNAUTHORIZED, NETWORK, MFA_INVALID_CODE...) para traducirlo. */
    errorCode: string | null;
    /** Desafio del segundo factor. Solo en memoria: recargar vuelve a pedir la contrasena. */
    mfaToken: string | null;
    /** Por que se cerro la ultima sesion: la pantalla de login lo explica. */
    lastEnd: SessionEnded | null;
    /**
     * Sesion ya revocada en el servidor, pendiente del aviso "Su sesion ha terminado". Mientras esta
     * puesto, isAuthenticated sigue en true para mostrar el aviso sobre la pantalla actual;
     * finalizeEnd() lleva al login.
     */
    sessionEnding: SessionEnded | null;

    login: (username: string, password: string) => Promise<LoginResult>;
    verifyMfa: (code: string) => Promise<LoginResult>;
    cancelMfa: () => void;
    refresh: () => Promise<void>;
    fetchMe: () => Promise<void>;
    logout: () => Promise<void>;
    /** Cierre iniciado en esta pestana (inactividad o techo): revoca en el servidor y avisa al resto. */
    endSession: (reason: SessionEndReason) => Promise<void>;
    /** Cierre que ya hizo otra pestana: solo hay que reflejarlo. */
    applyRemoteEnd: (ended: SessionEnded) => void;
    /** El usuario cerro el aviso o se agoto su cuenta regresiva: al login. */
    finalizeEnd: () => void;
}

const minutes = (ms: number) => Math.round(ms / 60_000);

/** Minutos para el texto del motivo; se calculan antes de borrar los limites. */
function minutesFor(reason: SessionEndReason): number | undefined {
    const limits = readLimits();
    if (!limits) return undefined;
    if (reason === "idle") return minutes(limits.idleTimeoutMs);
    if (reason === "max") return minutes(limits.sessionMaxMs);
    return undefined;
}

/** El servidor explica el rechazo del refresh con un code. */
function reasonFromCode(code: string | undefined): SessionEndReason {
    if (code === "IDLE_EXPIRED") return "idle";
    if (code === "SESSION_EXPIRED") return "max";
    return "server";
}

const closed = { user: null, isAuthenticated: false, sessionEnding: null, mfaToken: null };

/** Un cierre manual va directo al login; los demas pasan antes por el aviso. */
function afterEnd(ended: SessionEnded) {
    return ended.reason === "logout" ? { ...closed, lastEnd: ended } : { lastEnd: ended, sessionEnding: ended };
}

function isMfaRequired(d: SessionPayload | MfaRequired): d is MfaRequired {
    return (d as MfaRequired).status === "MFA_REQUIRED" && typeof (d as MfaRequired).mfaToken === "string";
}

// Un solo refresh en vuelo: el servidor rota el refresh token y uno simultaneo llegaria revocado.
let refreshInFlight: Promise<void> | null = null;

export const useAuthStore = create<AuthState>()(
    persist(
        (set, get) => {
            const openSession = async (data: SessionPayload) => {
                const limits = limitsFromResponse(data);
                if (limits) startSession(limits);
                set({
                    user: {
                        username: data.username ?? "",
                        email: data.email ?? "",
                        roles: (data.roles ?? []) as Role[],
                    },
                    isAuthenticated: true,
                    loading: false,
                    mfaToken: null,
                    errorCode: null,
                    lastEnd: null,
                    sessionEnding: null,
                });
                await get().fetchMe();
            };

            return {
                user: null,
                isAuthenticated: false,
                loading: false,
                errorCode: null,
                mfaToken: null,
                lastEnd: null,
                sessionEnding: null,

                login: async (username, password) => {
                    set({ loading: true, errorCode: null });
                    const res = await dataService.post<SessionPayload | MfaRequired>("/auth/login", {
                        username,
                        password,
                    });
                    if (!res.ok) {
                        set({ loading: false, errorCode: res.error.code, isAuthenticated: false });
                        return { ok: false };
                    }
                    if (isMfaRequired(res.data)) {
                        set({ loading: false, mfaToken: res.data.mfaToken });
                        return { ok: false, mfaRequired: true };
                    }
                    await openSession(res.data);
                    return { ok: true };
                },

                verifyMfa: async (code) => {
                    const mfaToken = get().mfaToken;
                    if (!mfaToken) return { ok: false };
                    set({ loading: true, errorCode: null });
                    const res = await dataService.post<SessionPayload>("/auth/mfa/verify", { mfaToken, code });
                    if (!res.ok) {
                        const code = res.error.code;
                        // Desafio agotado o caducado: hay que volver a pedir la contrasena.
                        const restart = code === "MFA_ATTEMPTS_EXCEEDED" || code === "MFA_CHALLENGE_EXPIRED";
                        set({ loading: false, errorCode: code, mfaToken: restart ? null : mfaToken });
                        return { ok: false };
                    }
                    await openSession(res.data);
                    return { ok: true };
                },

                cancelMfa: () => set({ mfaToken: null, errorCode: null }),

                refresh: () => {
                    if (refreshInFlight) return refreshInFlight;
                    refreshInFlight = (async () => {
                        // Con el aviso de cierre en pantalla no hay nada que renovar.
                        if (get().sessionEnding || !get().isAuthenticated) return;

                        // Renovar ante un 401 no puede saltarse la inactividad: una consulta en
                        // segundo plano renovaria la sesion sin que el usuario haya hecho nada.
                        const limits = readLimits();
                        if (limits) {
                            const snap = evaluate(limits, readLastActivity(limits.issuedAt), Date.now());
                            if (snap.phase === "expired-idle") return get().endSession("idle");
                            if (snap.phase === "expired-max") return get().endSession("max");
                        }

                        const res = await dataService.post<SessionPayload>("/auth/refresh", {});
                        if (res.ok) {
                            const next = limitsFromResponse(res.data);
                            if (next) writeLimits(next);
                            set({ isAuthenticated: true });
                        } else if (res.error.status === 401) {
                            const reason = reasonFromCode(res.error.code);
                            set(afterEnd(announceEnd(reason, minutesFor(reason))));
                        } else {
                            // Un fallo de red no cierra la sesion: el siguiente intento lo resolvera.
                        }
                    })().finally(() => {
                        refreshInFlight = null;
                    });
                    return refreshInFlight;
                },

                fetchMe: async () => {
                    const res = await dataService.get<AuthUser>("/users/me");
                    if (res.ok) {
                        const { username, email, roles } = res.data;
                        set({ user: { username, email, roles } });
                    }
                },

                logout: async () => {
                    await dataService.post("/auth/logout", {});
                    set(afterEnd(announceEnd("logout")));
                },

                endSession: async (reason) => {
                    const mins = minutesFor(reason);
                    // Revocar en el servidor ANTES de avisar: si el refresh sigue vivo, no se cerro nada.
                    await dataService.post("/auth/logout", {});
                    set(afterEnd(announceEnd(reason, mins)));
                },

                applyRemoteEnd: (ended) => {
                    if (!get().isAuthenticated || get().sessionEnding) return;
                    set(afterEnd(ended));
                },

                finalizeEnd: () => {
                    if (get().sessionEnding) set(closed);
                },
            };
        },
        {
            name: "as2.auth",
            // Nunca se guardan tokens (viajan en cookies HttpOnly): solo quien es y por que salio.
            partialize: (s) => ({
                user: s.user,
                isAuthenticated: s.isAuthenticated,
                lastEnd: s.lastEnd,
                sessionEnding: s.sessionEnding,
            }),
        },
    ),
);

// El cliente HTTP renueva la sesion con este store ante un 401.
setSessionHandler({
    refresh: () => useAuthStore.getState().refresh(),
    isAuthenticated: () => {
        const s = useAuthStore.getState();
        return s.isAuthenticated && !s.sessionEnding;
    },
});

export function hasAnyRole(user: AuthUser | null, roles: readonly Role[]): boolean {
    return !!user?.roles?.some((r) => roles.includes(r));
}
