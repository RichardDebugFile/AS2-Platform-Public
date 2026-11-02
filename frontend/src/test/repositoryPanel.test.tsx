import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import DocumentsPage from "../pages/documents/DocumentsPage";
import { SAMPLE_850 } from "../pages/documents/sample850";
import { searchParams } from "../service/documentsApi";
import { useAuthStore, type AuthUser } from "../store/authStore";
import { usePartnersStore } from "../store/partnersStore";
import i18n from "../i18n";
import { formatDateTime } from "../utils/dateTime";
import { ADMIN, AUDITOR, json, resetAuth, stubFetch } from "./helpers";

const OPERATOR: AuthUser = { username: "operador1", email: "operador1@example.com", roles: ["OPERATOR"] };

const doc = {
    id: "11111111-1111-1111-1111-111111111111",
    partnerAs2Name: "SOCIO-A",
    direction: "INBOUND",
    typeCode: "850",
    version: "4010",
    as2From: "ID-SOCIO",
    as2To: "ID-EMPRESA",
    messageId: "<m1@example.com>",
    isaControl: "000000123",
    gsControl: "456",
    stControl: "0789",
    mic: null,
    micAlgorithm: null,
    signingAlgorithm: null,
    encryptionAlgorithm: null,
    sizeBytes: 420,
    sha256: "a".repeat(64),
    status: "RECEIVED",
    validationDetail: null,
    attributes: null,
    fileName: "EDI_850_4010_000000123_456_0789_20261101093000000.edi",
    partnerCopyStatus: "PENDING",
    createdBy: "operador1",
    createdAt: "2026-11-01T14:30:00Z",
    updatedAt: "2026-11-01T14:30:00Z",
};

const stats = {
    total: 3,
    byStatus: { RECEIVED: 2, VALIDATION_FAILED: 1 },
    byType: { "850": 3 },
    byDirection: { INBOUND: 2, OUTBOUND: 1 },
    lastDays: [{ day: "2026-11-01", count: 3 }],
};

let fetchMock: Mock;
const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        method: (c[1]?.method ?? "GET") as string,
        body: c[1]?.body as string | undefined,
        headers: (c[1]?.headers ?? {}) as Record<string, string>,
    }));

