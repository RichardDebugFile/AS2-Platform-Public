import { MailOutlined, SaveOutlined } from "@ant-design/icons";
import { Alert, App, Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
    DEFAULT_PORTS,
    smtpApi,
    type SmtpConfig,
    type SmtpConfigRequest,
    type SmtpSecurity,
    type TestEmailResult,
} from "../../service/smtpApi";
import { useAuthStore } from "../../store/authStore";
import { formatDateTime } from "../../utils/dateTime";

const SECURITIES: SmtpSecurity[] = ["STARTTLS", "SSL", "NONE"];
const KNOWN_PORTS = new Set([25, 465, 587, 1025]);

interface FormValues {
    host: string;
    port: number;
    security: SmtpSecurity;
    username?: string;
    password?: string;
    fromAddress: string;
    fromName?: string;
}

type TestOutcome = { ok: true; result: TestEmailResult } | { ok: false; message: string };

function toForm(c: SmtpConfig): Partial<FormValues> {
    if (!c.configured) return { security: "STARTTLS", port: DEFAULT_PORTS.STARTTLS, fromName: "Plataforma AS2/EDI" };
    return {
        host: c.host ?? undefined,
        port: c.port ?? undefined,
        security: c.security ?? "STARTTLS",
        username: c.username ?? undefined,
        fromAddress: c.fromAddress ?? undefined,
        fromName: c.fromName ?? undefined,
    };
}

/**
 * Configuracion del servidor de correo (AS2-106, adelanto de HU-21), solo para ADMIN: servidor, puerto,
 * seguridad, usuario y remitente, y un correo de prueba para comprobarla. La contrasena nunca vuelve del
 * servidor; vacia, se conserva la guardada.
 */
