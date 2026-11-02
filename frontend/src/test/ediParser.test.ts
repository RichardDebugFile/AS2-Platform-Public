import { describe, expect, it } from "vitest";
import { SAMPLE_850 } from "../pages/documents/sample850";
import { describeElements, splitX12 } from "../utils/ediParser";

const ids = (text: string) => splitX12(text).segments.map((s) => s.id);
const EXPECTED = ["ISA", "GS", "ST", "BEG", "REF", "DTM", "N1", "N1", "PO1", "PID", "PO1", "CTT", "SE", "GE", "IEA"];

describe("visor de segmentos X12", () => {
    it("separa un 850 con ~ y salto de linea", () => {
        expect(ids(SAMPLE_850)).toEqual(EXPECTED);
    });

    it("da el mismo resultado con solo ~, con CRLF y con LF", () => {
        expect(ids(SAMPLE_850.replaceAll("\n", ""))).toEqual(EXPECTED);
        expect(ids(SAMPLE_850.replaceAll("~\n", "\r\n"))).toEqual(EXPECTED);
        expect(ids(SAMPLE_850.replaceAll("~\n", "\n"))).toEqual(EXPECTED);
    });

    it("conserva los elementos vacios en su posicion", () => {
        const beg = splitX12(SAMPLE_850).segments.find((s) => s.id === "BEG")!;
        expect(beg.elements).toEqual(["00", "SA", "OC-2026-001", "", "20261101"]);
        expect(describeElements(beg)).toBe("BEG01 00   ·   BEG02 SA   ·   BEG03 OC-2026-001   ·   BEG05 20261101");
    });

    it("sin ISA separa por ~ o por linea", () => {
        expect(ids("ST*997*0001~AK1*PO*1~")).toEqual(["ST", "AK1"]);
        expect(ids("")).toEqual([]);
    });
});