beforeEach(() => {
    resetAuth();
    usePartnersStore.setState({ partners: [] });
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        const method = init?.method ?? "GET";
        if (path.startsWith("/documents/search")) {
            return json(200, { content: [doc], number: 0, size: 20, totalElements: 1, totalPages: 1 });
        }
        if (path === "/documents/stats") return json(200, stats);
        if (path === `/documents/${doc.id}/payload`) return new Response(SAMPLE_850, { status: 200 });
        if (path === `/documents/${doc.id}/storage-status`) {
            return json(200, { database: true, repositoryFile: true, fileName: doc.fileName, partnerCopy: false, partnerCopyStatus: "PENDING" });
        }
        if (path === `/documents/${doc.id}` && method === "DELETE") return new Response(null, { status: 204 });
        if (path.startsWith("/documents/ingest/as2")) {
            return json(201, { outcome: "CREATED", document: { ...doc, direction: "OUTBOUND", status: "CREATED" } });
        }
        if (path.startsWith("/partners")) {
            return json(200, [{ id: "p1", as2Name: "SOCIO-A", partnerName: "Socio de ejemplo", active: true }]);
        }
        if (path === "/documents/x12/generate") return new Response(SAMPLE_850, { status: 200 });
        return json(404);
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderAs(user: AuthUser) {
    useAuthStore.setState({ isAuthenticated: true, user });
    render(
        <MemoryRouter>
            <AppProviders>
                <DocumentsPage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("repositorio de documentos", () => {
    it("los filtros vacios no viajan en la busqueda", () => {
        expect(searchParams({ partner: " SOCIO-A ", type: "", direction: "INBOUND" }, 2, 50)).toBe(
            "partner=SOCIO-A&direction=INBOUND&page=2&size=50",
        );
    });

    it("lista los documentos con sus estadisticas", async () => {
        renderAs(AUDITOR);

        expect(await screen.findByText("SOCIO-A")).toBeInTheDocument();
        expect(screen.getByText("Recibido")).toBeInTheDocument();
        expect(screen.getByText("Copia pendiente")).toBeInTheDocument();
        expect(screen.getByText("000000123")).toBeInTheDocument();
        expect(screen.getByText("2 / 1")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /Registrar documento/ })).not.toBeInTheDocument();
    });

    it("busca con los filtros elegidos", async () => {
        renderAs(AUDITOR);
        await screen.findByText("SOCIO-A");
        fireEvent.change(screen.getByPlaceholderText("Control ISA"), { target: { value: "000000123" } });
        fireEvent.click(screen.getByRole("button", { name: /Buscar/ }));

        await waitFor(() =>
            expect(calls().some((c) => c.url === "/documents/search?isa=000000123&page=0&size=20")).toBe(true),
        );
    });

    it("el detalle muestra los segmentos y donde esta guardado; un auditor no puede borrar", async () => {
        renderAs(AUDITOR);
        fireEvent.click(await screen.findByText("SOCIO-A"));

        const drawer = await screen.findByRole("dialog");
        expect(within(drawer).getByText("Documento 850 · SOCIO-A")).toBeInTheDocument();
        expect(within(drawer).getByText("<m1@example.com>")).toBeInTheDocument();
        expect(within(drawer).queryByRole("button", { name: "Eliminar" })).not.toBeInTheDocument();
        // La lista y el detalle muestran la fecha con el mismo formato, con el mes en letras.
        const fecha = formatDateTime(doc.createdAt, i18n.language);
        expect(fecha).toMatch(/nov/);
        expect(within(drawer).getByText(fecha)).toBeInTheDocument();
        expect(screen.getAllByText(fecha).length).toBeGreaterThanOrEqual(2);

        fireEvent.click(within(drawer).getByRole("tab", { name: "Contenido" }));
        expect(await within(drawer).findByText("Inicio de la orden de compra")).toBeInTheDocument();

        fireEvent.click(within(drawer).getByRole("tab", { name: "Almacenamiento" }));
        expect(within(drawer).getByText("Archivo en el repositorio")).toBeInTheDocument();
        expect(within(drawer).getByText("No está")).toBeInTheDocument();
        expect(within(drawer).getByText(/reintenta la copia cada hora/)).toBeInTheDocument();
    });

    it("un administrador borra un documento", async () => {
        renderAs(ADMIN);
        fireEvent.click(await screen.findByText("SOCIO-A"));
        const drawer = await screen.findByRole("dialog");
        fireEvent.click(within(drawer).getByRole("button", { name: "Eliminar" }));
        fireEvent.click(await screen.findByRole("button", { name: /OK|Aceptar/ }));

        await waitFor(() =>
            expect(calls().some((c) => c.url === `/documents/${doc.id}` && c.method === "DELETE")).toBe(true),
        );
    });

    it("un operador guarda en el repositorio el documento que genero, como saliente", async () => {
        renderAs(OPERATOR);
        fireEvent.click(screen.getByRole("tab", { name: "Generar documento" }));
        fireEvent.click(await screen.findByRole("button", { name: "Generar" }));
        fireEvent.click(await screen.findByRole("button", { name: "Guardar en el repositorio" }));

        const modal = await screen.findByRole("dialog");
        expect(within(modal).getByLabelText("Documento X12")).toHaveValue(SAMPLE_850);
        expect(within(modal).getByRole("radio", { name: "Saliente" })).toBeChecked();

        fireEvent.mouseDown(within(modal).getByRole("combobox", { name: "Socio" }));
        fireEvent.click(await screen.findByText("SOCIO-A · Socio de ejemplo"));
        fireEvent.change(within(modal).getByPlaceholderText("<id@example.com>"), {
            target: { value: "<prueba-1@example.com>" },
        });
        fireEvent.click(within(modal).getByRole("button", { name: "Registrar" }));

        await waitFor(() => expect(calls().some((c) => c.url.startsWith("/documents/ingest/as2"))).toBe(true));
        const ingest = calls().find((c) => c.url.startsWith("/documents/ingest/as2"))!;
        expect(ingest.url).toBe("/documents/ingest/as2?partner=SOCIO-A&direction=OUTBOUND");
        expect(ingest.method).toBe("POST");
        expect(ingest.body).toBe(SAMPLE_850);
        expect(ingest.headers["Message-ID"]).toBe("<prueba-1@example.com>");
        expect(ingest.headers["Content-Type"]).toContain("text/plain");
    });
});
