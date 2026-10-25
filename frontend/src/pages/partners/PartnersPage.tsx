import { PlusOutlined } from "@ant-design/icons";
import { App, Button, Input, Modal, Segmented, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { hasAnyRole, useAuthStore } from "../../store/authStore";
import { usePartnersStore, type Partner } from "../../store/partnersStore";
import PartnerDrawer from "./PartnerDrawer";
import { HEALTH_COLOR, STATE_COLOR } from "./folderStyles";

/** Socios comerciales: todos los roles consultan; ADMIN y OPERATOR registran y editan. */
export default function PartnersPage() {
    const { t } = useTranslation("partners");
    const { message, modal } = App.useApp();
    const user = useAuthStore((s) => s.user);
    const { partners, loading, error, showInactive, folderStatuses, fetchPartners, fetchFolderStatuses, setActive, remove, purge } =
        usePartnersStore();
    const [query, setQuery] = useState("");
    const [drawer, setDrawer] = useState<{ partner: Partner | null } | null>(null);
    const [purging, setPurging] = useState<Partner | null>(null);
    const [confirmText, setConfirmText] = useState("");

    const canEdit = hasAnyRole(user, ["ADMIN", "OPERATOR"]);
    const isAdmin = hasAnyRole(user, ["ADMIN"]);

    useEffect(() => {
        void fetchPartners(false);
    }, [fetchPartners]);

    // Estado de carpetas de los activos (HU-09), cada vez que cambia la lista.
    useEffect(() => {
        if (!showInactive && partners.length > 0) void fetchFolderStatuses();
    }, [partners, showInactive, fetchFolderStatuses]);

    const visible = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return partners;
        return partners.filter((p) => p.as2Name.toLowerCase().includes(q) || (p.partnerName ?? "").toLowerCase().includes(q));
    }, [partners, query]);

    const notify = (res: { ok: boolean; message?: string }, okKey: string) => {
        if (res.ok) void message.success(t(okKey));
        else void message.error(res.message);
        return res.ok;
    };

    const deactivate = (p: Partner) =>
        modal.confirm({
            title: t("list.confirmDeactivate", { name: p.as2Name }),
            okButtonProps: { danger: true },
            okText: t("list.deactivate"),
            onOk: async () => void notify(await remove(p.id), "list.deactivated"),
        });

    const reactivate = async (p: Partner) => notify(await setActive(p.id, true), "list.activated");

    const onPurge = async () => {
        if (!purging) return;
        if (notify(await purge(purging.id, confirmText), "list.purged")) setPurging(null);
    };

    const columns: ColumnsType<Partner> = [
        {
            title: t("list.columns.as2Name"),
            dataIndex: "as2Name",
            render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
        },
        { title: t("list.columns.partnerName"), dataIndex: "partnerName", render: (v?: string) => v ?? "—" },
        {
            title: t("list.columns.endpoint"),
            dataIndex: "endpointUrl",
            render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
        },
        {
            title: t("list.columns.type"),
            dataIndex: "partnerType",
            render: (v?: string) => (v ? t(`types.${v}`) : "—"),
        },
        {
            title: t("list.columns.folders"),
            key: "folders",
            render: (_: unknown, p: Partner) => {
                const f = folderStatuses[p.id];
                if (!p.active || !f) return "—";
                return (
                    <Space size={4} wrap>
                        <Tag color={STATE_COLOR[f.folderState]}>{t(`folders.states.${f.folderState}`)}</Tag>
                        {f.health !== "OK" && <Tag color={HEALTH_COLOR[f.health]}>{t(`folders.healths.${f.health}`)}</Tag>}
                    </Space>
                );
            },
        },
        {
            title: t("list.columns.status"),
            dataIndex: "active",
            render: (active: boolean) => (
                <Tag color={active ? "green" : "default"}>{active ? t("list.statusActive") : t("list.statusInactive")}</Tag>
            ),
        },
        {
            title: t("list.columns.actions"),
            key: "actions",
            render: (_: unknown, p: Partner) => (
                <Space wrap>
                    {p.active && (
                        <Button size="small" onClick={() => setDrawer({ partner: p })}>
                            {canEdit ? t("list.edit") : t("list.view")}
                        </Button>
                    )}
                    {canEdit && p.active && (
                        <Button size="small" danger onClick={() => deactivate(p)}>
                            {t("list.deactivate")}
                        </Button>
                    )}
                    {canEdit && !p.active && (
                        <Button size="small" onClick={() => void reactivate(p)}>
                            {t("list.activate")}
                        </Button>
                    )}
                    {isAdmin && !p.active && (
                        <Button
                            size="small"
                            danger
                            onClick={() => {
                                setConfirmText("");
                                setPurging(p);
                            }}
                        >
                            {t("list.purge")}
                        </Button>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <>
            <Space style={{ width: "100%", justifyContent: "space-between", marginBottom: 16 }} wrap>
                <Typography.Title level={3} style={{ margin: 0 }}>
                    {t("title")}
                </Typography.Title>
                <Space wrap>
                    <Segmented
                        value={showInactive ? "inactive" : "active"}
                        options={[
                            { value: "active", label: t("list.active") },
                            { value: "inactive", label: t("list.inactive") },
                        ]}
                        onChange={(v) => void fetchPartners(v === "inactive")}
                    />
                    <Input.Search
                        allowClear
                        placeholder={t("list.search")}
                        onChange={(e) => setQuery(e.target.value)}
                        onSearch={setQuery}
                        style={{ width: 280 }}
                    />
                    {canEdit && (
                        <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawer({ partner: null })}>
                            {t("list.new")}
                        </Button>
                    )}
                </Space>
            </Space>
            {error && <Typography.Text type="danger">{error}</Typography.Text>}
            <Table<Partner>
                rowKey="id"
                columns={columns}
                dataSource={visible}
                loading={loading}
                locale={{ emptyText: t("list.empty") }}
                pagination={{ pageSize: 20, hideOnSinglePage: true }}
                scroll={{ x: true }}
            />

            {drawer && (
                <PartnerDrawer
                    key={drawer.partner?.id ?? "new"}
                    partner={drawer.partner}
                    readOnly={!canEdit}
                    isAdmin={isAdmin}
                    onClose={() => setDrawer(null)}
                />
            )}

            <Modal
                open={purging !== null}
                title={t("list.purgeTitle", { name: purging?.as2Name ?? "" })}
                okText={t("list.purgeOk")}
                okButtonProps={{ danger: true, disabled: confirmText.trim() !== purging?.as2Name }}
                onOk={() => void onPurge()}
                onCancel={() => setPurging(null)}
                destroyOnHidden
            >
                <Typography.Paragraph>{t("list.purgeBody")}</Typography.Paragraph>
                <Input value={confirmText} onChange={(e) => setConfirmText(e.target.value)} placeholder={purging?.as2Name} />
            </Modal>
        </>
    );
}
