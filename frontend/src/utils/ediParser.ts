/**
 * Separa un documento X12 en segmentos para mostrarlo en la consola (HU-12). Lee los separadores del
 * ISA de ancho fijo, igual que el servicio: el cuarto caracter separa elementos y el 106 termina cada
 * segmento ("~", o un salto de linea LF o CRLF). Sin ISA, separa por "~" o por linea y asume "*".
 */

export interface EdiSegment {
    /** Posicion del segmento en el documento, desde 1. */
    index: number;
    /** Identificador: ISA, GS, ST, BEG, PO1... */
    id: string;
    /** Elementos en orden; el primero es el 01 (BEG01). */
    elements: string[];
}

export interface EdiDocument {
    elementSeparator: string;
    segments: EdiSegment[];
}

const ISA_LENGTH = 106;
const LINE_BREAK = /\r\n|\r|\n/;
const SIMPLE_SPLIT = /~\s*|\r\n|\r|\n/;

/** Quita los saltos de linea de los extremos sin expresion regular (una como /[\r\n]+$/ es cuadratica). */
export function trimLineBreaks(s: string): string {
    let start = 0;
    let end = s.length;
    while (start < end && (s[start] === "\r" || s[start] === "\n")) start++;
    while (end > start && (s[end - 1] === "\r" || s[end - 1] === "\n")) end--;
    return s.slice(start, end);
}

export function splitX12(text: string): EdiDocument {
    const doc = text.replace(/^\uFEFF/, "").trimStart();
    const hasIsa = doc.startsWith("ISA") && doc.length >= ISA_LENGTH;
    const element = hasIsa ? doc.charAt(3) : "*";
    const terminator = hasIsa ? doc.charAt(ISA_LENGTH - 1) : "";

    let parts: string[];
    if (!hasIsa) parts = doc.split(SIMPLE_SPLIT);
    else if (terminator === "\r" || terminator === "\n") parts = doc.split(LINE_BREAK);
    else parts = doc.split(terminator);

    const segments = parts
        .map(trimLineBreaks)
        .filter((p) => p.trim() !== "")
        .map((p, i) => {
            const [id, ...elements] = p.split(element);
            return { index: i + 1, id: id.trim(), elements };
        });
    return { elementSeparator: element, segments };
}

/** Elementos con contenido y su posicion de dos digitos: "BEG03 OC-1". */
export function describeElements(segment: EdiSegment): string {
    return segment.elements
        .map((value, i) => ({ value, pos: String(i + 1).padStart(2, "0") }))
        .filter((e) => e.value.trim() !== "")
        .map((e) => `${segment.id}${e.pos} ${e.value.trim()}`)
        .join("   ·   ");
}
