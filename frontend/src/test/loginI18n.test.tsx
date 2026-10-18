import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AppProviders from "../AppProviders";
import i18n, { LANGUAGE_KEY } from "../i18n";
import AppRoutes from "../routes/AppRoutes";
import { useAuthStore } from "../store/authStore";
import { json, resetAuth, stubFetch } from "./helpers";

function renderLogin() {
    render(
        <MemoryRouter initialEntries={["/"]}>
            <AppProviders>
                <AppRoutes />
            </AppProviders>
        </MemoryRouter>,
    );
}

beforeEach(() => {
    resetAuth();
    stubFetch();
});

afterEach(() => vi.unstubAllGlobals());

describe("idioma", () => {
    it("al cambiar a English los textos cambian y el idioma se recuerda", async () => {
        renderLogin();
        expect(screen.getByRole("button", { name: "Iniciar sesión" })).toBeInTheDocument();

        fireEvent.click(screen.getByText("EN"));

        expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
        expect(screen.getByText("Welcome")).toBeInTheDocument();
        expect(screen.getByText("Username")).toBeInTheDocument();
        expect(i18n.language).toBe("en");
        expect(localStorage.getItem(LANGUAGE_KEY)).toBe("en");
    });

    it("traduce el error de credenciales y el motivo del cierre anterior", async () => {
        await i18n.changeLanguage("en");
        useAuthStore.setState({ lastEnd: { reason: "idle", minutes: 15, at: 1 } });
        renderLogin();
        expect(screen.getByText("Session closed due to inactivity (15 min). Please sign in again.")).toBeInTheDocument();

        useAuthStore.setState({ errorCode: "UNAUTHORIZED" });
        expect(await screen.findByText("Invalid username or password")).toBeInTheDocument();
    });

    it("login con segundo factor", async () => {
        const fetchMock = stubFetch();
        fetchMock.mockResolvedValueOnce(json(200, { status: "MFA_REQUIRED", mfaToken: "t" }));
        renderLogin();

        fireEvent.change(screen.getByPlaceholderText("Su usuario"), { target: { value: "admin" } });
        fireEvent.change(screen.getByPlaceholderText("Su contraseña"), { target: { value: "secreta" } });
        fireEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));

        expect(await screen.findByText("Verificación en dos pasos")).toBeInTheDocument();
        fireEvent.click(screen.getByText("Volver al inicio de sesión"));
        expect(await screen.findByText("Bienvenido")).toBeInTheDocument();
    });
});
