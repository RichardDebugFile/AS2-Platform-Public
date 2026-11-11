import { CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Badge, Button, Card, Col, Empty, Row, Space, Statistic, Switch, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { dashboardApi, type Dashboard, type ServiceStatus, type SystemStatus } from "../../service/dashboardApi";
import type { ApiResponse } from "../../service/dataService";
import { documentsApi, type Direction, type DocumentStatus, type EdiDocument } from "../../service/documentsApi";
import { formatDateTime } from "../../utils/dateTime";
import { DirectionTag, StatusTag } from "../documents/documentTags";
import { healthLevel, KNOWN_COMPONENTS, KNOWN_HEALTH } from "./serviceHealth";

/** Intervalo de la revision automatica. */
export const AUTO_REFRESH_MS = 30_000;
const RECENT = 10;

interface Flow {
    today: Dashboard | null;
    recent: EdiDocument[];
    lastInbound: EdiDocument | null;
    lastOutbound: EdiDocument | null;
    error: string | null;
}

async function fetchFlow(): Promise<Flow> {
    const today = dayjs().format("YYYY-MM-DD");
    const last = (direction: Direction) => documentsApi.search({ direction }, 0, 1);
    const [dash, recent, inbound, outbound] = await Promise.all([
        dashboardApi.dashboard({ from: today, to: today }),
        documentsApi.recent(RECENT),
        last("INBOUND"),
        last("OUTBOUND"),
    ]);
    const first = (r: typeof inbound) => (r.ok ? (r.data.content[0] ?? null) : null);
    const failed = [dash, recent, inbound, outbound].find((r) => !r.ok);
    return {
        today: dash.ok ? dash.data : null,
        recent: recent.ok && Array.isArray(recent.data) ? recent.data : [],
        lastInbound: first(inbound),
        lastOutbound: first(outbound),
        error: failed && !failed.ok ? failed.error.message : null,
    };
}

function HealthTag({ status, label }: Readonly<{ status: string; label: string }>) {
    const level = healthLevel(status);
    let icon = <CloseCircleOutlined />;
    if (level === "success") icon = <CheckCircleOutlined />;
    else if (level === "warning") icon = <ExclamationCircleOutlined />;
    const colorName = { success: "green", warning: "orange", error: "red", default: "default" }[level];
    return (
        <Tag color={colorName} icon={icon}>
            {label}
        </Tag>
    );
}

/**
 * Estado del sistema (HU-20, AS2-99): cada microservicio con su salud, latencia y verificaciones
 * internas, y el flujo reciente de documentos. Solo consulta; la ven todos los roles.
 */
export default function SystemPage() {
    const { t, i18n } = useTranslation("common");
    const [status, setStatus] = useState<SystemStatus | null>(null);
    const [statusError, setStatusError] = useState<string | null>(null);
    const [flow, setFlow] = useState<Flow | null>(null);
    const [loading, setLoading] = useState(true);
    const [auto, setAuto] = useState(true);

    const applyStatus = useCallback((r: ApiResponse<SystemStatus>) => {
        setLoading(false);
        if (r.ok) {
            setStatus(r.data);
            setStatusError(null);
        } else {
            setStatusError(r.error.message);
        }
    }, []);

    const refresh = useCallback(() => {
        setLoading(true);
        void dashboardApi.systemStatus().then(applyStatus);
        void fetchFlow().then(setFlow);
    }, [applyStatus]);

    useEffect(() => {
        void dashboardApi.systemStatus().then(applyStatus);
        void fetchFlow().then(setFlow);
    }, [applyStatus]);

    useEffect(() => {
        if (!auto) return undefined;
        const id = globalThis.setInterval(refresh, AUTO_REFRESH_MS);
        return () => globalThis.clearInterval(id);
    }, [auto, refresh]);

    const when = (iso: string) => formatDateTime(iso, i18n.language);
    const healthText = (s: string) => (KNOWN_HEALTH.has(s) ? t(`home.system.status.${s}`) : s);
    const componentState = (st: string) => {
        if (st === "UP") return t("systemPage.componentOk");
        if (st === "DOWN") return t("systemPage.componentDown");
        return healthText(st);
    };
    const componentText = (c: string) => (KNOWN_COMPONENTS.has(c) ? t(`systemPage.components.${c}`) : c);
    const services = Array.isArray(status?.services) ? (status?.services ?? []) : [];
    const problems = services.filter((s) => s.status !== "UP").length;

    const columns: ColumnsType<EdiDocument> = [
        { title: t("systemPage.flow.columns.createdAt"), dataIndex: "createdAt", render: (v: string) => <span className="nowrap">{when(v)}</span> },
        { title: t("systemPage.flow.columns.direction"), dataIndex: "direction", render: (v: Direction) => <DirectionTag direction={v} /> },
        { title: t("systemPage.flow.columns.partner"), dataIndex: "partnerAs2Name", render: (v: string) => <span className="nowrap">{v}</span> },
        { title: t("systemPage.flow.columns.type"), dataIndex: "typeCode", render: (v: string) => <Tag className="mono-text">{v}</Tag> },
        { title: t("systemPage.flow.columns.status"), dataIndex: "status", render: (v: DocumentStatus) => <StatusTag status={v} /> },
    ];

    const lastText = (d: EdiDocument | null) => (d ? `${when(d.createdAt)} · ${d.partnerAs2Name}` : t("systemPage.flow.none"));

    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                {t("systemPage.intro")}
            </Typography.Paragraph>

            <Card size="small">
                <div className="system-toolbar">
                    <Space wrap>
                        {status &&
                            (problems === 0 ? (
                                <HealthTag status="UP" label={t("systemPage.allUp")} />
                            ) : (
                                <HealthTag status="DOWN" label={t("systemPage.problems", { count: problems })} />
                            ))}
                        {status?.checkedAt && (
                            <Typography.Text type="secondary">{t("home.system.checkedAt", { at: when(status.checkedAt) })}</Typography.Text>
                        )}
                    </Space>
                    <Space wrap>
                        <Space size={6}>
                            <Switch size="small" checked={auto} onChange={setAuto} aria-label={t("systemPage.auto")} />
                            <Typography.Text>{t("systemPage.auto")}</Typography.Text>
                        </Space>
                        <Button icon={<ReloadOutlined />} loading={loading} onClick={refresh}>
                            {t("home.system.refresh")}
                        </Button>
                    </Space>
                </div>
            </Card>

            {statusError && <Alert type="error" showIcon message={t("home.system.error", { message: statusError })} />}

            <Row gutter={[12, 12]}>
                {services.map((s: ServiceStatus) => {
                    const components = Object.entries(s.components ?? {});
                    return (
                        <Col key={s.service} xs={24} md={12} xl={8}>
                            <Card
                                size="small"
                                className="kpi-card"
                                data-testid={`service-card-${s.service}`}
                                title={<span className="mono-text">{s.service}</span>}
                                extra={<HealthTag status={s.status} label={healthText(s.status)} />}
                            >
                                <Typography.Text type="secondary">
                                    {t("systemPage.latency")}{" "}
                                    <span className="mono-text">
                                        {s.latencyMs != null && s.service !== "gateway"
                                            ? t("home.system.latency", { ms: s.latencyMs })
                                            : "—"}
                                    </span>
                                </Typography.Text>
                                {components.length > 0 ? (
                                    <ul className="component-list">
                                        {components.map(([name, st]) => (
                                            <li key={name}>
                                                <Badge status={healthLevel(st)} text={componentText(name)} />
                                                <Typography.Text type="secondary">{componentState(st)}</Typography.Text>
                                            </li>
                                        ))}
                                    </ul>
                                ) : (
                                    <Typography.Paragraph type="secondary" style={{ margin: "8px 0 0" }}>
                                        {s.service === "gateway" ? t("systemPage.gatewayNote") : t("systemPage.noComponents")}
                                    </Typography.Paragraph>
                                )}
                            </Card>
                        </Col>
                    );
                })}
            </Row>

            <Card size="small" title={t("systemPage.flow.title")}>
                {flow?.error && (
                    <Alert type="warning" showIcon message={t("systemPage.flow.error", { message: flow.error })} style={{ marginBottom: 12 }} />
                )}
                <Row gutter={[12, 12]}>
                    <Col xs={12} lg={6}>
                        <Statistic title={t("systemPage.flow.today")} value={flow?.today?.total ?? 0} loading={!flow} />
                    </Col>
                    <Col xs={12} lg={6}>
                        <Statistic title={t("systemPage.flow.inbound")} value={flow?.today?.byDirection?.INBOUND ?? 0} loading={!flow} />
                    </Col>
                    <Col xs={12} lg={6}>
                        <Statistic title={t("systemPage.flow.outbound")} value={flow?.today?.byDirection?.OUTBOUND ?? 0} loading={!flow} />
                    </Col>
                    <Col xs={12} lg={6}>
                        <Statistic title={t("systemPage.flow.failed")} value={flow?.today?.failed ?? 0} loading={!flow} />
                    </Col>
                </Row>
                <div className="flow-last">
                    <Typography.Text>
                        <Typography.Text type="secondary">{t("systemPage.flow.lastInbound")}</Typography.Text> {lastText(flow?.lastInbound ?? null)}
                    </Typography.Text>
                    <Typography.Text>
                        <Typography.Text type="secondary">{t("systemPage.flow.lastOutbound")}</Typography.Text> {lastText(flow?.lastOutbound ?? null)}
                    </Typography.Text>
                </div>
                <Typography.Title level={5} style={{ marginTop: 0 }}>
                    {t("systemPage.flow.recent", { count: RECENT })}
                </Typography.Title>
                <Table<EdiDocument>
                    size="small"
                    rowKey="id"
                    columns={columns}
                    dataSource={flow?.recent ?? []}
                    loading={!flow}
                    pagination={false}
                    scroll={{ x: true }}
                    locale={{ emptyText: <Empty description={t("systemPage.flow.empty")} /> }}
                />
            </Card>
        </Space>
    );
}
