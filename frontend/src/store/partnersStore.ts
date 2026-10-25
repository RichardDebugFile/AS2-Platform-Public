import { create } from "zustand";
import { dataService, type ApiResponse } from "../service/dataService";

// Tipos del API de partners-service (HU-08). Los enumerados viajan por su nombre.
export type TransportType = "HTTP" | "HTTPS";
export type PartnerType = "CUSTOMER" | "SUPPLIER" | "DISTRIBUTOR" | "INTERNAL";
export type MdnMode = "SYNC" | "ASYNC";
export type SendingMode = "NONE" | "REGULAR" | "FILENAME_FILTER";
export type ArchiveMode = "NONE" | "MOVE" | "DELETE";
export type MimeType = "APPLICATION_EDI_X12" | "APPLICATION_EDIFACT" | "APPLICATION_XML" | "TEXT_PLAIN";
export type MdnReceiptType = "SIGNED" | "UNSIGNED";

/** Campos editables del socio (cuerpo de alta y edicion). */
export interface PartnerInput {
    as2Name: string;
    partnerName?: string;
    partnerAs2Name?: string;
    partnerType?: PartnerType;
    externalId?: string;
    description?: string;
    serverAddress: string;
    port: number;
    transportType?: TransportType;
    path?: string;
    basicAuthUsername?: string;
    /** Solo se envia; vacio en una edicion conserva la clave guardada. */
    basicAuthPassword?: string;
    connectionTimeoutSeconds?: number;
    readTimeoutSeconds?: number;
    maxRetryAttempts?: number;
    retryBackoffMs?: number;
    signingAlgorithm?: string;
    encryptionAlgorithm?: string;
    micAlgorithm?: string;
    compressionEnabled?: boolean;
    requestMdn?: boolean;
    requestSignedMdn?: boolean;
    mdnMode?: MdnMode;
    mdnReceiptUrl?: string;
    validateCertificate?: boolean;
    validateSignature?: boolean;
    validateMic?: boolean;
    enableDualStorage?: boolean;
    enableIncomingQueue?: boolean;
    sharedFolderName?: string;
    dataLocation?: string;
    contactName?: string;
    contactPhone?: string;
    contactEmail?: string;
    tradingPartnerEmail?: string;
    alertOnFailure?: boolean;
}

export type FolderState = "LOCAL" | "SAMBA_LINKED" | "PRODUCTION";
export type FolderHealth = "OK" | "MISSING" | "NO_ACCESS" | "ERROR";

export interface FolderEntry {
    name: string;
    path: string;
    exists: boolean;
    readable: boolean;
    writable: boolean;
    linked: boolean;
    target?: string | null;
}

export interface FolderStatus {
    partnerId: string;
    as2Name: string;
    folderState: FolderState;
    sharedFolderName?: string | null;
    health: FolderHealth;
    folders: FolderEntry[];
    problems: string[];
}

export interface FolderPrecheck {
    ok: boolean;
    sharedFolderName: string;
    target: string;
    problems: string[];
    actions: string[];
}

export interface Partner extends Omit<PartnerInput, "basicAuthPassword"> {
    id: string;
    folderState?: FolderState;
    endpointUrl: string;
    hasBasicAuthPassword: boolean;
    active: boolean;
    createdBy?: string;
    createdAt?: string;
    updatedBy?: string;
    updatedAt?: string;
}

export interface OutgoingConfig {
    localFolder: string;
    sendingMode: SendingMode;
    filenameFilter?: string;
    encryptionAlgorithm?: string;
    signingAlgorithm?: string;
    compression?: boolean;
    mimeType?: MimeType;
    archiveMode?: ArchiveMode;
    archiveFolder?: string;
}

export interface IncomingConfig {
    incomingFolder: string;
    parserProfile?: string;
}

export interface MdnConfig {
    localFolder: string;
    receiptType: MdnReceiptType;
    deliveryMode: MdnMode;
    asyncMdnUrl?: string;
}

export interface PartnerConfigs {
    outgoing: OutgoingConfig;
    incoming: IncomingConfig;
    mdn: MdnConfig;
}

export interface AlgorithmItem {
    key: string;
    label: string;
    status: "recommended" | "ok" | "legacy";
}

export interface AlgorithmCatalog {
    signing: AlgorithmItem[];
    encryption: AlgorithmItem[];
    mic: AlgorithmItem[];
}

export type ConfigKind = "outgoing" | "incoming" | "mdn";

export type ActionResult<T = undefined> = { ok: true; data: T } | { ok: false; message: string };

const BASE = "/partners";
const byId = (id: string) => `${BASE}/${encodeURIComponent(id)}`;

/** Quita textos vacios: el API trata null como "valor por defecto" o "conservar el actual". */
export function clean<T extends object>(values: T): T {
    return Object.fromEntries(
        Object.entries(values).filter(([, v]) => v !== undefined && v !== null && !(typeof v === "string" && v.trim() === "")),
    ) as T;
}

function result<T>(res: ApiResponse<T>): ActionResult<T> {
    return res.ok ? { ok: true, data: res.data } : { ok: false, message: res.error.message };
}

