import { dataService } from "./dataService";
import type { Direction } from "./documentsApi";

/** Trafico de un socio en el rango del tablero. */
export interface PartnerTraffic {
    partner: string;
    inbound: number;
    outbound: number;
    ok: number;
    failed: number;
    total: number;
}

/** Documentos de un dia: terminados bien, con error y el resto (en curso o repetidos). */
export interface DayTraffic {
    day: string;
    ok: number;
    failed: number;
    other: number;
    total: number;
}

/** Tablero de Inicio (AS2-99), tal como lo devuelve documents-service. */
export interface Dashboard {
    from: string;
    to: string;
    total: number;
    ok: number;
    failed: number;
    inProgress: number;
    duplicates: number;
    /** Porcentaje con un decimal; null si en el rango ningun documento llego a un resultado final. */
    successRate: number | null;
    byStatus: Record<string, number>;
    byDirection: Record<string, number>;
    byPartner: PartnerTraffic[];
    byDay: DayTraffic[];
}

export interface DashboardFilter {
    /** AAAA-MM-DD */
    from?: string;
    to?: string;
    direction?: Direction;
    partner?: string;
}

export type ServiceHealth = "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNREACHABLE" | string;

export interface ServiceStatus {
    service: string;
    status: ServiceHealth;
    latencyMs: number | null;
}

export interface SystemStatus {
    checkedAt: string;
    services: ServiceStatus[];
}

/** Parametros del tablero sin los vacios. */
export function dashboardParams(filter: DashboardFilter): string {
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(filter)) {
        if (typeof v === "string" && v.trim() !== "") p.set(k, v.trim());
    }
    return p.toString();
}

export const dashboardApi = {
    dashboard: (filter: DashboardFilter = {}) => {
        const q = dashboardParams(filter);
        return dataService.get<Dashboard>(`/documents/dashboard${q ? `?${q}` : ""}`);
    },
    /** Salud de cada servicio, consultada por el gateway. */
    systemStatus: () => dataService.get<SystemStatus>("/system/status"),
};
