import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import PartnersPage from "../pages/partners/PartnersPage";
import { useAuthStore, type AuthUser } from "../store/authStore";
import { usePartnersStore } from "../store/partnersStore";
import { ADMIN, AUDITOR, json, resetAuth, stubFetch } from "./helpers";

const OPERATOR: AuthUser = { username: "operador1", email: "operador1@example.com", roles: ["OPERATOR"] };

const active = {
    id: "p1",
    as2Name: "SOCIO-A",
    partnerName: "Socio de prueba",
    partnerType: "CUSTOMER",
    serverAddress: "as2.socio.example",
    port: 4443,
    transportType: "HTTPS",
    path: "/as2",
    endpointUrl: "https://as2.socio.example:4443/as2",
    hasBasicAuthPassword: true,
    signingAlgorithm: "RSA_SHA256",
    encryptionAlgorithm: "AES256",
    micAlgorithm: "SHA256",
    mdnMode: "SYNC",
    active: true,
};
const inactive = { ...active, id: "p2", as2Name: "SOCIO-VIEJO", partnerName: "Antiguo", active: false };

const catalog = {
    signing: [
        { key: "RSA_SHA256", label: "RSA-SHA256", status: "recommended" },
        { key: "RSA_SHA1", label: "RSA-SHA1", status: "legacy" },
    ],
    encryption: [
        { key: "AES256", label: "AES-256", status: "recommended" },
        { key: "TRIPLE_DES", label: "3DES", status: "legacy" },
    ],
    mic: [
        { key: "SHA256", label: "SHA-256", status: "recommended" },
        { key: "SHA1", label: "SHA-1", status: "legacy" },
    ],
};

const configs: Record<string, unknown> = {
    "outgoing-config": { localFolder: "/data/partners/SOCIO-A/Outgoing", sendingMode: "REGULAR", archiveMode: "MOVE", mimeType: "APPLICATION_EDI_X12" },
    "incoming-config": { incomingFolder: "/data/partners/SOCIO-A/Incoming" },
    "mdn-config": { localFolder: "/data/partners/SOCIO-A/MDN", receiptType: "SIGNED", deliveryMode: "SYNC" },
};

let fetchMock: Mock;

/** Peticiones hechas, sin la base del API. */
const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        method: (c[1]?.method as string | undefined) ?? "GET",
        body: c[1]?.body ? JSON.parse(String(c[1].body)) : undefined,
    }));

