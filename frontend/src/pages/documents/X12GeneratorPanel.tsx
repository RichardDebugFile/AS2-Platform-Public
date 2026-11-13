import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { Alert, App, Button, Col, Form, Input, InputNumber, Row, Segmented, Select, Space, Switch, Typography } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { X12_TYPES, x12Api, type GenerateRequest, type X12Type } from "../../service/x12Api";
import As2TestSend from "./As2TestSend";

interface Props {
    /** Lleva el documento generado al visor. */
    onOpenInViewer: (text: string) => void;
    /** Lo registra en el repositorio como documento saliente (HU-14). */
    onSaveToRepository?: (text: string) => void;
}

interface LineForm {
    productId: string;
    description?: string;
    quantity: number;
    unitPrice: number;
}

interface FormValues {
    version: string;
    production: boolean;
    senderQualifier: string;
    senderId: string;
    receiverQualifier: string;
    receiverId: string;
    poNumber: string;
    reference?: string;
    deliveryDays: number;
    buyerName: string;
    buyerId: string;
    sellerName: string;
    sellerId: string;
    lines: LineForm[];
    promotionId: string;
    days: number;
    vendorName: string;
    vendorId: string;
    original: string;
}

/** Valores de ejemplo (ficticios) para que una prueba con un socio salga en pocos clics. */
const INITIAL: Partial<FormValues> = {
    version: "4010",
    production: false,
    senderQualifier: "08",
    senderId: "ID-EMPRESA",
    receiverQualifier: "12",
    receiverId: "ID-SOCIO",
    poNumber: "OC-2026-001",
    deliveryDays: 7,
    buyerName: "Comprador Ejemplo",
    buyerId: "COMP-01",
    sellerName: "Proveedor Ejemplo",
    sellerId: "PROV-01",
    lines: [{ productId: "ROSA-ROJA-60", description: "Rosa roja 60 cm", quantity: 120, unitPrice: 0.85 }],
    promotionId: "PROMO-NOV",
    days: 30,
    vendorName: "Proveedor Ejemplo",
    vendorId: "PROV-01",
};

function toRequest(type: X12Type, v: FormValues): GenerateRequest {
    const common = { type, version: v.version, production: v.production };
    if (type === "997") return { ...common, original: v.original };
    const envelope = {
        sender: { qualifier: v.senderQualifier, id: v.senderId },
        receiver: { qualifier: v.receiverQualifier, id: v.receiverId },
    };
    if (type === "889") {
        return {
            ...common,
            ...envelope,
            promotion: { id: v.promotionId, days: v.days, vendor: { name: v.vendorName, id: v.vendorId } },
        };
    }
    return {
        ...common,
        ...envelope,
        purchaseOrder: {
            number: v.poNumber,
            reference: v.reference,
            deliveryDays: v.deliveryDays,
            buyer: { name: v.buyerName, id: v.buyerId },
            seller: { name: v.sellerName, id: v.sellerId },
            lines: v.lines,
        },
    };
}

