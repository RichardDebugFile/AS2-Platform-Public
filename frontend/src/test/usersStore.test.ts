import { beforeEach, describe, expect, it, vi } from "vitest";
import { dataService } from "../service/dataService";
import { useUsersStore, type User } from "../store/usersStore";

vi.mock("../service/dataService", () => ({
    dataService: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));

const get = vi.mocked(dataService.get);
const post = vi.mocked(dataService.post);
const patch = vi.mocked(dataService.patch);

const admin: User = {
    id: "u1",
    username: "admin",
    email: "admin@example.com",
    roles: ["ADMIN"],
    active: true,
    mfaEnabled: false,
};

function page(content: User[], number = 0, size = 20) {
    return {
        ok: true as const,
        data: { content, number, size, totalElements: content.length, totalPages: content.length ? 1 : 0 },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    useUsersStore.setState({
        users: [],
        loading: false,
        error: null,
        query: "",
        pagination: { current: 1, pageSize: 20, total: 0, totalPages: 0 },
    });
});

describe("usersStore", () => {
    it("fetchUsers carga la pagina y pasa de base 0 (Spring) a base 1 (tabla)", async () => {
        get.mockResolvedValue(page([admin]));

        await useUsersStore.getState().fetchUsers(0, 20);

        const s = useUsersStore.getState();
        expect(get).toHaveBeenCalledWith("/users?page=0&size=20");
        expect(s.users).toEqual([admin]);
        expect(s.pagination).toEqual({ current: 1, pageSize: 20, total: 1, totalPages: 1 });
        expect(s.loading).toBe(false);
    });

    it("fetchUsers envia el filtro de busqueda", async () => {
        get.mockResolvedValue(page([]));

        await useUsersStore.getState().fetchUsers(0, 10, "ops");

        expect(get).toHaveBeenCalledWith("/users?page=0&size=10&q=ops");
        expect(useUsersStore.getState().query).toBe("ops");
    });

    it("fetchUsers guarda el mensaje de error", async () => {
        get.mockResolvedValue({ ok: false, error: { code: "HTTP_403", message: "Prohibido", status: 403 } });

        await useUsersStore.getState().fetchUsers();

        expect(useUsersStore.getState().error).toBe("Prohibido");
        expect(useUsersStore.getState().loading).toBe(false);
    });

    it("loading es true mientras carga", async () => {
        let resolve!: (v: ReturnType<typeof page>) => void;
        get.mockReturnValue(new Promise((r) => (resolve = r)));

        const pending = useUsersStore.getState().fetchUsers();
        expect(useUsersStore.getState().loading).toBe(true);
        resolve(page([]));
        await pending;

        expect(useUsersStore.getState().loading).toBe(false);
    });

    it("createUser crea y recarga la pagina visible", async () => {
        post.mockResolvedValue({ ok: true, data: admin });
        get.mockResolvedValue(page([admin]));
        const nuevo = { username: "ana", email: "ana@example.com", password: "password1", roles: ["AUDITOR" as const] };

        const r = await useUsersStore.getState().createUser(nuevo);

        expect(r).toEqual({ ok: true });
        expect(post).toHaveBeenCalledWith("/users", nuevo);
        expect(get).toHaveBeenCalledWith("/users?page=0&size=20");
    });

    it("createUser devuelve el mensaje del servidor (p. ej. usuario duplicado)", async () => {
        post.mockResolvedValue({ ok: false, error: { code: "USERNAME_TAKEN", message: "Ya existe", status: 409 } });

        const r = await useUsersStore
            .getState()
            .createUser({ username: "admin", email: "a@example.com", password: "password1", roles: ["ADMIN"] });

        expect(r).toEqual({ ok: false, message: "Ya existe" });
        expect(get).not.toHaveBeenCalled();
    });

    it("updateUserRoles recarga la misma pagina y filtro", async () => {
        useUsersStore.setState({ pagination: { current: 2, pageSize: 10, total: 15, totalPages: 2 }, query: "op" });
        patch.mockResolvedValue({ ok: true, data: admin });
        get.mockResolvedValue(page([], 1, 10));

        const r = await useUsersStore.getState().updateUserRoles("u1", ["OPERATOR", "AUDITOR"]);

        expect(r.ok).toBe(true);
        expect(patch).toHaveBeenCalledWith("/users/u1/roles", { roles: ["OPERATOR", "AUDITOR"] });
        expect(get).toHaveBeenCalledWith("/users?page=1&size=10&q=op");
    });

    it("updateUserStatus desactiva y propaga errores", async () => {
        patch.mockResolvedValueOnce({ ok: true, data: { ...admin, active: false } });
        get.mockResolvedValue(page([]));

        expect((await useUsersStore.getState().updateUserStatus("u1", false)).ok).toBe(true);
        expect(patch).toHaveBeenCalledWith("/users/u1/active", { active: false });

        patch.mockResolvedValueOnce({ ok: false, error: { code: "USER_NOT_FOUND", message: "No existe", status: 404 } });
        expect(await useUsersStore.getState().updateUserStatus("x", true)).toEqual({ ok: false, message: "No existe" });
    });
});
