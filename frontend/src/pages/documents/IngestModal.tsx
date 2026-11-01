import { UploadOutlined } from "@ant-design/icons";
import { App, Button, Form, Input, Modal, Radio, Select, Upload } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { DIRECTIONS, documentsApi, type Direction, type EdiDocument } from "../../service/documentsApi";
import { usePartnersStore } from "../../store/partnersStore";

interface Props {
    open: boolean;
    /** Documento con el que se abre (p. ej. el recien generado), y su direccion. */
    initial?: { content: string; direction: Direction } | null;
    onClose: () => void;
    onSaved: (doc: EdiDocument) => void;
}

interface FormValues {
    partner: string;
    direction: Direction;
    messageId?: string;
    content: string;
}

/**
 * Registro manual de un documento en el repositorio (HU-14): el mismo camino que usara el motor AS2,
 * util para cargar documentos de prueba o recibidos por otro medio.
 */
export default function IngestModal({ open, initial, onClose, onSaved }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    const { message } = App.useApp();
    const [form] = Form.useForm<FormValues>();
    const [busy, setBusy] = useState(false);
    const partners = usePartnersStore((s) => s.partners);
    const fetchPartners = usePartnersStore((s) => s.fetchPartners);

    useEffect(() => {
        if (!open) return;
        if (partners.length === 0) void fetchPartners();
        form.setFieldsValue({ direction: initial?.direction ?? "INBOUND", content: initial?.content ?? "" });
    }, [open, initial, partners.length, fetchPartners, form]);

    const required = [{ required: true, message: t("repository.upload.required") }];

    const submit = async (v: FormValues) => {
        setBusy(true);
        const r = await documentsApi.ingest({
            partner: v.partner,
            direction: v.direction,
            content: v.content,
            messageId: v.messageId?.trim() || undefined,
        });
        setBusy(false);
        if (!r.ok) return void message.error(r.error.message);
        if (r.data.outcome === "DUPLICATE") {
            message.warning(t("repository.upload.duplicate"));
        } else {
            message.success(
                t("repository.upload.created", { status: t(`repository.status.${r.data.document.status}`) }),
            );
        }
        form.resetFields();
        onSaved(r.data.document);
    };

    return (
        <Modal
            open={open}
            title={t("repository.upload.title")}
            onCancel={onClose}
            okText={t("repository.upload.save")}
            confirmLoading={busy}
            onOk={() => form.submit()}
            width="min(760px, 100vw)"
            destroyOnHidden
        >
            <Form<FormValues> form={form} layout="vertical" onFinish={(v) => void submit(v)}>
                <Form.Item label={t("repository.upload.partner")} name="partner" rules={required}>
                    <Select
                        showSearch
                        aria-label={t("repository.upload.partner")}
                        options={partners.map((p) => ({ value: p.as2Name, label: `${p.as2Name} · ${p.partnerName ?? ""}` }))}
                    />
                </Form.Item>
                <Form.Item label={t("repository.upload.direction")} name="direction" rules={required}>
                    <Radio.Group
                        optionType="button"
                        options={DIRECTIONS.map((d) => ({ value: d, label: t(`repository.direction.${d}`) }))}
                    />
                </Form.Item>
                <Form.Item
                    label={t("repository.upload.messageId")}
                    name="messageId"
                    extra={t("repository.upload.messageIdHelp")}
                >
                    <Input className="mono-text" placeholder="<id@example.com>" />
                </Form.Item>
                <Form.Item label={t("repository.upload.content")} name="content" rules={required}>
                    <Input.TextArea aria-label={t("repository.upload.content")} className="mono-area" rows={8} />
                </Form.Item>
                <Upload
                    accept=".edi,.x12,.txt"
                    showUploadList={false}
                    beforeUpload={(file) => {
                        void file.text().then((text) => form.setFieldValue("content", text));
                        return false;
                    }}
                >
                    <Button icon={<UploadOutlined />}>{t("repository.upload.file")}</Button>
                </Upload>
            </Form>
        </Modal>
    );
}