beforeEach(() => {
    resetAuth();
    usePartnersStore.setState({ partners: [], showInactive: false, loading: false, error: null, catalog: null });
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        const method = init?.method ?? "GET";
        if (path === "/catalogs/algorithms") return json(200, catalog);
        if (path === "/partners/inactive") return json(200, [inactive]);
        if (path === "/partners" && method === "GET") return json(200, [active]);
        if (path === "/partners/complete") return json(201, { partner: { ...active, id: "p9" } });
        const cfg = path.split("/").at(-1) ?? "";
        if (cfg in configs) return json(200, configs[cfg]);
        if (method === "DELETE" || method === "PATCH") return json(204);
        return json(200, active);
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderAs(user: AuthUser) {
    useAuthStore.setState({ isAuthenticated: true, user });
    render(
        <MemoryRouter>
            <AppProviders>
                <PartnersPage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("pantalla de socios", () => {
    it("lista los socios con su direccion AS2, tipo y estado", async () => {
        renderAs(OPERATOR);
        expect(await screen.findByText("SOCIO-A")).toBeInTheDocument();
        expect(screen.getByText("https://as2.socio.example:4443/as2")).toBeInTheDocument();
        expect(screen.getByText("Cliente")).toBeInTheDocument();
        expect(screen.getByText("Activo")).toBeInTheDocument();
    });

    it("filtra por identificador o nombre", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.change(screen.getByPlaceholderText("Buscar por identificador o nombre"), { target: { value: "otro" } });
        expect(screen.queryByText("SOCIO-A")).not.toBeInTheDocument();
        expect(screen.getByText("No hay socios registrados")).toBeInTheDocument();
    });

    it("un AUDITOR solo consulta: sin alta, sin baja y el detalle en solo lectura", async () => {
        renderAs(AUDITOR);
        await screen.findByText("SOCIO-A");
        expect(screen.queryByRole("button", { name: /Nuevo socio/ })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Dar de baja" })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Ver" }));
        expect(await screen.findByText("Socio SOCIO-A (solo lectura)")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Guardar" })).not.toBeInTheDocument();
        expect(screen.getByLabelText("Identificador AS2")).toBeDisabled();
    });

    it("un operador registra un socio con el alta completa", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByRole("button", { name: /Nuevo socio/ }));
        expect(await screen.findByText(/se crean sus configuraciones de salida, entrada y MDN/)).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText("Identificador AS2"), { target: { value: "SOCIO-NUEVO" } });
        fireEvent.change(screen.getByLabelText("Servidor"), { target: { value: "as2.nuevo.example" } });
        fireEvent.click(screen.getByRole("button", { name: "Registrar socio" }));

        await waitFor(() => expect(calls().some((c) => c.url === "/partners/complete")).toBe(true));
        const body = calls().find((c) => c.url === "/partners/complete")?.body;
        expect(body.partner).toMatchObject({
            as2Name: "SOCIO-NUEVO",
            serverAddress: "as2.nuevo.example",
            port: 443,
            transportType: "HTTPS",
            signingAlgorithm: "RSA_SHA256",
        });
        expect(await screen.findByText("Socio registrado con sus carpetas y configuración por defecto")).toBeInTheDocument();
    });

    it("sin servidor no se envia y se muestra la pestana de conexion", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByRole("button", { name: /Nuevo socio/ }));
        fireEvent.change(await screen.findByLabelText("Identificador AS2"), { target: { value: "SOCIO-NUEVO" } });
        fireEvent.click(screen.getByRole("button", { name: "Registrar socio" }));
        await waitFor(() => expect(screen.getByRole("tab", { name: "Conexión" })).toHaveAttribute("aria-selected", "true"));
        expect(calls().some((c) => c.method === "POST")).toBe(false);
    });

    it("los algoritmos de compatibilidad se aplican con un boton", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByRole("button", { name: /Nuevo socio/ }));
        fireEvent.change(await screen.findByLabelText("Identificador AS2"), { target: { value: "SOCIO-ANTIGUO" } });
        fireEvent.change(screen.getByLabelText("Servidor"), { target: { value: "as2.antiguo.example" } });
        await waitFor(() => expect(calls().some((c) => c.url === "/catalogs/algorithms")).toBe(true));
        fireEvent.click(screen.getByRole("tab", { name: "Seguridad AS2" }));
        fireEvent.click(await screen.findByRole("button", { name: "Compatibilidad con socios antiguos" }));
        fireEvent.click(screen.getByRole("button", { name: "Registrar socio" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/complete")).toBe(true));
        expect(calls().find((c) => c.url === "/partners/complete")?.body.partner).toMatchObject({
            signingAlgorithm: "RSA_SHA1",
            encryptionAlgorithm: "TRIPLE_DES",
            micAlgorithm: "SHA1",
        });
    });

    it("edita el socio y guarda una configuracion por separado", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByRole("button", { name: "Editar" }));
        expect(await screen.findByText("Socio SOCIO-A")).toBeInTheDocument();
        expect(screen.getByText("Hay una clave guardada; déjelo vacío para conservarla.")).toBeInTheDocument();
        await waitFor(() => expect(screen.getByLabelText("Carpeta de entrada")).toHaveValue("/data/partners/SOCIO-A/Incoming"));

        fireEvent.change(screen.getByLabelText("Nombre comercial"), { target: { value: "Socio renombrado" } });
        fireEvent.click(screen.getByRole("button", { name: "Guardar" }));
        await waitFor(() => expect(calls().some((c) => c.method === "PUT" && c.url === "/partners/p1")).toBe(true));
        const put = calls().find((c) => c.method === "PUT" && c.url === "/partners/p1")?.body;
        expect(put.partnerName).toBe("Socio renombrado");
        expect(put).not.toHaveProperty("basicAuthPassword");
    });

    it("guarda la pestana de entrada con su propio PUT", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByRole("button", { name: "Editar" }));
        const folder = await screen.findByLabelText("Carpeta de entrada");
        await waitFor(() => expect(folder).toHaveValue("/data/partners/SOCIO-A/Incoming"));
        fireEvent.change(folder, { target: { value: "/data/partners/SOCIO-A/Recibidos" } });
        fireEvent.click(screen.getByRole("tab", { name: "Entrada" }));
        const pane = screen.getByRole("tabpanel", { name: "Entrada" });
        fireEvent.click(within(pane).getByRole("button", { name: "Guardar esta pestaña" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/incoming-config")).toBe(true));
        expect(calls().find((c) => c.method === "PUT" && c.url === "/partners/p1/incoming-config")?.body).toEqual({
            incomingFolder: "/data/partners/SOCIO-A/Recibidos",
        });
    });

    it("da de baja tras confirmar", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByRole("button", { name: "Dar de baja" }));
        const dialog = await screen.findByRole("dialog");
        fireEvent.click(within(dialog).getByRole("button", { name: "Dar de baja" }));
        await waitFor(() => expect(calls().some((c) => c.method === "DELETE" && c.url === "/partners/p1")).toBe(true));
    });

    it("solo ADMIN borra definitivamente, escribiendo el identificador", async () => {
        renderAs(ADMIN);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByText("Dados de baja"));
        expect(await screen.findByText("SOCIO-VIEJO")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Borrar definitivamente" }));
        const dialog = await screen.findByRole("dialog");
        const ok = within(dialog).getByRole("button", { name: "Borrar" });
        expect(ok).toBeDisabled();
        fireEvent.change(within(dialog).getByPlaceholderText("SOCIO-VIEJO"), { target: { value: "SOCIO-VIEJO" } });
        fireEvent.click(ok);
        await waitFor(() =>
            expect(calls().some((c) => c.method === "DELETE" && c.url === "/partners/p2/purge?confirm=SOCIO-VIEJO")).toBe(true),
        );
    });

    it("un operador reactiva pero no ve el borrado definitivo", async () => {
        renderAs(OPERATOR);
        await screen.findByText("SOCIO-A");
        fireEvent.click(screen.getByText("Dados de baja"));
        await screen.findByText("SOCIO-VIEJO");
        expect(screen.queryByRole("button", { name: "Borrar definitivamente" })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Reactivar" }));
        await waitFor(() => expect(calls().some((c) => c.method === "PATCH" && c.url === "/partners/p2/status")).toBe(true));
    });
});
