import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import AppProviders from "../AppProviders";
import As2TestSend from "../pages/documents/As2TestSend";
import { json, resetAuth, stubFetch } from "./helpers";

const ok = {
    delivered: true,
    messageId: "<as2-1@example.com>",
    httpStatus: 200,
    elapsedMs: 412,
    disposition: "automatic-action/MDN-sent-automatically; processed",
    mdnSigned: true,
    mdnSignatureValid: true,
    micSent: "abc123=",
    micReceived: "abc123=",
    micAlgorithm: "sha-256",
    micMatches: true,
    originalMessageId: "<as2-1@example.com>",
    text: null,
};

let fetchMock: Mock;

beforeEach(() => {
    resetAuth();
    fetchMock = stubFetch();
});

afterEach(() => vi.unstubAllGlobals());

function renderSend(content: string | null = "ISA*00~") {
    render(
        <AppProviders>
            <As2TestSend content={content} fileName="x12-850.edi" />
        </AppProviders>,
    );
}

describe("envio AS2 de prueba", () => {
    it("envia el documento y muestra el MDN", async () => {
        fetchMock.mockResolvedValue(json(200, ok));
        renderSend();

        fireEvent.click(screen.getByRole("button", { name: /Enviar por AS2/ }));

        const dialog = await screen.findByRole("dialog");
        expect(within(dialog).getByText(/Entregado/)).toBeInTheDocument();
        expect(within(dialog).getByText("Firma del MDN válida")).toBeInTheDocument();
        expect(within(dialog).getByText("El MIC del MDN coincide con el enviado")).toBeInTheDocument();
        expect(within(dialog).getByText("<as2-1@example.com>")).toBeInTheDocument();

        const [url, init] = fetchMock.mock.calls[0];
        expect(String(url)).toBe("https://as2.example.com/api/as2/test-send?fileName=x12-850.edi");
        expect(init.method).toBe("POST");
        expect(init.body).toBe("ISA*00~");
        expect(init.headers["X-XSRF-TOKEN"]).toBe("prueba");
    });

    it("avisa si el MIC no coincide", async () => {
        fetchMock.mockResolvedValue(json(200, { ...ok, delivered: false, micMatches: false }));
        renderSend();

        fireEvent.click(screen.getByRole("button", { name: /Enviar por AS2/ }));

        const dialog = await screen.findByRole("dialog");
        expect(within(dialog).getByText(/No entregado/)).toBeInTheDocument();
        expect(within(dialog).getByText("El MIC del MDN no coincide")).toBeInTheDocument();
    });

    it("con el motor AS2 caido lo dice y muestra el motivo", async () => {
        fetchMock.mockResolvedValue(json(502, { error: "Connection refused" }));
        renderSend();

        fireEvent.click(screen.getByRole("button", { name: /Enviar por AS2/ }));

        const dialog = await screen.findByRole("dialog");
        expect(within(dialog).getByText("El motor AS2 o el socio de prueba no respondió, o el envío falló.")).toBeInTheDocument();
        expect(within(dialog).getByText("Connection refused")).toBeInTheDocument();
    });

    it("con el motor AS2 detenido (503 del gateway) da el mismo aviso", async () => {
        fetchMock.mockResolvedValue(json(503, { code: "SERVICE_UNAVAILABLE", message: "El servicio as2-adapter no responde", error: "El servicio as2-adapter no responde" }));
        renderSend();

        fireEvent.click(screen.getByRole("button", { name: /Enviar por AS2/ }));

        const dialog = await screen.findByRole("dialog");
        expect(within(dialog).getByText("El motor AS2 o el socio de prueba no respondió, o el envío falló.")).toBeInTheDocument();
        expect(within(dialog).getByText("El servicio as2-adapter no responde")).toBeInTheDocument();
    });

    it("sin documento el boton esta deshabilitado", async () => {
        renderSend(null);
        await waitFor(() => expect(screen.getByRole("button", { name: /Enviar por AS2/ })).toBeDisabled());
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
