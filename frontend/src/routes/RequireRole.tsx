import { Navigate, Outlet } from "react-router-dom";
import { hasAnyRole, useAuthStore, type Role } from "../store/authStore";

/**
 * Rutas de un rol: el menu ya las esconde, y esto evita abrirlas escribiendo la URL. La seguridad
 * real esta en el servidor (gateway y auth-service responden 401/403).
 */
export default function RequireRole({ allowedRoles }: Readonly<{ allowedRoles: readonly Role[] }>) {
    const user = useAuthStore((s) => s.user);
    if (!hasAnyRole(user, allowedRoles)) return <Navigate to="/dashboard" replace />;
    return <Outlet />;
}
