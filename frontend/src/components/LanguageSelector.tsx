import { GlobalOutlined } from "@ant-design/icons";
import { Segmented, Select } from "antd";
import { useTranslation } from "react-i18next";

type Props = { variant?: "segmented" | "select" };

/** Cambia el idioma de toda la consola; i18next lo recuerda en el navegador. */
export default function LanguageSelector({ variant = "select" }: Props) {
    const { i18n, t } = useTranslation("common");
    const current = i18n.language?.startsWith("en") ? "en" : "es";
    const change = (lang: string) => void i18n.changeLanguage(lang);

    if (variant === "segmented") {
        return (
            <Segmented
                aria-label={t("language.label")}
                value={current}
                onChange={(v) => change(String(v))}
                options={[
                    { label: "ES", value: "es" },
                    { label: "EN", value: "en" },
                ]}
            />
        );
    }
    return (
        <Select
            aria-label={t("language.label")}
            value={current}
            onChange={change}
            suffixIcon={<GlobalOutlined />}
            style={{ width: 120 }}
            options={[
                { value: "es", label: t("language.es") },
                { value: "en", label: t("language.en") },
            ]}
        />
    );
}
