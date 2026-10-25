import { create } from "zustand";
import { dataService } from "../service/dataService";

export type AuditStatus = "SUCCESS" | "FAILURE";

export interface AuditChange {
    from: unknown;
    to: unknown;
}

export interface AuditEvent {
    id: string;
    occurredAt: string;
    eventType: string;
    status: AuditStatus;
    actor: string;
    source: string;
    partnerId?: string | null;
    partnerAs2Name?: string | null;
    messageId?: string | null;
    detail?: string | null;
    changes: Record<string, AuditChange>;
}

/** Filtros de la bitacora; todos opcionales. Las fechas van en ISO-8601 (from inclusivo, to exclusivo). */
export interface AuditFilters {
    partnerAs2Name?: string;
    eventType?: string;
    status?: AuditStatus;
    actor?: string;
    messageId?: string;
    from?: string;
    to?: string;
}

interface Page<T> {
    content: T[];
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

/** Parametros de consulta sin los filtros vacios. */
export function auditParams(filters: AuditFilters): URLSearchParams {
    const params = new URLSearchParams();
    for (const [k, v] of Object.entries(filters)) {
        if (typeof v === "string" && v.trim() !== "") params.set(k, v.trim());
    }
    return params;
}

interface AuditState {
    events: AuditEvent[];
    filters: AuditFilters;
    loading: boolean;
    error: string | null;
    /** current empieza en 1 (tabla de la consola); el API pagina desde 0. */
    pagination: { current: number; pageSize: number; total: number };
    eventTypes: string[];
    fetchEvents: (filters?: AuditFilters, page?: number, size?: number) => Promise<void>;
    fetchEventTypes: () => Promise<void>;
    /** CSV con los filtros vigentes; null si fallo (el mensaje queda en error). */
    exportCsv: () => Promise<string | null>;
}

export const useAuditStore = create<AuditState>((set, get) => ({
    events: [],
    filters: {},
    loading: false,
    error: null,
    pagination: { current: 1, pageSize: 20, total: 0 },
    eventTypes: [],

    fetchEvents: async (filters = get().filters, page = 0, size = get().pagination.pageSize) => {
        set({ loading: true, error: null, filters });
        const params = auditParams(filters);
        params.set("page", String(page));
        params.set("size", String(size));
        const res = await dataService.get<Page<AuditEvent>>(`/audit-logs?${params.toString()}`);
        if (!res.ok) {
            set({ loading: false, error: res.error.message, events: [] });
            return;
        }
        set({
            loading: false,
            events: res.data.content,
            pagination: { current: res.data.number + 1, pageSize: res.data.size, total: res.data.totalElements },
        });
    },

    fetchEventTypes: async () => {
        const res = await dataService.get<string[]>("/audit-logs/event-types");
        if (res.ok && Array.isArray(res.data)) set({ eventTypes: res.data });
    },

    exportCsv: async () => {
        const query = auditParams(get().filters).toString();
        const url = query ? "/audit-logs/export?" + query : "/audit-logs/export";
        const res = await dataService.get<string>(url);
        if (!res.ok) {
            set({ error: res.error.message });
            return null;
        }
        return String(res.data);
    },
}));
