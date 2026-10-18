import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { dataService, resolveHost, setSessionHandler, xsrfToken } from "../service/dataService";

const fetchMock = vi.fn();

function respond(status: number, body: string) {
    fetchMock.mockResolvedValue(new Response(body, { status }));
}

beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    window.APP_CONFIG = { VITE_API_HOST: "https://api.example.com/api", VITE_API_AUTH: "https://api.example.com" };
});

afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    delete window.APP_CONFIG;
});

describe("dataService", () => {
    it("envia /users y /auth a auth-service y el resto a la API", () => {
        expect(resolveHost("/users?page=0")).toBe("https://api.example.com");
        expect(resolveHost("/auth/login")).toBe("https://api.example.com");
        expect(resolveHost("/partners")).toBe("https://api.example.com/api");
    });

    it("manda las cookies y devuelve el JSON", async () => {
        respond(200, '{"id":"u1"}');

        const r = await dataService.get<{ id: string }>("/users/me");

        expect(r).toEqual({ ok: true, data: { id: "u1" } });
        expect(fetchMock).toHaveBeenCalledWith(
            "https://api.example.com/users/me",
            expect.objectContaining({ credentials: "include" }),
        );
    });

    it("serializa el cuerpo en POST y PATCH", async () => {
        respond(201, "{}");

        await dataService.post("/users", { username: "ana" });
        await dataService.patch("/users/u1/active", { active: false });

        expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST", body: '{"username":"ana"}' });
        expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: "PATCH", body: '{"active":false}' });
    });

    it("manda el token CSRF solo en las peticiones que modifican", async () => {
        document.cookie = "XSRF-TOKEN=abc%3D123; path=/";
        respond(200, "{}");

        await dataService.get("/users/me");
        await dataService.post("/auth/login", {});

        const getHeaders = fetchMock.mock.calls[0][1].headers as Record<string, string>;
        const postHeaders = fetchMock.mock.calls[1][1].headers as Record<string, string>;
        expect(getHeaders["X-XSRF-TOKEN"]).toBeUndefined();
        expect(postHeaders["X-XSRF-TOKEN"]).toBe("abc=123");
        expect(xsrfToken()).toBe("abc=123");
        document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    });

    it("sin cookie XSRF-TOKEN no inventa la cabecera", async () => {
        respond(200, "{}");

        await dataService.patch("/users/u1/active", { active: true });

        const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>;
        expect(headers["X-XSRF-TOKEN"]).toBeUndefined();
        expect(xsrfToken()).toBeUndefined();
    });

    it("traduce el error del servidor a code/message", async () => {
        respond(409, '{"status":409,"code":"USERNAME_TAKEN","message":"Ya existe"}');

        const r = await dataService.post("/users", {});

        expect(r).toEqual({ ok: false, error: { code: "USERNAME_TAKEN", message: "Ya existe", status: 409 } });
    });

    it("error sin JSON: codigo HTTP generico", async () => {
        respond(502, "Bad Gateway");

        const r = await dataService.get("/partners");

        expect(r).toEqual({ ok: false, error: { code: "HTTP_502", message: "Error HTTP 502", status: 502 } });
    });

    it("respuesta vacia o de texto se devuelve tal cual", async () => {
        respond(200, "pong");

        expect(await dataService.get("/ping")).toEqual({ ok: true, data: "pong" });
    });

    it("fallo de red", async () => {
        fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

        const r = await dataService.get("/users/me");

        expect(r.ok).toBe(false);
        expect(!r.ok && r.error.code).toBe("NETWORK");
    });

    it("sin configuracion no llama al servidor", async () => {
        window.APP_CONFIG = {};
        vi.stubEnv("VITE_API_AUTH", undefined as unknown as string);

        const r = await dataService.get("/users/me");

        expect(!r.ok && r.error.code).toBe("CONFIG_MISSING");
        expect(fetchMock).not.toHaveBeenCalled();
        vi.unstubAllEnvs();
    });

    describe("renovacion transparente de la sesion", () => {
        const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status });

        afterEach(() => setSessionHandler(null));

        it("ante un 401 renueva una vez y reintenta sin que la pantalla lo note", async () => {
            const refresh = vi.fn().mockResolvedValue(undefined);
            setSessionHandler({ refresh, isAuthenticated: () => true });
            fetchMock.mockResolvedValueOnce(json(401, { code: "UNAUTHORIZED" })).mockResolvedValueOnce(json(200, [{ id: 1 }]));

            const r = await dataService.get("/partners");

            expect(r).toEqual({ ok: true, data: [{ id: 1 }] });
            expect(refresh).toHaveBeenCalledTimes(1);
            expect(fetchMock).toHaveBeenCalledTimes(2);
        });

        it("varias peticiones con 401 a la vez comparten un solo refresh", async () => {
            let release: () => void = () => {};
            const refresh = vi.fn(() => new Promise<void>((r) => (release = r)));
            setSessionHandler({ refresh, isAuthenticated: () => true });
            fetchMock.mockImplementation(async () => json(401, {}));
            fetchMock.mockResolvedValueOnce(json(401, {})).mockResolvedValueOnce(json(401, {}));

            const both = Promise.all([dataService.get("/a"), dataService.get("/b")]);
            await vi.waitFor(() => expect(refresh).toHaveBeenCalled());
            fetchMock.mockImplementation(async () => json(200, {}));
            release();
            await both;

            expect(refresh).toHaveBeenCalledTimes(1);
        });

        it("si no se pudo renovar, responde sesion expirada sin reintentar", async () => {
            setSessionHandler({ refresh: vi.fn().mockResolvedValue(undefined), isAuthenticated: () => false });
            fetchMock.mockResolvedValueOnce(json(401, {}));

            const r = await dataService.get("/partners");

            expect(!r.ok && r.error).toMatchObject({ code: "UNAUTHORIZED", status: 401 });
            expect(fetchMock).toHaveBeenCalledTimes(1);
        });

        it("no intenta renovar en /auth/* (login o el propio refresh)", async () => {
            const refresh = vi.fn().mockResolvedValue(undefined);
            setSessionHandler({ refresh, isAuthenticated: () => true });
            fetchMock.mockResolvedValueOnce(json(401, { code: "UNAUTHORIZED" }));

            await dataService.post("/auth/login", {});

            expect(refresh).not.toHaveBeenCalled();
        });

        it("PUT y DELETE", async () => {
            fetchMock.mockResolvedValueOnce(json(200, {})).mockResolvedValueOnce(new Response(null, { status: 204 }));
            await dataService.put("/x", { a: 1 });
            await dataService.delete("/x");
            expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "PUT", body: '{"a":1}' });
            expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: "DELETE" });
        });
    });
});
