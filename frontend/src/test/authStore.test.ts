import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { readEnded, readLimits } from "../session/sessionSync";
import { useAuthStore } from "../store/authStore";
import { ADMIN, json, resetAuth, sessionBody, stubFetch } from "./helpers";

let fetchMock: Mock;

beforeEach(() => {
    resetAuth();
    fetchMock = stubFetch();
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
});

const calls = () => fetchMock.mock.calls.map((c) => String(c[0]).replace("https://as2.example.com", ""));

describe("authStore", () => {
    it("login correcto: abre la sesion con los limites del servidor y confirma el usuario", async () => {
        fetchMock.mockResolvedValueOnce(json(200, sessionBody(ADMIN))).mockResolvedValueOnce(json(200, ADMIN));

        const r = await useAuthStore.getState().login("admin", "secreta");

        expect(r).toEqual({ ok: true });
        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, user: ADMIN, errorCode: null });
        expect(readLimits()?.idleTimeoutMs).toBe(15 * 60_000);
        expect(calls()).toEqual(["/auth/login", "/users/me"]);
        // Nunca se guardan tokens en el navegador.
        expect(localStorage.getItem("as2.auth") ?? "").not.toMatch(/token/i);
    });

    it("credenciales invalidas: deja el codigo para traducirlo", async () => {
        fetchMock.mockResolvedValueOnce(json(401, { code: "UNAUTHORIZED", message: "x" }));

        expect(await useAuthStore.getState().login("admin", "mal")).toEqual({ ok: false });
        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: false, errorCode: "UNAUTHORIZED" });
    });

    it("con MFA pide el codigo y abre la sesion al verificarlo", async () => {
        fetchMock
            .mockResolvedValueOnce(json(200, { status: "MFA_REQUIRED", mfaToken: "desafio" }))
            .mockResolvedValueOnce(json(401, { code: "MFA_INVALID_CODE" }))
            .mockResolvedValueOnce(json(200, sessionBody(ADMIN)))
            .mockResolvedValueOnce(json(200, ADMIN));

        expect(await useAuthStore.getState().login("admin", "secreta")).toEqual({ ok: false, mfaRequired: true });
        expect(await useAuthStore.getState().verifyMfa("000000")).toEqual({ ok: false });
        expect(useAuthStore.getState()).toMatchObject({ mfaToken: "desafio", errorCode: "MFA_INVALID_CODE" });
        expect(await useAuthStore.getState().verifyMfa("123456")).toEqual({ ok: true });
        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, mfaToken: null });
        expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ mfaToken: "desafio", code: "123456" });
    });

    it("desafio MFA agotado: vuelve a pedir la contrasena", async () => {
        useAuthStore.setState({ mfaToken: "desafio" });
        fetchMock.mockResolvedValueOnce(json(401, { code: "MFA_ATTEMPTS_EXCEEDED" }));

        await useAuthStore.getState().verifyMfa("111111");

        expect(useAuthStore.getState().mfaToken).toBeNull();
        useAuthStore.getState().cancelMfa();
        expect(useAuthStore.getState().errorCode).toBeNull();
    });

    it("refresh correcto: actualiza los limites", async () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        fetchMock.mockResolvedValueOnce(json(200, sessionBody(ADMIN)));

        await useAuthStore.getState().refresh();

        expect(readLimits()).not.toBeNull();
        expect(useAuthStore.getState().isAuthenticated).toBe(true);
    });

    it("refresh rechazado por inactividad: cierra con el motivo y lo anuncia a las otras pestanas", async () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        fetchMock.mockResolvedValueOnce(json(401, { code: "IDLE_EXPIRED" }));

        await useAuthStore.getState().refresh();

        expect(useAuthStore.getState().sessionEnding?.reason).toBe("idle");
        expect(readEnded()?.reason).toBe("idle");
        useAuthStore.getState().finalizeEnd();
        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: false, user: null });
        expect(useAuthStore.getState().lastEnd?.reason).toBe("idle");
    });

    it("refresh rechazado por el techo o por otro motivo", async () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        fetchMock.mockResolvedValueOnce(json(401, { code: "SESSION_EXPIRED" }));
        await useAuthStore.getState().refresh();
        expect(useAuthStore.getState().sessionEnding?.reason).toBe("max");

        useAuthStore.setState({ sessionEnding: null });
        fetchMock.mockResolvedValueOnce(json(401, { code: "TOKEN_INVALID" }));
        await useAuthStore.getState().refresh();
        expect(useAuthStore.getState().sessionEnding?.reason).toBe("server");
    });

    it("un fallo de red al renovar no cierra la sesion", async () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

        await useAuthStore.getState().refresh();

        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, sessionEnding: null });
    });

    it("dos refresh simultaneos hacen una sola llamada (el servidor rota el token)", async () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        fetchMock.mockResolvedValue(json(200, sessionBody(ADMIN)));

        await Promise.all([useAuthStore.getState().refresh(), useAuthStore.getState().refresh()]);

        expect(calls()).toEqual(["/auth/refresh"]);
    });

    it("no renueva una sesion inactiva: la cierra por inactividad", async () => {
        vi.useFakeTimers({ toFake: ["Date"] });
        vi.setSystemTime(Date.UTC(2026, 9, 17, 12, 0));
        fetchMock.mockResolvedValueOnce(json(200, sessionBody(ADMIN))).mockResolvedValueOnce(json(200, ADMIN));
        await useAuthStore.getState().login("admin", "secreta");

        vi.setSystemTime(Date.UTC(2026, 9, 17, 12, 16));
        fetchMock.mockResolvedValueOnce(json(204, ""));
        await useAuthStore.getState().refresh();

        expect(calls()).toEqual(["/auth/login", "/users/me", "/auth/logout"]);
        expect(useAuthStore.getState().sessionEnding).toMatchObject({ reason: "idle", minutes: 15 });
    });

    it("logout revoca en el servidor y va directo al login, sin aviso", async () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        fetchMock.mockResolvedValueOnce(json(204, ""));

        await useAuthStore.getState().logout();

        expect(calls()).toEqual(["/auth/logout"]);
        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: false, sessionEnding: null });
    });

    it("refleja el cierre que hizo otra pestana", () => {
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        useAuthStore.getState().applyRemoteEnd({ reason: "max", minutes: 60, at: 1 });
        expect(useAuthStore.getState().sessionEnding?.reason).toBe("max");
    });
});
