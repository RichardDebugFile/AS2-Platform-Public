import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import CertificatesPanel from "../pages/partners/CertificatesPanel";
import { formatFingerprint, useCertificatesStore, type Certificate } from "../store/certificatesStore";
import type { Partner } from "../store/partnersStore";
import { json, resetAuth, stubFetch } from "./helpers";

const partner = { id: "p1", as2Name: "SOCIO-A", serverAddress: "as2.socio.example", port: 443, endpointUrl: "", hasBasicAuthPassword: false, active: true } as Partner;

const cert = (id: string, over: Partial<Certificate> = {}): Certificate => ({
    id,
    partnerId: "p1",
    keyUsage: "ENCRYPTION",
    issuedTo: `cert-${id}`,
    issuedBy: "CA de prueba",
    subjectDn: `CN=cert-${id}`,
    issuerDn: "CN=CA de prueba",
    serialNumber: "1F",
    fingerprintSha256: "ab".repeat(32),
    validFrom: "2026-01-01T00:00:00Z",
    validTo: "2027-12-31T00:00:00Z",
    validity: "VALID",
    daysToExpire: 400,
    active: true,
    preferred: false,
    ...over,
});

let list: Certificate[];
let fetchMock: Mock;
const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        method: (c[1]?.method as string | undefined) ?? "GET",
        body: c[1]?.body,
        headers: (c[1]?.headers ?? {}) as Record<string, string>,
    }));

beforeEach(() => {
    resetAuth();
    useCertificatesStore.setState({ byPartner: {}, loading: false });
    list = [cert("c2", { preferred: true, issuedTo: "nuevo" }), cert("c1", { issuedTo: "saliente", validity: "EXPIRING", daysToExpire: 12 })];
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        const method = init?.method ?? "GET";
        if (path === "/partners/p1/certificates" && method === "GET") return json(200, list);
        if (path === "/partners/p1/certificates/upload") {
            return json(201, { certificate: cert("c3"), outcome: "CREATED", warnings: [] });
        }
        if (path.endsWith("/deactivate")) {
            return json(200, { sameUsage: list, warnings: ["No queda ningun certificado de cifrado activo"] });
        }
        return json(200, { sameUsage: list, warnings: [] });
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderPanel(canEdit = true, isAdmin = true) {
    render(
        <MemoryRouter>
            <AppProviders>
                <CertificatesPanel partner={partner} canEdit={canEdit} isAdmin={isAdmin} />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("certificados del socio", () => {
    it("muestra el preferente y el respaldo con su vigencia y huella abreviada", async () => {
        renderPanel();
        expect(await screen.findByText("nuevo")).toBeInTheDocument();
        expect(screen.getByText("Preferente")).toBeInTheDocument();
        expect(screen.getByText("Respaldo")).toBeInTheDocument();
        expect(screen.getByText("Vence en 12 días")).toBeInTheDocument();
        expect(screen.getAllByText(formatFingerprint("ab".repeat(32), true))).toHaveLength(2);
        expect(screen.getByText("Doble compuerta")).toBeInTheDocument();
    });

    it("carga un certificado como multipart sin Content-Type JSON", async () => {
        renderPanel();
        await screen.findByText("nuevo");
        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        fireEvent.change(input, { target: { files: [new File(["-----BEGIN CERTIFICATE-----"], "socio.pem")] } });
        fireEvent.click(await screen.findByRole("button", { name: "Cargar" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/certificates/upload")).toBe(true));
        const call = calls().find((c) => c.url === "/partners/p1/certificates/upload")!;
        expect(call.method).toBe("POST");
        expect(call.body).toBeInstanceOf(FormData);
        expect((call.body as FormData).get("keyUsage")).toBe("ENCRYPTION");
        expect(call.headers["Content-Type"]).toBeUndefined();
        expect(await screen.findByText("Certificado cargado")).toBeInTheDocument();
    });

    it("desactivar el unico activo de un uso pide confirmacion explicando el efecto", async () => {
        list = [cert("c1", { preferred: true, issuedTo: "unico" })];
        renderPanel();
        await screen.findByText("unico");
        fireEvent.click(screen.getByRole("button", { name: "Desactivar" }));
        const dialog = await screen.findByRole("dialog");
        expect(within(dialog).getByText(/los envíos a este socio fallarán/)).toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole("button", { name: "Desactivar" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/certificates/c1/deactivate")).toBe(true));
        expect(await screen.findByText(/No queda ningun certificado de cifrado activo/)).toBeInTheDocument();
    });

    it("marcar preferente llama al servicio", async () => {
        renderPanel();
        await screen.findByText("saliente");
        fireEvent.click(screen.getByRole("button", { name: "Marcar preferente" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/certificates/c1/prefer" && c.method === "POST")).toBe(true));
    });

    it("solo ADMIN ve Eliminar; un operador carga y desactiva", async () => {
        renderPanel(true, false);
        await screen.findByText("nuevo");
        expect(screen.queryByRole("button", { name: "Eliminar" })).not.toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: "Desactivar" })).toHaveLength(2);
        expect(screen.getByRole("button", { name: "Cargar" })).toBeInTheDocument();
    });

    it("en solo lectura no hay carga ni acciones de cambio, pero se puede descargar", async () => {
        renderPanel(false, false);
        await screen.findByText("nuevo");
        expect(screen.queryByRole("button", { name: "Cargar" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Desactivar" })).not.toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: "Descargar PEM" })).toHaveLength(2);
    });

    it("un certificado inactivo y vigente se puede activar; uno caducado no", async () => {
        list = [cert("c1", { active: false, issuedTo: "inactivo" }), cert("c2", { active: false, validity: "EXPIRED", issuedTo: "viejo" })];
        renderPanel();
        await screen.findByText("inactivo");
        expect(screen.getAllByRole("button", { name: "Activar" })).toHaveLength(1);
        expect(screen.getByText("Caducado")).toBeInTheDocument();
    });
});