interface PartnersState {
    partners: Partner[];
    /** Lista visible: activos o dados de baja. */
    showInactive: boolean;
    loading: boolean;
    error: string | null;
    catalog: AlgorithmCatalog | null;
    /** Estado de las carpetas por id de socio (activos). */
    folderStatuses: Record<string, FolderStatus>;
    fetchFolderStatuses: () => Promise<void>;
    getFolderStatus: (id: string) => Promise<ActionResult<FolderStatus>>;
    repairFolders: (id: string) => Promise<ActionResult<FolderStatus>>;
    precheckFolders: (id: string, sharedFolderName: string) => Promise<ActionResult<FolderPrecheck>>;
    linkFolders: (id: string, sharedFolderName: string) => Promise<ActionResult<FolderStatus>>;
    setProduction: (id: string, enabled: boolean, confirm: string) => Promise<ActionResult<Partner>>;
    fetchPartners: (inactive?: boolean) => Promise<void>;
    fetchCatalog: () => Promise<void>;
    getConfigs: (id: string) => Promise<ActionResult<PartnerConfigs>>;
    /** Alta completa: el socio y sus tres configuraciones por defecto, en una sola transaccion. */
    createPartner: (p: PartnerInput) => Promise<ActionResult<Partner>>;
    updatePartner: (id: string, p: PartnerInput) => Promise<ActionResult<Partner>>;
    saveConfig: <K extends ConfigKind>(id: string, kind: K, cfg: PartnerConfigs[K]) => Promise<ActionResult>;
    setActive: (id: string, active: boolean) => Promise<ActionResult>;
    remove: (id: string) => Promise<ActionResult>;
    purge: (id: string, confirm: string) => Promise<ActionResult>;
}

export const usePartnersStore = create<PartnersState>((set, get) => {
    const reload = () => get().fetchPartners(get().showInactive);

    const afterChange = async <T>(res: ApiResponse<T>): Promise<ActionResult<T>> => {
        const r = result(res);
        if (r.ok) await reload();
        return r;
    };

    const done = (r: ActionResult<unknown>): ActionResult => (r.ok ? { ok: true, data: undefined } : r);

    /** Guarda el estado de carpetas recibido para que la lista lo muestre sin otra consulta. */
    const remember = (r: ActionResult<FolderStatus>): ActionResult<FolderStatus> => {
        if (r.ok) set({ folderStatuses: { ...get().folderStatuses, [r.data.partnerId]: r.data } });
        return r;
    };

    return {
        partners: [],
        showInactive: false,
        loading: false,
        error: null,
        catalog: null,
        folderStatuses: {},

        fetchFolderStatuses: async () => {
            const res = await dataService.get<FolderStatus[]>(`${BASE}/folder-statuses`);
            if (res.ok && Array.isArray(res.data)) {
                set({ folderStatuses: Object.fromEntries(res.data.map((f) => [f.partnerId, f])) });
            }
        },

        getFolderStatus: async (id) => remember(result(await dataService.get<FolderStatus>(`${byId(id)}/folders`))),

        repairFolders: async (id) => remember(result(await dataService.post<FolderStatus>(`${byId(id)}/folders/repair`))),

        precheckFolders: async (id, sharedFolderName) =>
            result(await dataService.post<FolderPrecheck>(`${byId(id)}/folders/precheck`, { sharedFolderName })),

        linkFolders: async (id, sharedFolderName) => {
            const res = remember(result(await dataService.post<FolderStatus>(`${byId(id)}/folders/link`, { sharedFolderName })));
            if (res.ok) await reload();
            return res;
        },

        setProduction: async (id, enabled, confirm) => {
            const res = await afterChange(await dataService.post<Partner>(`${byId(id)}/production`, { enabled, confirm }));
            if (res.ok) await get().getFolderStatus(id);
            return res;
        },

        fetchPartners: async (inactive = false) => {
            set({ loading: true, error: null, showInactive: inactive });
            const res = await dataService.get<Partner[]>(inactive ? `${BASE}/inactive` : BASE);
            if (!res.ok) {
                set({ loading: false, error: res.error.message, partners: [] });
                return;
            }
            set({ loading: false, partners: res.data });
        },

        fetchCatalog: async () => {
            if (get().catalog) return;
            const res = await dataService.get<AlgorithmCatalog>("/catalogs/algorithms");
            if (res.ok) set({ catalog: res.data });
        },

        getConfigs: async (id) => {
            const [outgoing, incoming, mdn] = await Promise.all([
                dataService.get<OutgoingConfig>(`${byId(id)}/outgoing-config`),
                dataService.get<IncomingConfig>(`${byId(id)}/incoming-config`),
                dataService.get<MdnConfig>(`${byId(id)}/mdn-config`),
            ]);
            if (!outgoing.ok) return result(outgoing);
            if (!incoming.ok) return result(incoming);
            if (!mdn.ok) return result(mdn);
            return { ok: true, data: { outgoing: outgoing.data, incoming: incoming.data, mdn: mdn.data } };
        },

        createPartner: async (p) => {
            const res = await afterChange(await dataService.post<{ partner: Partner }>(`${BASE}/complete`, { partner: clean(p) }));
            return res.ok ? { ok: true, data: res.data.partner } : res;
        },

        updatePartner: async (id, p) => afterChange(await dataService.put<Partner>(byId(id), clean(p))),

        saveConfig: async (id, kind, cfg) => done(result(await dataService.put(`${byId(id)}/${kind}-config`, clean(cfg)))),

        setActive: async (id, active) => done(await afterChange(await dataService.patch(`${byId(id)}/status`, { active }))),

        remove: async (id) => done(await afterChange(await dataService.delete(byId(id)))),

        purge: async (id, confirm) =>
            done(await afterChange(await dataService.delete(`${byId(id)}/purge?confirm=${encodeURIComponent(confirm)}`))),
    };
});
