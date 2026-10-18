import { App as AntApp, ConfigProvider } from "antd";
import enUS from "antd/locale/en_US";
import esES from "antd/locale/es_ES";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

/** Idioma y tema de Ant Design acordes al idioma activo de la consola. */
export default function AppProviders({ children }: { children: ReactNode }) {
    const { i18n } = useTranslation();
    const locale = i18n.language?.startsWith("en") ? enUS : esES;
    return (
        <ConfigProvider
            locale={locale}
            theme={{ token: { colorPrimary: "#0f766e", borderRadius: 8, fontFamily: "system-ui, -apple-system, 'Segoe UI', sans-serif" } }}
        >
            <AntApp>{children}</AntApp>
        </ConfigProvider>
    );
}
