import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

/** Rutas privadas: sin sesion, al login recordando a donde se queria ir. */
export default function RequireAuth() {
    const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
    const location = useLocation();
    if (!isAuthenticated) {
        return <Navigate to="/" state={{ from: location }} replace />;
    }
    return <Outlet />;
}
