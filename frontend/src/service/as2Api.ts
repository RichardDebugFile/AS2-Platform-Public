import { dataService } from "./dataService";

/** Resultado de un envio AS2 de prueba (AS2-100), tal como lo devuelve el as2-adapter. */
export interface TestSendResult {
    delivered: boolean;
    messageId: string;
    httpStatus: number;
    elapsedMs: number;
    disposition: string | null;
    mdnSigned: boolean;
    mdnSignatureValid: boolean;
    micSent: string | null;
    micReceived: string | null;
    micAlgorithm: string | null;
    micMatches: boolean;
    originalMessageId: string | null;
    text: string | null;
}

export interface SendResult {
    documentId: string;
    messageId: string;
    status: string;
    mic: string;
    mdnReceived: boolean;
    mdnDisposition: string | null;
    reason: string | null;
}

export const as2Api = {
    send: (partner: string, content: string, fileName: string) =>
        dataService.postText<SendResult>(`/as2/send?${new URLSearchParams({ partner, fileName }).toString()}`, content),
    /**
     * Envia el documento al socio de prueba: firmado, cifrado y con MDN firmado sincrono. Un 502
     * significa que el motor AS2 o el socio no respondieron, o que el envio fallo.
     */
    testSend: (content: string, fileName: string) =>
        dataService.postText<TestSendResult>(`/as2/test-send?${new URLSearchParams({ fileName }).toString()}`, content),
};
