import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AppProviders from "../AppProviders";
import AppRoutes from "../routes/AppRoutes";
import { menuFor } from "../routes/menu";
import { startSession } from "../session/sessionSync";
import { useAuthStore, type AuthUser } from "../store/authStore";
import { ADMIN, AUDITOR, json, resetAuth, stubFetch } from "./helpers";

function renderAt(path: string) {
    return render(
        <MemoryRouter initialEntries={[path]}>
            <AppProviders>
                <AppRoutes />
            </AppProviders>
        </MemoryRouter>,
    );
}

function signIn(user: AuthUser) {
    const now = Date.now();
    startSession({
        sessionExpiresAt: now + 3_600_000,
        refreshExpiresAt: now + 900_000,
        idleTimeoutMs: 900_000,
        accessTtlMs: 300_000,
        sessionMaxMs: 3_600_000,
        issuedAt: now,
    });
    useAuthStore.setState({ isAuthenticated: true, user });
}

beforeEach(() => {
    resetAuth();
    stubFetch().mockImplementation(async () => json(200, { content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 }));
});

afterEach(() => vi.unstubAllGlobals());

describe("rutas y menu por rol", () => {
    it("acceso sin sesion: /partners lleva al login", async () => {
        renderAt("/partners");
        expect(await screen.findByRole("button", { name: "Iniciar sesión" })).toBeInTheDocument();
    });

    it("una ruta desconocida lleva al login", async () => {
        renderAt("/no-existe");
        expect(await screen.findByText("Bienvenido")).toBeInTheDocument();
    });

    it("un AUDITOR no ve la seccion Administracion", async () => {
        signIn(AUDITOR);
        renderAt("/dashboard");
        const menu = await screen.findByRole("menu");
        expect(within(menu).getByText("Socios comerciales")).toBeInTheDocument();
        expect(within(menu).queryByText("Administración")).not.toBeInTheDocument();
        expect(within(menu).queryByText("Usuarios")).not.toBeInTheDocument();
    });

    it("un ADMIN ve la seccion Administracion", async () => {
        signIn(ADMIN);
        renderAt("/dashboard");
        const menu = await screen.findByRole("menu");
        expect(within(menu).getByText("Administración")).toBeInTheDocument();
        expect(within(menu).getByText("Usuarios")).toBeInTheDocument();
    });

    it("un AUDITOR que escribe la URL de Usuarios vuelve al inicio", async () => {
        signIn(AUDITOR);
        renderAt("/admin/users");
        expect(await screen.findByText("Hola, auditor1")).toBeInTheDocument();
    });

    it("con sesion, el login lleva al inicio", async () => {
        signIn(ADMIN);
        renderAt("/");
        expect(await screen.findByText("Hola, admin")).toBeInTheDocument();
    });

    it("menuFor quita las entradas de otros roles y las categorias vacias", () => {
        expect(menuFor(["OPERATOR"]).map((s) => s.key)).toEqual(["operation", "partners", "documents", "alerts"]);
        expect(menuFor(["ADMIN"]).map((s) => s.key)).toContain("administration");
        expect(menuFor(undefined).map((s) => s.key)).not.toContain("administration");
    });
});
