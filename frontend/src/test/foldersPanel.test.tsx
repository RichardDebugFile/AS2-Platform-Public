import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import FoldersPanel from "../pages/partners/FoldersPanel";
import PartnersPage from "../pages/partners/PartnersPage";
import { useAuthStore } from "../store/authStore";
import { usePartnersStore, type FolderStatus, type Partner } from "../store/partnersStore";
import { ADMIN, json, resetAuth, stubFetch } from "./helpers";

const partner = {
    id: "p1",
    as2Name: "SOCIO-A",
    serverAddress: "as2.socio.example",
    port: 4443,
    endpointUrl: "https://as2.socio.example:4443/as2",
    hasBasicAuthPassword: false,
    active: true,
    folderState: "LOCAL",
} as Partner;

const tree = ["Archive", "Certs", "Error", "Incoming", "MDN", "Outgoing"];
const folders = (linked: boolean) =>
    tree.map((name) => ({
        name,
        path: `/opt/as2_data/partners/SOCIO-A/${name}`,
        exists: true,
        readable: true,
        writable: true,
        linked,
        target: linked ? `/mnt/erp/SocioA/${name}` : null,
    }));
const local: FolderStatus = { partnerId: "p1", as2Name: "SOCIO-A", folderState: "LOCAL", health: "OK", folders: folders(false), problems: [] };
const linkedStatus: FolderStatus = { ...local, folderState: "SAMBA_LINKED", sharedFolderName: "SocioA", folders: folders(true) };

let fetchMock: Mock;
const calls = () =>
    fetchMock.mock.calls.map((c) => ({
        url: String(c[0]).replace("https://as2.example.com/api", ""),
        method: (c[1]?.method as string | undefined) ?? "GET",
        body: c[1]?.body ? JSON.parse(String(c[1].body)) : undefined,
    }));

let current: FolderStatus;
let precheckOk = false;

beforeEach(() => {
    resetAuth();
    usePartnersStore.setState({ partners: [], showInactive: false, loading: false, error: null, catalog: null, folderStatuses: {} });
    current = local;
    precheckOk = false;
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
        const path = String(url).replace("https://as2.example.com/api", "");
        if (path === "/partners/p1/folders") return json(200, current);
        if (path === "/partners/folder-statuses") return json(200, [current]);
        if (path === "/partners/p1/folders/precheck") {
            return json(200, precheckOk
                ? { ok: true, sharedFolderName: "SocioA", target: "/mnt/erp/SocioA", problems: [], actions: ["Incoming: se enlazara con /mnt/erp/SocioA/Incoming"] }
                : { ok: false, sharedFolderName: "SocioA", target: "/mnt/erp/SocioA", problems: ["La carpeta compartida 'SocioA' no existe en el montaje (/mnt/erp)"], actions: [] });
        }
        if (path === "/partners/p1/folders/link") {
            current = linkedStatus;
            return json(200, linkedStatus);
        }
        if (path === "/partners/p1/production") return json(200, { ...partner, folderState: "PRODUCTION", enableIncomingQueue: true });
        if (path === "/partners" && (init?.method ?? "GET") === "GET") return json(200, [partner]);
        if (path === "/catalogs/algorithms") return json(200, { signing: [], encryption: [], mic: [] });
        return json(200, {});
    });
});

afterEach(() => vi.unstubAllGlobals());

