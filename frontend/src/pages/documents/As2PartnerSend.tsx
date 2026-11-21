import { SendOutlined } from "@ant-design/icons";
import { Alert, Button, Descriptions, Modal, Select, Space } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { as2Api, type SendResult } from "../../service/as2Api";
import { usePartnersStore } from "../../store/partnersStore";

interface Props {
    content: string | null;
    fileName: string;
}

export default function As2PartnerSend({ content, fileName }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    const partners = usePartnersStore((s) => s.partners);
    const fetchPartners = usePartnersStore((s) => s.fetchPartners);
    const [open, setOpen] = useState(false);
    const [partner, setPartner] = useState<string>();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string>();
    const [result, setResult] = useState<SendResult>();

    const show = () => {
        setPartner(undefined);
        setError(undefined);
        setResult(undefined);
        setOpen(true);
        void fetchPartners();
    };

    const send = async () => {
        if (!content || !partner) return;
        setBusy(true);
        const response = await as2Api.send(partner, content, fileName);
        setBusy(false);
        setError(response.ok ? undefined : response.error.detail ?? response.error.message);
        setResult(response.ok ? response.data : undefined);
    };
    const delivered = result?.status === "MDN_RECEIVED" || result?.status === "DELIVERED";

    return (
        <>
            <Button icon={<SendOutlined aria-hidden="true" />} disabled={!content} onClick={show}>{t("as2Send.open")}</Button>
            <Modal
                open={open}
                title={t("as2Send.open")}
                closable={!busy}
                maskClosable={!busy}
                onCancel={() => !busy && setOpen(false)}
                onOk={() => result ? setOpen(false) : void send()}
                okText={result ? t("as2Test.close") : t("as2Send.confirm")}
                okButtonProps={{ disabled: !result && (!partner || !content) }}
                confirmLoading={busy}
            >
                <Space direction="vertical" style={{ width: "100%" }}>
                    <Select
                        style={{ width: "100%" }}
                        showSearch
                        optionFilterProp="label"
                        aria-label={t("repository.upload.partner")}
                        placeholder={t("repository.upload.partner")}
                        disabled={busy || Boolean(result)}
                        options={partners.filter((p) => p.active !== false).map((p) => ({
                            value: p.as2Name,
                            label: p.partnerName ? `${p.as2Name} · ${p.partnerName}` : p.as2Name,
                        }))}
                        value={partner}
                        onChange={setPartner}
                    />
                    {error && <Alert type="error" showIcon message={error} />}
                    {result && (
                        <>
                            <Alert
                                type={delivered ? "success" : "error"}
                                showIcon
                                message={t(`repository.status.${result.status}`)}
                                description={result.reason}
                            />
                            <Descriptions size="small" column={1}>
                                <Descriptions.Item label={t("as2Test.fields.messageId")}>
                                    {result.messageId}
                                </Descriptions.Item>
                                {result.mdnDisposition && (
                                    <Descriptions.Item label={t("as2Test.fields.disposition")}>
                                        {result.mdnDisposition}
                                    </Descriptions.Item>
                                )}
                            </Descriptions>
                        </>
                    )}
                </Space>
            </Modal>
        </>
    );
}
