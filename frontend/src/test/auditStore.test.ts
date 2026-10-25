import dayjs from "dayjs";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { toFilters } from "../pages/audit/auditFilters";
import { auditParams, useAuditStore } from "../store/auditStore";
import { json, resetAuth, stubFetch } from "./helpers";

let fetchMock: Mock;
const page = { content: [{ id: "e1" }], number: 1, size: 10, totalElements: 11, totalPages: 2 };
const urls = () => fetchMock.mock.calls.map((c) => String(c[0]).replace("https://as2.example.com/api", ""));

beforeEach(() => {
    resetAuth();
    useAuditStore.setState({ events: [], filters: {}, loading: false, error: null, eventTypes: [], pagination: { current: 1, pageSize: 20, total: 0 } });
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async () => json(200, page));
});

afterEach(() => vi.unstubAllGlobals());

describe("store de la bitacora", () => {
    it("consulta con los filtros no vacios y la pagina pedida", async () => {
        await useAuditStore.getState().fetchEvents({ partnerAs2Name: " SOCIO-A ", actor: "", status: "FAILURE" }, 1, 10);
        expect(urls()[0]).toBe("/audit-logs?partnerAs2Name=SOCIO-A&status=FAILURE&page=1&size=10");
        expect(useAuditStore.getState().pagination).toEqual({ current: 2, pageSize: 10, total: 11 });
        expect(useAuditStore.getState().events).toHaveLength(1);
    });

    it("cambiar de pagina conserva los filtros vigentes", async () => {
        await useAuditStore.getState().fetchEvents({ eventType: "PARTNER_UPDATED" }, 0, 10);
        await useAuditStore.getState().fetchEvents(undefined, 1);
        expect(urls()[1]).toBe("/audit-logs?eventType=PARTNER_UPDATED&page=1&size=10");
    });

    it("un error queda visible", async () => {
        fetchMock.mockImplementation(async () => json(403, { code: "FORBIDDEN", message: "Sin permiso" }));
        await useAuditStore.getState().fetchEvents({});
        expect(useAuditStore.getState().error).toBe("Sin permiso");
    });

    it("la exportacion usa los mismos filtros y devuelve el texto", async () => {
        fetchMock.mockImplementation(async (url: string) =>
            String(url).includes("/export") ? new Response("﻿fecha_utc,evento\r\n", { status: 200 }) : json(200, page),
        );
        await useAuditStore.getState().fetchEvents({ messageId: "<m1@as2.example.com>" });
        const csv = await useAuditStore.getState().exportCsv();
        expect(csv).toContain("fecha_utc,evento");
        expect(urls()[1]).toBe("/audit-logs/export?messageId=%3Cm1%40as2.example.com%3E");
    });

    it("si la exportacion falla devuelve null", async () => {
        fetchMock.mockImplementation(async () => json(500, { code: "X", message: "Fallo" }));
        expect(await useAuditStore.getState().exportCsv()).toBeNull();
        expect(useAuditStore.getState().error).toBe("Fallo");
    });

    it("carga los tipos de evento", async () => {
        fetchMock.mockImplementation(async () => json(200, ["DOCUMENT_SENT", "PARTNER_CREATED"]));
        await useAuditStore.getState().fetchEventTypes();
        expect(useAuditStore.getState().eventTypes).toEqual(["DOCUMENT_SENT", "PARTNER_CREATED"]);
    });
});

describe("filtros del formulario", () => {
    it("el rango de fechas cubre el dia final completo", () => {
        const f = toFilters({ range: [dayjs("2026-10-20T15:00:00"), dayjs("2026-10-21T08:00:00")], actor: "ana" });
        expect(f.from).toBe(dayjs("2026-10-20T00:00:00").toISOString());
        expect(f.to).toBe(dayjs("2026-10-22T00:00:00").toISOString());
        expect(f.actor).toBe("ana");
        expect(toFilters({}).from).toBeUndefined();
    });

    it("auditParams omite vacios y espacios", () => {
        expect(auditParams({ actor: "  ", eventType: "X" }).toString()).toBe("eventType=X");
    });
});
