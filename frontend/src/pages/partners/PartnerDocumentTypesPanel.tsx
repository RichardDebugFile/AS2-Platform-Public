import { DeleteOutlined, PlusOutlined, SaveOutlined } from "@ant-design/icons";
import { Alert, App, Button, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { ApiResponse } from "../../service/dataService";
import {
    DOCUMENT_DIRECTIONS,
    documentTypesApi,
    formatFields,
    parseFields,
    typeLabel,
    type DocumentDirection,
    type DocumentType,
    type PartnerDocumentType,
} from "../../service/documentTypesApi";
import type { Partner } from "../../store/partnersStore";

interface Props {
    partner: Partner;
    /** ADMIN u OPERATOR con el socio activo. */
    canEdit: boolean;
}

/** Fila en edicion; los datos adicionales se escriben como "Nombre: valor" por linea. */
interface Row {
    key: string;
    documentTypeId?: string;
    direction: DocumentDirection;
    notes: string;
    fields: string;
}

let rowSeq = 0;
const newKey = () => `fila-${++rowSeq}`;

function toRows(list: PartnerDocumentType[]): Row[] {
    return list.map((a) => ({
        key: a.id,
        documentTypeId: a.documentType.id,
        direction: a.direction,
        notes: a.notes ?? "",
        fields: formatFields(a.extraFields),
    }));
}

/**
 * Tipos de documento que intercambia el socio (HU-13): de que tipo, en que direccion, con que datos
 * adicionales y que respuesta se espera de el segun el catalogo. Guardar reemplaza la lista completa.
 */
export default function PartnerDocumentTypesPanel({ partner, canEdit }: Readonly<Props>) {
    const { t } = useTranslation("partners");
    const { message } = App.useApp();
    const [catalog, setCatalog] = useState<DocumentType[]>([]);
    const [rows, setRows] = useState<Row[]>([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const apply = useCallback(
        ([types, assigned]: [ApiResponse<DocumentType[]>, ApiResponse<PartnerDocumentType[]>]) => {
            setLoading(false);
            if (types.ok) setCatalog(Array.isArray(types.data) ? types.data : []);
            if (assigned.ok) setRows(toRows(Array.isArray(assigned.data) ? assigned.data : []));
            const failed = [types, assigned].find((r) => !r.ok);
            setError(failed && !failed.ok ? failed.error.message : null);
        },
        [],
    );

    useEffect(() => {
        void Promise.all([documentTypesApi.list(), documentTypesApi.partnerTypes(partner.id)]).then(apply);
    }, [partner.id, apply]);

    const byId = useMemo(() => new Map(catalog.map((d) => [d.id, d])), [catalog]);

    const update = (key: string, change: Partial<Row>) =>
        setRows((current) => current.map((r) => (r.key === key ? { ...r, ...change } : r)));

    const onSave = async () => {
        if (rows.some((r) => !r.documentTypeId)) {
            void message.error(t("documentTypes.missingType"));
            return;
        }
        setSaving(true);
        const res = await documentTypesApi.savePartnerTypes(
            partner.id,
            rows.map((r) => ({
                documentTypeId: r.documentTypeId!,
                direction: r.direction,
                notes: r.notes.trim() || undefined,
                extraFields: parseFields(r.fields),
            })),
        );
        setSaving(false);
        if (!res.ok) {
            void message.error(res.error.message);
            return;
        }
        setRows(toRows(res.data));
        void message.success(t("documentTypes.saved"));
    };

    const responsesOf = (id?: string) => {
        const responses = id ? (byId.get(id)?.responses ?? []) : [];
        return responses.length === 0 ? (
            "—"
        ) : (
            <Space size={[4, 4]} wrap>
                {responses.map((r) => (
                    <Tag key={r.id}>{typeLabel(r)}</Tag>
                ))}
            </Space>
        );
    };

    const columns: ColumnsType<Row> = [
        {
            title: t("documentTypes.columns.type"),
            key: "type",
            width: 260,
            render: (_, r) =>
                canEdit ? (
                    <Select
                        aria-label={t("documentTypes.columns.type")}
                        style={{ width: "100%" }}
                        showSearch
                        optionFilterProp="label"
                        placeholder={t("documentTypes.pickType")}
                        value={r.documentTypeId}
                        onChange={(v: string) => update(r.key, { documentTypeId: v })}
                        options={catalog.map((d) => ({ value: d.id, label: `${typeLabel(d)} · ${d.name}` }))}
                    />
                ) : (
                    (() => {
                        const d = r.documentTypeId ? byId.get(r.documentTypeId) : undefined;
                        return d ? `${typeLabel(d)} · ${d.name}` : "—";
                    })()
                ),
        },
        {
            title: t("documentTypes.columns.direction"),
            key: "direction",
            width: 150,
            render: (_, r) =>
                canEdit ? (
                    <Select
                        aria-label={t("documentTypes.columns.direction")}
                        style={{ width: "100%" }}
                        value={r.direction}
                        onChange={(v: DocumentDirection) => update(r.key, { direction: v })}
                        options={DOCUMENT_DIRECTIONS.map((d) => ({ value: d, label: t(`documentTypes.direction.${d}`) }))}
                    />
                ) : (
                    <Tag color={r.direction === "INBOUND" ? "geekblue" : "purple"}>{t(`documentTypes.direction.${r.direction}`)}</Tag>
                ),
        },
        {
            title: t("documentTypes.columns.responses"),
            key: "responses",
            render: (_, r) => responsesOf(r.documentTypeId),
        },
        {
            title: t("documentTypes.columns.fields"),
            key: "fields",
            render: (_, r) =>
                canEdit ? (
                    <Input.TextArea
                        aria-label={t("documentTypes.columns.fields")}
                        autoSize={{ minRows: 1, maxRows: 4 }}
                        placeholder={t("documentTypes.fieldsPlaceholder")}
                        value={r.fields}
                        onChange={(e) => update(r.key, { fields: e.target.value })}
                    />
                ) : (
                    <span className="cell-wrap">{r.fields || "—"}</span>
                ),
        },
        {
            title: t("documentTypes.columns.notes"),
            key: "notes",
            render: (_, r) =>
                canEdit ? (
                    <Input
                        aria-label={t("documentTypes.columns.notes")}
                        maxLength={500}
                        value={r.notes}
                        onChange={(e) => update(r.key, { notes: e.target.value })}
                    />
                ) : (
                    r.notes || "—"
                ),
        },
        ...(canEdit
            ? [
                  {
                      key: "remove",
                      width: 50,
                      render: (_: unknown, r: Row) => (
                          <Button
                              size="small"
                              danger
                              icon={<DeleteOutlined />}
                              aria-label={t("documentTypes.remove")}
                              onClick={() => setRows((current) => current.filter((x) => x.key !== r.key))}
                          />
                      ),
                  },
              ]
            : []),
    ];

    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Typography.Text type="secondary">{t("documentTypes.intro")}</Typography.Text>
            {error && <Alert type="error" showIcon message={error} />}
            <Table<Row>
                rowKey="key"
                size="small"
                columns={columns}
                dataSource={rows}
                loading={loading}
                pagination={false}
                scroll={{ x: true }}
                locale={{ emptyText: t("documentTypes.empty") }}
            />
            {canEdit && (
                <Space>
                    <Button
                        icon={<PlusOutlined />}
                        onClick={() => setRows((current) => [...current, { key: newKey(), direction: "INBOUND", notes: "", fields: "" }])}
                    >
                        {t("documentTypes.add")}
                    </Button>
                    <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => void onSave()}>
                        {t("documentTypes.save")}
                    </Button>
                </Space>
            )}
        </Space>
    );
}
