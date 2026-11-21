import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import AppProviders from "../AppProviders";
import As2PartnerSend from "../pages/documents/As2PartnerSend";
import { usePartnersStore } from "../store/partnersStore";
import { json, resetAuth, stubFetch } from "./helpers";

beforeEach(() => {
    resetAuth();
    usePartnersStore.setState({ partners: [], showInactive: false, error: null, loading: false });
});

afterEach(() => vi.unstubAllGlobals());

it("envía al socio seleccionado y muestra el motivo devuelto en el acuse", async () => {
    const fetch = stubFetch();
    fetch.mockResolvedValueOnce(json(200, [{ as2Name: "SOCIO-A", partnerName: "Socio de prueba", active: true }]));
    fetch.mockResolvedValueOnce(json(200, {
        documentId: "doc-1",
        messageId: "<orden@example.com>",
        status: "SEND_FAILED",
        mic: "QUJD",
        mdnReceived: true,
        mdnDisposition: "processed/Error: decryption-failed",
        reason: "El socio rechazó el documento: processed/Error: decryption-failed",
    }));
    render(<AppProviders><As2PartnerSend content="ISA*00~" fileName="850.edi" /></AppProviders>);

    fireEvent.click(screen.getByRole("button", { name: "Enviar a un socio" }));
    const dialog = await screen.findByRole("dialog");
    const send = within(dialog).getByRole("button", { name: "Enviar documento" });
    expect(send).toBeDisabled();
    await waitFor(() => expect(usePartnersStore.getState().partners).toHaveLength(1));
    fireEvent.mouseDown(within(dialog).getByRole("combobox"));
    fireEvent.click(await screen.findByText("SOCIO-A · Socio de prueba", { selector: ".ant-select-item-option-content" }));
    fireEvent.click(send);

    expect(await within(dialog).findByText("processed/Error: decryption-failed")).toBeInTheDocument();
    expect(within(dialog).getByText("<orden@example.com>")).toBeInTheDocument();
    expect(fetch.mock.calls[1][0]).toBe("https://as2.example.com/api/as2/send?partner=SOCIO-A&fileName=850.edi");
});
