import { create } from "zustand";
import { dataService } from "../service/dataService";

export type Role = "ADMIN" | "OPERATOR" | "AUDITOR";

export interface User {
    id: string;
    username: string;
    email: string;
    roles: Role[];
    active: boolean;
    mfaEnabled: boolean;
}

export interface NewUser {
    username: string;
    email: string;
    password: string;
    roles: Role[];
}

/** Pagina de Spring Data (solo los campos que usa la consola). */
interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

export type ActionResult = { ok: true } | { ok: false; message: string };

interface UsersState {
    users: User[];
    loading: boolean;
    error: string | null;
    /** current empieza en 1 (tabla de la consola); Spring pagina desde 0. */
    pagination: { current: number; pageSize: number; total: number; totalPages: number };
    query: string;
    fetchUsers: (page?: number, size?: number, query?: string) => Promise<void>;
    createUser: (u: NewUser) => Promise<ActionResult>;
    updateUserRoles: (userId: string, roles: Role[]) => Promise<ActionResult>;
    updateUserStatus: (userId: string, active: boolean) => Promise<ActionResult>;
}

const initialPagination = { current: 1, pageSize: 20, total: 0, totalPages: 0 };

export const useUsersStore = create<UsersState>((set, get) => {
    /** Tras un cambio se recarga la pagina visible con el mismo filtro. */
    const reload = () => {
        const { pagination, query } = get();
        return get().fetchUsers(pagination.current - 1, pagination.pageSize, query);
    };

    const afterChange = async (res: { ok: boolean; error?: { message: string } }): Promise<ActionResult> => {
        if (!res.ok) return { ok: false, message: res.error?.message ?? "Error desconocido" };
        await reload();
        return { ok: true };
    };

    return {
        users: [],
        loading: false,
        error: null,
        pagination: initialPagination,
        query: "",

        fetchUsers: async (page = 0, size = 20, query = "") => {
            set({ loading: true, error: null, query });
            const params = new URLSearchParams({ page: String(page), size: String(size) });
            if (query) params.set("q", query);
            const res = await dataService.get<Page<User>>(`/users?${params.toString()}`);
            if (!res.ok) {
                set({ loading: false, error: res.error.message });
                return;
            }
            set({
                loading: false,
                users: res.data.content,
                pagination: {
                    current: res.data.number + 1,
                    pageSize: res.data.size,
                    total: res.data.totalElements,
                    totalPages: res.data.totalPages,
                },
            });
        },

        createUser: async (u) => afterChange(await dataService.post<User>("/users", u)),

        updateUserRoles: async (userId, roles) =>
            afterChange(await dataService.patch<User>(`/users/${encodeURIComponent(userId)}/roles`, { roles })),

        updateUserStatus: async (userId, active) =>
            afterChange(await dataService.patch<User>(`/users/${encodeURIComponent(userId)}/active`, { active })),
    };
});
