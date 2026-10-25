import { Navigate, Route, Routes } from "react-router-dom";
import HomePage from "../pages/HomePage";
import SectionPlaceholder from "../pages/SectionPlaceholder";
import UsersPage from "../pages/UsersPage";
import PartnersPage from "../pages/partners/PartnersPage";
import AuditPage from "../pages/audit/AuditPage";
import Login from "../pages/auth/Login";
import AppLayout from "./AppLayout";
import PublicRoutes from "./PublicRoutes";
import RequireAuth from "./RequireAuth";
import RequireRole from "./RequireRole";
import { ADMIN_ONLY } from "./menu";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="/" element={<PublicRoutes />}>
                <Route index element={<Login />} />
            </Route>

            <Route element={<RequireAuth />}>
                <Route element={<AppLayout />}>
                    <Route path="/dashboard" element={<HomePage />} />
                    <Route path="/partners" element={<PartnersPage />} />
                    <Route path="/documents" element={<SectionPlaceholder ns="documents" />} />
                    <Route path="/alerts" element={<SectionPlaceholder ns="alerts" />} />
                    <Route path="/audit" element={<AuditPage />} />
                    <Route element={<RequireRole allowedRoles={ADMIN_ONLY} />}>
                        <Route path="/admin/users" element={<UsersPage />} />
                    </Route>
                </Route>
            </Route>

            {/* Ruta desconocida: al login (o al inicio si ya hay sesion). */}
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}
