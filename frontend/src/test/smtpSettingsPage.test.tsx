import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import SmtpSettingsPage from "../pages/alerts/SmtpSettingsPage";
import { menuFor } from "../routes/menu";
import { useAuthStore } from "../store/authStore";
import { ADMIN, json, resetAuth, stubFetch } from "./helpers";

const configured = {
    configured: true,
    host: "mailpit",
    port: 1025,
    security: "NONE",
    username: "alertas",
    passwordSet: true,
    fromAddress: "alertas@example.com",
    fromName: "Plataforma AS2/EDI",
    updatedAt: "2026-11-14T17:00:00Z",
    updatedBy: "ricardo",
};

const empty = {
    configured: false,
    host: null,
    port: null,
    security: null,
    username: null,
    passwordSet: false,
    fromAddress: null,
    fromName: null,
    updatedAt: null,
    updatedBy: null,
};

let fetchMock: Mock;
let current: unknown = configured;

const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        method: (c[1]?.method as string | undefined) ?? "GET",
        body: c[1]?.body as string | undefined,
    }));

beforeEach(() => {
    resetAuth();
    current = configured;
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        if (path === "/smtp-config" && (init?.method ?? "GET") === "GET") return json(200, current);
        if (path === "/smtp-config" && init?.method === "PUT") return json(200, { ...configured, host: "smtp.example.com" });
        if (path === "/smtp-config/test") {
            return json(200, { sent: true, to: "admin@example.com", server: "mailpit:1025", elapsedMs: 35 });
        }
        return json(404);
    });
    useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
});

afterEach(() => vi.unstubAllGlobals());

function renderPage() {
    render(
        <MemoryRouter>
            <AppProviders>
                <SmtpSettingsPage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("configuracion de correo", () => {
    it("solo la ve ADMIN, en el menu de Alertas", () => {
        expect(menuFor(["ADMIN"]).find((s) => s.key === "alerts")?.entries.map((e) => e.path)).toEqual(["/alerts/smtp"]);
        expect(menuFor(["OPERATOR"]).some((s) => s.key === "alerts")).toBe(false);
        expect(menuFor(["AUDITOR"]).some((s) => s.key === "alerts")).toBe(false);
    });

    it("sin configuracion lo dice y no deja enviar la prueba", async () => {
        current = empty;
        renderPage();

        expect(await screen.findByText("Todavía no hay un servidor de correo configurado.")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Enviar correo de prueba/ })).toBeDisabled();
    });

    it("muestra la configuracion guardada sin la contrasena y la conserva al guardar", async () => {
        renderPage();

        expect(await screen.findByDisplayValue("mailpit")).toBeInTheDocument();
        expect(screen.getByPlaceholderText("Guardada: déjela vacía para conservarla")).toHaveValue("");
        expect(screen.getByTestId("smtp-updated")).toHaveTextContent("Guardado por ricardo");

        fireEvent.change(screen.getByLabelText("Servidor"), { target: { value: "smtp.example.com" } });
        fireEvent.click(screen.getByRole("button", { name: /Guardar/ }));

        await waitFor(() => expect(calls().some((c) => c.method === "PUT")).toBe(true));
        const body = JSON.parse(calls().find((c) => c.method === "PUT")!.body!);
        expect(body).toMatchObject({ host: "smtp.example.com", port: 1025, security: "NONE", username: "alertas" });
        expect(body).not.toHaveProperty("password");
        expect(await screen.findByText("Configuración de correo guardada")).toBeInTheDocument();
    });

    it("envia el correo de prueba al correo del usuario y dice por donde salio", async () => {
        renderPage();
        await screen.findByDisplayValue("mailpit");

        expect(screen.getByLabelText("Destinatario")).toHaveValue("admin@example.com");
        fireEvent.click(screen.getByRole("button", { name: /Enviar correo de prueba/ }));

        expect(await screen.findByText("Correo de prueba enviado a admin@example.com por mailpit:1025 en 35 ms.")).toBeInTheDocument();
        const test = calls().find((c) => c.url === "/smtp-config/test")!;
        expect(test.method).toBe("POST");
        expect(JSON.parse(test.body!)).toEqual({ to: "admin@example.com" });
    });

    it("si el servidor no acepta el correo muestra la causa", async () => {
        fetchMock.mockImplementation(async (url: string) => {
            const path = String(url).replace("https://as2.example.com/api", "");
            if (path === "/smtp-config/test") {
                return json(502, {
                    status: 502,
                    code: "SMTP_ERROR",
                    message: "No se pudo enviar el correo de prueba por mailpit:2: Connection refused",
                });
            }
            return json(200, configured);
        });
        renderPage();
        await screen.findByDisplayValue("mailpit");

        fireEvent.click(screen.getByRole("button", { name: /Enviar correo de prueba/ }));

        expect(await screen.findByText("No se envió el correo de prueba")).toBeInTheDocument();
        expect(screen.getByText("No se pudo enviar el correo de prueba por mailpit:2: Connection refused")).toBeInTheDocument();
    });

    it("al cambiar la seguridad propone su puerto habitual", async () => {
        renderPage();
        await screen.findByDisplayValue("mailpit");

        fireEvent.mouseDown(screen.getByRole("combobox", { name: "Seguridad" }));
        fireEvent.click(await screen.findByTitle("STARTTLS (puerto 587)"));

        await waitFor(() => expect(screen.getByLabelText("Puerto")).toHaveValue("587"));
    });
});
