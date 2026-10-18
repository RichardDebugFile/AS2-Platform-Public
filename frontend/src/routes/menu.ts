import type { Role } from "../store/authStore";

export interface MenuEntry {
    /** Ruta de la pantalla. */
    path: string;
    /** Clave de traduccion (espacio common). */
    label: string;
    /** Roles que la ven; sin roles = todos los autenticados. */
    roles?: readonly Role[];
}

export interface MenuSection {
    key: string;
    label: string;
    entries: MenuEntry[];
}

export const ADMIN_ONLY: readonly Role[] = ["ADMIN"];

/**
 * Menu de la consola por categorias. Es la unica fuente para el menu, las migas de pan y las
 * restricciones por rol de las rutas (RequireRole usa los mismos roles).
 *
 * No hay ruta "/users": ese prefijo lo atiende el API de auth-service en el proxy, y recargar la
 * pagina (F5) mostraria la respuesta del API en lugar de la consola.
 */
export const MENU: MenuSection[] = [
    { key: "operation", label: "nav.operation", entries: [{ path: "/dashboard", label: "nav.home" }] },
    { key: "partners", label: "nav.partnersGroup", entries: [{ path: "/partners", label: "nav.partners" }] },
    { key: "documents", label: "nav.documentsGroup", entries: [{ path: "/documents", label: "nav.documents" }] },
    { key: "alerts", label: "nav.alertsGroup", entries: [{ path: "/alerts", label: "nav.alerts" }] },
    {
        key: "administration",
        label: "nav.administration",
        entries: [{ path: "/admin/users", label: "nav.users", roles: ADMIN_ONLY }],
    },
];

function visible(entry: MenuEntry, roles: readonly Role[]): boolean {
    return !entry.roles || entry.roles.some((r) => roles.includes(r));
}

/** Menu que ve un usuario: sin las entradas de otros roles ni las categorias que quedan vacias. */
export function menuFor(roles: readonly Role[] | undefined): MenuSection[] {
    const own = roles ?? [];
    return MENU.map((s) => ({ ...s, entries: s.entries.filter((e) => visible(e, own)) })).filter(
        (s) => s.entries.length > 0,
    );
}

/** Categoria y pantalla de una ruta, para las migas de pan. */
export function locate(path: string): { section: MenuSection; entry: MenuEntry } | null {
    for (const section of MENU) {
        const entry = section.entries.find((e) => e.path === path);
        if (entry) return { section, entry };
    }
    return null;
}
