import { useCallback, useEffect, useRef, useState } from "react";
import { useAuthStore } from "../store/authStore";
import { ENDED_NOTICE_MS, evaluate, type SessionSnapshot } from "./sessionPolicy";
import { readEnded, readLastActivity, readLimits, subscribe, touchActivity, tryAcquireKeepalive } from "./sessionSync";

/** Eventos que cuentan como "el usuario esta aqui". */
const ACTIVITY_EVENTS: (keyof WindowEventMap)[] = ["mousemove", "mousedown", "keydown", "scroll", "touchstart", "wheel"];

/** No escribir en localStorage con cada pixel del raton. */
const ACTIVITY_THROTTLE_MS = 5_000;

/** Un keepalive fallido no se reintenta antes de esto. */
const KEEPALIVE_RETRY_MS = 60_000;

const TICK_MS = 1_000;

/**
 * Identifica a esta pestana en el cerrojo del keepalive (un modulo = una carga de pagina). No es un
 * secreto: solo distingue pestanas. getRandomValues funciona tambien fuera de HTTPS (randomUUID no).
 */
const TAB_ID = Array.from(crypto.getRandomValues(new Uint32Array(2)), (n) => n.toString(36)).join("");

/**
 * Vigila los dos limites de la sesion (inactividad y duracion maxima) y expone el estado del aviso.
 *
 * - Compara marcas de tiempo, no cuenta ciclos: si el navegador ralentiza los temporizadores de una
 *   pestana en segundo plano, el siguiente ciclo se pone al dia solo.
 * - Durante el aviso, la actividad pasiva NO cuenta: hay que pulsar "Seguir conectado".
 * - Mientras el usuario esta activo, renueva el token antes de que el servidor deje de aceptarlo
 *   (keepalive), aunque la pantalla no haga llamadas al API.
 */
export function useSessionGuard() {
    const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
    const sessionEnding = useAuthStore((s) => s.sessionEnding);
    const refresh = useAuthStore((s) => s.refresh);
    const endSession = useAuthStore((s) => s.endSession);
    const applyRemoteEnd = useAuthStore((s) => s.applyRemoteEnd);
    const finalizeEnd = useAuthStore((s) => s.finalizeEnd);

    const [snapshot, setSnapshot] = useState<SessionSnapshot | null>(null);
    const [endingRemainingMs, setEndingRemainingMs] = useState<number | null>(null);
    const lastKeepalive = useRef(0);
    const lastTouch = useRef(0);
    const phase = useRef<SessionSnapshot["phase"] | null>(null);
    const closing = useRef(false);
    const limitsRequested = useRef(false);
    const endingRef = useRef(sessionEnding);

    useEffect(() => {
        endingRef.current = sessionEnding;
    }, [sessionEnding]);

    const tick = useCallback(() => {
        const ending = endingRef.current;
        if (ending) {
            const remaining = ENDED_NOTICE_MS - (Date.now() - ending.at);
            setSnapshot(null);
            phase.current = null;
            setEndingRemainingMs(Math.max(0, remaining));
            if (remaining <= 0) finalizeEnd();
            return;
        }
        setEndingRemainingMs(null);
        if (closing.current) return;
        const limits = readLimits();
        if (!limits) {
            // Autenticado segun el estado guardado pero sin limites (se limpio el almacenamiento):
            // se pide un refresh, que trae los limites o lleva al login.
            setSnapshot(null);
            phase.current = null;
            if (!limitsRequested.current) {
                limitsRequested.current = true;
                void refresh();
            }
            return;
        }
        const now = Date.now();
        const snap = evaluate(limits, readLastActivity(limits.issuedAt), now);
        phase.current = snap.phase;
        setSnapshot(snap);

        if (snap.phase === "expired-idle" || snap.phase === "expired-max") {
            closing.current = true;
            void endSession(snap.phase === "expired-idle" ? "idle" : "max");
            return;
        }
        if (
            snap.keepaliveDue &&
            now - lastKeepalive.current >= KEEPALIVE_RETRY_MS &&
            tryAcquireKeepalive(TAB_ID, now)
        ) {
            lastKeepalive.current = now;
            void refresh();
        }
    }, [endSession, refresh, finalizeEnd]);

    useEffect(() => {
        // Sin sesion no hay nada que vigilar (el aviso se monta dentro del layout con sesion).
        if (!isAuthenticated) return;

        const onActivity = () => {
            if (phase.current === "warn-idle") return; // solo "Seguir conectado" saca del aviso
            const now = Date.now();
            if (now - lastTouch.current < ACTIVITY_THROTTLE_MS) return;
            lastTouch.current = now;
            touchActivity(now);
        };
        const onVisible = () => {
            if (document.visibilityState === "visible") tick();
        };
        const unsubscribe = subscribe((event) => {
            if (event === "ended") {
                const ended = readEnded();
                if (ended) {
                    closing.current = true;
                    applyRemoteEnd(ended);
                }
                return;
            }
            tick(); // actividad o limites nuevos en otra pestana: recalcular ya
        });

        ACTIVITY_EVENTS.forEach((ev) => window.addEventListener(ev, onActivity, { passive: true }));
        document.addEventListener("visibilitychange", onVisible);
        const interval = setInterval(tick, TICK_MS);
        tick();

        return () => {
            ACTIVITY_EVENTS.forEach((ev) => window.removeEventListener(ev, onActivity));
            document.removeEventListener("visibilitychange", onVisible);
            clearInterval(interval);
            unsubscribe();
        };
    }, [isAuthenticated, sessionEnding, tick, applyRemoteEnd]);

    /** "Seguir conectado": reinicia la inactividad y renueva en el servidor. El techo no se mueve. */
    const continueSession = useCallback(async () => {
        touchActivity();
        lastTouch.current = Date.now();
        phase.current = "active";
        tick();
        await refresh();
        tick();
    }, [refresh, tick]);

    /** "Cerrar sesion" desde el aviso: decision explicita, sin aviso posterior. */
    const endNow = useCallback(() => {
        closing.current = true;
        return endSession("logout");
    }, [endSession]);

    return {
        snapshot,
        continueSession,
        endNow,
        ending: sessionEnding ? { ...sessionEnding, remainingMs: endingRemainingMs ?? ENDED_NOTICE_MS } : null,
        dismissEnded: finalizeEnd,
    };
}
