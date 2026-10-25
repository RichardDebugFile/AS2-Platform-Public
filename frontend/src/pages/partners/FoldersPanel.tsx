import { CheckCircleTwoTone, LinkOutlined, ReloadOutlined, WarningTwoTone } from "@ant-design/icons";
import { Alert, App, Button, Descriptions, Input, List, Modal, Space, Table, Tag, Typography } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { usePartnersStore, type FolderEntry, type FolderPrecheck, type Partner } from "../../store/partnersStore";
import { HEALTH_COLOR, STATE_COLOR } from "./folderStyles";

interface Props {
    partner: Partner;
    /** ADMIN u OPERATOR: verifica y repara. */
    canEdit: boolean;
    /** Solo ADMIN enlaza con el ERP y activa produccion. */
    isAdmin: boolean;
}

/** Carpetas del socio (HU-09): estado, verificacion previa, enlace con el ERP y produccion. */
export default function FoldersPanel({ partner, canEdit, isAdmin }: Readonly<Props>) {
    const { t } = useTranslation("partners");
    const { message } = App.useApp();
    const { folderStatuses, getFolderStatus, repairFolders, precheckFolders, linkFolders, setProduction } =
        usePartnersStore();
    const status = folderStatuses[partner.id];
    const [shared, setShared] = useState(partner.sharedFolderName ?? "");
    const [precheck, setPrecheck] = useState<FolderPrecheck | null>(null);
    const [busy, setBusy] = useState(false);
    const [productionDialog, setProductionDialog] = useState<boolean | null>(null);
    const [confirm, setConfirm] = useState("");

    useEffect(() => {
        void getFolderStatus(partner.id);
    }, [partner.id, getFolderStatus]);

    const state = status?.folderState ?? partner.folderState ?? "LOCAL";
    const linked = state !== "LOCAL";

    const run = async <T,>(action: () => Promise<{ ok: true; data: T } | { ok: false; message: string }>, okKey?: string) => {
        setBusy(true);
        const res = await action();
        setBusy(false);
        if (!res.ok) void message.error(res.message);
        else if (okKey) void message.success(t(okKey));
        return res;
    };

    const onPrecheck = async () => {
        const res = await run(() => precheckFolders(partner.id, shared));
        setPrecheck(res.ok ? res.data : null);
    };

    const onLink = async () => {
        const res = await run(() => linkFolders(partner.id, shared), "folders.linked");
        if (res.ok) setPrecheck(null);
    };

    const onProduction = async () => {
        if (productionDialog === null) return;
        const res = await run(() => setProduction(partner.id, productionDialog, confirm), productionDialog ? "folders.productionOn" : "folders.productionOff");
        if (res.ok) setProductionDialog(null);
    };

    // El enlace solo se habilita con una verificacion positiva del mismo nombre.
    const canLink = isAdmin && !linked && precheck?.ok === true && precheck.sharedFolderName === shared.trim();

    const columns = [
        { title: t("folders.columns.name"), dataIndex: "name" },
        {
            title: t("folders.columns.path"),
            dataIndex: "path",
            render: (v: string, f: FolderEntry) => (
                <Space direction="vertical" size={0}>
                    <Typography.Text code>{v}</Typography.Text>
                    {f.linked && (
                        <Typography.Text type="secondary">
                            <LinkOutlined /> {f.target}
                        </Typography.Text>
                    )}
                </Space>
            ),
        },
        {
            title: t("folders.columns.status"),
            key: "ok",
            render: (_: unknown, f: FolderEntry) =>
                f.exists && f.readable && f.writable ? (
                    <CheckCircleTwoTone twoToneColor="#52c41a" aria-label={t("folders.ok")} />
                ) : (
                    <WarningTwoTone twoToneColor="#faad14" aria-label={t("folders.problem")} />
                ),
        },
    ];

    return (
        <Space direction="vertical" style={{ width: "100%" }} size="middle">
            <Descriptions size="small" column={{ xs: 1, md: 3 }}>
                <Descriptions.Item label={t("folders.state")}>
                    <Tag color={STATE_COLOR[state]}>{t(`folders.states.${state}`)}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t("folders.health")}>
                    {status ? <Tag color={HEALTH_COLOR[status.health]}>{t(`folders.healths.${status.health}`)}</Tag> : "—"}
                </Descriptions.Item>
                <Descriptions.Item label={t("folders.shared")}>{partner.sharedFolderName ?? status?.sharedFolderName ?? "—"}</Descriptions.Item>
            </Descriptions>

            {status && status.problems.length > 0 && (
                <Alert
                    type={status.health === "ERROR" ? "error" : "warning"}
                    showIcon
                    message={t("folders.problemsTitle")}
                    description={
                        <ul style={{ margin: 0, paddingInlineStart: 18 }}>
                            {status.problems.map((p) => (
                                <li key={p}>{p}</li>
                            ))}
                        </ul>
                    }
                />
            )}

            <Table<FolderEntry> size="small" rowKey="name" pagination={false} columns={columns} dataSource={status?.folders ?? []} />

            <Space wrap>
                <Button icon={<ReloadOutlined />} onClick={() => void getFolderStatus(partner.id)}>
                    {t("folders.refresh")}
                </Button>
                {canEdit && !linked && status?.health === "MISSING" && (
                    <Button loading={busy} onClick={() => void run(() => repairFolders(partner.id), "folders.repaired")}>
                        {t("folders.repair")}
                    </Button>
                )}
            </Space>

            {!linked && canEdit && (
                <>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        {t("folders.linkTitle")}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                        {t("folders.linkHelp")}
                    </Typography.Paragraph>
                    <Space.Compact style={{ maxWidth: 480, width: "100%" }}>
                        <Input
                            aria-label={t("folders.shared")}
                            placeholder={t("folders.sharedPlaceholder")}
                            value={shared}
                            onChange={(e) => {
                                setShared(e.target.value);
                                setPrecheck(null);
                            }}
                        />
                        <Button disabled={!shared.trim()} loading={busy} onClick={() => void onPrecheck()}>
                            {t("folders.precheck")}
                        </Button>
                    </Space.Compact>
                    {precheck && (
                        <Alert
                            type={precheck.ok ? "success" : "error"}
                            showIcon
                            message={precheck.ok ? t("folders.precheckOk", { target: precheck.target }) : t("folders.precheckFailed")}
                            description={
                                <List
                                    size="small"
                                    dataSource={precheck.ok ? precheck.actions : precheck.problems}
                                    renderItem={(i) => <List.Item style={{ padding: "2px 0" }}>{i}</List.Item>}
                                />
                            }
                        />
                    )}
                    <div>
                        <Button type="primary" icon={<LinkOutlined />} disabled={!canLink} loading={busy} onClick={() => void onLink()}>
                            {t("folders.link")}
                        </Button>
                        {!isAdmin && (
                            <Typography.Text type="secondary" style={{ marginInlineStart: 8 }}>
                                {t("folders.adminOnly")}
                            </Typography.Text>
                        )}
                    </div>
                </>
            )}

            {linked && isAdmin && (
                <>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                        {t("folders.productionTitle")}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                        {state === "PRODUCTION" ? t("folders.productionOnHelp") : t("folders.productionOffHelp")}
                    </Typography.Paragraph>
                    <div>
                        <Button
                            danger={state === "PRODUCTION"}
                            type={state === "PRODUCTION" ? "default" : "primary"}
                            onClick={() => {
                                setConfirm("");
                                setProductionDialog(state !== "PRODUCTION");
                            }}
                        >
                            {state === "PRODUCTION" ? t("folders.deactivateProduction") : t("folders.activateProduction")}
                        </Button>
                    </div>
                </>
            )}

            <Modal
                open={productionDialog !== null}
                title={productionDialog ? t("folders.activateProduction") : t("folders.deactivateProduction")}
                okText={t("folders.confirmOk")}
                okButtonProps={{ disabled: confirm.trim() !== partner.as2Name, danger: productionDialog === false }}
                onOk={() => void onProduction()}
                onCancel={() => setProductionDialog(null)}
                destroyOnHidden
            >
                <Typography.Paragraph>
                    {productionDialog ? t("folders.confirmOn", { name: partner.as2Name }) : t("folders.confirmOff", { name: partner.as2Name })}
                </Typography.Paragraph>
                <Input aria-label={t("folders.confirmLabel")} value={confirm} onChange={(e) => setConfirm(e.target.value)} placeholder={partner.as2Name} />
            </Modal>
        </Space>
    );
}
