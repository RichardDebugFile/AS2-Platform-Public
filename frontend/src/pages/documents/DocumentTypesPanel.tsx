import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import { Alert, App, Button, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { ApiResponse } from "../../service/dataService";
import {
    DOCUMENT_STANDARDS,
    documentTypesApi,
    typeLabel,
    type DocumentStandard,
    type DocumentType,
    type DocumentTypeRequest,
} from "../../service/documentTypesApi";

interface Props {
    /** ADMIN u OPERATOR: registran, modifican y borran tipos. */
    canEdit: boolean;
}

interface FormValues {
    standard: DocumentStandard;
    code: string;
    version: string;
    name: string;
    description?: string;
    responseTypeIds?: string[];
}

/** Editando: null = alta; un tipo = cambio; undefined = cerrado. */
type Editing = DocumentType | null | undefined;

/**
 * Catalogo de tipos de documento EDI (HU-13): cada tipo es unico por estandar, codigo y version y
 * tiene sus respuestas esperadas (850 -> 855, 856, 810). Un tipo que usa algun socio no se borra.
 */
export default function DocumentTypesPanel({ canEdit }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    const { message } = App.useApp();
    const [types, setTypes] = useState<DocumentType[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<Editing>(undefined);
    const [deleting, setDeleting] = useState<DocumentType | null>(null);
    const [busy, setBusy] = useState(false);
    const [form] = Form.useForm<FormValues>();

    const apply = useCallback((res: ApiResponse<DocumentType[]>) => {
        setLoading(false);
        if (res.ok) {
            setTypes(Array.isArray(res.data) ? res.data : []);
            setError(null);
        } else {
            setError(res.error.message);
        }
    }, []);

    const load = () => {
        setLoading(true);
        void documentTypesApi.list().then(apply);
    };

    useEffect(() => {
        void documentTypesApi.list().then(apply);
    }, [apply]);

    const open = (type: DocumentType | null) => {
        setEditing(type);
        form.setFieldsValue(
            type
                ? {
                      standard: type.standard,
                      code: type.code,
                      version: type.version,
                      name: type.name,
                      description: type.description ?? undefined,
                      responseTypeIds: type.responses.map((r) => r.id),
                  }
                : { standard: "X12", code: undefined, version: "4010", name: undefined, description: undefined, responseTypeIds: [] },
        );
    };

    const onSave = async (v: FormValues) => {
        const request: DocumentTypeRequest = {
            standard: v.standard,
            code: v.code.trim(),
            version: v.version.trim(),
            name: v.name.trim(),
            description: v.description?.trim() || undefined,
            responseTypeIds: v.responseTypeIds ?? [],
        };
        setBusy(true);
        const res = editing ? await documentTypesApi.update(editing.id, request) : await documentTypesApi.create(request);
        setBusy(false);
        if (!res.ok) {
            void message.error(res.error.message);
            return;
        }
        void message.success(t(editing ? "catalog.updated" : "catalog.created", { type: typeLabel(res.data) }));
        setEditing(undefined);
        load();
    };

    const onDelete = async () => {
        if (!deleting) return;
        setBusy(true);
        const res = await documentTypesApi.remove(deleting.id);
        setBusy(false);
        setDeleting(null);
        if (!res.ok) {
            void message.error(res.error.message);
            return;
        }
        void message.success(t("catalog.deleted", { type: typeLabel(deleting) }));
        load();
    };

    const columns: ColumnsType<DocumentType> = [
        {
            title: t("catalog.columns.type"),
            key: "type",
            render: (_, d) => <Tag color="blue">{typeLabel(d)}</Tag>,
        },
        { title: t("catalog.columns.name"), dataIndex: "name" },
        {
            title: t("catalog.columns.responses"),
            key: "responses",
            render: (_, d) =>
                d.responses.length === 0 ? (
                    "—"
                ) : (
                    <Space size={[4, 4]} wrap>
                        {d.responses.map((r) => (
                            <Tag key={r.id}>{typeLabel(r)}</Tag>
                        ))}
                    </Space>
                ),
        },
        { title: t("catalog.columns.partners"), dataIndex: "partnerCount", width: 90 },
        ...(canEdit
            ? [
                  {
                      title: t("catalog.columns.actions"),
                      key: "actions",
                      width: 110,
                      render: (_: unknown, d: DocumentType) => (
                          <Space>
                              <Button size="small" icon={<EditOutlined />} aria-label={t("catalog.edit")} onClick={() => open(d)} />
                              <Tooltip title={d.partnerCount > 0 ? t("catalog.inUse", { count: d.partnerCount }) : undefined}>
                                  <Button
                                      size="small"
                                      danger
                                      icon={<DeleteOutlined />}
                                      aria-label={t("catalog.delete")}
                                      disabled={d.partnerCount > 0}
                                      onClick={() => setDeleting(d)}
                                  />
                              </Tooltip>
                          </Space>
                      ),
                  },
              ]
            : []),
    ];

    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <div className="page-toolbar">
                <Typography.Text type="secondary">{t("catalog.intro")}</Typography.Text>
                <div className="page-toolbar-spacer" />
                {canEdit && (
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => open(null)}>
                        {t("catalog.new")}
                    </Button>
                )}
            </div>
            {error && <Alert type="error" showIcon message={t("catalog.loadError", { message: error })} />}
            <Table<DocumentType>
                rowKey="id"
                columns={columns}
                dataSource={types}
                loading={loading}
                pagination={false}
                scroll={{ x: true }}
                locale={{ emptyText: t("catalog.empty") }}
            />

            <Modal
                open={editing !== undefined}
                title={editing ? t("catalog.editTitle", { type: typeLabel(editing) }) : t("catalog.newTitle")}
                okText={t("catalog.save")}
                confirmLoading={busy}
                onOk={() => form.submit()}
                onCancel={() => setEditing(undefined)}
                forceRender
            >
                <Form<FormValues> form={form} layout="vertical" onFinish={(v) => void onSave(v)}>
                    <Space.Compact block>
                        <Form.Item name="standard" label={t("catalog.form.standard")} style={{ width: "34%" }}>
                            <Select options={DOCUMENT_STANDARDS.map((s) => ({ value: s, label: s }))} />
                        </Form.Item>
                        <Form.Item
                            name="code"
                            label={t("catalog.form.code")}
                            style={{ width: "33%" }}
                            rules={[{ required: true, whitespace: true, message: t("catalog.form.required") }]}
                        >
                            <Input maxLength={10} placeholder="850" />
                        </Form.Item>
                        <Form.Item
                            name="version"
                            label={t("catalog.form.version")}
                            style={{ width: "33%" }}
                            rules={[{ required: true, whitespace: true, message: t("catalog.form.required") }]}
                        >
                            <Input maxLength={10} placeholder="4010" />
                        </Form.Item>
                    </Space.Compact>
                    <Form.Item
                        name="name"
                        label={t("catalog.form.name")}
                        rules={[{ required: true, whitespace: true, message: t("catalog.form.required") }]}
                    >
                        <Input maxLength={120} />
                    </Form.Item>
                    <Form.Item name="description" label={t("catalog.form.description")}>
                        <Input.TextArea maxLength={500} autoSize={{ minRows: 2, maxRows: 4 }} />
                    </Form.Item>
                    <Form.Item name="responseTypeIds" label={t("catalog.form.responses")} extra={t("catalog.form.responsesHelp")}>
                        <Select
                            mode="multiple"
                            allowClear
                            optionFilterProp="label"
                            options={types
                                .filter((d) => d.id !== editing?.id)
                                .map((d) => ({ value: d.id, label: `${typeLabel(d)} · ${d.name}` }))}
                        />
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                open={deleting !== null}
                title={t("catalog.deleteTitle")}
                okText={t("catalog.delete")}
                okButtonProps={{ danger: true }}
                confirmLoading={busy}
                onOk={() => void onDelete()}
                onCancel={() => setDeleting(null)}
            >
                {deleting && t("catalog.deleteBody", { type: typeLabel(deleting) })}
            </Modal>
        </Space>
    );
}
