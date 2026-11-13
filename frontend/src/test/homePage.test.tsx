import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import dayjs from "dayjs";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import HomePage, { DEFAULT_DAYS } from "../pages/HomePage";
import { dashboardParams } from "../service/dashboardApi";
import { useAuthStore } from "../store/authStore";
import { usePartnersStore } from "../store/partnersStore";
import { AUDITOR, json, resetAuth, stubFetch } from "./helpers";

const dashboard = {
    from: "2026-11-04",
    to: "2026-11-10",
    total: 12,
    ok: 8,
    failed: 2,
    inProgress: 1,
    duplicates: 1,
    successRate: 80.0,
    byStatus: { DELIVERED: 8, SEND_FAILED: 2, MDN_PENDING: 1, DUPLICATE: 1 },
    byDirection: { INBOUND: 5, OUTBOUND: 7 },
    byPartner: [
        { partner: "SOCIO-A", inbound: 3, outbound: 6, ok: 7, failed: 1, total: 9 },
        { partner: "SOCIO-B", inbound: 2, outbound: 1, ok: 1, failed: 1, total: 3 },
    ],
    byDay: [
        { day: "2026-11-09", ok: 3, failed: 1, other: 0, total: 4 },
        { day: "2026-11-10", ok: 5, failed: 1, other: 2, total: 8 },
    ],
};

const empty = {
    ...dashboard,
    total: 0,
    ok: 0,
    failed: 0,
    inProgress: 0,
    duplicates: 0,
    successRate: null,
    byStatus: {},
    byDirection: {},
    byPartner: [],
    byDay: [],
};

const status = {
    checkedAt: "2026-11-10T15:00:00Z",
    services: [
        { service: "gateway", status: "UP", latencyMs: 0 },
        { service: "partners-service", status: "UP", latencyMs: 12 },
        { service: "documents-service", status: "UNREACHABLE", latencyMs: null },
    ],
};

let fetchMock: Mock;
let dashboardBody: unknown = dashboard;
const paths = () => fetchMock.mock.calls.map((c) => String(c[0]).replace("https://as2.example.com/api", ""));

beforeEach(() => {
    resetAuth();
    dashboardBody = dashboard;
    usePartnersStore.setState({ partners: [] });
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        if (path.startsWith("/documents/dashboard")) return json(200, dashboardBody);
        if (path === "/system/status") return json(200, status);
        if (path.startsWith("/partners")) {
            return json(200, [
                { id: "p1", as2Name: "SOCIO-B", partnerName: "Socio B", active: true },
                { id: "p2", as2Name: "SOCIO-A", partnerName: "Socio A", active: true },
            ]);
        }
        return json(404);
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderHome() {
    useAuthStore.setState({ isAuthenticated: true, user: AUDITOR });
    render(
        <MemoryRouter>
            <AppProviders>
                <HomePage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("inicio (tablero)", () => {
    it("los filtros vacios no viajan", () => {
        expect(dashboardParams({ from: "2026-11-01", to: "2026-11-07", partner: " ", direction: undefined })).toBe(
            "from=2026-11-01&to=2026-11-07",
        );
    });

    it("pide los ultimos siete dias al abrirse", async () => {
        renderHome();
        const to = dayjs().format("YYYY-MM-DD");
        const from = dayjs().subtract(DEFAULT_DAYS - 1, "day").format("YYYY-MM-DD");
        await waitFor(() => expect(paths()).toContain(`/documents/dashboard?from=${from}&to=${to}`));
    });

    it("muestra las cifras, el resultado y el trafico por socio", async () => {
        renderHome();

        expect(await screen.findByText("SOCIO-B")).toBeInTheDocument();
        expect(screen.getByText("Tasa de éxito")).toBeInTheDocument();
        expect(screen.getByText("En curso: 1 · Repetidos: 1")).toBeInTheDocument();
        // Socios en el orden del servidor (de mas a menos documentos).
        const rows = screen.getAllByRole("row").map((r) => r.textContent ?? "");
        expect(rows.findIndex((r) => r.includes("SOCIO-A"))).toBeLessThan(rows.findIndex((r) => r.includes("SOCIO-B")));
        // Barra de resultados: un segmento por resultado presente, con su leyenda.
        expect(screen.getByTestId("outcome-OK")).toBeInTheDocument();
        expect(screen.getByTestId("outcome-FAILED")).toBeInTheDocument();
        expect(screen.getByText("66,7 %")).toBeInTheDocument();
    });

    it("sin documentos lo dice y no inventa una tasa de exito", async () => {
        dashboardBody = empty;
        renderHome();

        expect(await screen.findByText("Ningún documento con resultado final")).toBeInTheDocument();
        expect(screen.getAllByText("No hay documentos en el rango elegido.").length).toBeGreaterThan(0);
        expect(screen.queryByTestId("outcome-OK")).not.toBeInTheDocument();
    });

    it("filtra por direccion", async () => {
        renderHome();
        await screen.findByText("SOCIO-B");

        fireEvent.mouseDown(screen.getByRole("combobox", { name: "Dirección" }));
        fireEvent.click(await screen.findByTitle("Saliente"));

        await waitFor(() => expect(paths().some((p) => p.includes("direction=OUTBOUND"))).toBe(true));
    });

    it("informa si el tablero no carga", async () => {
        fetchMock.mockImplementation(async (url: string) => {
            const path = String(url).replace("https://as2.example.com/api", "");
            if (path.startsWith("/documents/dashboard")) return json(400, { message: "El rango no puede superar 366 dias" });
            if (path === "/system/status") return json(200, status);
            return json(200, []);
        });
        renderHome();

        expect(await screen.findByText("No se pudo cargar el tablero: El rango no puede superar 366 dias")).toBeInTheDocument();
    });

    it("muestra la salud de cada servicio", async () => {
        renderHome();

        const docs = await screen.findByTestId("service-documents-service");
        expect(within(docs).getByText("Sin respuesta")).toBeInTheDocument();
        const partners = screen.getByTestId("service-partners-service");
        expect(within(partners).getByText("En línea")).toBeInTheDocument();
        expect(within(partners).getByText("12 ms")).toBeInTheDocument();
    });
});
