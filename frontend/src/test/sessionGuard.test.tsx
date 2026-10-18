import { act, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import AppRoutes from "../routes/AppRoutes";
import { readLastActivity } from "../session/sessionSync";
import { useAuthStore } from "../store/authStore";
import { ADMIN, json, resetAuth, sessionBody, stubFetch } from "./helpers";

const MIN = 60_000;
const T0 = Date.UTC(2026, 9, 17, 12, 0, 0);
let fetchMock: Mock;

async function loginAndRender() {
    fetchMock.mockResolvedValueOnce(json(200, sessionBody(ADMIN, T0))).mockResolvedValueOnce(json(200, ADMIN));
    await useAuthStore.getState().login("admin", "secreta");
    fetchMock.mockImplementation(async () => json(200, sessionBody(ADMIN, Date.now())));
    render(
        <MemoryRouter initialEntries={["/dashboard"]}>
            <AppProviders>
                <AppRoutes />
            </AppProviders>
        </MemoryRouter>,
    );
}

/** Avanza el reloj de a un segundo, como el ciclo real de vigilancia. */
async function advance(ms: number) {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ms);
    });
}

beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: false });
    vi.setSystemTime(T0);
    resetAuth();
    fetchMock = stubFetch();
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe("aviso de inactividad", () => {
    it("aparece al minuto 14 con cuenta regresiva y, sin respuesta, cierra la sesion por inactividad", async () => {
        await loginAndRender();
        await advance(13 * MIN + 50_000);
        expect(screen.queryByText("Sesión inactiva")).not.toBeInTheDocument();

        await advance(10_000);
        expect(screen.getByText("Sesión inactiva")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Seguir conectado" })).toBeInTheDocument();
        expect(screen.getByTestId("idle-countdown").textContent).toMatch(/^(1:00|0:[0-5]\d)$/);

        await advance(MIN);
        expect(screen.getByText("Su sesión se cerró por inactividad")).toBeInTheDocument();
        const logoutCall = fetchMock.mock.calls.find((c) => String(c[0]).endsWith("/auth/logout"));
        expect(logoutCall).toBeDefined();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Ir al inicio de sesión" }));
        });
        await advance(100);
        expect(screen.getByText("Sesión cerrada por inactividad (15 min). Inicie sesión de nuevo.")).toBeInTheDocument();
    });

    it("Seguir conectado reinicia la inactividad y renueva en el servidor", async () => {
        await loginAndRender();
        await advance(14 * MIN + 5_000);
        expect(screen.getByText("Sesión inactiva")).toBeInTheDocument();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Seguir conectado" }));
        });
        // Sin "Seguir conectado" la sesion habria vencido al minuto 15; tres minutos despues sigue viva.
        await advance(3 * MIN);

        expect(fetchMock.mock.calls.some((c) => String(c[0]).endsWith("/auth/refresh"))).toBe(true);
        expect(useAuthStore.getState()).toMatchObject({ isAuthenticated: true, sessionEnding: null });
        expect(readLastActivity()).toBeGreaterThanOrEqual(T0 + 14 * MIN);
    });

    it("la actividad en otra pestana evita que esta cierre la sesion", async () => {
        await loginAndRender();
        for (let m = 1; m <= 20; m++) {
            await advance(MIN);
            // Otra pestana registra actividad cada minuto (el evento storage solo llega a las demas).
            localStorage.setItem("as2.session.lastActivity", String(Date.now()));
            window.dispatchEvent(new StorageEvent("storage", { key: "as2.session.lastActivity" }));
        }
        expect(screen.queryByText("Sesión inactiva")).not.toBeInTheDocument();
        expect(useAuthStore.getState().sessionEnding).toBeNull();
        expect(readLastActivity()).toBeGreaterThan(T0 + 19 * MIN);
    });

    it("el cierre hecho en otra pestana se refleja con el mismo aviso", async () => {
        await loginAndRender();
        await act(async () => {
            localStorage.setItem("as2.session.ended", JSON.stringify({ reason: "max", minutes: 60, at: Date.now() }));
            window.dispatchEvent(new StorageEvent("storage", { key: "as2.session.ended", newValue: "x" }));
        });
        expect(screen.getByText("Su sesión alcanzó su duración máxima")).toBeInTheDocument();
    });
});
