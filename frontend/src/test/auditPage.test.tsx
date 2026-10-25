import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import AuditPage from "../pages/audit/AuditPage";
import { useAuditStore } from "../store/auditStore";
import { useAuthStore } from "../store/authStore";
import { AUDITOR, json, resetAuth, stubFetch } from "./helpers";

const events = [
    {
        id: "e1",
        occurredAt: "2026-10-20T15:30:00Z",
        eventType: "PARTNER_UPDATED",
        status: "SUCCESS",
        actor: "operador1",
        source: "partners-service",
        partnerAs2Name: "SOCIO-A",
        messageId: null,
        detail: "Socio actualizado: SOCIO-A",
        changes: { serverAddress: { from: "as2.socio.example", to: "as2-nuevo.socio.example" } },
    },
    {
        id: "e2",
        occurredAt: "2026-10-20T15:31:00Z",
        eventType: "DOCUMENT_SENT",
        status: "FAILURE",
        actor: "system",
        source: "as2-adapter",
        partnerAs2Name: "SOCIO-B",
        messageId: "<m1@as2.example.com>",
        detail: "Sin respuesta del socio",
        changes: {},
    },
];

let fetchMock: Mock;
const urls = () => fetchMock.mock.calls.map((c) => String(c[0]).replace("https://as2.example.com/api", ""));

beforeEach(() => {
    resetAuth();
    useAuditStore.setState({ events: [], filters: {}, loading: false, error: null, eventTypes: [], pagination: { current: 1, pageSize: 20, total: 0 } });
    fetchMock = stubFetch();
    fetchMock.mockImplementation(async (url: string) => {
        const path = String(url);
        if (path.endsWith("/event-types")) return json(200, ["DOCUMENT_SENT", "PARTNER_UPDATED"]);
        if (path.includes("/export")) return new Response("﻿fecha_utc\r\n", { status: 200 });
        return json(200, { content: events, number: 0, size: 20, totalElements: 2, totalPages: 1 });
    });
    useAuthStore.setState({ isAuthenticated: true, user: AUDITOR });
});

afterEach(() => vi.unstubAllGlobals());

function renderPage() {
    render(
        <MemoryRouter>
            <AppProviders>
                <AuditPage />
            </AppProviders>
        </MemoryRouter>,
    );
}

describe("pantalla de la bitacora", () => {
    it("lista los eventos con su tipo traducido, socio, usuario y estado", async () => {
        renderPage();
        expect(await screen.findByText("Socio modificado")).toBeInTheDocument();
        expect(screen.getByText("SOCIO-B")).toBeInTheDocument();
        expect(screen.getByText("operador1")).toBeInTheDocument();
        expect(screen.getByText("Fallo")).toBeInTheDocument();
        expect(urls()[0]).toBe("/audit-logs?page=0&size=20");
    });

    it("un auditor solo consulta y exporta: no hay acciones de modificacion", async () => {
        renderPage();
        await screen.findByText("Socio modificado");
        const buttons = screen.getAllByRole("button").map((b) => b.textContent ?? "");
        expect(buttons.some((b) => /Editar|Eliminar|Borrar|Guardar/.test(b))).toBe(false);
        expect(screen.getByRole("button", { name: /Exportar CSV/ })).toBeInTheDocument();
    });

    it("al expandir un evento muestra los datos cambiados", async () => {
        renderPage();
        await screen.findByText("Socio modificado");
        fireEvent.click(screen.getAllByRole("button", { name: /expandir fila|expand row/i })[0]);
        expect(await screen.findByText("as2-nuevo.socio.example")).toBeInTheDocument();
        expect(screen.getByText("serverAddress")).toBeInTheDocument();
    });

    it("filtra por Message-ID", async () => {
        renderPage();
        await screen.findByText("Socio modificado");
        fireEvent.change(screen.getByPlaceholderText("Message-ID"), { target: { value: "<m1@as2.example.com>" } });
        fireEvent.click(screen.getByRole("button", { name: /Buscar/ }));
        await waitFor(() => expect(urls().some((u) => u.includes("messageId=%3Cm1%40as2.example.com%3E"))).toBe(true));
    });

    it("exporta un CSV con los filtros vigentes", async () => {
        const createUrl = vi.fn(() => "blob:bitacora");
        const revoke = vi.fn();
        vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revoke }));
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
        renderPage();
        await screen.findByText("Socio modificado");
        fireEvent.click(screen.getByRole("button", { name: /Exportar CSV/ }));
        await waitFor(() => expect(click).toHaveBeenCalled());
        expect(urls().some((u) => u.startsWith("/audit-logs/export"))).toBe(true);
        expect(revoke).toHaveBeenCalledWith("blob:bitacora");
        click.mockRestore();
    });

    it("limpiar vuelve a la consulta sin filtros", async () => {
        renderPage();
        await screen.findByText("Socio modificado");
        const filtros = screen.getByPlaceholderText("Usuario");
        fireEvent.change(filtros, { target: { value: "ana" } });
        fireEvent.click(screen.getByRole("button", { name: /Buscar/ }));
        await waitFor(() => expect(urls().some((u) => u.includes("actor=ana"))).toBe(true));
        fireEvent.click(screen.getByRole("button", { name: "Limpiar" }));
        await waitFor(() => expect(urls().at(-1)).toBe("/audit-logs?page=0&size=20"));
        expect(within(screen.getByRole("table")).getAllByRole("row").length).toBeGreaterThan(1);
    });
});
