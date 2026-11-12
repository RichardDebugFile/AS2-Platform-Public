import { render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import SystemPage from "../pages/system/SystemPage";
import { menuFor } from "../routes/menu";
import { useAuthStore } from "../store/authStore";
import { AUDITOR, json, resetAuth, stubFetch } from "./helpers";

const status = {
    checkedAt: "2026-11-10T15:00:00Z",
    services: [
        { service: "gateway", status: "UP", latencyMs: 0, components: {} },
        { service: "partners-service", status: "UP", latencyMs: 14, components: { db: "UP", diskSpace: "UP", ping: "UP" } },
        { service: "documents-service", status: "DOWN", latencyMs: 30, components: { db: "DOWN", ping: "UP" } },
        { service: "as2-adapter", status: "UNREACHABLE", latencyMs: null, components: {} },
    ],
};

const doc = (id: string, direction: string, partner: string) => ({
    id,
    partnerAs2Name: partner,
    direction,
    typeCode: "850",
    status: "DELIVERED",
    createdAt: "2026-11-10T14:00:00Z",
});

let fetchMock: Mock;
const paths = () => fetchMock.mock.calls.map((c) => String(c[0]).replace("https://as2.example.com/api", ""));

beforeEach(() => {
    resetAuth();
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        if (path === "/system/status") return json(200, status);
        if (path.startsWith("/documents/dashboard")) {
            return json(200, { total: 5, failed: 1, byDirection: { INBOUND: 2, OUTBOUND: 3 } });
        }
        if (path.startsWith("/documents/recent")) return json(200, [doc("d1", "OUTBOUND", "SOCIO-RECIENTE")]);
        if (path.includes("direction=INBOUND")) {
            return json(200, { content: [doc("d2", "INBOUND", "SOCIO-ENTRA")], number: 0, size: 1, totalElements: 1, totalPages: 1 });
        }
        if (path.includes("direction=OUTBOUND")) {
            return json(200, { content: [], number: 0, size: 1, totalElements: 0, totalPages: 0 });
        }
        return json(404);
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderPage() {
    useAuthStore.setState({ isAuthenticated: true, user: AUDITOR });
    render(
        <MemoryRouter>
            <AppProviders>
                <SystemPage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("estado del sistema", () => {
    it("esta en el menu de Auditoria, junto a la bitacora, para todos los roles", () => {
        const audit = menuFor(["AUDITOR"]).find((s) => s.key === "audit");
        expect(audit?.entries.map((e) => e.path)).toEqual(["/audit", "/system-status"]);
    });

    it("muestra cada servicio con su estado y sus verificaciones internas", async () => {
        renderPage();

        const partners = await screen.findByTestId("service-card-partners-service");
        expect(within(partners).getByText("Base de datos")).toBeInTheDocument();
        expect(within(partners).getByText("Espacio en disco")).toBeInTheDocument();
        expect(within(partners).getByText("14 ms")).toBeInTheDocument();

        const documents = screen.getByTestId("service-card-documents-service");
        expect(within(documents).getByText("Caído")).toBeInTheDocument();
        expect(within(documents).getByText("Con falla")).toBeInTheDocument();

        const adapter = screen.getByTestId("service-card-as2-adapter");
        expect(within(adapter).getByText("Sin respuesta")).toBeInTheDocument();
        expect(within(adapter).getByText("El servicio no publica verificaciones internas.")).toBeInTheDocument();

        expect(screen.getByText("2 servicios con problemas")).toBeInTheDocument();
    });

    it("muestra el flujo de documentos de hoy y los ultimos registrados", async () => {
        renderPage();

        expect(await screen.findByText("SOCIO-RECIENTE")).toBeInTheDocument();
        expect(screen.getByText(/SOCIO-ENTRA/)).toBeInTheDocument();
        expect(screen.getByText("ninguno todavía")).toBeInTheDocument();
        await waitFor(() => expect(paths()).toContain("/documents/recent?limit=10"));
    });
});
