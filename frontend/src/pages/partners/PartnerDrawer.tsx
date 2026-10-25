import { Alert, App, Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, Tabs, Tag, Typography } from "antd";
import type { TabsProps } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { FIELD_TAB, NEW_PARTNER, editableFields, endpointPreview } from "./partnerForm";
import {
    usePartnersStore,
    type AlgorithmItem,
    type ConfigKind,
    type Partner,
    type PartnerConfigs,
    type PartnerInput,
} from "../../store/partnersStore";

interface Props {
    /** null: alta de un socio nuevo. */
    partner: Partner | null;
    readOnly: boolean;
    onClose: () => void;
}

/**
 * Alta y edicion de un socio: datos, conexion y seguridad; en la edicion, tambien sus configuraciones.
 * La pagina lo monta de nuevo en cada apertura (key), asi arranca siempre limpio.
 */
export default function PartnerDrawer({ partner, readOnly, onClose }: Readonly<Props>) {
    const { t } = useTranslation("partners");
    const { message } = App.useApp();
    const { catalog, fetchCatalog, createPartner, updatePartner, getConfigs, saveConfig } = usePartnersStore();
    const [form] = Form.useForm<PartnerInput>();
    const [outgoingForm] = Form.useForm<PartnerConfigs["outgoing"]>();
    const [incomingForm] = Form.useForm<PartnerConfigs["incoming"]>();
    const [mdnForm] = Form.useForm<PartnerConfigs["mdn"]>();
    const [saving, setSaving] = useState(false);
    const [configError, setConfigError] = useState<string | null>(null);
    const [tab, setTab] = useState("general");
    const values = Form.useWatch([], form) ?? {};
    const sendingMode = Form.useWatch("sendingMode", outgoingForm);
    const archiveMode = Form.useWatch("archiveMode", outgoingForm);
    const mdnDelivery = Form.useWatch("deliveryMode", mdnForm);

    const editing = partner !== null;

    useEffect(() => {
        void fetchCatalog();
    }, [fetchCatalog]);

    useEffect(() => {
        if (!partner) return;
        void getConfigs(partner.id).then((res) => {
            if (!res.ok) {
                setConfigError(res.message);
                return;
            }
            outgoingForm.setFieldsValue(res.data.outgoing);
            incomingForm.setFieldsValue(res.data.incoming);
            mdnForm.setFieldsValue(res.data.mdn);
        });
    }, [partner, outgoingForm, incomingForm, mdnForm, getConfigs]);

    const required = [{ required: true, message: t("form.required") }];
    const email = [{ type: "email" as const, message: t("form.invalidEmail") }];
    const opt = (keys: string[], prefix = "options") => keys.map((k) => ({ value: k, label: t(`${prefix}.${k}`) }));

    const algorithmOptions = (items: AlgorithmItem[] | undefined) =>
        (items ?? []).map((i) => ({
            value: i.key,
            label: (
                <Space size={4}>
                    {i.label}
                    {i.status === "recommended" && <Tag color="green">{t("form.recommendedTag")}</Tag>}
                    {i.status === "legacy" && <Tag color="orange">{t("form.legacyTag")}</Tag>}
                </Space>
            ),
        }));

    /** Aplica los algoritmos del catalogo con ese estado (recomendados o de compatibilidad). */
    const applyAlgorithms = (status: AlgorithmItem["status"]) => {
        if (!catalog) return;
        const pick = (items: AlgorithmItem[]) => items.find((i) => i.status === status)?.key;
        form.setFieldsValue({
            signingAlgorithm: pick(catalog.signing),
            encryptionAlgorithm: pick(catalog.encryption),
            micAlgorithm: pick(catalog.mic),
        });
    };

    const onSubmit = async (v: PartnerInput) => {
        setSaving(true);
        // En la edicion se parte del socio guardado: los campos que la pantalla no muestra (p. ej. la
        // carpeta compartida, HU-09) se reenvian tal cual y no se pierden.
        const res = editing ? await updatePartner(partner.id, { ...editableFields(partner), ...v }) : await createPartner(v);
        setSaving(false);
        if (!res.ok) {
            void message.error(res.message);
            return;
        }
        void message.success(t(editing ? "drawer.saved" : "drawer.created"));
        onClose();
    };

    const onSaveConfig = async <K extends ConfigKind>(kind: K, cfg: PartnerConfigs[K]) => {
        if (!partner) return;
        const res = await saveConfig(partner.id, kind, cfg);
        if (res.ok) void message.success(t("drawer.saved"));
        else void message.error(res.message);
    };

    const saveSection = (submit: () => void) =>
        !readOnly && (
            <Button type="primary" onClick={submit}>
                {t("form.saveSection")}
            </Button>
        );

    const general = (
        <>
            <Form.Item name="as2Name" label={t("form.as2Name")} extra={t("form.as2NameHelp")} rules={required}>
                <Input maxLength={128} autoComplete="off" />
            </Form.Item>
            <Row gutter={16}>
                <Col xs={24} md={12}>
                    <Form.Item name="partnerName" label={t("form.partnerName")}>
                        <Input maxLength={255} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="partnerType" label={t("form.partnerType")}>
                        <Select allowClear options={opt(["CUSTOMER", "SUPPLIER", "DISTRIBUTOR", "INTERNAL"], "types")} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="partnerAs2Name" label={t("form.partnerAs2Name")}>
                        <Input maxLength={128} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="externalId" label={t("form.externalId")}>
                        <Input maxLength={128} />
                    </Form.Item>
                </Col>
            </Row>
            <Form.Item name="description" label={t("form.description")}>
                <Input.TextArea rows={2} maxLength={2000} />
            </Form.Item>
            <Row gutter={16}>
                <Col xs={24} md={12}>
                    <Form.Item name="contactName" label={t("form.contactName")}>
                        <Input maxLength={255} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="contactPhone" label={t("form.contactPhone")}>
                        <Input maxLength={50} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="contactEmail" label={t("form.contactEmail")} rules={email}>
                        <Input maxLength={255} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="tradingPartnerEmail" label={t("form.tradingPartnerEmail")} rules={email}>
                        <Input maxLength={255} />
                    </Form.Item>
                </Col>
            </Row>
            <Form.Item name="alertOnFailure" label={t("form.alertOnFailure")} valuePropName="checked">
                <Switch />
            </Form.Item>
        </>
    );

    const connection = (
        <>
            <Row gutter={16}>
                <Col xs={24} md={6}>
                    <Form.Item name="transportType" label={t("form.transportType")}>
                        <Select options={["HTTPS", "HTTP"].map((v) => ({ value: v, label: v }))} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="serverAddress" label={t("form.serverAddress")} extra={t("form.serverAddressHelp")} rules={required}>
                        <Input maxLength={255} autoComplete="off" />
                    </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                    <Form.Item name="port" label={t("form.port")} rules={required}>
                        <InputNumber min={1} max={65535} style={{ width: "100%" }} />
                    </Form.Item>
                </Col>
            </Row>
            <Form.Item name="path" label={t("form.path")}>
                <Input maxLength={255} />
            </Form.Item>
            <Typography.Paragraph type="secondary">
                {t("form.endpoint")}: <Typography.Text code>{endpointPreview(values) || "—"}</Typography.Text>
            </Typography.Paragraph>
            <Row gutter={16}>
                <Col xs={24} md={12}>
                    <Form.Item name="basicAuthUsername" label={t("form.basicAuthUsername")}>
                        <Input maxLength={255} autoComplete="off" />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item
                        name="basicAuthPassword"
                        label={t("form.basicAuthPassword")}
                        extra={partner?.hasBasicAuthPassword ? t("form.basicAuthPasswordKeep") : undefined}
                    >
                        <Input.Password maxLength={255} autoComplete="new-password" />
                    </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                    <Form.Item name="connectionTimeoutSeconds" label={t("form.connectionTimeoutSeconds")}>
                        <InputNumber min={1} max={3600} style={{ width: "100%" }} />
                    </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                    <Form.Item name="readTimeoutSeconds" label={t("form.readTimeoutSeconds")}>
                        <InputNumber min={1} max={3600} style={{ width: "100%" }} />
                    </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                    <Form.Item name="maxRetryAttempts" label={t("form.maxRetryAttempts")}>
                        <InputNumber min={0} max={20} style={{ width: "100%" }} />
                    </Form.Item>
                </Col>
                <Col xs={12} md={6}>
                    <Form.Item name="retryBackoffMs" label={t("form.retryBackoffMs")}>
                        <InputNumber min={0} max={3_600_000} step={1000} style={{ width: "100%" }} />
                    </Form.Item>
                </Col>
            </Row>
        </>
    );

    const toggles = (names: (keyof PartnerInput)[]) => (
        <Row gutter={16}>
            {names.map((n) => (
                <Col xs={12} md={8} key={n}>
                    <Form.Item name={n} label={t(`form.${n}`)} valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Col>
            ))}
        </Row>
    );

    const security = (
        <>
            {!readOnly && (
                <Space wrap style={{ marginBottom: 16 }}>
                    <Button onClick={() => applyAlgorithms("recommended")}>{t("form.recommended")}</Button>
                    <Button onClick={() => applyAlgorithms("legacy")}>{t("form.legacy")}</Button>
                </Space>
            )}
            <Row gutter={16}>
                <Col xs={24} md={8}>
                    <Form.Item name="signingAlgorithm" label={t("form.signingAlgorithm")}>
                        <Select options={algorithmOptions(catalog?.signing)} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                    <Form.Item name="encryptionAlgorithm" label={t("form.encryptionAlgorithm")}>
                        <Select options={algorithmOptions(catalog?.encryption)} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                    <Form.Item name="micAlgorithm" label={t("form.micAlgorithm")}>
                        <Select options={algorithmOptions(catalog?.mic)} />
                    </Form.Item>
                </Col>
            </Row>
            {toggles(["compressionEnabled", "requestMdn", "requestSignedMdn", "validateCertificate", "validateSignature", "validateMic"])}
            <Row gutter={16}>
                <Col xs={24} md={8}>
                    <Form.Item name="mdnMode" label={t("form.mdnMode")}>
                        <Select options={opt(["SYNC", "ASYNC"])} />
                    </Form.Item>
                </Col>
                {values.mdnMode === "ASYNC" && (
                    <Col xs={24} md={16}>
                        <Form.Item name="mdnReceiptUrl" label={t("form.mdnReceiptUrl")} rules={required}>
                            <Input maxLength={1024} />
                        </Form.Item>
                    </Col>
                )}
            </Row>
        </>
    );

    const outgoing = (
        <Form form={outgoingForm} layout="vertical" disabled={readOnly} onFinish={(v) => void onSaveConfig("outgoing", v)}>
            <Form.Item name="localFolder" label={t("form.localFolder")} rules={required}>
                <Input maxLength={512} />
            </Form.Item>
            <Row gutter={16}>
                <Col xs={24} md={12}>
                    <Form.Item name="sendingMode" label={t("form.sendingMode")} rules={required}>
                        <Select options={opt(["NONE", "REGULAR", "FILENAME_FILTER"])} />
                    </Form.Item>
                </Col>
                {sendingMode === "FILENAME_FILTER" && (
                    <Col xs={24} md={12}>
                        <Form.Item name="filenameFilter" label={t("form.filenameFilter")} rules={required}>
                            <Input maxLength={255} placeholder="*.edi" />
                        </Form.Item>
                    </Col>
                )}
                <Col xs={24} md={12}>
                    <Form.Item name="signingAlgorithm" label={t("form.signingAlgorithm")}>
                        <Select allowClear options={algorithmOptions(catalog?.signing)} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="encryptionAlgorithm" label={t("form.encryptionAlgorithm")}>
                        <Select allowClear options={algorithmOptions(catalog?.encryption)} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="mimeType" label={t("form.mimeType")}>
                        <Select
                            options={[
                                { value: "APPLICATION_EDI_X12", label: "application/edi-x12" },
                                { value: "APPLICATION_EDIFACT", label: "application/edifact" },
                                { value: "APPLICATION_XML", label: "application/xml" },
                                { value: "TEXT_PLAIN", label: "text/plain" },
                            ]}
                        />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="compression" label={t("form.compression")} valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="archiveMode" label={t("form.archiveMode")}>
                        <Select
                            options={[
                                { value: "MOVE", label: t("options.MOVE") },
                                { value: "NONE", label: t("options.KEEP") },
                                { value: "DELETE", label: t("options.DELETE") },
                            ]}
                        />
                    </Form.Item>
                </Col>
                {archiveMode === "MOVE" && (
                    <Col xs={24} md={12}>
                        <Form.Item name="archiveFolder" label={t("form.archiveFolder")}>
                            <Input maxLength={512} />
                        </Form.Item>
                    </Col>
                )}
            </Row>
            {saveSection(() => outgoingForm.submit())}
        </Form>
    );

    const incoming = (
        <Form form={incomingForm} layout="vertical" disabled={readOnly} onFinish={(v) => void onSaveConfig("incoming", v)}>
            <Form.Item name="incomingFolder" label={t("form.incomingFolder")} rules={required}>
                <Input maxLength={512} />
            </Form.Item>
            <Form.Item name="parserProfile" label={t("form.parserProfile")}>
                <Input maxLength={128} />
            </Form.Item>
            {saveSection(() => incomingForm.submit())}
        </Form>
    );

    const mdn = (
        <Form form={mdnForm} layout="vertical" disabled={readOnly} onFinish={(v) => void onSaveConfig("mdn", v)}>
            <Form.Item name="localFolder" label={t("form.localFolder")} rules={required}>
                <Input maxLength={512} />
            </Form.Item>
            <Row gutter={16}>
                <Col xs={24} md={12}>
                    <Form.Item name="receiptType" label={t("form.receiptType")} rules={required}>
                        <Select options={opt(["SIGNED", "UNSIGNED"])} />
                    </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                    <Form.Item name="deliveryMode" label={t("form.deliveryMode")} rules={required}>
                        <Select options={opt(["SYNC", "ASYNC"])} />
                    </Form.Item>
                </Col>
            </Row>
            {mdnDelivery === "ASYNC" && (
                <Form.Item name="asyncMdnUrl" label={t("form.asyncMdnUrl")} rules={required}>
                    <Input maxLength={1024} />
                </Form.Item>
            )}
            {saveSection(() => mdnForm.submit())}
        </Form>
    );

    const partnerTabs: TabsProps["items"] = [
        { key: "general", label: t("drawer.tabs.general"), children: general, forceRender: true },
        { key: "connection", label: t("drawer.tabs.connection"), children: connection, forceRender: true },
        { key: "security", label: t("drawer.tabs.security"), children: security, forceRender: true },
    ];
    const configTabs: TabsProps["items"] = editing
        ? [
              { key: "outgoing", label: t("drawer.tabs.outgoing"), children: outgoing, forceRender: true },
              { key: "incoming", label: t("drawer.tabs.incoming"), children: incoming, forceRender: true },
              { key: "mdn", label: t("drawer.tabs.mdn"), children: mdn, forceRender: true },
          ]
        : [];

    const name = partner?.as2Name ?? "";
    let title = t("drawer.newTitle");
    if (editing) title = t(readOnly ? "drawer.viewTitle" : "drawer.editTitle", { name });

    return (
        <Drawer
            open
            title={title}
            width={760}
            onClose={onClose}
            extra={
                <Space>
                    <Button onClick={onClose}>{t("drawer.cancel")}</Button>
                    {!readOnly && (
                        <Button type="primary" loading={saving} onClick={() => form.submit()}>
                            {editing ? t("drawer.save") : t("drawer.create")}
                        </Button>
                    )}
                </Space>
            }
        >
            {!editing && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t("drawer.foldersHint")} />}
            {configError && (
                <Alert type="error" showIcon style={{ marginBottom: 16 }} message={t("drawer.loadError", { message: configError })} />
            )}
            {/* Los datos del socio son un formulario (sin <form> propio para no anidar los de cada
                configuracion, que se guardan en su pestana). */}
            <Form<PartnerInput>
                form={form}
                component={false}
                layout="vertical"
                disabled={readOnly}
                initialValues={partner ? { ...partner, basicAuthPassword: undefined } : NEW_PARTNER}
                onFinish={(v) => void onSubmit(v)}
                onFinishFailed={({ errorFields }) => setTab(FIELD_TAB[String(errorFields[0]?.name[0])] ?? "general")}
            >
                <Tabs activeKey={tab} onChange={setTab} items={[...partnerTabs, ...configTabs]} />
            </Form>
        </Drawer>
    );
}
