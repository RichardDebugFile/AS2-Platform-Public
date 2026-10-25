import { Result } from "antd";
import { useTranslation } from "react-i18next";

/** Seccion del menu cuya pantalla llega en una historia posterior. */
export default function SectionPlaceholder({ ns }: Readonly<{ ns: "documents" | "alerts" }>) {
    const { t } = useTranslation([ns, "common"]);
    return <Result status="info" title={t("title", { ns })} subTitle={t("placeholder.body", { ns: "common" })} />;
}
