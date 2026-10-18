import { useEffect } from "react";
import { useAuthStore } from "../store/authStore";

/**
 * Al cargar la pagina con una sesion guardada se confirma con el servidor quien es el usuario: si
 * las cookies ya no valen, el cliente intentara renovar y, si no puede, llevara al login.
 */
export function useAuthInitializer() {
    const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
    const fetchMe = useAuthStore((s) => s.fetchMe);
    useEffect(() => {
        if (isAuthenticated) void fetchMe();
        // Solo al cargar la consola.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
}
