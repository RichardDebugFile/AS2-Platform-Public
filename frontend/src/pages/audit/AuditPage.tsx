import { DownloadOutlined, SearchOutlined } from "@ant-design/icons";
import { App, Button, DatePicker, Descriptions, Empty, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useAuditStore, type AuditEvent } from "../../store/auditStore";
import { toFilters, type FilterForm } from "./auditFilters";

/** Texto de un valor anterior o nuevo en la tabla de cambios. */
function shown(v: unknown): string {
    if (v === null || v === undefined || v === "") return "—";
    return typeof v === "object" ? JSON.stringify(v) : String(v);
}

/** Descarga un texto como archivo en el navegador. */
function download(text: string, filename: string) {
    const url = URL.createObjectURL(new Blob([text], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
}

/** Bitacora de auditoria: consulta con filtros y exportacion. Solo lectura para todos los roles. */
export default function AuditPage() {
    const { t, i18n } = useTranslation("audit");
    const { message } = App.useApp();
    const { events, loading, error, pagination, eventTypes, fetchEvents, fetchEventTypes, exportCsv } = useAuditStore();
    const [form] = Form.useForm<FilterForm>();

    useEffect(() => {
        void fetchEvents({}, 0, 20);
        void fetchEventTypes();
    }, [fetchEvents, fetchEventTypes]);

    const typeLabel = (type: string) => t(`types.${type}`, { defaultValue: type });
    const when = (iso: string) =>
        new Intl.DateTimeFormat(i18n.language, { dateStyle: "short", timeStyle: "medium" }).format(new Date(iso));

    const onExport = async () => {
        const csv = await exportCsv();
        if (csv === null) return void message.error(t("exportError"));
        download(csv, `bitacora-${new Date().toISOString().slice(0, 10)}.csv`);
    };

    const columns: ColumnsType<AuditEvent> = [
        { title: t("columns.occurredAt"), dataIndex: "occurredAt", width: 180, render: (v: string) => when(v) },
        { title: t("columns.eventType"), dataIndex: "eventType", render: (v: string) => <Tag>{typeLabel(v)}</Tag> },
        { title: t("columns.partner"), dataIndex: "partnerAs2Name", render: (v?: string | null) => v ?? "—" },
        { title: t("columns.actor"), dataIndex: "actor" },
        {
            title: t("columns.status"),
            dataIndex: "status",
            render: (v: AuditEvent["status"]) => <Tag color={v === "SUCCESS" ? "green" : "red"}>{t(`status.${v}`)}</Tag>,
        },
        { title: t("columns.detail"), dataIndex: "detail", ellipsis: true, render: (v?: string | null) => v ?? "" },
    ];

    const expanded = (e: AuditEvent) => {
        const changes = Object.entries(e.changes ?? {});
        return (
            <Space direction="vertical" style={{ width: "100%" }}>
                <Descriptions size="small" column={{ xs: 1, md: 3 }}>
                    <Descriptions.Item label={t("columns.source")}>{e.source}</Descriptions.Item>
                    <Descriptions.Item label={t("columns.messageId")}>{e.messageId ?? "—"}</Descriptions.Item>
                    <Descriptions.Item label={t("columns.eventType")}>{e.eventType}</Descriptions.Item>
                </Descriptions>
                {changes.length === 0 ? (
                    <Typography.Text type="secondary">{t("noChanges")}</Typography.Text>
                ) : (
                    <Table
                        size="small"
                        pagination={false}
                        rowKey="field"
                        dataSource={changes.map(([field, c]) => ({ field, from: shown(c?.from), to: shown(c?.to) }))}
                        columns={[
                            { title: t("changes.field"), dataIndex: "field" },
                            { title: t("changes.from"), dataIndex: "from" },
                            { title: t("changes.to"), dataIndex: "to" },
                        ]}
                    />
                )}
            </Space>
        );
    };

    return (
        <>
            <Typography.Title level={3}>{t("title")}</Typography.Title>
            <Form<FilterForm>
                form={form}
                layout="inline"
                style={{ marginBottom: 16, rowGap: 8 }}
                onFinish={(v) => void fetchEvents(toFilters(v), 0, pagination.pageSize)}
            >
                <Form.Item name="partnerAs2Name">
                    <Input allowClear placeholder={t("filters.partner")} style={{ width: 180 }} />
                </Form.Item>
                <Form.Item name="eventType">
                    <Select
                        allowClear
                        showSearch
                        placeholder={t("filters.eventType")}
                        style={{ width: 220 }}
                        options={eventTypes.map((v) => ({ value: v, label: typeLabel(v) }))}
                    />
                </Form.Item>
                <Form.Item name="status">
                    <Select
                        allowClear
                        placeholder={t("filters.status")}
                        style={{ width: 140 }}
                        options={(["SUCCESS", "FAILURE"] as const).map((v) => ({ value: v, label: t(`status.${v}`) }))}
                    />
                </Form.Item>
                <Form.Item name="actor">
                    <Input allowClear placeholder={t("filters.actor")} style={{ width: 150 }} />
                </Form.Item>
                <Form.Item name="messageId">
                    <Input allowClear placeholder={t("filters.messageId")} style={{ width: 220 }} />
                </Form.Item>
                <Form.Item name="range">
                    <DatePicker.RangePicker allowEmpty={[true, true]} placeholder={[t("filters.from"), t("filters.to")]} />
                </Form.Item>
                <Space>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
                        {t("filters.search")}
                    </Button>
                    <Button
                        onClick={() => {
                            form.resetFields();
                            void fetchEvents({}, 0, pagination.pageSize);
                        }}
                    >
                        {t("filters.clear")}
                    </Button>
                    <Button icon={<DownloadOutlined />} onClick={() => void onExport()}>
                        {t("export")}
                    </Button>
                </Space>
            </Form>
            {error && <Typography.Text type="danger">{error}</Typography.Text>}
            <Table<AuditEvent>
                rowKey="id"
                columns={columns}
                dataSource={events}
                loading={loading}
                locale={{ emptyText: <Empty description={t("empty")} /> }}
                expandable={{ expandedRowRender: expanded }}
                scroll={{ x: true }}
                pagination={{
                    current: pagination.current,
                    pageSize: pagination.pageSize,
                    total: pagination.total,
                    showSizeChanger: true,
                    onChange: (page, size) => void fetchEvents(undefined, page - 1, size),
                }}
            />
        </>
    );
}
