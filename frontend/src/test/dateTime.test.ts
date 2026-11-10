import { describe, expect, it } from "vitest";
import { formatDateTime } from "../utils/dateTime";

describe("formatDateTime", () => {
    const iso = "2026-11-02T05:40:16Z";

    it("da el mismo formato con o sin la region del idioma", () => {
        expect(formatDateTime(iso, "es-EC")).toBe(formatDateTime(iso, "es"));
        expect(formatDateTime(iso, "en-US")).toBe(formatDateTime(iso, "en"));
    });

    it("lleva el mes en letras", () => {
        expect(formatDateTime(iso, "es")).toMatch(/nov/);
        expect(formatDateTime(iso, "en")).toMatch(/Nov/);
    });
});