export default function SmtpSettingsPage() {
    const { t, i18n } = useTranslation("alerts");
    const { message } = App.useApp();
    const email = useAuthStore((s) => s.user?.email ?? "");
    const [form] = Form.useForm<FormValues>();
    const [config, setConfig] = useState<SmtpConfig | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [to, setTo] = useState(email);
    const [sending, setSending] = useState(false);
    const [outcome, setOutcome] = useState<TestOutcome | null>(null);

    const show = useCallback(
        (c: SmtpConfig) => {
            setConfig(c);
            form.setFieldsValue({ ...toForm(c), password: undefined });
        },
        [form],
    );

    useEffect(() => {
        void smtpApi.get().then((res) => {
            if (res.ok) show(res.data);
            else setLoadError(res.error.message);
        });
    }, [show]);

    const onSecurity = (security: SmtpSecurity) => {
        const port = form.getFieldValue("port") as number | undefined;
        if (port === undefined || port === null || KNOWN_PORTS.has(port)) form.setFieldValue("port", DEFAULT_PORTS[security]);
    };

    const onSave = async (v: FormValues) => {
        setSaving(true);
        const request: SmtpConfigRequest = {
            host: v.host.trim(),
            port: v.port,
            security: v.security,
            username: v.username?.trim() || undefined,
            password: v.password || undefined,
            fromAddress: v.fromAddress.trim(),
            fromName: v.fromName?.trim() || undefined,
        };
        const res = await smtpApi.save(request);
        setSaving(false);
        if (!res.ok) {
            void message.error(res.error.message);
            return;
        }
        show(res.data);
        setOutcome(null);
        void message.success(t("smtp.saved"));
    };

    const onTest = async () => {
        setSending(true);
        setOutcome(null);
        const res = await smtpApi.sendTest(to.trim());
        setSending(false);
        setOutcome(res.ok ? { ok: true, result: res.data } : { ok: false, message: res.error.message });
    };

    const passwordSet = config?.passwordSet ?? false;

    return (
        <Row gutter={[16, 16]}>
            <Col xs={24} xl={15}>
                <Card title={t("title")}>
                    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                        <Typography.Text type="secondary">{t("smtp.intro")}</Typography.Text>
                        {loadError && <Alert type="error" showIcon message={t("smtp.loadError", { message: loadError })} />}
                        {config && !config.configured && <Alert type="info" showIcon message={t("smtp.notConfigured")} />}
                        {config?.configured && config.updatedAt && (
                            <Typography.Text type="secondary" data-testid="smtp-updated">
                                {t("smtp.updated", {
                                    by: config.updatedBy,
                                    at: formatDateTime(config.updatedAt, i18n.language),
                                })}
                            </Typography.Text>
                        )}
                        <Form<FormValues> form={form} layout="vertical" onFinish={(v) => void onSave(v)} requiredMark>
                            <Row gutter={12}>
                                <Col xs={24} md={12}>
                                    <Form.Item
                                        name="host"
                                        label={t("smtp.host")}
                                        extra={t("smtp.hostHelp")}
                                        rules={[{ required: true, whitespace: true, message: t("smtp.required") }]}
                                    >
                                        <Input autoComplete="off" />
                                    </Form.Item>
                                </Col>
                                <Col xs={12} md={5}>
                                    <Form.Item name="port" label={t("smtp.port")} rules={[{ required: true, message: t("smtp.required") }]}>
                                        <InputNumber min={1} max={65535} style={{ width: "100%" }} />
                                    </Form.Item>
                                </Col>
                                <Col xs={12} md={7}>
                                    <Form.Item name="security" label={t("smtp.security")} rules={[{ required: true }]}>
                                        <Select
                                            onChange={onSecurity}
                                            options={SECURITIES.map((s) => ({ value: s, label: t(`smtp.securityOptions.${s}`) }))}
                                        />
                                    </Form.Item>
                                </Col>
                                <Col xs={24} md={12}>
                                    <Form.Item name="username" label={t("smtp.username")} extra={t("smtp.usernameHelp")}>
                                        <Input autoComplete="off" />
                                    </Form.Item>
                                </Col>
                                <Col xs={24} md={12}>
                                    <Form.Item name="password" label={t("smtp.password")}>
                                        <Input.Password
                                            autoComplete="new-password"
                                            placeholder={passwordSet ? t("smtp.passwordKeep") : undefined}
                                        />
                                    </Form.Item>
                                </Col>
                                <Col xs={24} md={12}>
                                    <Form.Item
                                        name="fromAddress"
                                        label={t("smtp.fromAddress")}
                                        rules={[
                                            { required: true, message: t("smtp.required") },
                                            { type: "email", message: t("smtp.invalidEmail") },
                                        ]}
                                    >
                                        <Input autoComplete="off" />
                                    </Form.Item>
                                </Col>
                                <Col xs={24} md={12}>
                                    <Form.Item name="fromName" label={t("smtp.fromName")}>
                                        <Input autoComplete="off" />
                                    </Form.Item>
                                </Col>
                            </Row>
                            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
                                {t("smtp.save")}
                            </Button>
                        </Form>
                        <Typography.Text type="secondary">{t("smtp.demoHint")}</Typography.Text>
                    </Space>
                </Card>
            </Col>
            <Col xs={24} xl={9}>
                <Card title={t("test.title")}>
                    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                        <Typography.Text type="secondary">{t("test.intro")}</Typography.Text>
                        <Input
                            aria-label={t("test.to")}
                            placeholder={t("test.to")}
                            value={to}
                            onChange={(e) => setTo(e.target.value)}
                            prefix={<MailOutlined />}
                        />
                        <Button
                            icon={<MailOutlined />}
                            loading={sending}
                            disabled={!config?.configured || to.trim() === ""}
                            onClick={() => void onTest()}
                        >
                            {t("test.send")}
                        </Button>
                        {config && !config.configured && <Typography.Text type="secondary">{t("test.needsConfig")}</Typography.Text>}
                        {outcome?.ok === true && (
                            <Alert
                                type="success"
                                showIcon
                                message={t("test.sent", {
                                    to: outcome.result.to,
                                    server: outcome.result.server,
                                    ms: outcome.result.elapsedMs,
                                })}
                            />
                        )}
                        {outcome?.ok === false && <Alert type="error" showIcon message={t("test.failed")} description={outcome.message} />}
                    </Space>
                </Card>
            </Col>
        </Row>
    );
}
