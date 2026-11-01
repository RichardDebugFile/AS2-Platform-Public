import { PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Card, Col, DatePicker, Empty, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
    DIRECTIONS,
    DOCUMENT_STATUSES,
    FAILED_STATUSES,
    documentsApi,
    type Direction,
    type DocumentFilter,
    type DocumentStats,
    type DocumentStatus,
    type EdiDocument,
    type Page,
} from "../../service/documentsApi";
import type { ApiResponse } from "../../service/dataService";
import { X12_TYPES } from "../../service/x12Api";
import DocumentDrawer from "./DocumentDrawer";
import { DirectionTag, StatusTag } from "./documentTags";
import IngestModal from "./IngestModal";

interface Props {
    canEdit: boolean;
    canDelete: boolean;
    onOpenInViewer: (text: string) => void;
    /** Documento que llega del generador para registrarlo. */
    pending: { content: string; direction: Direction } | null;
    onPendingHandled: () => void;
}

interface FilterForm {
    partner?: string;
    direction?: Direction;
    type?: string;
    status?: DocumentStatus;
    messageId?: string;
    isa?: string;
    range?: [Dayjs | null, Dayjs | null] | null;
}

function toFilter(v: FilterForm): DocumentFilter {
    const { range, ...rest } = v;
    return {
        ...rest,
        from: range?.[0]?.format("YYYY-MM-DD"),
        to: range?.[1]?.format("YYYY-MM-DD"),
    };
}

interface Fetched {
    page: ApiResponse<Page<EdiDocument>>;
    stats: DocumentStats | null;
    current: number;
    pageSize: number;
}

async function fetchPage(f: DocumentFilter, current: number, pageSize: number): Promise<Fetched> {
    const [page, stats] = await Promise.all([documentsApi.search(f, current - 1, pageSize), documentsApi.stats()]);
    return { page, stats: stats.ok ? stats.data : null, current, pageSize };
}

function sum(values: Record<string, number> | undefined, keep: (k: string) => boolean = () => true): number {
    return Object.entries(values ?? {})
        .filter(([k]) => keep(k))
        .reduce((acc, [, n]) => acc + n, 0);
}

