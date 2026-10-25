import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { endpointPreview } from "../pages/partners/partnerForm";
import { clean, usePartnersStore } from "../store/partnersStore";
import { json, resetAuth, stubFetch } from "./helpers";

let fetchMock: Mock;

const partner = { id: "p1", as2Name: "SOCIO-A", serverAddress: "as2.socio.example", port: 4443, active: true };

const calls = () => fetchMock.mock.calls.map((c) => ({ url: String(c[0]), method: c[1]?.method ?? "GET", body: c[1]?.body }));

beforeEach(() => {
    resetAuth();
    usePartnersStore.setState({ partners: [], showInactive: false, loading: false, error: null, catalog: null });
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async () => json(200, [partner]));
});

afterEach(() => vi.unstubAllGlobals());

describe("store de socios", () => {
    it("carga los activos o los dados de baja", async () => {
        await usePartnersStore.getState().fetchPartners();
        await usePartnersStore.getState().fetchPartners(true);
        expect(calls().map((c) => c.url)).toEqual([
            "https://as2.example.com/api/partners",
            "https://as2.example.com/api/partners/inactive",
        ]);
        expect(usePartnersStore.getState().showInactive).toBe(true);
        expect(usePartnersStore.getState().partners).toHaveLength(1);
    });

    it("un error del API queda visible y la lista se vacia", async () => {
        fetchMock.mockImplementation(async () => json(503, { code: "UNAVAILABLE", message: "Servicio no disponible" }));
        await usePartnersStore.getState().fetchPartners();
        expect(usePartnersStore.getState().error).toBe("Servicio no disponible");
        expect(usePartnersStore.getState().partners).toEqual([]);
    });

    it("el alta usa el endpoint completo, sin textos vacios, y recarga la lista", async () => {
        fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
            init?.method === "POST" ? json(201, { partner }) : json(200, [partner]),
        );
        const res = await usePartnersStore
            .getState()
            .createPartner({ as2Name: "SOCIO-A", serverAddress: "as2.socio.example", port: 4443, description: "  " });
        expect(res).toEqual({ ok: true, data: partner });
        const post = calls().find((c) => c.method === "POST");
        expect(post?.url).toBe("https://as2.example.com/api/partners/complete");
        expect(JSON.parse(String(post?.body))).toEqual({
            partner: { as2Name: "SOCIO-A", serverAddress: "as2.socio.example", port: 4443 },
        });
        expect(calls().at(-1)?.url).toBe("https://as2.example.com/api/partners");
    });

    it("un rechazo devuelve el mensaje del servicio y no recarga", async () => {
        fetchMock.mockImplementation(async () =>
            json(409, { code: "AS2_NAME_TAKEN", message: "El identificador AS2 'SOCIO-A' ya esta en uso" }),
        );
        const res = await usePartnersStore.getState().updatePartner("p1", { as2Name: "SOCIO-A", serverAddress: "h", port: 1 });
        expect(res).toEqual({ ok: false, message: "El identificador AS2 'SOCIO-A' ya esta en uso" });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it("lee las tres configuraciones juntas", async () => {
        fetchMock.mockImplementation(async (url: string) => json(200, { url }));
        const res = await usePartnersStore.getState().getConfigs("p1");
        expect(res.ok && Object.keys(res.data)).toEqual(["outgoing", "incoming", "mdn"]);
        expect(calls().map((c) => c.url.split("/p1/")[1])).toEqual(["outgoing-config", "incoming-config", "mdn-config"]);
    });

    it("si falla una configuracion, devuelve su error", async () => {
        fetchMock.mockImplementation(async (url: string) =>
            String(url).endsWith("mdn-config") ? json(404, { code: "CONFIG_NOT_FOUND", message: "Sin MDN" }) : json(200, {}),
        );
        expect(await usePartnersStore.getState().getConfigs("p1")).toEqual({ ok: false, message: "Sin MDN" });
    });

    it("guarda una configuracion con PUT en su recurso", async () => {
        fetchMock.mockImplementation(async () => json(200, {}));
        const res = await usePartnersStore.getState().saveConfig("p1", "incoming", { incomingFolder: "/data/in", parserProfile: "" });
        expect(res.ok).toBe(true);
        expect(calls()[0]).toMatchObject({ url: "https://as2.example.com/api/partners/p1/incoming-config", method: "PUT" });
        expect(JSON.parse(String(calls()[0].body))).toEqual({ incomingFolder: "/data/in" });
    });

    it("baja logica, reactivacion y borrado definitivo con confirmacion", async () => {
        fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
            init?.method && init.method !== "GET" ? json(204) : json(200, []),
        );
        const store = usePartnersStore.getState();
        await store.remove("p1");
        await store.setActive("p1", true);
        await store.purge("p1", "SOCIO A&B");
        const changes = calls().filter((c) => c.method !== "GET");
        expect(changes.map((c) => `${c.method} ${c.url.replace("https://as2.example.com/api", "")}`)).toEqual([
            "DELETE /partners/p1",
            "PATCH /partners/p1/status",
            "DELETE /partners/p1/purge?confirm=SOCIO%20A%26B",
        ]);
        expect(JSON.parse(String(changes[1].body))).toEqual({ active: true });
    });

    it("el catalogo de algoritmos se pide una sola vez", async () => {
        fetchMock.mockImplementation(async () => json(200, { signing: [], encryption: [], mic: [] }));
        await usePartnersStore.getState().fetchCatalog();
        await usePartnersStore.getState().fetchCatalog();
        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(calls()[0].url).toBe("https://as2.example.com/api/catalogs/algorithms");
    });
});

describe("utilidades del formulario", () => {
    it("clean quita nulos, indefinidos y textos vacios pero conserva false y 0", () => {
        expect(clean({ a: "", b: null, c: undefined, d: false, e: 0, f: "x" })).toEqual({ d: false, e: 0, f: "x" });
    });

    it("la direccion resultante se arma como en el servicio", () => {
        expect(endpointPreview({ serverAddress: "as2.socio.example", port: 4443, path: "as2" })).toBe(
            "https://as2.socio.example:4443/as2",
        );
        expect(endpointPreview({ serverAddress: "192.0.2.10", port: 8080, transportType: "HTTP" })).toBe("http://192.0.2.10:8080");
        expect(endpointPreview({ port: 443 })).toBe("");
    });
});
