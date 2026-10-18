import { describe, expect, it } from "vitest";
import { evaluate, formatCountdown, warnBeforeMs, type SessionLimits } from "../session/sessionPolicy";

const MIN = 60_000;
const T0 = Date.UTC(2026, 9, 17, 12, 0, 0);

/** Configuracion por defecto de auth-service: access 5, inactividad 15, techo 60 minutos. */
function limits(over: Partial<SessionLimits> = {}): SessionLimits {
    return {
        sessionExpiresAt: T0 + 60 * MIN,
        refreshExpiresAt: T0 + 15 * MIN,
        idleTimeoutMs: 15 * MIN,
        accessTtlMs: 5 * MIN,
        sessionMaxMs: 60 * MIN,
        issuedAt: T0,
        ...over,
    };
}

describe("sessionPolicy", () => {
    it("con 15 min de inactividad el aviso aparece al minuto 14", () => {
        expect(evaluate(limits(), T0, T0 + 13 * MIN + 59_000).phase).toBe("active");
        const warn = evaluate(limits(), T0, T0 + 14 * MIN);
        expect(warn.phase).toBe("warn-idle");
        expect(formatCountdown(warn.idleRemainingMs)).toBe("1:00");
    });

    it("a los 15 min sin actividad la sesion vence por inactividad", () => {
        expect(evaluate(limits(), T0, T0 + 15 * MIN).phase).toBe("expired-idle");
    });

    it("la actividad reinicia el contador", () => {
        expect(evaluate(limits(), T0 + 10 * MIN, T0 + 20 * MIN).phase).toBe("active");
    });

    it("el techo manda aunque haya actividad", () => {
        const l = limits({ refreshExpiresAt: T0 + 60 * MIN });
        expect(evaluate(l, T0 + 59 * MIN, T0 + 59 * MIN + 10_000).phase).toBe("warn-max");
        expect(evaluate(l, T0 + 60 * MIN, T0 + 60 * MIN).phase).toBe("expired-max");
    });

    it("pide keepalive cuando el refresh esta por vencer y el usuario sigue activo", () => {
        const l = limits({ refreshExpiresAt: T0 + 15 * MIN });
        expect(evaluate(l, T0 + 9 * MIN, T0 + 9 * MIN).keepaliveDue).toBe(false);
        expect(evaluate(l, T0 + 11 * MIN, T0 + 11 * MIN).keepaliveDue).toBe(true);
    });

    it("no renueva una sesion desatendida (sin actividad desde la ultima renovacion)", () => {
        const l = limits({ refreshExpiresAt: T0 + 15 * MIN });
        expect(evaluate(l, T0, T0 + 11 * MIN).keepaliveDue).toBe(false);
    });

    it("no pide keepalive si el techo ya recorta la ventana de renovacion", () => {
        const l = limits({ refreshExpiresAt: T0 + 60 * MIN });
        expect(evaluate(l, T0 + 57 * MIN, T0 + 57 * MIN).keepaliveDue).toBe(false);
    });

    it("adapta el aviso a inactividades muy cortas", () => {
        expect(warnBeforeMs(15 * MIN)).toBe(MIN);
        expect(warnBeforeMs(60_000)).toBe(30_000);
    });

    it("la cuenta regresiva nunca es negativa", () => {
        expect(formatCountdown(-5)).toBe("0:00");
        expect(formatCountdown(61_500)).toBe("1:02");
    });
});
