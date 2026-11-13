import { Navigate, Outlet, useLocation } from "react-router-dom";
import { hasAnyRole, useAuthStore, type Role } from "../store/authStore";

/** Estado de navegacion con el que el inicio avisa que la pantalla pedida no esta permitida. */
export interface DeniedState {
    denied: string;
}

/**
 * Rutas de un rol: el menu ya las esconde, y esto evita abrirlas escribiendo la URL. La seguridad
 * real esta en el servidor (gateway y auth-service responden 401/403). Lleva al inicio diciendo por que.
 */
export default function RequireRole({ allowedRoles }: Readonly<{ allowedRoles: readonly Role[] }>) {
    const user = useAuthStore((s) => s.user);
    const { pathname } = useLocation();
    if (!hasAnyRole(user, allowedRoles)) {
        const state: DeniedState = { denied: pathname };
        return <Navigate to="/dashboard" replace state={state} />;
    }
    return <Outlet />;
}
