import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import DocumentTypesPanel from "../pages/documents/DocumentTypesPanel";
import PartnerDocumentTypesPanel from "../pages/partners/PartnerDocumentTypesPanel";
import { formatFields, parseFields } from "../service/documentTypesApi";
import type { Partner } from "../store/partnersStore";
import { json, resetAuth, stubFetch } from "./helpers";

const ref = (id: string, code: string) => ({ id, standard: "X12", code, version: "4010", name: `Tipo ${code}` });
const t856 = { ...ref("t856", "856"), description: null, responses: [], partnerCount: 0 };
const t850 = { ...ref("t850", "850"), description: null, responses: [ref("t856", "856")], partnerCount: 2 };
const catalog = [t850, t856];
const partner = { id: "p1", as2Name: "SOCIO-A", active: true } as Partner;

let fetchMock: Mock;
const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        method: (c[1]?.method as string | undefined) ?? "GET",
        body: c[1]?.body as string | undefined,
    }));

beforeEach(() => {
    resetAuth();
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        const method = init?.method ?? "GET";
        if (path === "/catalogs/document-types" && method === "GET") return json(200, catalog);
        if (path === "/catalogs/document-types" && method === "POST") {
            return json(409, { status: 409, code: "DOCUMENT_TYPE_DUPLICATE", message: "Ya existe X12 850 4010 en el catalogo" });
        }
        if (path === "/partners/p1/document-types" && method === "GET") {
            return json(200, [
                {
                    id: "a1",
                    documentType: ref("t850", "850"),
                    direction: "INBOUND",
                    notes: null,
                    extraFields: { "Codigo de proveedor": "PRV-1" },
                    expectedResponses: [ref("t856", "856")],
                },
            ]);
        }
        if (path === "/partners/p1/document-types" && method === "PUT") return json(200, []);
        return json(404);
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderUi(ui: React.ReactNode) {
    render(
        <MemoryRouter>
            <AppProviders>{ui}</AppProviders>
        </MemoryRouter>,
    );
}

describe("catalogo de tipos de documento (HU-13)", () => {
    it("los datos adicionales se escriben como Nombre: valor por linea", () => {
        expect(parseFields("Codigo de proveedor: PRV-1\n\n  Sucursal :  Quito  \nsin valor")).toEqual({
            "Codigo de proveedor": "PRV-1",
            Sucursal: "Quito",
            "sin valor": "",
        });
        expect(formatFields({ a: "1", b: "2" })).toBe("a: 1\nb: 2");
    });

    it("muestra cada tipo con su respuesta esperada y cuantos socios lo usan", async () => {
        renderUi(<DocumentTypesPanel canEdit />);

        const row = (await screen.findByText("Tipo 850")).closest("tr")!;
        expect(within(row).getByText("X12 850 4010")).toBeInTheDocument();
        expect(within(row).getByText("X12 856 4010")).toBeInTheDocument();
        expect(within(row).getByText("2")).toBeInTheDocument();
        // En uso por socios: no se puede borrar.
        expect(within(row).getByRole("button", { name: "Borrar" })).toBeDisabled();
    });

    it("un tipo repetido muestra el aviso del servidor", async () => {
        renderUi(<DocumentTypesPanel canEdit />);
        await screen.findByText("Tipo 850");

        fireEvent.click(screen.getByRole("button", { name: /Nuevo tipo/ }));
        const dialog = await screen.findByRole("dialog");
        fireEvent.change(within(dialog).getByLabelText("Código"), { target: { value: "850" } });
        fireEvent.change(within(dialog).getByLabelText("Nombre"), { target: { value: "Otra orden" } });
        fireEvent.click(within(dialog).getByRole("button", { name: "Guardar" }));

        expect(await screen.findByText("Ya existe X12 850 4010 en el catalogo")).toBeInTheDocument();
        const post = calls().find((c) => c.method === "POST")!;
        expect(JSON.parse(post.body!)).toMatchObject({ standard: "X12", code: "850", version: "4010", name: "Otra orden" });
    });

    it("un auditor solo consulta el catalogo", async () => {
        renderUi(<DocumentTypesPanel canEdit={false} />);
        await screen.findByText("Tipo 850");
        expect(screen.queryByRole("button", { name: /Nuevo tipo/ })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Borrar" })).not.toBeInTheDocument();
    });

    it("la pestaña del socio muestra sus documentos y guarda la lista completa", async () => {
        renderUi(<PartnerDocumentTypesPanel partner={partner} canEdit />);

        expect(await screen.findByDisplayValue("Codigo de proveedor: PRV-1")).toBeInTheDocument();
        expect(screen.getByText("X12 856 4010")).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: /Guardar documentos/ }));

        await waitFor(() => expect(calls().some((c) => c.method === "PUT")).toBe(true));
        const put = calls().find((c) => c.method === "PUT")!;
        expect(JSON.parse(put.body!)).toEqual({
            documentTypes: [
                { documentTypeId: "t850", direction: "INBOUND", extraFields: { "Codigo de proveedor": "PRV-1" } },
            ],
        });
    });

    it("una fila sin tipo no se guarda", async () => {
        renderUi(<PartnerDocumentTypesPanel partner={partner} canEdit />);
        await screen.findByDisplayValue("Codigo de proveedor: PRV-1");

        fireEvent.click(screen.getByRole("button", { name: /Agregar documento/ }));
        fireEvent.click(screen.getByRole("button", { name: /Guardar documentos/ }));

        expect(await screen.findByText("Elija el tipo de documento de cada fila")).toBeInTheDocument();
        expect(calls().some((c) => c.method === "PUT")).toBe(false);
    });

    it("sin permiso de edicion la pestaña es de solo lectura", async () => {
        renderUi(<PartnerDocumentTypesPanel partner={partner} canEdit={false} />);
        expect(await screen.findByText("Entrada")).toBeInTheDocument();
        expect(screen.getByText("X12 850 4010 · Tipo 850")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: /Guardar documentos/ })).not.toBeInTheDocument();
    });
});
