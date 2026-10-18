import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import UsersPage from "../pages/UsersPage";
import { json, resetAuth, stubFetch } from "./helpers";

let fetchMock: Mock;

const page = {
    content: [
        { id: "u1", username: "operador1", email: "op@example.com", roles: ["OPERATOR"], active: true, mfaEnabled: false },
        { id: "u2", username: "auditor1", email: "au@example.com", roles: ["AUDITOR"], active: false, mfaEnabled: true },
    ],
    number: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
};

beforeEach(() => {
    resetAuth();
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async () => json(200, page));
});

afterEach(() => vi.unstubAllGlobals());

function renderPage() {
    render(
        <MemoryRouter>
            <AppProviders>
                <UsersPage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("pantalla de usuarios", () => {
    it("lista los usuarios con sus roles y estado", async () => {
        renderPage();
        expect(await screen.findByText("operador1")).toBeInTheDocument();
        expect(screen.getByText("Operador")).toBeInTheDocument();
        expect(screen.getByText("Inactivo")).toBeInTheDocument();
        expect(String(fetchMock.mock.calls[0][0])).toContain("/users?page=0&size=20");
    });

    it("activa un usuario inactivo", async () => {
        renderPage();
        fireEvent.click(await screen.findByRole("button", { name: "Activar" }));
        await waitFor(() =>
            expect(fetchMock.mock.calls.some((c) => String(c[0]).endsWith("/users/u2/active") && c[1].method === "PATCH")).toBe(true),
        );
    });

    it("busca por usuario o correo", async () => {
        renderPage();
        const search = await screen.findByPlaceholderText("Buscar por usuario o correo");
        fireEvent.change(search, { target: { value: "ana" } });
        fireEvent.keyDown(search, { key: "Enter", code: "Enter", keyCode: 13 });
        await waitFor(() => expect(fetchMock.mock.calls.some((c) => String(c[0]).includes("q=ana"))).toBe(true));
    });
});
