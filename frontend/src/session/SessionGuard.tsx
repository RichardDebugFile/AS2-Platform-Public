import { useState } from "react";
import { Button, Modal, Typography } from "antd";
import { ClockCircleOutlined, LogoutOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { formatCountdown } from "./sessionPolicy";
import { useSessionGuard } from "./useSessionGuard";

const { Text } = Typography;

/**
 * Avisos de la sesion. Se monta una vez dentro del layout autenticado. Son modales controlados (no
 * Modal.confirm) porque la cuenta regresiva se vuelve a dibujar cada segundo.
 */
export default function SessionGuard() {
    const { t } = useTranslation("session");
    const { snapshot, continueSession, endNow, ending, dismissEnded } = useSessionGuard();
    const [busy, setBusy] = useState(false);
    const [maxDismissed, setMaxDismissed] = useState(false);

    if (ending) {
        // La sesion ya esta revocada: solo se informa. Cerrar el aviso por cualquier via lleva al login.
        return (
            <Modal
                open
                title={
                    <span>
                        <LogoutOutlined style={{ marginRight: 8, color: "#dc2626" }} />
                        {t(`ended.title.${ending.reason}`)}
                    </span>
                }
                onCancel={dismissEnded}
                destroyOnHidden
                footer={[
                    <Button key="login" type="primary" onClick={dismissEnded}>
                        {t("ended.goToLogin")}
                    </Button>,
                ]}
            >
                <p>{t("ended.body")}</p>
                <Text type="secondary">
                    {t("ended.redirectIn")}{" "}
                    <Text strong data-testid="ended-countdown">
                        {formatCountdown(ending.remainingMs)}
                    </Text>
                </Text>
            </Modal>
        );
    }

    if (!snapshot) return null;

    const onContinue = async () => {
        setBusy(true);
        try {
            await continueSession();
        } finally {
            setBusy(false);
        }
    };

    const idleOpen = snapshot.phase === "warn-idle";
    // El aviso del techo se puede cerrar: no hay nada que extender (vuelve en una sesion nueva).
    const maxOpen = snapshot.phase === "warn-max" && !maxDismissed;

    return (
        <>
            <Modal
                open={idleOpen}
                title={
                    <span>
                        <ClockCircleOutlined style={{ marginRight: 8, color: "#d97706" }} />
                        {t("idle.title")}
                    </span>
                }
                closable={false}
                maskClosable={false}
                keyboard={false}
                destroyOnHidden
                footer={[
                    <Button key="end" onClick={() => void endNow()} disabled={busy}>
                        {t("idle.end")}
                    </Button>,
                    <Button key="continue" type="primary" onClick={() => void onContinue()} loading={busy}>
                        {t("idle.continue")}
                    </Button>,
                ]}
            >
                <p>{t("idle.body")}</p>
                <Text type="secondary">
                    {t("idle.remaining")}{" "}
                    <Text strong data-testid="idle-countdown">
                        {formatCountdown(snapshot.idleRemainingMs)}
                    </Text>
                </Text>
            </Modal>

            <Modal
                open={maxOpen}
                title={
                    <span>
                        <ClockCircleOutlined style={{ marginRight: 8, color: "#dc2626" }} />
                        {t("max.title")}
                    </span>
                }
                closable={false}
                maskClosable={false}
                keyboard={false}
                destroyOnHidden
                footer={[
                    <Button key="end" onClick={() => void endNow()}>
                        {t("max.endNow")}
                    </Button>,
                    <Button key="ok" type="primary" onClick={() => setMaxDismissed(true)}>
                        {t("max.ok")}
                    </Button>,
                ]}
            >
                <p>{t("max.body")}</p>
                <Text type="secondary">
                    {t("idle.remaining")}{" "}
                    <Text strong data-testid="max-countdown">
                        {formatCountdown(snapshot.sessionRemainingMs)}
                    </Text>
                </Text>
            </Modal>
        </>
    );
}
