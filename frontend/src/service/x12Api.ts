import { dataService } from "./dataService";

export interface Party {
    qualifier: string;
    id: string;
}

/** Resumen de POST /documents/x12/parse. */
export interface X12Summary {
    typeCode: string;
    version: string;
    functionalCode: string;
    sender: Party;
    receiver: Party;
    groupSender: string;
    groupReceiver: string;
    interchangeDate: string;
    interchangeTime: string;
    usageIndicator: string;
    controls: Record<string, string>;
    complete: boolean;
    segmentCount: number;
    elementSeparator: string;
    componentSeparator: string;
    segmentTerminator: string;
}

export type ValidationStatus = "VALIDATED" | "VALIDATION_FAILED";

export interface X12Validation {
    status: ValidationStatus;
    typeCode: string;
    version: string;
    missingSegments: string[];
    problems: string[];
}

export interface X12Schema {
    typeCode: string;
    description: string;
    requiredSegments: string[];
}

export type X12Type = "850" | "997" | "889";
export const X12_TYPES: readonly X12Type[] = ["850", "997", "889"];

export interface Company {
    name: string;
    id: string;
}

export interface GenerateRequest {
    type: X12Type;
    version?: string;
    production?: boolean;
    sender?: Party;
    receiver?: Party;
    purchaseOrder?: {
        number: string;
        reference?: string;
        deliveryDays?: number;
        buyer: Company;
        seller: Company;
        lines: { productId: string; description?: string; quantity: number; unitPrice: number; unit?: string }[];
    };
    promotion?: { id: string; days?: number; vendor: Company };
    /** Solo 997: el documento que se acusa. */
    original?: string;
}

const BASE = "/documents/x12";

export const x12Api = {
    parse: (text: string) => dataService.postText<X12Summary>(`${BASE}/parse`, text),
    validate: (text: string) => dataService.postText<X12Validation>(`${BASE}/validate`, text),
    generate: (req: GenerateRequest) => dataService.post<string>(`${BASE}/generate`, req),
    schemas: () => dataService.get<X12Schema[]>(`${BASE}/schemas`),
};
