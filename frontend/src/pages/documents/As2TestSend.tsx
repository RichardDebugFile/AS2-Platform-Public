import { CheckCircleFilled, CloseCircleFilled, SendOutlined } from "@ant-design/icons";
import { Alert, Button, Descriptions, Modal, Space, Tag, Typography } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { as2Api, type TestSendResult } from "../../service/as2Api";
import type { ApiError } from "../../service/dataService";
import { color } from "../../theme";

interface Props {
    /** Documento a enviar; null mientras no esta disponible. */
    content: string | null;
    fileName: string;
}

function Check({ ok, yes, no }: Readonly<{ ok: boolean; yes: string; no: string }>) {
    return ok ? (
        <Space size={6}>
            <CheckCircleFilled style={{ color: color.success }} />
            {yes}
        </Space>
    ) : (
        <Space size={6}>
            <CloseCircleFilled style={{ color: color.error }} />
            {no}
        </Space>
    );
}

/**
 * Envio AS2 de prueba (HU-15, AS2-100): manda el documento al socio de prueba y muestra el MDN.
 * No registra el envio en el repositorio; eso llega con el envio definitivo de HU-15.
 */
export default function As2TestSend({ content, fileName }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState<TestSendResult | null>(null);
    const [error, setError] = useState<ApiError | null>(null);
    const [open, setOpen] = useState(false);

    const send = async () => {
        if (content === null) return;
        setBusy(true);
        const r = await as2Api.testSend(content, fileName);
        setBusy(false);
        setResult(r.ok ? r.data : null);
        setError(r.ok ? null : r.error);
        setOpen(true);
    };

    const errorText = (e: ApiError) => {
        // 502: el motor respondio pero el socio no; 503: el gateway no llega al motor (detenido o caido).
        if (e.status === 502 || e.status === 503) return t("as2Test.unavailable");
        if (e.status === 403) return t("as2Test.forbidden");
        return e.message;
    };

    return (
        <>
            <Button icon={<SendOutlined />} disabled={content === null} loading={busy} onClick={() => void send()}>
                {t("as2Test.send")}
            </Button>
            <Modal
                open={open}
                title={t("as2Test.title")}
                onCancel={() => setOpen(false)}
                footer={<Button onClick={() => setOpen(false)}>{t("as2Test.close")}</Button>}
                width="min(720px, 100vw)"
            >
                <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                    <Typography.Text type="secondary">{t("as2Test.help")}</Typography.Text>
                    {error && (
                        <Alert
                            type="error"
                            showIcon
                            message={errorText(error)}
                            description={error.detail ? <span className="mono-text">{error.detail}</span> : undefined}
                        />
                    )}
                    {result && (
                        <>
                            <Alert
                                type={result.delivered ? "success" : "error"}
                                showIcon
                                message={result.delivered ? t("as2Test.delivered") : t("as2Test.notDelivered")}
                            />
                            <Descriptions size="small" column={1} bordered>
                                <Descriptions.Item label={t("as2Test.fields.disposition")}>
                                    <span className="cell-break mono-text">{result.disposition ?? "—"}</span>
                                </Descriptions.Item>
                                <Descriptions.Item label={t("as2Test.fields.signature")}>
                                    <Check
                                        ok={result.mdnSigned && result.mdnSignatureValid}
                                        yes={t("as2Test.signatureOk")}
                                        no={result.mdnSigned ? t("as2Test.signatureBad") : t("as2Test.unsigned")}
                                    />
                                </Descriptions.Item>
                                <Descriptions.Item label={t("as2Test.fields.mic")}>
                                    <Space direction="vertical" size={2}>
                                        <Check ok={result.micMatches} yes={t("as2Test.micOk")} no={t("as2Test.micBad")} />
                                        <span className="cell-break mono-text">
                                            {result.micSent ?? "—"}
                                            {result.micAlgorithm ? ` (${result.micAlgorithm})` : ""}
                                        </span>
                                    </Space>
                                </Descriptions.Item>
                                <Descriptions.Item label={t("as2Test.fields.messageId")}>
                                    <span className="cell-break mono-text">{result.messageId}</span>
                                </Descriptions.Item>
                                <Descriptions.Item label={t("as2Test.fields.http")}>
                                    <Tag>{result.httpStatus}</Tag>
                                    {t("as2Test.elapsed", { ms: result.elapsedMs })}
                                </Descriptions.Item>
                                <Descriptions.Item label={t("as2Test.fields.file")}>
                                    <span className="mono-text">{fileName}</span>
                                </Descriptions.Item>
                            </Descriptions>
                        </>
                    )}
                </Space>
            </Modal>
        </>
    );
}
