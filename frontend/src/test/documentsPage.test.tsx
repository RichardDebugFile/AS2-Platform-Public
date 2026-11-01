import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import DocumentsPage from "../pages/documents/DocumentsPage";
import { SAMPLE_850 } from "../pages/documents/sample850";
import { useAuthStore, type AuthUser } from "../store/authStore";
import { AUDITOR, json, resetAuth, stubFetch } from "./helpers";

const OPERATOR: AuthUser = { username: "operador1", email: "operador1@example.com", roles: ["OPERATOR"] };

const summary = {
    typeCode: "850",
    version: "4010",
    functionalCode: "PO",
    sender: { qualifier: "12", id: "ID-SOCIO" },
    receiver: { qualifier: "08", id: "ID-EMPRESA" },
    groupSender: "ID-SOCIO",
    groupReceiver: "ID-EMPRESA",
    interchangeDate: "261101",
    interchangeTime: "0930",
    usageIndicator: "T",
    controls: { ISA13: "000000123", GS06: "456", ST02: "0789" },
    complete: true,
    segmentCount: 15,
    elementSeparator: "*",
    componentSeparator: ">",
    segmentTerminator: "~",
};

let validation: unknown;
let fetchMock: Mock;
const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        body: c[1]?.body as string | undefined,
        headers: (c[1]?.headers ?? {}) as Record<string, string>,
    }));

beforeEach(() => {
    resetAuth();
    validation = { status: "VALIDATED", typeCode: "850", version: "4010", missingSegments: [], problems: [] };
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        if (path === "/documents/x12/parse") return json(200, summary);
        if (path === "/documents/x12/validate") return json(200, validation);
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

describe("documentos X12", () => {
    it("interpreta un documento: resumen, validacion y segmentos con su nombre", async () => {
        renderAs(AUDITOR);
        fireEvent.click(screen.getByRole("button", { name: "Cargar ejemplo" }));
        fireEvent.click(screen.getByRole("button", { name: "Interpretar" }));

        expect(await screen.findByText("Válido según el esquema del 850")).toBeInTheDocument();
        expect(screen.getByText("12 · ID-SOCIO")).toBeInTheDocument();
        expect(screen.getByText("ISA13 000000123 GS06 456 ST02 0789")).toBeInTheDocument();
        expect(screen.getByText("Pruebas")).toBeInTheDocument();
        expect(screen.getByText("Segmentos (15)")).toBeInTheDocument();
        expect(screen.getByText("Inicio de la orden de compra")).toBeInTheDocument();
        expect(screen.getAllByText("Línea de la orden")).toHaveLength(2);

        const parse = calls().find((c) => c.url === "/documents/x12/parse")!;
        expect(parse.body).toBe(SAMPLE_850);
        expect(parse.headers["Content-Type"]).toContain("text/plain");
    });

    it("muestra que segmentos faltan y que sobres no cierran", async () => {
        validation = {
            status: "VALIDATION_FAILED",
            typeCode: "850",
            version: "4010",
            missingSegments: ["CTT"],
            problems: ["SE01 declara 11 segmentos y la transaccion tiene 10"],
        };
        renderAs(AUDITOR);
        fireEvent.click(screen.getByRole("button", { name: "Cargar ejemplo" }));
        fireEvent.click(screen.getByRole("button", { name: "Interpretar" }));

        expect(await screen.findByText("No cumple el esquema del 850")).toBeInTheDocument();
        expect(screen.getByText("Faltan los segmentos: CTT")).toBeInTheDocument();
        expect(screen.getByText("SE01 declara 11 segmentos y la transaccion tiene 10")).toBeInTheDocument();
    });

    it("un auditor solo ve el visor", () => {
        renderAs(AUDITOR);
        expect(screen.getByRole("tab", { name: "Visor X12" })).toBeInTheDocument();
        expect(screen.queryByRole("tab", { name: "Generar documento" })).not.toBeInTheDocument();
    });

    it("un operador genera un 850 y lo abre en el visor", async () => {
        renderAs(OPERATOR);
        fireEvent.click(screen.getByRole("tab", { name: "Generar documento" }));
        fireEvent.click(await screen.findByRole("button", { name: "Generar" }));

        const result = await screen.findByRole("textbox", { name: "Documento generado" });
        expect((result as HTMLTextAreaElement).value).toBe(SAMPLE_850);
        const body = JSON.parse(calls().find((c) => c.url === "/documents/x12/generate")!.body!);
        expect(body).toMatchObject({
            type: "850",
            version: "4010",
            sender: { qualifier: "08", id: "ID-EMPRESA" },
            receiver: { qualifier: "12", id: "ID-SOCIO" },
            purchaseOrder: { number: "OC-2026-001", lines: [{ productId: "ROSA-ROJA-60", quantity: 120 }] },
        });

        fireEvent.click(screen.getByRole("button", { name: "Abrir en el visor" }));
        await waitFor(() => expect(screen.getByRole("tab", { name: "Visor X12", selected: true })).toBeInTheDocument());
        const viewer = screen.getByRole("textbox", { name: "Visor X12" }) as HTMLTextAreaElement;
        expect(viewer.value).toBe(SAMPLE_850);
    });

    it("el 997 pide el documento que se acusa, no el emisor ni el receptor", async () => {
        renderAs(OPERATOR);
        fireEvent.click(screen.getByRole("tab", { name: "Generar documento" }));
        fireEvent.click(await screen.findByText("997 · Acuse funcional"));

        expect(screen.getByLabelText("Documento que se acusa")).toBeInTheDocument();
        expect(screen.queryByText("Emisor")).not.toBeInTheDocument();
        fireEvent.change(screen.getByLabelText("Documento que se acusa"), { target: { value: SAMPLE_850 } });
        fireEvent.click(screen.getByRole("button", { name: "Generar" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/documents/x12/generate")).toBe(true));
        const body = JSON.parse(calls().find((c) => c.url === "/documents/x12/generate")!.body!);
        expect(body).toEqual({ type: "997", version: "4010", production: false, original: SAMPLE_850 });
        expect(within(document.body).queryByText("Obligatorio")).not.toBeInTheDocument();
    });
});
