import { DownloadOutlined, UploadOutlined } from "@ant-design/icons";
import { Alert, App, Button, Input, Modal, Select, Space, Table, Tag, Tooltip, Typography, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
    KEY_USAGES,
    formatFingerprint,
    useCertificatesStore,
    type Certificate,
    type KeyUsage,
    type Validity,
} from "../../store/certificatesStore";
import type { Partner } from "../../store/partnersStore";

interface Props {
    partner: Partner;
    /** ADMIN u OPERATOR: cargan, marcan preferente, activan y desactivan. */
    canEdit: boolean;
    /** Solo ADMIN elimina. */
    isAdmin: boolean;
}

const VALIDITY_COLOR: Record<Validity, string> = {
    VALID: "green",
    EXPIRING: "orange",
    EXPIRED: "red",
    NOT_YET_VALID: "blue",
};

const EMPTY: Certificate[] = [];

/** Pendiente de confirmar: desactivar o eliminar un certificado. */
type Pending = { action: "deactivate" | "remove"; certificate: Certificate } | null;

/**
 * Certificados X.509 del socio (HU-11) con doble compuerta: al cargar uno nuevo, el anterior del
 * mismo uso sigue activo como respaldo, para que el intercambio no se corte durante la rotacion.
 */
export default function CertificatesPanel({ partner, canEdit, isAdmin }: Readonly<Props>) {
    const { t } = useTranslation("partners");
    const { message } = App.useApp();
    const { byPartner, loading, fetchCertificates, upload, prefer, activate, deactivate, remove, download } =
        useCertificatesStore();
    const certificates = byPartner[partner.id] ?? EMPTY;
    const [usage, setUsage] = useState<KeyUsage>("ENCRYPTION");
    const [file, setFile] = useState<File | null>(null);
    const [description, setDescription] = useState("");
    const [busy, setBusy] = useState(false);
    const [warnings, setWarnings] = useState<string[]>([]);
    const [pending, setPending] = useState<Pending>(null);

    useEffect(() => {
        void fetchCertificates(partner.id);
    }, [partner.id, fetchCertificates]);

    const run = async <T extends { warnings: string[] }>(
        action: () => Promise<{ ok: true; data: T } | { ok: false; message: string }>,
        okKey: string,
    ) => {
        setBusy(true);
        const res = await action();
        setBusy(false);
        if (!res.ok) {
            void message.error(res.message);
            return res;
        }
        void message.success(t(okKey));
        setWarnings(res.data.warnings);
        return res;
    };

    const onUpload = async () => {
        if (!file) return;
        const res = await run(() => upload(partner.id, file, usage, description), "certificates.uploaded");
        if (res.ok) {
            if (res.data.outcome !== "CREATED") void message.info(t(`certificates.outcomes.${res.data.outcome}`));
            setFile(null);
            setDescription("");
        }
    };

    const onConfirm = async () => {
        if (!pending) return;
        const { action, certificate } = pending;
        const res =
            action === "remove"
                ? await run(() => remove(partner.id, certificate.id), "certificates.removed")
                : await run(() => deactivate(partner.id, certificate.id), "certificates.deactivated");
        if (res.ok) setPending(null);
    };

    /** Desactivar o eliminar el unico activo de su uso deja al socio sin certificado para ese uso. */
    const isLastActive = (c: Certificate) =>
        c.active && certificates.filter((x) => x.keyUsage === c.keyUsage && x.active).length === 1;

    const validityTag = (c: Certificate) => {
        const label =
            c.validity === "EXPIRING"
                ? t("certificates.validity.EXPIRING", { count: c.daysToExpire })
                : t(`certificates.validity.${c.validity}`);
        return <Tag color={VALIDITY_COLOR[c.validity]}>{label}</Tag>;
    };

    const roleTag = (c: Certificate) => {
        if (!c.active) return <Tag>{t("certificates.inactive")}</Tag>;
        return c.preferred ? <Tag color="gold">{t("certificates.preferred")}</Tag> : <Tag color="cyan">{t("certificates.backup")}</Tag>;
    };

    const actions = (c: Certificate) => (
        <Space wrap size={0}>
            <Tooltip title={t("certificates.download")}>
                <Button
                    size="small"
                    icon={<DownloadOutlined />}
                    aria-label={t("certificates.download")}
                    onClick={() => void download(partner.id, c)}
                />
            </Tooltip>
            {canEdit && c.active && !c.preferred && c.validity !== "EXPIRED" && (
                <Button type="link" size="small" disabled={busy} onClick={() => void run(() => prefer(partner.id, c.id), "certificates.preferredSet")}>
                    {t("certificates.makePreferred")}
                </Button>
            )}
            {canEdit && !c.active && c.validity !== "EXPIRED" && (
                <Button type="link" size="small" disabled={busy} onClick={() => void run(() => activate(partner.id, c.id), "certificates.activated")}>
                    {t("certificates.activate")}
                </Button>
            )}
            {canEdit && c.active && (
                <Button type="link" size="small" disabled={busy} onClick={() => setPending({ action: "deactivate", certificate: c })}>
                    {t("certificates.deactivate")}
                </Button>
            )}
            {isAdmin && (
                <Button type="link" size="small" danger disabled={busy} onClick={() => setPending({ action: "remove", certificate: c })}>
                    {t("certificates.remove")}
                </Button>
            )}
        </Space>
    );

    const columns: ColumnsType<Certificate> = [
        {
            title: t("certificates.columns.issuedTo"),
            key: "issuedTo",
            render: (_: unknown, c: Certificate) => (
                <Space direction="vertical" size={0} style={{ minWidth: 200 }}>
                    <Typography.Text strong>{c.issuedTo}</Typography.Text>
                    <Typography.Text type="secondary">{t("certificates.issuedBy", { name: c.issuedBy })}</Typography.Text>
                    {c.description && <Typography.Text type="secondary">{c.description}</Typography.Text>}
                </Space>
            ),
        },
        {
            title: t("certificates.columns.fingerprint"),
            dataIndex: "fingerprintSha256",
            render: (v: string) => (
                <Tooltip title={formatFingerprint(v)}>
                    <span className="mono-text">{formatFingerprint(v, true)}</span>
                </Tooltip>
            ),
        },
        {
            title: t("certificates.columns.validity"),
            key: "validity",
            render: (_: unknown, c: Certificate) => (
                <Space direction="vertical" size={0}>
                    <span className="nowrap">
                        {c.validFrom.slice(0, 10)} → {c.validTo.slice(0, 10)}
                    </span>
                    {validityTag(c)}
                </Space>
            ),
        },
        { title: t("certificates.columns.status"), key: "status", render: (_: unknown, c: Certificate) => roleTag(c) },
        { title: t("certificates.columns.actions"), key: "actions", render: (_: unknown, c: Certificate) => actions(c) },
    ];

    const confirmText = () => {
        if (!pending) return "";
        const { action, certificate } = pending;
        const key = action === "remove" ? "certificates.confirmRemove" : "certificates.confirmDeactivate";
        const base = t(key, { name: certificate.issuedTo });
        if (!isLastActive(certificate)) return base;
        const effect = t(`certificates.lastActive.${certificate.keyUsage}`);
        return `${base} ${effect}`;
    };

    return (
        <Space direction="vertical" style={{ width: "100%" }} size="middle">
            <Alert type="info" showIcon message={t("certificates.dualGateTitle")} description={t("certificates.dualGateHelp")} />

            {canEdit && (
                <Space wrap align="start">
                    <Select<KeyUsage>
                        aria-label={t("certificates.usage")}
                        value={usage}
                        onChange={setUsage}
                        style={{ width: 200 }}
                        options={KEY_USAGES.map((u) => ({ value: u, label: t(`certificates.usages.${u}`) }))}
                    />
                    <Upload
                        accept=".pem,.crt,.cer,.der"
                        maxCount={1}
                        fileList={file ? [{ uid: "1", name: file.name, status: "done" }] : []}
                        beforeUpload={(f) => {
                            setFile(f);
                            return false;
                        }}
                        onRemove={() => setFile(null)}
                    >
                        <Button icon={<UploadOutlined />}>{t("certificates.chooseFile")}</Button>
                    </Upload>
                    <Input
                        placeholder={t("certificates.descriptionPlaceholder")}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        maxLength={255}
                        style={{ width: 260 }}
                    />
                    <Button type="primary" disabled={!file} loading={busy} onClick={() => void onUpload()}>
                        {t("certificates.upload")}
                    </Button>
                </Space>
            )}

            {warnings.length > 0 && (
                <Alert
                    type="warning"
                    showIcon
                    closable
                    onClose={() => setWarnings([])}
                    message={
                        <ul style={{ margin: 0, paddingInlineStart: 18 }}>
                            {warnings.map((w) => (
                                <li key={w}>{w}</li>
                            ))}
                        </ul>
                    }
                />
            )}

            {KEY_USAGES.map((u) => (
                <div key={u}>
                    <Typography.Title level={5} style={{ marginTop: 0 }}>
                        {t(`certificates.usages.${u}`)}
                    </Typography.Title>
                    <Table<Certificate>
                        size="small"
                        rowKey="id"
                        pagination={false}
                        loading={loading}
                        columns={columns}
                        dataSource={certificates.filter((c) => c.keyUsage === u)}
                        locale={{ emptyText: t("certificates.empty") }}
                        scroll={{ x: true }}
                    />
                </div>
            ))}

            <Modal
                open={pending !== null}
                title={pending?.action === "remove" ? t("certificates.remove") : t("certificates.deactivate")}
                okText={pending?.action === "remove" ? t("certificates.remove") : t("certificates.deactivate")}
                okButtonProps={{ danger: true, loading: busy }}
                onOk={() => void onConfirm()}
                onCancel={() => setPending(null)}
                destroyOnHidden
            >
                <Typography.Paragraph>{confirmText()}</Typography.Paragraph>
            </Modal>
        </Space>
    );
}
