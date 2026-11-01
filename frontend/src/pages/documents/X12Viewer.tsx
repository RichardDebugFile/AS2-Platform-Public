import { Alert, Button, Descriptions, Empty, Input, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { x12Api, type X12Summary, type X12Validation } from "../../service/x12Api";
import { describeElements, splitX12, type EdiSegment } from "../../utils/ediParser";
import { SAMPLE_850 } from "./sample850";

interface Props {
    text: string;
    onTextChange: (text: string) => void;
}

interface Result {
    summary: X12Summary | null;
    validation: X12Validation | null;
    segments: EdiSegment[];
    error: string | null;
}

const EMPTY: Result = { summary: null, validation: null, segments: [], error: null };

/** Fin de segmento legible: "~", "salto de linea" (LF o CRLF). */
function terminatorLabel(t: string, lineBreak: string): string {
    return t === "\n" || t === "\r\n" || t === "\r" ? lineBreak : t;
}

/**
 * Visor X12 (HU-12): el servicio interpreta y valida el documento; la tabla de segmentos se arma en
 * la consola con el nombre de cada segmento.
 */
export default function X12Viewer({ text, onTextChange }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState<Result>(EMPTY);

    const interpret = async () => {
        setBusy(true);
        const [parsed, validated] = await Promise.all([x12Api.parse(text), x12Api.validate(text)]);
        setBusy(false);
        const error = (!parsed.ok && parsed.error.message) || (!validated.ok && validated.error.message) || null;
        setResult({
            summary: parsed.ok ? parsed.data : null,
            validation: validated.ok ? validated.data : null,
            segments: splitX12(text).segments,
            error,
        });
    };

    const columns: ColumnsType<EdiSegment> = [
        { title: t("viewer.columns.index"), dataIndex: "index", width: 56 },
        {
            title: t("viewer.columns.segment"),
            dataIndex: "id",
            width: 110,
            render: (id: string) => <Tag className="mono-text">{id}</Tag>,
        },
        {
            title: t("viewer.columns.name"),
            key: "name",
            width: 240,
            render: (_: unknown, s: EdiSegment) => t(`segments.${s.id}`, { defaultValue: "—" }),
        },
        {
            title: t("viewer.columns.content"),
            key: "content",
            render: (_: unknown, s: EdiSegment) => <span className="mono-wrap">{describeElements(s)}</span>,
        },
    ];

    const s = result.summary;
    const v = result.validation;
    return (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                {t("viewer.intro")}
            </Typography.Paragraph>
            <Input.TextArea
                aria-label={t("tabs.viewer")}
                className="mono-area"
                rows={8}
                value={text}
                placeholder={t("viewer.placeholder")}
                onChange={(e) => onTextChange(e.target.value)}
            />
            <Space wrap>
                <Button type="primary" disabled={!text.trim()} loading={busy} onClick={() => void interpret()}>
                    {t("viewer.interpret")}
                </Button>
                <Button onClick={() => onTextChange(SAMPLE_850)}>{t("viewer.sample")}</Button>
                <Button
                    onClick={() => {
                        onTextChange("");
                        setResult(EMPTY);
                    }}
                >
                    {t("viewer.clear")}
                </Button>
            </Space>

            {result.error && <Alert type="error" showIcon message={result.error} />}

            {s && s.typeCode === "UNKNOWN" && <Alert type="warning" showIcon message={t("viewer.unknown")} />}

            {v && s && s.typeCode !== "UNKNOWN" && (
                <Alert
                    type={v.status === "VALIDATED" ? "success" : "error"}
                    showIcon
                    message={t(v.status === "VALIDATED" ? "viewer.valid" : "viewer.invalid", { type: v.typeCode })}
                    description={
                        v.status === "VALIDATED" ? undefined : (
                            <ul style={{ margin: 0, paddingInlineStart: 18 }}>
                                {v.missingSegments.length > 0 && (
                                    <li>{t("viewer.missing", { segments: v.missingSegments.join(", ") })}</li>
                                )}
                                {v.problems.map((p) => (
                                    <li key={p}>{p}</li>
                                ))}
                            </ul>
                        )
                    }
                />
            )}

            {s && s.typeCode !== "UNKNOWN" && (
                <Descriptions title={t("viewer.summary")} bordered size="small" column={{ xs: 1, md: 2 }}>
                    <Descriptions.Item label={t("viewer.fields.type")}>
                        {t(`generator.types.${s.typeCode}`, { defaultValue: `${s.typeCode} · ${s.functionalCode}` })}
                    </Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.version")}>{s.version}</Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.sender")}>
                        <span className="mono-text">
                            {s.sender.qualifier} · {s.sender.id}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.receiver")}>
                        <span className="mono-text">
                            {s.receiver.qualifier} · {s.receiver.id}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.controls")}>
                        <span className="mono-text">
                            {Object.entries(s.controls)
                                .map(([k, val]) => `${k} ${val}`)
                                .join("   ")}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.datetime")}>
                        <span className="mono-text">
                            {s.interchangeDate} {s.interchangeTime}
                        </span>
                    </Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.usage")}>
                        {t(`viewer.usage.${s.usageIndicator}`, { defaultValue: s.usageIndicator })}
                    </Descriptions.Item>
                    <Descriptions.Item label={t("viewer.fields.delimiters")}>
                        <span className="mono-text">
                            {s.elementSeparator} {s.componentSeparator} {terminatorLabel(s.segmentTerminator, t("viewer.lineBreak"))}
                        </span>
                    </Descriptions.Item>
                </Descriptions>
            )}

            {result.segments.length > 0 ? (
                <>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        {t("viewer.segments", { count: result.segments.length })}
                    </Typography.Title>
                    <Table<EdiSegment>
                        size="small"
                        rowKey="index"
                        pagination={false}
                        columns={columns}
                        dataSource={result.segments}
                        scroll={{ x: true }}
                    />
                </>
            ) : (
                !s && <Empty description={t("viewer.empty")} />
            )}
        </Space>
    );
}
