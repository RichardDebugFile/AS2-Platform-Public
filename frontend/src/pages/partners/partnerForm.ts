import type { Partner, PartnerInput } from "../../store/partnersStore";

/** Valores con los que arranca el alta (los mismos que aplica el servicio si no se envian). */
export const NEW_PARTNER: Partial<PartnerInput> = {
    transportType: "HTTPS",
    port: 443,
    path: "/as2",
    signingAlgorithm: "RSA_SHA256",
    encryptionAlgorithm: "AES256",
    micAlgorithm: "SHA256",
    compressionEnabled: false,
    requestMdn: true,
    requestSignedMdn: true,
    mdnMode: "SYNC",
    validateCertificate: true,
    validateSignature: true,
    validateMic: true,
    alertOnFailure: true,
};

/** Pestana de cada campo del socio que puede fallar la validacion (el resto esta en "general"). */
export const FIELD_TAB: Record<string, string> = {
    serverAddress: "connection",
    port: "connection",
    mdnReceiptUrl: "security",
};

/** Direccion AS2 que resulta de protocolo, servidor, puerto y ruta (como la arma el servicio). */
export function endpointPreview(v: Partial<PartnerInput>): string {
    if (!v.serverAddress) return "";
    const scheme = (v.transportType ?? "HTTPS").toLowerCase();
    let path = v.path ?? "";
    if (path && !path.startsWith("/")) path = "/" + path;
    const port = v.port ? ":" + String(v.port) : "";
    return `${scheme}://${v.serverAddress.trim()}${port}${path}`;
}

/** Lo que el API devuelve del socio pero no acepta en la edicion. */
const RESPONSE_ONLY = new Set(["id", "endpointUrl", "hasBasicAuthPassword", "active", "createdBy", "createdAt", "updatedBy", "updatedAt"]);

/** Campos del socio que acepta la edicion. */
export function editableFields(p: Partner): Partial<PartnerInput> {
    return Object.fromEntries(Object.entries(p).filter(([k]) => !RESPONSE_ONLY.has(k))) as Partial<PartnerInput>;
}
