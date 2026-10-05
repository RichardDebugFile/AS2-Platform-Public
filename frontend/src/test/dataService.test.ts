import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { dataService, resolveHost } from "../service/dataService";

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
});