/** Generador de documentos X12 (HU-12): 850, 997 y 889, con el ISA y los conteos calculados por el servicio. */
export default function X12GeneratorPanel({ onOpenInViewer, onSaveToRepository }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    const { message } = App.useApp();
    const [form] = Form.useForm<FormValues>();
    const [type, setType] = useState<X12Type>("850");
    const [busy, setBusy] = useState(false);
    const [output, setOutput] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    const required = [{ required: true, message: t("generator.required") }];

    const onFinish = async (values: FormValues) => {
        setBusy(true);
        setError(null);
        const res = await x12Api.generate(toRequest(type, values));
        setBusy(false);
        if (res.ok) {
            setOutput(String(res.data));
        } else {
            setOutput(null);
            setError(res.error.message);
        }
    };

    const copy = async () => {
        if (!output) return;
        await navigator.clipboard?.writeText(output);
        void message.success(t("generator.copied"));
    };

    const download = () => {
        if (!output) return;
        const url = URL.createObjectURL(new Blob([output], { type: "text/plain" }));
        const a = document.createElement("a");
        a.href = url;
        a.download = `x12-${type}.edi`;
        a.click();
        URL.revokeObjectURL(url);
    };

    const party = (prefix: "sender" | "receiver") => (
        <Col xs={24} md={12}>
            <Typography.Text strong>{t(`generator.${prefix}`)}</Typography.Text>
            <Row gutter={8} style={{ marginTop: 8 }}>
                <Col span={8}>
                    <Form.Item label={t("generator.qualifier")} name={`${prefix}Qualifier`} rules={required}>
                        <Input maxLength={2} />
                    </Form.Item>
                </Col>
                <Col span={16}>
                    <Form.Item label={t("generator.id")} name={`${prefix}Id`} rules={required}>
                        <Input maxLength={15} />
                    </Form.Item>
                </Col>
            </Row>
        </Col>
    );

    const company = (prefix: "buyer" | "seller" | "vendor") => (
        <Col xs={24} md={12}>
            <Typography.Text strong>{t(`generator.${prefix}`)}</Typography.Text>
            <Row gutter={8} style={{ marginTop: 8 }}>
                <Col span={14}>
                    <Form.Item label={t("generator.name")} name={`${prefix}Name`} rules={required}>
                        <Input />
                    </Form.Item>
                </Col>
                <Col span={10}>
                    <Form.Item label={t("generator.id")} name={`${prefix}Id`} rules={required}>
                        <Input />
                    </Form.Item>
                </Col>
            </Row>
        </Col>
    );

    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                {t("generator.intro")}
            </Typography.Paragraph>
            <Segmented<X12Type>
                aria-label={t("generator.type")}
                value={type}
                onChange={(v) => {
                    setType(v);
                    setOutput(null);
                    setError(null);
                }}
                options={X12_TYPES.map((v) => ({ value: v, label: t(`generator.types.${v}`) }))}
            />
            <Form<FormValues> form={form} layout="vertical" initialValues={INITIAL} onFinish={(v) => void onFinish(v)}>
                <Row gutter={16}>
                    <Col xs={12} md={6}>
                        <Form.Item label={t("generator.version")} name="version">
                            <Select options={[{ value: "4010" }, { value: "5010" }]} />
                        </Form.Item>
                    </Col>
                    <Col xs={12} md={8}>
                        <Form.Item label={t("generator.production")} name="production" valuePropName="checked">
                            <Switch />
                        </Form.Item>
                    </Col>
                </Row>

                {type === "997" ? (
                    <Form.Item label={t("generator.original")} name="original" rules={required} extra={t("generator.originalHelp")}>
                        <Input.TextArea className="mono-area" rows={6} />
                    </Form.Item>
                ) : (
                    <Row gutter={16}>
                        {party("sender")}
                        {party("receiver")}
                    </Row>
                )}

                {type === "850" && (
                    <>
                        <Row gutter={16}>
                            <Col xs={24} md={8}>
                                <Form.Item label={t("generator.poNumber")} name="poNumber" rules={required}>
                                    <Input />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={8}>
                                <Form.Item label={t("generator.reference")} name="reference">
                                    <Input />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={8}>
                                <Form.Item label={t("generator.deliveryDays")} name="deliveryDays">
                                    <InputNumber min={0} style={{ width: "100%" }} />
                                </Form.Item>
                            </Col>
                        </Row>
                        <Row gutter={16}>
                            {company("buyer")}
                            {company("seller")}
                        </Row>
                        <Typography.Text strong>{t("generator.lines")}</Typography.Text>
                        <Form.List name="lines">
                            {(fields, { add, remove }) => (
                                <div style={{ marginTop: 8 }}>
                                    {fields.map((f) => (
                                        <Row gutter={8} key={f.key} align="bottom">
                                            <Col xs={24} md={6}>
                                                <Form.Item label={t("generator.product")} name={[f.name, "productId"]} rules={required}>
                                                    <Input />
                                                </Form.Item>
                                            </Col>
                                            <Col xs={24} md={8}>
                                                <Form.Item label={t("generator.description")} name={[f.name, "description"]}>
                                                    <Input />
                                                </Form.Item>
                                            </Col>
                                            <Col xs={10} md={4}>
                                                <Form.Item label={t("generator.quantity")} name={[f.name, "quantity"]} rules={required}>
                                                    <InputNumber min={0} style={{ width: "100%" }} />
                                                </Form.Item>
                                            </Col>
                                            <Col xs={10} md={4}>
                                                <Form.Item label={t("generator.price")} name={[f.name, "unitPrice"]} rules={required}>
                                                    <InputNumber min={0} step={0.01} style={{ width: "100%" }} />
                                                </Form.Item>
                                            </Col>
                                            <Col xs={4} md={2}>
                                                <Form.Item>
                                                    <Button
                                                        aria-label={t("generator.removeLine")}
                                                        icon={<DeleteOutlined />}
                                                        disabled={fields.length === 1}
                                                        onClick={() => remove(f.name)}
                                                    />
                                                </Form.Item>
                                            </Col>
                                        </Row>
                                    ))}
                                    <Button icon={<PlusOutlined />} onClick={() => add({ quantity: 1, unitPrice: 0 })}>
                                        {t("generator.addLine")}
                                    </Button>
                                </div>
                            )}
                        </Form.List>
                    </>
                )}

                {type === "889" && (
                    <Row gutter={16}>
                        <Col xs={24} md={8}>
                            <Form.Item label={t("generator.promotionId")} name="promotionId" rules={required}>
                                <Input />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={4}>
                            <Form.Item label={t("generator.days")} name="days">
                                <InputNumber min={1} style={{ width: "100%" }} />
                            </Form.Item>
                        </Col>
                        {company("vendor")}
                    </Row>
                )}

                <Button type="primary" htmlType="submit" loading={busy} style={{ marginTop: 16 }}>
                    {t("generator.generate")}
                </Button>
            </Form>

            {error && <Alert type="error" showIcon message={error} />}

            {output && (
                <>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        {t("generator.result")}
                    </Typography.Title>
                    <Input.TextArea aria-label={t("generator.result")} className="mono-area" rows={10} readOnly value={output} />
                    <Space wrap>
                        <Button onClick={() => void copy()}>{t("generator.copy")}</Button>
                        <Button onClick={download}>{t("generator.download")}</Button>
                        <Button type="primary" ghost onClick={() => onOpenInViewer(output)}>
                            {t("generator.openInViewer")}
                        </Button>
                        {onSaveToRepository && (
                            <Button type="primary" onClick={() => onSaveToRepository(output)}>
                                {t("generator.saveToRepository")}
                            </Button>
                        )}
                        <As2TestSend content={output} fileName={`x12-${type}.edi`} />
                    </Space>
                </>
            )}
        </Space>
    );
}
