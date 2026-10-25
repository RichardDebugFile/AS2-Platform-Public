import type { Dayjs } from "dayjs";
import type { AuditFilters } from "../../store/auditStore";

export interface FilterForm {
    partnerAs2Name?: string;
    eventType?: string;
    status?: AuditFilters["status"];
    actor?: string;
    messageId?: string;
    range?: [Dayjs | null, Dayjs | null] | null;
}

/** Del formulario a los filtros del API: el rango de fechas incluye el dia final completo. */
export function toFilters(v: FilterForm): AuditFilters {
    const [from, to] = v.range ?? [];
    return {
        partnerAs2Name: v.partnerAs2Name,
        eventType: v.eventType,
        status: v.status,
        actor: v.actor,
        messageId: v.messageId,
        from: from ? from.startOf("day").toISOString() : undefined,
        to: to ? to.add(1, "day").startOf("day").toISOString() : undefined,
    };
}
