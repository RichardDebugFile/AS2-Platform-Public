import { LockOutlined, SafetyOutlined, UserOutlined } from "@ant-design/icons";
import { Alert, Button, Form, Input, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";
import logo from "../../assets/logo.svg";
import LanguageSelector from "../../components/LanguageSelector";
import type { SessionEnded } from "../../session/sessionSync";
import { useAuthStore } from "../../store/authStore";

const { Title, Text } = Typography;

type LoginForm = { username: string; password: string };
type MfaForm = { code: string };

const MFA_ERRORS = new Set(["MFA_INVALID_CODE", "MFA_ATTEMPTS_EXCEEDED", "MFA_CHALLENGE_EXPIRED"]);

type Translate = (key: string, options?: Record<string, unknown>) => string;

/** Texto del error de inicio de sesion, por codigo (el mensaje del servidor no se muestra tal cual). */
function errorMessage(code: string | null, t: Translate): string | null {
    if (!code) return null;
    if (code === "UNAUTHORIZED") return t("invalidCredentials");
    if (MFA_ERRORS.has(code)) return t(`mfa.${code}`);
    return t(`errors.${code}`, { ns: "common", defaultValue: t("errors.UNKNOWN", { ns: "common" }) });
}

/** Por que se cerro la sesion anterior: sin esto el usuario aparece en el login sin explicacion. */
function endMessage(lastEnd: SessionEnded | null, t: Translate): string | null {
    if (!lastEnd || lastEnd.reason === "logout") return null;
    const sinMinutos = (lastEnd.reason === "idle" || lastEnd.reason === "max") && lastEnd.minutes == null;
    const key = sinMinutos ? `reasons.${lastEnd.reason}NoMinutes` : `reasons.${lastEnd.reason}`;
    return t(key, { ns: "session", minutes: lastEnd.minutes });
}

/** Inicio de sesion en uno o dos pasos (contrasena y, si el usuario lo tiene activo, codigo MFA). */
export default function Login() {
    const { t } = useTranslation(["auth", "session", "common"]);
    const navigate = useNavigate();
    const location = useLocation();
    const { login, verifyMfa, cancelMfa, mfaToken, loading, errorCode, lastEnd } = useAuthStore();

    const goOn = () => {
        const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
        void navigate(from && from !== "/" ? from : "/dashboard", { replace: true });
    };

    const onLogin = async (v: LoginForm) => {
        if ((await login(v.username, v.password)).ok) goOn();
    };
    const onMfa = async (v: MfaForm) => {
        if ((await verifyMfa(v.code.trim())).ok) goOn();
    };

    const errorText = errorMessage(errorCode, t);
    const endText = endMessage(lastEnd, t);

    return (
        <div className="login">
            <aside className="login-brand">
                <img src={logo} alt="" width={64} height={64} />
                <Title level={2} style={{ color: "#fff", margin: "16px 0 8px" }}>
                    {t("app.name", { ns: "common" })}
                </Title>
                <Text style={{ color: "#ccfbf1" }}>{t("app.subtitle", { ns: "common" })}</Text>
            </aside>
            <main className="login-panel">
                <div className="login-lang">
                    <LanguageSelector variant="segmented" />
                </div>
                <div className="login-form">
                    <Title level={2} style={{ marginBottom: 4 }}>
                        {mfaToken ? t("mfa.title") : t("welcome")}
                    </Title>
                    <Text type="secondary">{mfaToken ? t("mfa.subtitle") : t("subtitle")}</Text>

                    {endText && !errorText && (
                        <Alert type="warning" showIcon message={endText} style={{ margin: "24px 0 0" }} />
                    )}
                    {errorText && <Alert type="error" showIcon message={errorText} style={{ margin: "24px 0 0" }} />}

                    {mfaToken ? (
                        <Form<MfaForm> layout="vertical" onFinish={onMfa} requiredMark={false} size="large" style={{ marginTop: 24 }}>
                            <Form.Item
                                label={t("mfa.code.label")}
                                name="code"
                                rules={[{ required: true, pattern: /^\s*\d{6}\s*$/, message: t("mfa.code.required") }]}
                            >
                                <Input
                                    prefix={<SafetyOutlined />}
                                    placeholder={t("mfa.code.placeholder")}
                                    inputMode="numeric"
                                    autoComplete="one-time-code"
                                    maxLength={6}
                                    autoFocus
                                />
                            </Form.Item>
                            <Button type="primary" htmlType="submit" loading={loading} block>
                                {t("mfa.verify")}
                            </Button>
                            <Button type="link" block onClick={cancelMfa} style={{ marginTop: 8 }}>
                                {t("mfa.back")}
                            </Button>
                        </Form>
                    ) : (
                        <Form<LoginForm> layout="vertical" onFinish={onLogin} requiredMark={false} size="large" style={{ marginTop: 24 }}>
                            <Form.Item label={t("username.label")} name="username" rules={[{ required: true, message: t("username.required") }]}>
                                <Input prefix={<UserOutlined />} placeholder={t("username.placeholder")} autoComplete="username" autoFocus />
                            </Form.Item>
                            <Form.Item label={t("password.label")} name="password" rules={[{ required: true, message: t("password.required") }]}>
                                <Input.Password prefix={<LockOutlined />} placeholder={t("password.placeholder")} autoComplete="current-password" />
                            </Form.Item>
                            <Button type="primary" htmlType="submit" loading={loading} block>
                                {t("signIn")}
                            </Button>
                        </Form>
                    )}
                </div>
            </main>
        </div>
    );
}
