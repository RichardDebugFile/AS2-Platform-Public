import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Badge, Button, Card, List, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { dashboardApi, type ServiceStatus, type SystemStatus } from "../../service/dashboardApi";
import type { ApiResponse } from "../../service/dataService";
import { formatDateTime } from "../../utils/dateTime";

/** Cada cuanto se vuelve a consultar la salud de los servicios. */
export const STATUS_REFRESH_MS = 30_000;

const KNOWN = new Set(["UP", "DOWN", "OUT_OF_SERVICE", "UNREACHABLE"]);

function badge(status: string): "success" | "error" | "warning" {
    if (status === "UP") return "success";
    if (status === "OUT_OF_SERVICE") return "warning";
    return "error";
}

/** Estado del sistema (HU-20): un renglon por servicio, con su latencia. */
export default function SystemStatusCard() {
    const { t, i18n } = useTranslation("common");
    const [data, setData] = useState<SystemStatus | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

    const apply = useCallback((r: ApiResponse<SystemStatus>) => {
        setLoading(false);
        if (r.ok) {
            setData(r.data);
            setError(null);
        } else {
            setError(r.error.message);
        }
    }, []);

    const refresh = useCallback(() => {
        setLoading(true);
        void dashboardApi.systemStatus().then(apply);
    }, [apply]);

    useEffect(() => {
        void dashboardApi.systemStatus().then(apply);
        const id = globalThis.setInterval(() => void dashboardApi.systemStatus().then(apply), STATUS_REFRESH_MS);
        return () => globalThis.clearInterval(id);
    }, [apply]);

    const label = (s: ServiceStatus) => (KNOWN.has(s.status) ? t(`home.system.status.${s.status}`) : s.status);

    return (
        <Card
            size="small"
            title={t("home.system.title")}
            extra={
                <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={refresh}>
                    {t("home.system.refresh")}
                </Button>
            }
        >
            {error && <Alert type="error" showIcon message={t("home.system.error", { message: error })} style={{ marginBottom: 8 }} />}
            <List<ServiceStatus>
                size="small"
                dataSource={Array.isArray(data?.services) ? data?.services : []}
                locale={{ emptyText: loading ? " " : t("home.system.empty") }}
                renderItem={(s) => (
                    <List.Item data-testid={`service-${s.service}`} className="service-row">
                        <Badge status={badge(s.status)} text={<span className="mono-text">{s.service}</span>} />
                        <span className="service-state">
                            <Typography.Text type="secondary">{label(s)}</Typography.Text>
                            {/* La latencia del gateway es la de si mismo: no aporta. */}
                            {s.status === "UP" && s.latencyMs != null && s.service !== "gateway" && (
                                <Typography.Text type="secondary" className="mono-text service-latency">
                                    {t("home.system.latency", { ms: s.latencyMs })}
                                </Typography.Text>
                            )}
                        </span>
                    </List.Item>
                )}
            />
            {data?.checkedAt && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {t("home.system.checkedAt", { at: formatDateTime(data.checkedAt, i18n.language) })}
                </Typography.Text>
            )}
        </Card>
    );
}
