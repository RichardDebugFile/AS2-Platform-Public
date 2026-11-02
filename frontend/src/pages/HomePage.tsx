import { Alert, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";
import type { DeniedState } from "../routes/RequireRole";
import { useAuthStore } from "../store/authStore";

/** Pantalla de inicio tras el login. El panel operativo con cifras llega con HU-20. */
export default function HomePage() {
    const { t } = useTranslation("common");
    const user = useAuthStore((s) => s.user);
    const roles = (user?.roles ?? []).map((r) => t(`roles.${r}`)).join(", ");
    const denied = (useLocation().state as DeniedState | null)?.denied;
    return (
        <>
            {denied && (
                <Alert
                    type="warning"
                    showIcon
                    closable
                    style={{ marginBottom: 16 }}
                    message={t("home.denied", { path: denied })}
                />
            )}
            <Typography.Title level={3}>{t("home.welcome", { name: user?.username ?? "" })}</Typography.Title>
            <Typography.Paragraph>{t("home.intro")}</Typography.Paragraph>
            <Typography.Text type="secondary">{t("home.role", { roles })}</Typography.Text>
        </>
    );
}
