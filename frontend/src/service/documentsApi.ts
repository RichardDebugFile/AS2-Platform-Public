import { dataService } from "./dataService";

export type Direction = "INBOUND" | "OUTBOUND";
export const DIRECTIONS: readonly Direction[] = ["INBOUND", "OUTBOUND"];

export type DocumentStatus =
    | "CREATED"
    | "RECEIVED"
    | "SENT"
    | "SEND_FAILED"
    | "MDN_PENDING"
    | "MDN_RECEIVED"
    | "MDN_FAILED"
    | "VALIDATED"
    | "VALIDATION_FAILED"
    | "DELIVERED"
    | "DELIVERY_FAILED"
    | "PROCESSING_FAILED"
    | "DUPLICATE";

export const DOCUMENT_STATUSES: readonly DocumentStatus[] = [
    "CREATED",
    "RECEIVED",
    "SENT",
    "SEND_FAILED",
    "MDN_PENDING",
    "MDN_RECEIVED",
    "MDN_FAILED",
    "VALIDATED",
    "VALIDATION_FAILED",
    "DELIVERED",
    "DELIVERY_FAILED",
    "PROCESSING_FAILED",
    "DUPLICATE",
];

/** Estados que indican un problema: se muestran en rojo. */
export const FAILED_STATUSES: ReadonlySet<DocumentStatus> = new Set([
    "SEND_FAILED",
    "MDN_FAILED",
    "VALIDATION_FAILED",
    "DELIVERY_FAILED",
    "PROCESSING_FAILED",
]);

/** Documento del repositorio (HU-14), tal como lo devuelve documents-service. */
export interface EdiDocument {
    id: string;
    partnerAs2Name: string;
    direction: Direction;
    typeCode: string;
    version: string;
    as2From: string | null;
    as2To: string | null;
    messageId: string;
    isaControl: string | null;
    gsControl: string | null;
    stControl: string | null;
    mic: string | null;
    micAlgorithm: string | null;
    signingAlgorithm: string | null;
    encryptionAlgorithm: string | null;
    sizeBytes: number;
    sha256: string;
    status: DocumentStatus;
    validationDetail: string | null;
    attributes: string | null;
    fileName: string | null;
    partnerCopyStatus: "COPIED" | "PENDING" | null;
    createdBy: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface Page<T> {
    content: T[];
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface DocumentFilter {
    partner?: string;
    direction?: Direction;
    type?: string;
    status?: DocumentStatus;
    /** AAAA-MM-DD */
    from?: string;
    to?: string;
    messageId?: string;
    isa?: string;
}

export interface StorageStatus {
    database: boolean;
    repositoryFile: boolean;
    fileName: string | null;
    partnerCopy: boolean;
    partnerCopyStatus: "COPIED" | "PENDING" | null;
}

export interface DocumentStats {
    total: number;
    byStatus: Record<string, number>;
    byType: Record<string, number>;
    byDirection: Record<string, number>;
    lastDays: { day: string; count: number }[];
}

export interface IngestRequest {
    partner: string;
    direction: Direction;
    content: string;
    messageId?: string;
}

export interface IngestResponse {
    outcome: "CREATED" | "DUPLICATE";
    document: EdiDocument;
}

const BASE = "/documents";
const byId = (id: string) => `${BASE}/${encodeURIComponent(id)}`;

/** Parametros de busqueda sin los vacios. */
export function searchParams(filter: DocumentFilter, page: number, size: number): string {
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(filter)) {
        if (typeof v === "string" && v.trim() !== "") p.set(k, v.trim());
    }
    p.set("page", String(page));
    p.set("size", String(size));
    return p.toString();
}

export const documentsApi = {
    search: (filter: DocumentFilter, page = 0, size = 20) =>
        dataService.get<Page<EdiDocument>>(`${BASE}/search?${searchParams(filter, page, size)}`),
    get: (id: string) => dataService.get<EdiDocument>(byId(id)),
    /** Contenido como texto (ISO-8859-1 en el servidor), para el visor. */
    payload: (id: string) => dataService.get<string>(`${byId(id)}/payload`),
    storageStatus: (id: string) => dataService.get<StorageStatus>(`${byId(id)}/storage-status`),
    stats: () => dataService.get<DocumentStats>(`${BASE}/stats`),
    remove: (id: string) => dataService.delete<void>(byId(id)),
    ingest: ({ partner, direction, content, messageId }: IngestRequest) =>
        dataService.postText<IngestResponse>(
            `${BASE}/ingest/as2?${new URLSearchParams({ partner, direction }).toString()}`,
            content,
            messageId ? { "Message-ID": messageId } : {},
        ),
};
