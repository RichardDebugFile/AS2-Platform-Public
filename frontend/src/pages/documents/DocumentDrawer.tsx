import { CheckCircleOutlined, CloseCircleOutlined } from "@ant-design/icons";
import { Alert, App, Button, Descriptions, Drawer, Popconfirm, Space, Table, Tabs, Tag, Typography } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { documentsApi, type EdiDocument, type StorageStatus } from "../../service/documentsApi";
import { describeElements, splitX12, type EdiSegment } from "../../utils/ediParser";
import { StatusTag } from "./documentTags";

interface Props {
    doc: EdiDocument | null;
    canDelete: boolean;
    onClose: () => void;
    onDeleted: () => void;
    onOpenInViewer: (text: string) => void;
}

/** Descarga el documento con sus bytes originales (el servidor lo entrega como ISO-8859-1). */
function download(text: string, fileName: string) {
    const bytes = Uint8Array.from(text, (c) => (c.codePointAt(0) ?? 0) & 0xff);
    const url = URL.createObjectURL(new Blob([bytes], { type: "application/edi-x12" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
}

function Presence({ ok, yes, no }: Readonly<{ ok: boolean; yes: string; no: string }>) {
    return ok ? (
        <Tag icon={<CheckCircleOutlined />} color="green">
            {yes}
        </Tag>
    ) : (
        <Tag icon={<CloseCircleOutlined />} color="red">
            {no}
        </Tag>
    );
}

/** Detalle de un documento del repositorio: resumen, contenido por segmentos y donde esta guardado. */
export default function DocumentDrawer({ doc, canDelete, onClose, onDeleted, onOpenInViewer }: Readonly<Props>) {
    const { t, i18n } = useTranslation("documents");
    const { message } = App.useApp();
    const [content, setContent] = useState<string | null>(null);
    const [storage, setStorage] = useState<StorageStatus | null>(null);
    const [error, setError] = useState<string | null>(null);

    // El panel monta un cajon nuevo por documento (key): el estado empieza vacio y solo se llena aqui.
    useEffect(() => {
        if (!doc) return;
        let current = true;
        void Promise.all([documentsApi.payload(doc.id), documentsApi.storageStatus(doc.id)]).then(([p, s]) => {
            if (!current) return;
            if (p.ok) setContent(String(p.data));
            if (s.ok) setStorage(s.data);
            if (!p.ok || !s.ok) setError(t("repository.detail.loadError"));
        });
        return () => {
            current = false;
        };
    }, [doc, t]);

    if (!doc) return null;

    const f = (k: string) => t(`repository.detail.fields.${k}`);
    const when = (iso: string) =>
        new Intl.DateTimeFormat(i18n.language, { dateStyle: "medium", timeStyle: "medium" }).format(new Date(iso));
    const segments = content === null ? [] : splitX12(content).segments;

    const remove = async () => {
        const r = await documentsApi.remove(doc.id);
        if (!r.ok) {
            message.error(r.error.message);
            return;
        }
        message.success(t("repository.detail.deleted"));
        onDeleted();
    };

    const summary = (
        <Descriptions size="small" column={1} bordered>
            <Descriptions.Item label={f("messageId")}>
                <span className="mono-wrap">{doc.messageId}</span>
            </Descriptions.Item>
            <Descriptions.Item label={f("as2")}>
                {(doc.as2From ?? "—") + " → " + (doc.as2To ?? "—")}
            </Descriptions.Item>
            <Descriptions.Item label={f("controls")}>
                <span className="mono-text">
                    {`ISA13 ${doc.isaControl ?? "—"} GS06 ${doc.gsControl ?? "—"} ST02 ${doc.stControl ?? "—"}`}
                </span>
            </Descriptions.Item>
            <Descriptions.Item label={f("version")}>{doc.version}</Descriptions.Item>
            <Descriptions.Item label={f("size")}>{`${doc.sizeBytes} B`}</Descriptions.Item>
            <Descriptions.Item label={f("sha256")}>
                <span className="mono-wrap">{doc.sha256}</span>
            </Descriptions.Item>
            {doc.mic && (
                <Descriptions.Item label={f("mic")}>
                    <span className="mono-wrap">{`${doc.mic} (${doc.micAlgorithm ?? "—"})`}</span>
                </Descriptions.Item>
            )}
            {(doc.signingAlgorithm || doc.encryptionAlgorithm) && (
                <Descriptions.Item label={f("algorithms")}>
                    {`${doc.signingAlgorithm ?? "—"} / ${doc.encryptionAlgorithm ?? "—"}`}
                </Descriptions.Item>
            )}
            <Descriptions.Item label={f("validation")}>
                <Space direction="vertical" size={4}>
                    <StatusTag status={doc.status} />
                    {doc.validationDetail && <Typography.Text type="secondary">{doc.validationDetail}</Typography.Text>}
                </Space>
            </Descriptions.Item>
            <Descriptions.Item label={f("createdBy")}>{doc.createdBy ?? "—"}</Descriptions.Item>
            <Descriptions.Item label={f("createdAt")}>{when(doc.createdAt)}</Descriptions.Item>
        </Descriptions>
    );

    const segmentTable = (
        <Table<EdiSegment>
            size="small"
            rowKey="index"
            pagination={false}
            loading={content === null && !error}
            dataSource={segments}
            columns={[
                { title: t("viewer.columns.index"), dataIndex: "index", width: 48 },
                {
                    title: t("viewer.columns.segment"),
                    dataIndex: "id",
                    width: 90,
                    render: (id: string) => <Tag className="mono-text">{id}</Tag>,
                },
                {
                    title: t("viewer.columns.name"),
                    key: "name",
                    width: 200,
                    render: (_: unknown, s: EdiSegment) => t(`segments.${s.id}`, { defaultValue: "—" }),
                },
                {
                    title: t("viewer.columns.content"),
                    key: "content",
                    render: (_: unknown, s: EdiSegment) => <span className="mono-wrap">{describeElements(s)}</span>,
                },
            ]}
        />
    );

    const storageView = (
        <Space direction="vertical" style={{ width: "100%" }}>
            <Descriptions size="small" column={1} bordered>
                <Descriptions.Item label={t("repository.detail.storageRows.database")}>
                    <Presence ok={storage?.database ?? true} yes={t("repository.detail.present")} no={t("repository.detail.missing")} />
                </Descriptions.Item>
                <Descriptions.Item label={t("repository.detail.storageRows.repositoryFile")}>
                    {storage && (
                        <Presence ok={storage.repositoryFile} yes={t("repository.detail.present")} no={t("repository.detail.missing")} />
                    )}
                </Descriptions.Item>
                <Descriptions.Item label={t("repository.detail.storageRows.partnerCopy")}>
                    {storage && (
                        <Presence ok={storage.partnerCopy} yes={t("repository.detail.present")} no={t("repository.detail.missing")} />
                    )}
                </Descriptions.Item>
                <Descriptions.Item label={f("fileName")}>
                    <span className="mono-wrap">{doc.fileName ?? "—"}</span>
                </Descriptions.Item>
            </Descriptions>
            {doc.partnerCopyStatus === "PENDING" && (
                <Alert type="warning" showIcon message={t("repository.detail.pendingHelp")} />
            )}
        </Space>
    );

    return (
        <Drawer
            open
            width="min(920px, 100vw)"
            onClose={onClose}
            title={t("repository.detail.title", { type: doc.typeCode, partner: doc.partnerAs2Name })}
            extra={
                <Space wrap>
                    <Button disabled={content === null} onClick={() => content !== null && download(content, doc.fileName ?? "documento.edi")}>
                        {t("repository.detail.download")}
                    </Button>
                    <Button disabled={content === null} onClick={() => content !== null && onOpenInViewer(content)}>
                        {t("repository.detail.openInViewer")}
                    </Button>
                    {canDelete && (
                        <Popconfirm title={t("repository.detail.deleteConfirm")} onConfirm={() => void remove()}>
                            <Button danger>{t("repository.detail.delete")}</Button>
                        </Popconfirm>
                    )}
                </Space>
            }
        >
            {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
            <Tabs
                items={[
                    { key: "summary", label: t("repository.detail.summary"), children: summary },
                    { key: "content", label: t("repository.detail.content"), children: segmentTable },
                    { key: "storage", label: t("repository.detail.storage"), children: storageView },
                ]}
            />
        </Drawer>
    );
}
