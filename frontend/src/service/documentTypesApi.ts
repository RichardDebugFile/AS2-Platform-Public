import { dataService } from "./dataService";

export type DocumentStandard = "X12" | "EDIFACT";
export type DocumentDirection = "INBOUND" | "OUTBOUND";

export const DOCUMENT_STANDARDS: DocumentStandard[] = ["X12", "EDIFACT"];
export const DOCUMENT_DIRECTIONS: DocumentDirection[] = ["INBOUND", "OUTBOUND"];

/** Referencia breve a un tipo del catalogo. */
export interface TypeRef {
    id: string;
    standard: DocumentStandard;
    code: string;
    version: string;
    name: string;
}

/** Tipo del catalogo EDI (HU-13) con sus respuestas esperadas y cuantos socios lo usan. */
export interface DocumentType extends TypeRef {
    description: string | null;
    responses: TypeRef[];
    partnerCount: number;
}

export interface DocumentTypeRequest {
    standard: DocumentStandard;
    code: string;
    version: string;
    name: string;
    description?: string;
    responseTypeIds?: string[];
}

/** Tipo que intercambia un socio, en una direccion, con sus datos adicionales. */
export interface PartnerDocumentType {
    id: string;
    documentType: TypeRef;
    direction: DocumentDirection;
    notes: string | null;
    extraFields: Record<string, string>;
    expectedResponses: TypeRef[];
}

export interface PartnerDocumentTypeItem {
    documentTypeId: string;
    direction: DocumentDirection;
    notes?: string;
    extraFields?: Record<string, string>;
}

/** "X12 850 4010". */
export const typeLabel = (t: Pick<TypeRef, "standard" | "code" | "version">) => `${t.standard} ${t.code} ${t.version}`;

/** "Nombre: valor" por linea, a {nombre: valor}; las lineas sin nombre se ignoran. */
export function parseFields(text: string): Record<string, string> {
    const out: Record<string, string> = {};
    for (const line of text.split("\n")) {
        const i = line.indexOf(":");
        const key = (i < 0 ? line : line.slice(0, i)).trim();
        if (key) out[key] = i < 0 ? "" : line.slice(i + 1).trim();
    }
    return out;
}

export function formatFields(fields: Record<string, string>): string {
    return Object.entries(fields)
        .map(([k, v]) => `${k}: ${v}`)
        .join("\n");
}

const CATALOG = "/catalogs/document-types";

export const documentTypesApi = {
    list: () => dataService.get<DocumentType[]>(CATALOG),
    create: (r: DocumentTypeRequest) => dataService.post<DocumentType>(CATALOG, r),
    update: (id: string, r: DocumentTypeRequest) => dataService.put<DocumentType>(`${CATALOG}/${id}`, r),
    /** 409 si algun socio lo usa: el mensaje nombra a los socios. */
    remove: (id: string) => dataService.delete<void>(`${CATALOG}/${id}`),
    partnerTypes: (partnerId: string) => dataService.get<PartnerDocumentType[]>(`/partners/${partnerId}/document-types`),
    /** Reemplaza la lista completa de tipos del socio. */
    savePartnerTypes: (partnerId: string, items: PartnerDocumentTypeItem[]) =>
        dataService.put<PartnerDocumentType[]>(`/partners/${partnerId}/document-types`, { documentTypes: items }),
};