/** Repositorio de documentos (HU-14): estadisticas, busqueda con filtros, detalle y registro manual. */
export default function RepositoryPanel({ canEdit, canDelete, onOpenInViewer, pending, onPendingHandled }: Readonly<Props>) {
    const { t, i18n } = useTranslation("documents");
    const [form] = Form.useForm<FilterForm>();
    const [filter, setFilter] = useState<DocumentFilter>({});
    const [page, setPage] = useState({ current: 1, pageSize: 20, total: 0 });
    const [rows, setRows] = useState<EdiDocument[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [stats, setStats] = useState<DocumentStats | null>(null);
    const [selected, setSelected] = useState<EdiDocument | null>(null);
    const [uploading, setUploading] = useState(false);

    const apply = useCallback((r: Fetched) => {
        setLoading(false);
        if (r.stats) setStats(r.stats);
        if (!r.page.ok) {
            setError(r.page.error.message);
            return;
        }
        setError(null);
        setRows(r.page.data.content);
        setPage({ current: r.current, pageSize: r.pageSize, total: r.page.data.totalElements });
    }, []);

    const load = (f: DocumentFilter, current: number, pageSize: number) => {
        setLoading(true);
        void fetchPage(f, current, pageSize).then(apply);
    };

    useEffect(() => {
        void fetchPage({}, 1, 20).then(apply);
    }, [apply]);

    const reload = () => load(filter, page.current, page.pageSize);
    const when = (iso: string) =>
        new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "medium" }).format(new Date(iso));

    const columns: ColumnsType<EdiDocument> = [
        { title: t("repository.columns.createdAt"), dataIndex: "createdAt", width: 170, render: (v: string) => <span className="nowrap">{when(v)}</span> },
        { title: t("repository.columns.direction"), dataIndex: "direction", render: (v: Direction) => <DirectionTag direction={v} /> },
        { title: t("repository.columns.partner"), dataIndex: "partnerAs2Name", render: (v: string) => <span className="nowrap">{v}</span> },
        { title: t("repository.columns.type"), dataIndex: "typeCode", render: (v: string) => <Tag className="mono-text">{v}</Tag> },
        { title: t("repository.columns.isa"), dataIndex: "isaControl", render: (v: string | null) => <span className="mono-text">{v ?? "—"}</span> },
        { title: t("repository.columns.status"), dataIndex: "status", render: (v: DocumentStatus) => <StatusTag status={v} /> },
        {
            title: t("repository.columns.copy"),
            dataIndex: "partnerCopyStatus",
            render: (v: EdiDocument["partnerCopyStatus"]) =>
                v ? <Tag color={v === "COPIED" ? "green" : "orange"}>{t(`repository.copy.${v}`)}</Tag> : "—",
        },
        { title: t("repository.columns.size"), dataIndex: "sizeBytes", align: "right", render: (v: number) => `${v} B` },
    ];

    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                {t("repository.intro")}
            </Typography.Paragraph>

            <Row gutter={[12, 12]}>
                <Col xs={12} md={6}>
                    <Card size="small">
                        <Statistic title={t("repository.stats.total")} value={stats?.total ?? 0} />
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card size="small">
                        <Statistic
                            title={t("repository.stats.lastDays")}
                            value={(stats?.lastDays ?? []).reduce((acc, d) => acc + d.count, 0)}
                        />
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card size="small">
                        <Statistic
                            title={t("repository.stats.failed")}
                            value={sum(stats?.byStatus, (k) => FAILED_STATUSES.has(k as DocumentStatus))}
                        />
                    </Card>
                </Col>
                <Col xs={12} md={6}>
                    <Card size="small">
                        <Statistic
                            title={t("repository.stats.inOut")}
                            value={`${stats?.byDirection.INBOUND ?? 0} / ${stats?.byDirection.OUTBOUND ?? 0}`}
                        />
                    </Card>
                </Col>
            </Row>

            <Form<FilterForm>
                form={form}
                layout="inline"
                style={{ rowGap: 8 }}
                onFinish={(v) => {
                    const f = toFilter(v);
                    setFilter(f);
                    load(f, 1, page.pageSize);
                }}
            >
                <Form.Item name="partner">
                    <Input allowClear placeholder={t("repository.filters.partner")} style={{ width: 160 }} />
                </Form.Item>
                <Form.Item name="direction">
                    <Select
                        allowClear
                        placeholder={t("repository.filters.direction")}
                        style={{ width: 140 }}
                        options={DIRECTIONS.map((d) => ({ value: d, label: t(`repository.direction.${d}`) }))}
                    />
                </Form.Item>
                <Form.Item name="type">
                    <Select
                        allowClear
                        placeholder={t("repository.filters.type")}
                        style={{ width: 110 }}
                        options={X12_TYPES.map((v) => ({ value: v, label: v }))}
                    />
                </Form.Item>
                <Form.Item name="status">
                    <Select
                        allowClear
                        placeholder={t("repository.filters.status")}
                        style={{ width: 170 }}
                        options={DOCUMENT_STATUSES.map((s) => ({ value: s, label: t(`repository.status.${s}`) }))}
                    />
                </Form.Item>
                <Form.Item name="messageId">
                    <Input allowClear placeholder={t("repository.filters.messageId")} style={{ width: 200 }} />
                </Form.Item>
                <Form.Item name="isa">
                    <Input allowClear placeholder={t("repository.filters.isa")} style={{ width: 130 }} />
                </Form.Item>
                <Form.Item name="range">
                    <DatePicker.RangePicker
                        allowEmpty={[true, true]}
                        placeholder={[t("repository.filters.from"), t("repository.filters.to")]}
                    />
                </Form.Item>
                <Space wrap>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                        {t("repository.filters.search")}
                    </Button>
                    <Button
                        onClick={() => {
                            form.resetFields();
                            setFilter({});
                            load({}, 1, page.pageSize);
                        }}
                    >
                        {t("repository.filters.clear")}
                    </Button>
                    {canEdit && (
                        <Button icon={<PlusOutlined />} onClick={() => setUploading(true)}>
                            {t("repository.upload.open")}
                        </Button>
                    )}
                </Space>
            </Form>

            {error && <Typography.Text type="danger">{error}</Typography.Text>}
            <Table<EdiDocument>
                rowKey="id"
                columns={columns}
                dataSource={rows}
                loading={loading}
                scroll={{ x: true }}
                locale={{ emptyText: <Empty description={t("repository.empty")} /> }}
                onRow={(r) => ({ onClick: () => setSelected(r), style: { cursor: "pointer" } })}
                pagination={{
                    current: page.current,
                    pageSize: page.pageSize,
                    total: page.total,
                    showSizeChanger: true,
                    onChange: (current, size) => load(filter, current, size),
                }}
            />

            <DocumentDrawer
                key={selected?.id ?? "none"}
                doc={selected}
                canDelete={canDelete}
                onClose={() => setSelected(null)}
                onOpenInViewer={onOpenInViewer}
                onDeleted={() => {
                    setSelected(null);
                    reload();
                }}
            />
            {canEdit && (
                <IngestModal
                    open={uploading || pending !== null}
                    initial={pending}
                    onClose={() => {
                        setUploading(false);
                        onPendingHandled();
                    }}
                    onSaved={(doc) => {
                        setUploading(false);
                        onPendingHandled();
                        reload();
                        setSelected(doc);
                    }}
                />
            )}
        </Space>
    );
}
