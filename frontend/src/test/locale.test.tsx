import { render, screen } from "@testing-library/react";
import { Pagination } from "antd";
import esES from "antd/es/locale/es_ES";
import dayjs from "dayjs";
import { afterEach, describe, expect, it } from "vitest";
import AppProviders from "../AppProviders";
import i18n, { dayjsLocaleFor } from "../i18n";

afterEach(async () => {
    await i18n.changeLanguage("es");
});

describe("idioma de los componentes", () => {
    it("el locale de antd es el objeto de idioma, no un modulo envuelto", () => {
        expect(esES.locale).toBe("es");
        expect(esES.Pagination?.items_per_page).toBe("/ página");
    });

    it("la paginacion sale en el idioma activo", async () => {
        render(
            <AppProviders>
                <Pagination total={100} showSizeChanger />
            </AppProviders>,
        );
        expect(await screen.findByText("10 / página")).toBeInTheDocument();
    });

    it("los calendarios siguen al idioma de la consola", async () => {
        expect(dayjsLocaleFor("es-EC")).toBe("es");
        expect(dayjsLocaleFor("en-US")).toBe("en");
        expect(dayjsLocaleFor(undefined)).toBe("es");

        await i18n.changeLanguage("en");
        expect(dayjs.locale()).toBe("en");
        await i18n.changeLanguage("es");
        expect(dayjs.locale()).toBe("es");
    });
});
