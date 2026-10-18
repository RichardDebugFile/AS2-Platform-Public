import { afterEach, describe, expect, it, vi } from "vitest";
import {
    announceEnd,
    limitsFromResponse,
    readEnded,
    readLastActivity,
    readLimits,
    resetSessionStorageForTests,
    startSession,
    subscribe,
    touchActivity,
    tryAcquireKeepalive,
} from "../session/sessionSync";

const payload = { expiresIn: 300, sessionExpiresAt: 4_000_000, refreshExpiresAt: 1_900_000, idleTimeoutSeconds: 900, sessionMaxSeconds: 3600 };

afterEach(() => {
    resetSessionStorageForTests();
    vi.restoreAllMocks();
});

describe("sessionSync", () => {
    it("convierte los limites del servidor a milisegundos", () => {
        expect(limitsFromResponse(payload, 1_000_000)).toEqual({
            sessionExpiresAt: 4_000_000,
            refreshExpiresAt: 1_900_000,
            idleTimeoutMs: 900_000,
            accessTtlMs: 300_000,
            sessionMaxMs: 3_600_000,
            issuedAt: 1_000_000,
        });
        expect(limitsFromResponse({ expiresIn: 300 })).toBeNull();
        expect(limitsFromResponse(null)).toBeNull();
    });

    it("al iniciar sesion guarda limites y actividad y olvida el cierre anterior", () => {
        announceEnd("idle", 15, 500);
        startSession(limitsFromResponse(payload, 1_000_000)!, 1_000_000);
        expect(readLimits()?.idleTimeoutMs).toBe(900_000);
        expect(readLastActivity()).toBe(1_000_000);
        expect(readEnded()).toBeNull();
    });

    it("la actividad solo avanza", () => {
        touchActivity(2_000);
        touchActivity(1_000);
        expect(readLastActivity()).toBe(2_000);
        // Con registro, el respaldo no se usa: renovar el token no cuenta como actividad.
        expect(readLastActivity(5_000)).toBe(2_000);
        resetSessionStorageForTests();
        expect(readLastActivity(5_000)).toBe(5_000);
    });

    it("el cierre se anuncia con su motivo y borra los limites", () => {
        startSession(limitsFromResponse(payload, 1)!, 1);
        announceEnd("max", 60, 9);
        expect(readLimits()).toBeNull();
        expect(readEnded()).toEqual({ reason: "max", minutes: 60, at: 9 });
    });

    it("solo una pestana obtiene el cerrojo del keepalive", () => {
        expect(tryAcquireKeepalive("a", 1_000)).toBe(true);
        expect(tryAcquireKeepalive("b", 2_000)).toBe(false);
        expect(tryAcquireKeepalive("a", 3_000)).toBe(true);
        expect(tryAcquireKeepalive("b", 40_000)).toBe(true); // el cerrojo de "a" caduco
    });

    it("avisa de los cambios hechos por otra pestana", () => {
        const seen: string[] = [];
        const off = subscribe((e) => seen.push(e));
        window.dispatchEvent(new StorageEvent("storage", { key: "as2.session.lastActivity", newValue: "1" }));
        window.dispatchEvent(new StorageEvent("storage", { key: "as2.session.ended", newValue: "{}" }));
        window.dispatchEvent(new StorageEvent("storage", { key: "otra", newValue: "x" }));
        off();
        window.dispatchEvent(new StorageEvent("storage", { key: "as2.session.limits", newValue: "{}" }));
        expect(seen).toEqual(["activity", "ended"]);
    });

    it("sin localStorage sigue funcionando en memoria", () => {
        vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new Error("bloqueado");
        });
        vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
            throw new Error("bloqueado");
        });
        touchActivity(7_000);
        expect(readLastActivity()).toBe(7_000);
    });

    it("ignora datos corruptos", () => {
        localStorage.setItem("as2.session.limits", "{no-json");
        localStorage.setItem("as2.session.ended", '{"reason":1}');
        expect(readLimits()).toBeNull();
        expect(readEnded()).toBeNull();
    });
});
