import { Alert, Button, Descriptions, Empty, Input, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { x12Api, type X12Summary, type X12Validation } from "../../service/x12Api";
import { describeElements, splitX12, type EdiSegment } from "../../utils/ediParser";
import { SAMPLE_850 } from "./sample850";

interface Props {
    text: string;
    onTextChange: (text: string) => void;
    /** Interpretar al montarse: el texto llega de otra pestana ("Abrir en el visor"). */
    interpretOnMount?: boolean;
}

interface Result {
    /** Texto al que corresponde el resultado: si el cuadro cambia, el resultado ya no aplica. */
    text: string;
    summary: X12Summary | null;
    validation: X12Validation | null;
    segments: EdiSegment[];
    error: string | null;
}

const EMPTY: Result = { text: "", summary: null, validation: null, segments: [], error: null };

/** Interpreta y valida en el servicio; los segmentos se separan en la consola. */
async function analyze(text: string): Promise<Result> {
    const [parsed, validated] = await Promise.all([x12Api.parse(text), x12Api.validate(text)]);
    const error = (!parsed.ok && parsed.error.message) || (!validated.ok && validated.error.message) || null;
    return {
        text,
        summary: parsed.ok ? parsed.data : null,
        validation: validated.ok ? validated.data : null,
        segments: splitX12(text).segments,
        error,
    };
}

/** Fin de segmento legible: "~", "salto de linea" (LF o CRLF). */
function terminatorLabel(t: string, lineBreak: string): string {
    return t === "\n" || t === "\r\n" || t === "\r" ? lineBreak : t;
}

/**
 * Visor X12 (HU-12): el servicio interpreta y valida el documento; la tabla de segmentos se arma en
 * la consola con el nombre de cada segmento.
 */
export default function X12Viewer({ text, onTextChange, interpretOnMount = false }: Readonly<Props>) {
    const { t } = useTranslation("documents");
    // Texto que se interpreta al montarse; el padre monta un visor nuevo (key) por cada apertura.
    const [initial] = useState(() => (interpretOnMount && text.trim() ? text : null));
    const [busy, setBusy] = useState(initial !== null);
    const [result, setResult] = useState<Result>(EMPTY);

    useEffect(() => {
        if (initial === null) return;
        let current = true;
        void analyze(initial).then((r) => {
            if (!current) return;
            setResult(r);
            setBusy(false);
        });
        return () => {
            current = false;
        };
    }, [initial]);

    const interpret = async () => {
        setBusy(true);
        const r = await analyze(text);
        setBusy(false);
        setResult(r);
    };

    // Un resultado de otro texto no se muestra: induciria a creer que el documento actual es valido.
    const shown = result.text === text ? result : EMPTY;
    const stale = result.text !== "" && result.text !== text && text.trim() !== "";

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

    const s = shown.summary;
    const v = shown.validation;
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

            {stale && <Alert type="info" showIcon message={t("viewer.stale")} />}

            {shown.error && <Alert type="error" showIcon message={shown.error} />}

            {s?.typeCode === "UNKNOWN" && <Alert type="warning" showIcon message={t("viewer.unknown")} />}

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

            {shown.segments.length > 0 ? (
                <>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        {t("viewer.segments", { count: shown.segments.length })}
                    </Typography.Title>
                    <Table<EdiSegment>
                        size="small"
                        rowKey="index"
                        pagination={false}
                        columns={columns}
                        dataSource={shown.segments}
                        scroll={{ x: true }}
                    />
                </>
            ) : (
                !s && !stale && !busy && <Empty description={t("viewer.empty")} />
            )}
        </Space>
    );
}
