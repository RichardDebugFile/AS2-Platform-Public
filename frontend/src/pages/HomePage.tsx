import { CheckCircleOutlined, CloseCircleOutlined, FileTextOutlined, ReloadOutlined, RiseOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, DatePicker, Empty, Row, Select, Space, Statistic, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";
import type { DeniedState } from "../routes/RequireRole";
import { dashboardApi, type Dashboard, type DashboardFilter, type PartnerTraffic } from "../service/dashboardApi";
import type { ApiResponse } from "../service/dataService";
import { DIRECTIONS, type Direction } from "../service/documentsApi";
import { useAuthStore } from "../store/authStore";
import { usePartnersStore } from "../store/partnersStore";
import { color } from "../theme";
import OutcomeBar from "./home/OutcomeBar";
import SystemStatusCard from "./home/SystemStatusCard";

/** La biblioteca de graficos va en su propio archivo: solo se descarga al abrir Inicio. */
const TrafficByDayChart = lazy(() => import("./home/TrafficByDayChart"));

/** Dias que muestra el tablero al abrirse (el de hoy incluido). */
export const DEFAULT_DAYS = 7;

type Range = [Dayjs, Dayjs];

interface Filters {
    range: Range;
    direction?: Direction;
    partner?: string;
}

const defaultRange = (): Range => [dayjs().subtract(DEFAULT_DAYS - 1, "day").startOf("day"), dayjs().startOf("day")];

function toApi(f: Filters): DashboardFilter {
    return {
        from: f.range[0].format("YYYY-MM-DD"),
        to: f.range[1].format("YYYY-MM-DD"),
        direction: f.direction,
        partner: f.partner,
    };
}

/** Inicio (HU-20, AS2-99): cifras del rango, resultado, trafico por dia y por socio, y salud del sistema. */
export default function HomePage() {
    const { t, i18n } = useTranslation("common");
    const { t: td } = useTranslation("documents");
    const user = useAuthStore((s) => s.user);
    const roles = (user?.roles ?? []).map((r) => t(`roles.${r}`)).join(", ");
    const denied = (useLocation().state as DeniedState | null)?.denied;
    const partners = usePartnersStore((s) => s.partners);
    const fetchPartners = usePartnersStore((s) => s.fetchPartners);

    const [filters, setFilters] = useState<Filters>(() => ({ range: defaultRange() }));
    const [data, setData] = useState<Dashboard | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

    const apply = useCallback((r: ApiResponse<Dashboard>) => {
        setLoading(false);
        if (r.ok) {
            setData(r.data);
            setError(null);
        } else {
            setError(r.error.message);
        }
    }, []);

    const load = (f: Filters) => {
        setFilters(f);
        setLoading(true);
        void dashboardApi.dashboard(toApi(f)).then(apply);
    };

    useEffect(() => {
        void dashboardApi.dashboard(toApi({ range: defaultRange() })).then(apply);
        void fetchPartners(false);
    }, [apply, fetchPartners]);

    const nf = useMemo(() => new Intl.NumberFormat(i18n.language), [i18n.language]);
    const partnerOptions = useMemo(
        () => [...new Set((Array.isArray(partners) ? partners : []).map((p) => p.as2Name))].sort((a, b) => a.localeCompare(b)).map((n) => ({ value: n, label: n })),
        [partners],
    );

    const columns: ColumnsType<PartnerTraffic> = [
        { title: t("home.byPartner.partner"), dataIndex: "partner", render: (v: string) => <span className="nowrap">{v}</span> },
        { title: t("home.byPartner.inbound"), dataIndex: "inbound", align: "right", render: (v: number) => nf.format(v) },
        { title: t("home.byPartner.outbound"), dataIndex: "outbound", align: "right", render: (v: number) => nf.format(v) },
        { title: t("home.byPartner.ok"), dataIndex: "ok", align: "right", render: (v: number) => nf.format(v) },
        { title: t("home.byPartner.failed"), dataIndex: "failed", align: "right", render: (v: number) => nf.format(v) },
        { title: t("home.byPartner.total"), dataIndex: "total", align: "right", render: (v: number) => <strong>{nf.format(v)}</strong> },
    ];

    const empty = !loading && !data?.total;

    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            {denied && <Alert type="warning" showIcon closable message={t("home.denied", { path: denied })} />}
            <div>
                <Typography.Title level={3} style={{ marginTop: 0, marginBottom: 4 }}>
                    {t("home.welcome", { name: user?.username ?? "" })}
                </Typography.Title>
                <Typography.Text type="secondary">
                    {t("home.intro")} {t("home.role", { roles })}
                </Typography.Text>
            </div>

            <Card size="small">
                <Space wrap size="middle" align="end">
                    <div>
                        <label className="filter-label" htmlFor="home-range">
                            {t("home.filters.range")}
                        </label>
                        <DatePicker.RangePicker
                            id="home-range"
                            value={filters.range}
                            allowClear={false}
                            disabledDate={(d) => d.isAfter(dayjs(), "day")}
                            onChange={(v) => {
                                if (v?.[0] && v[1]) load({ ...filters, range: [v[0], v[1]] });
                            }}
                        />
                    </div>
                    <div>
                        <label className="filter-label" htmlFor="home-direction">
                            {t("home.filters.direction")}
                        </label>
                        <Select<Direction>
                            id="home-direction"
                            allowClear
                            placeholder={t("home.filters.all")}
                            style={{ width: 150 }}
                            value={filters.direction}
                            options={DIRECTIONS.map((d) => ({ value: d, label: td(`repository.direction.${d}`) }))}
                            onChange={(v) => load({ ...filters, direction: v })}
                        />
                    </div>
                    <div>
                        <label className="filter-label" htmlFor="home-partner">
                            {t("home.filters.partner")}
                        </label>
                        <Select<string>
                            id="home-partner"
                            allowClear
                            showSearch
                            placeholder={t("home.filters.all")}
                            style={{ width: 200 }}
                            value={filters.partner}
                            options={partnerOptions}
                            onChange={(v) => load({ ...filters, partner: v })}
                        />
                    </div>
                    <Space>
                        <Button icon={<ReloadOutlined />} onClick={() => load(filters)} loading={loading}>
                            {t("home.filters.refresh")}
                        </Button>
                        <Button onClick={() => load({ range: defaultRange() })}>{t("home.filters.clear")}</Button>
                    </Space>
                </Space>
            </Card>

            {error && <Alert type="error" showIcon message={t("home.error", { message: error })} />}

            <Row gutter={[12, 12]}>
                <Col xs={12} lg={6}>
                    <Card size="small" className="kpi-card">
                        <Statistic
                            title={t("home.kpi.total")}
                            value={data?.total ?? 0}
                            loading={loading && !data}
                            formatter={(v) => nf.format(Number(v))}
                            prefix={<FileTextOutlined style={{ color: color.primary }} />}
                        />
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {t("home.kpi.detail", { inProgress: data?.inProgress ?? 0, duplicates: data?.duplicates ?? 0 })}
                        </Typography.Text>
                    </Card>
                </Col>
                <Col xs={12} lg={6}>
                    <Card size="small" className="kpi-card">
                        <Statistic
                            title={t("home.kpi.ok")}
                            value={data?.ok ?? 0}
                            loading={loading && !data}
                            formatter={(v) => nf.format(Number(v))}
                            prefix={<CheckCircleOutlined style={{ color: color.success }} />}
                        />
                    </Card>
                </Col>
                <Col xs={12} lg={6}>
                    <Card size="small" className="kpi-card">
                        <Statistic
                            title={t("home.kpi.failed")}
                            value={data?.failed ?? 0}
                            loading={loading && !data}
                            formatter={(v) => nf.format(Number(v))}
                            prefix={<CloseCircleOutlined style={{ color: color.error }} />}
                        />
                    </Card>
                </Col>
                <Col xs={12} lg={6}>
                    <Card size="small" className="kpi-card">
                        <Statistic
                            title={t("home.kpi.successRate")}
                            value={data?.successRate ?? "—"}
                            loading={loading && !data}
                            formatter={(v) => (typeof v === "number" ? nf.format(v) : v)}
                            suffix={data?.successRate == null ? undefined : "%"}
                            prefix={<RiseOutlined style={{ color: color.textSecondary }} />}
                        />
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {data?.successRate == null ? t("home.kpi.noRate") : t("home.kpi.rateHint")}
                        </Typography.Text>
                    </Card>
                </Col>
            </Row>

            <Row gutter={[12, 12]}>
                <Col xs={24} xl={16}>
                    <Card size="small" title={t("home.byDay.title")}>
                        {empty ? <Empty description={t("home.empty")} /> : (
                            <Suspense fallback={<div className="chart-box" />}>
                                <TrafficByDayChart days={data?.byDay ?? []} />
                            </Suspense>
                        )}
                    </Card>
                </Col>
                <Col xs={24} xl={8}>
                    <Card size="small" title={t("home.outcome.title")}>
                        {data && !empty ? <OutcomeBar data={data} /> : <Empty description={t("home.empty")} />}
                    </Card>
                </Col>
            </Row>

            <Row gutter={[12, 12]}>
                <Col xs={24} xl={16}>
                    <Card size="small" title={t("home.byPartner.title")}>
                        <Table<PartnerTraffic>
                            size="small"
                            rowKey="partner"
                            columns={columns}
                            dataSource={data?.byPartner ?? []}
                            loading={loading}
                            pagination={{ pageSize: 8, hideOnSinglePage: true }}
                            locale={{ emptyText: <Empty description={t("home.empty")} /> }}
                            scroll={{ x: true }}
                        />
                    </Card>
                </Col>
                <Col xs={24} xl={8}>
                    <SystemStatusCard />
                </Col>
            </Row>
        </Space>
    );
}
