import { describe, expect, it } from "vitest";
import { resources } from "../i18n";

function keys(obj: unknown, prefix = ""): string[] {
    if (!obj || typeof obj !== "object") return [prefix];
    return Object.entries(obj as Record<string, unknown>).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k));
}

describe("traducciones", () => {
    it("espanol e ingles tienen exactamente las mismas claves en cada espacio", () => {
        for (const ns of Object.keys(resources.es) as (keyof typeof resources.es)[]) {
            expect(keys(resources.en[ns]).sort(), ns).toEqual(keys(resources.es[ns]).sort());
        }
    });

    it("ningun texto queda vacio", () => {
        for (const lang of ["es", "en"] as const) {
            for (const [ns, data] of Object.entries(resources[lang])) {
                const empty = keys(data).filter((k) => {
                    const v = k.split(".").reduce<unknown>((o, p) => (o as Record<string, unknown>)[p], data);
                    return typeof v !== "string" || v.trim() === "";
                });
                expect(empty, `${lang}/${ns}`).toEqual([]);
            }
        }
    });
});
