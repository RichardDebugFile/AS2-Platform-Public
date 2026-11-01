import { create } from "zustand";
import { dataService, type ApiResponse } from "../service/dataService";
import type { ActionResult } from "./partnersStore";

// Certificados X.509 de los socios (HU-11). Los enumerados viajan por su nombre.
export type KeyUsage = "ENCRYPTION" | "SIGNATURE" | "SSL";
export type Validity = "VALID" | "EXPIRING" | "EXPIRED" | "NOT_YET_VALID";
export type UploadOutcome = "CREATED" | "ALREADY_ACTIVE" | "REACTIVATED";

export const KEY_USAGES: KeyUsage[] = ["ENCRYPTION", "SIGNATURE", "SSL"];

export interface Certificate {
    id: string;
    partnerId: string;
    keyUsage: KeyUsage;
    issuedTo: string;
    issuedBy: string;
    subjectDn: string;
    issuerDn: string;
    serialNumber: string;
    fingerprintSha256: string;
    validFrom: string;
    validTo: string;
    validity: Validity;
    daysToExpire: number;
    active: boolean;
    preferred: boolean;
    description?: string | null;
    createdBy?: string;
    createdAt?: string;
    pem?: string | null;
}

export interface UploadResult {
    certificate: Certificate;
    outcome: UploadOutcome;
    warnings: string[];
}

export interface ChangeResult {
    sameUsage: Certificate[];
    warnings: string[];
}

/** Huella en pares hexadecimales separados por ":"; abreviada, los primeros 8 bytes. */
export function formatFingerprint(hex: string, short = false): string {
    const pairs = hex.toUpperCase().match(/.{2}/g) ?? [];
    return short ? `${pairs.slice(0, 8).join(":")}…` : pairs.join(":");
}

interface CertificatesState {
    /** Certificados por id de socio. */
    byPartner: Record<string, Certificate[]>;
    loading: boolean;
    fetchCertificates: (partnerId: string) => Promise<void>;
    upload: (partnerId: string, file: File, keyUsage: KeyUsage, description?: string) => Promise<ActionResult<UploadResult>>;
    prefer: (partnerId: string, id: string) => Promise<ActionResult<ChangeResult>>;
    activate: (partnerId: string, id: string) => Promise<ActionResult<ChangeResult>>;
    deactivate: (partnerId: string, id: string) => Promise<ActionResult<ChangeResult>>;
    remove: (partnerId: string, id: string) => Promise<ActionResult<ChangeResult>>;
    /** Descarga el PEM del certificado como archivo. */
    download: (partnerId: string, certificate: Certificate) => Promise<ActionResult>;
}

const base = (partnerId: string) => `/partners/${encodeURIComponent(partnerId)}/certificates`;

function result<T>(res: ApiResponse<T>): ActionResult<T> {
    return res.ok ? { ok: true, data: res.data } : { ok: false, message: res.error.message };
}

export const useCertificatesStore = create<CertificatesState>((set, get) => {
    /** Tras cualquier cambio se vuelve a leer la lista: el preferente de otro certificado pudo cambiar. */
    const afterChange = async <T>(partnerId: string, res: ApiResponse<T>): Promise<ActionResult<T>> => {
        const r = result(res);
        if (r.ok) await get().fetchCertificates(partnerId);
        return r;
    };
    const change = (action: string) => async (partnerId: string, id: string) =>
        afterChange(partnerId, await dataService.post<ChangeResult>(`${base(partnerId)}/${encodeURIComponent(id)}/${action}`));

    return {
        byPartner: {},
        loading: false,

        fetchCertificates: async (partnerId) => {
            set({ loading: true });
            const res = await dataService.get<Certificate[]>(base(partnerId));
            set({ loading: false });
            if (res.ok && Array.isArray(res.data)) set({ byPartner: { ...get().byPartner, [partnerId]: res.data } });
        },

        upload: async (partnerId, file, keyUsage, description) => {
            const form = new FormData();
            form.append("file", file);
            form.append("keyUsage", keyUsage);
            if (description?.trim()) form.append("description", description.trim());
            return afterChange(partnerId, await dataService.upload<UploadResult>(`${base(partnerId)}/upload`, form));
        },

        prefer: change("prefer"),
        activate: change("activate"),
        deactivate: change("deactivate"),

        remove: async (partnerId, id) =>
            afterChange(partnerId, await dataService.delete<ChangeResult>(`${base(partnerId)}/${encodeURIComponent(id)}`)),

        download: async (partnerId, certificate) => {
            const res = await dataService.get<string>(`${base(partnerId)}/${encodeURIComponent(certificate.id)}/pem`);
            if (!res.ok) return { ok: false, message: res.error.message };
            const url = URL.createObjectURL(new Blob([String(res.data)], { type: "application/x-pem-file" }));
            const a = document.createElement("a");
            a.href = url;
            a.download = `${certificate.keyUsage.toLowerCase()}_${certificate.fingerprintSha256.slice(0, 8)}.pem`;
            a.click();
            URL.revokeObjectURL(url);
            return { ok: true, data: undefined };
        },
    };
});
