import { describe, expect, it } from "vitest";
import { initials } from "../routes/initials";

describe("iniciales del avatar", () => {
    it("toma la primera letra de las dos primeras partes del usuario", () => {
        expect(initials("ana.operadora")).toBe("AO");
        expect(initials("luis_admin")).toBe("LA");
    });

    it("con una sola parte usa sus dos primeras letras", () => {
        expect(initials("admin")).toBe("AD");
    });

    it("sin usuario muestra un marcador", () => {
        expect(initials(undefined)).toBe("?");
    });
});
