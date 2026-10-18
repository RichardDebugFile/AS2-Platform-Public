import { Navigate, Outlet } from "react-router-dom";
import { useAuthStore } from "../store/authStore";

/** El login no se muestra a quien ya tiene sesion. */
export default function PublicRoutes() {
    const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
    if (isAuthenticated) return <Navigate to="/dashboard" replace />;
    return <Outlet />;
}
