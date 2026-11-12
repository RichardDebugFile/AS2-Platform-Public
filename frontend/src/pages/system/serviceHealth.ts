/** Estados de salud que la consola sabe nombrar; cualquier otro se muestra tal cual. */
export const KNOWN_HEALTH = new Set(["UP", "DOWN", "OUT_OF_SERVICE", "UNREACHABLE", "UNKNOWN"]);

/** Verificaciones internas de Spring Boot con nombre traducido. */
export const KNOWN_COMPONENTS = new Set(["db", "diskSpace", "ping", "livenessState", "readinessState", "ssl", "mail"]);

export type HealthLevel = "success" | "warning" | "error" | "default";

/** Verde en marcha, ambar fuera de servicio o desconocido, rojo caido o sin respuesta. */
export function healthLevel(status: string): HealthLevel {
    if (status === "UP") return "success";
    if (status === "OUT_OF_SERVICE" || status === "UNKNOWN") return "warning";
    return "error";
}