function renderPanel(p: Partner = partner, isAdmin = true, canEdit = true) {
    render(
        <MemoryRouter>
            <AppProviders>
                <FoldersPanel partner={p} canEdit={canEdit} isAdmin={isAdmin} />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("carpetas del socio", () => {
    it("muestra la etapa, el estado y las seis carpetas", async () => {
        renderPanel();
        expect(await screen.findByText("/opt/as2_data/partners/SOCIO-A/Incoming")).toBeInTheDocument();
        expect(screen.getByText("Local")).toBeInTheDocument();
        expect(screen.getByText("Correctas")).toBeInTheDocument();
        expect(screen.getAllByText(/^\/opt\/as2_data\/partners\/SOCIO-A\//)).toHaveLength(6);
    });

    it("precheck negativo: muestra el problema y Enlazar queda deshabilitado", async () => {
        renderPanel();
        await screen.findByText("Local");
        fireEvent.change(screen.getByLabelText("Carpeta compartida"), { target: { value: "SocioA" } });
        fireEvent.click(screen.getByRole("button", { name: "Verificar" }));
        expect(await screen.findByText(/no existe en el montaje/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Enlazar/ })).toBeDisabled();
    });

    it("precheck positivo habilita Enlazar; al enlazar queda enlazado con el ERP", async () => {
        precheckOk = true;
        renderPanel();
        await screen.findByText("Local");
        expect(screen.getByRole("button", { name: /Enlazar/ })).toBeDisabled();
        fireEvent.change(screen.getByLabelText("Carpeta compartida"), { target: { value: "SocioA" } });
        fireEvent.click(screen.getByRole("button", { name: "Verificar" }));
        expect(await screen.findByText(/Verificación correcta/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: /Enlazar/ }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/folders/link")).toBe(true));
        expect(calls().find((c) => c.url === "/partners/p1/folders/link")?.body).toEqual({ sharedFolderName: "SocioA" });
        expect(await screen.findByText("Enlazado con el ERP")).toBeInTheDocument();
        expect(screen.getAllByText(/\/mnt\/erp\/SocioA\//).length).toBe(6);
    });

    it("cambiar el nombre tras verificar obliga a verificar de nuevo", async () => {
        precheckOk = true;
        renderPanel();
        await screen.findByText("Local");
        const input = screen.getByLabelText("Carpeta compartida");
        fireEvent.change(input, { target: { value: "SocioA" } });
        fireEvent.click(screen.getByRole("button", { name: "Verificar" }));
        await screen.findByText(/Verificación correcta/);
        fireEvent.change(input, { target: { value: "SocioB" } });
        expect(screen.getByRole("button", { name: /Enlazar/ })).toBeDisabled();
    });

    it("un operador verifica pero no enlaza", async () => {
        precheckOk = true;
        renderPanel(partner, false);
        await screen.findByText("Local");
        fireEvent.change(screen.getByLabelText("Carpeta compartida"), { target: { value: "SocioA" } });
        fireEvent.click(screen.getByRole("button", { name: "Verificar" }));
        await screen.findByText(/Verificación correcta/);
        expect(screen.getByRole("button", { name: /Enlazar/ })).toBeDisabled();
        expect(screen.getByText("Solo un administrador puede enlazar.")).toBeInTheDocument();
    });

    it("carpetas que faltan: se pueden crear desde el panel", async () => {
        current = { ...local, health: "MISSING", problems: ["Error: no existe"] };
        renderPanel();
        expect(await screen.findByText("Error: no existe")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", { name: "Crear las carpetas que faltan" }));
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/folders/repair" && c.method === "POST")).toBe(true));
    });

    it("produccion: el administrador la activa escribiendo el identificador", async () => {
        current = linkedStatus;
        renderPanel({ ...partner, folderState: "SAMBA_LINKED", sharedFolderName: "SocioA" });
        fireEvent.click(await screen.findByRole("button", { name: "Activar producción" }));
        const dialog = await screen.findByRole("dialog");
        const ok = within(dialog).getByRole("button", { name: "Confirmar" });
        expect(ok).toBeDisabled();
        fireEvent.change(within(dialog).getByLabelText("Identificador AS2 para confirmar"), { target: { value: "SOCIO-A" } });
        fireEvent.click(ok);
        await waitFor(() => expect(calls().some((c) => c.url === "/partners/p1/production")).toBe(true));
        expect(calls().find((c) => c.url === "/partners/p1/production")?.body).toEqual({ enabled: true, confirm: "SOCIO-A" });
    });

    it("montaje caido: el estado aparece en error con su explicacion", async () => {
        current = { ...linkedStatus, health: "ERROR", problems: ["La carpeta compartida /mnt/erp/SocioA no esta disponible (¿recurso compartido desmontado?)"] };
        renderPanel({ ...partner, folderState: "SAMBA_LINKED", sharedFolderName: "SocioA" });
        expect(await screen.findByText(/recurso compartido desmontado/)).toBeInTheDocument();
        expect(screen.getByText("Error", { selector: ".ant-tag" })).toBeInTheDocument();
    });

    it("la lista de socios muestra la etapa de carpetas de cada uno", async () => {
        current = { ...linkedStatus, health: "ERROR" };
        useAuthStore.setState({ isAuthenticated: true, user: ADMIN });
        render(
            <MemoryRouter>
                <AppProviders>
                    <PartnersPage />
                </AppProviders>
            </MemoryRouter>,
        );
        expect(await screen.findByText("Enlazado con el ERP")).toBeInTheDocument();
        expect(screen.getByText("Error")).toBeInTheDocument();
    });
});
