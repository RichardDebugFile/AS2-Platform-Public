import { App as AntApp, ConfigProvider } from "antd";
// Rutas ESM: las de "antd/locale/*" son CommonJS y Vite las entrega envueltas en { default }, con lo
// que ConfigProvider no reconoce el idioma y los componentes quedan en ingles.
import enUS from "antd/es/locale/en_US";
import esES from "antd/es/locale/es_ES";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { antdTheme } from "./theme";

/** Idioma de Ant Design acorde al idioma activo y tema del sistema de diseño (theme.ts). */
export default function AppProviders({ children }: Readonly<{ children: ReactNode }>) {
    const { i18n } = useTranslation();
    const locale = i18n.language?.startsWith("en") ? enUS : esES;
    return (
        <ConfigProvider
            locale={locale}
            theme={antdTheme}
        >
            <AntApp>{children}</AntApp>
        </ConfigProvider>
    );
}
