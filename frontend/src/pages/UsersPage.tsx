import { PlusOutlined } from "@ant-design/icons";
import { App, Button, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useUsersStore, type NewUser, type Role, type User } from "../store/usersStore";

const ROLES: Role[] = ["ADMIN", "OPERATOR", "AUDITOR"];

/** Administracion de usuarios (solo ADMIN): alta, roles y activacion. */
export default function UsersPage() {
    const { t } = useTranslation(["admin", "common"]);
    const { message, modal } = App.useApp();
    const { users, loading, error, pagination, query, fetchUsers, createUser, updateUserRoles, updateUserStatus } =
        useUsersStore();
    const [creating, setCreating] = useState(false);
    const [editing, setEditing] = useState<User | null>(null);
    const [createForm] = Form.useForm<NewUser>();
    const [rolesForm] = Form.useForm<{ roles: Role[] }>();

    useEffect(() => {
        void fetchUsers(0, 20, "");
    }, [fetchUsers]);

    const roleOptions = ROLES.map((r) => ({ value: r, label: t(`roles.${r}`, { ns: "common" }) }));

    const notify = (res: { ok: true } | { ok: false; message: string }, okKey: string) => {
        if (res.ok) void message.success(t(okKey));
        else void message.error(res.message);
        return res.ok;
    };

    const onCreate = async (values: NewUser) => {
        if (notify(await createUser(values), "users.created")) {
            setCreating(false);
            createForm.resetFields();
        }
    };

    const onSaveRoles = async ({ roles }: { roles: Role[] }) => {
        if (editing && notify(await updateUserRoles(editing.id, roles), "users.saved")) setEditing(null);
    };

    const toggle = (u: User) => {
        const run = async () => notify(await updateUserStatus(u.id, !u.active), "users.saved");
        if (!u.active) return void run();
        modal.confirm({ title: t("users.confirmDeactivate", { username: u.username }), onOk: run });
    };

    const columns: ColumnsType<User> = [
        { title: t("users.columns.username"), dataIndex: "username" },
        { title: t("users.columns.email"), dataIndex: "email" },
        {
            title: t("users.columns.roles"),
            dataIndex: "roles",
            render: (roles: Role[]) => roles.map((r) => <Tag key={r}>{t(`roles.${r}`, { ns: "common" })}</Tag>),
        },
        {
            title: t("users.columns.status"),
            dataIndex: "active",
            render: (active: boolean) => (
                <Tag color={active ? "green" : "default"}>{active ? t("users.active") : t("users.inactive")}</Tag>
            ),
        },
        {
            title: t("users.columns.mfa"),
            dataIndex: "mfaEnabled",
            render: (on: boolean) => (on ? t("users.mfaOn") : t("users.mfaOff")),
        },
        {
            title: t("users.columns.actions"),
            key: "actions",
            render: (_: unknown, u: User) => (
                <Space>
                    <Button
                        size="small"
                        onClick={() => {
                            setEditing(u);
                            rolesForm.setFieldsValue({ roles: u.roles });
                        }}
                    >
                        {t("users.editRoles")}
                    </Button>
                    <Button size="small" danger={u.active} onClick={() => toggle(u)}>
                        {u.active ? t("users.deactivate") : t("users.activate")}
                    </Button>
                </Space>
            ),
        },
    ];

    return (
        <>
            <Space style={{ width: "100%", justifyContent: "space-between", marginBottom: 16 }} wrap>
                <Typography.Title level={3} style={{ margin: 0 }}>
                    {t("users.title")}
                </Typography.Title>
                <Space wrap>
                    <Input.Search
                        allowClear
                        placeholder={t("users.search")}
                        defaultValue={query}
                        onSearch={(q) => void fetchUsers(0, pagination.pageSize, q)}
                        style={{ width: 280 }}
                    />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
                        {t("users.new")}
                    </Button>
                </Space>
            </Space>
            {error && <Typography.Text type="danger">{error}</Typography.Text>}
            <Table<User>
                rowKey="id"
                columns={columns}
                dataSource={users}
                loading={loading}
                pagination={{
                    current: pagination.current,
                    pageSize: pagination.pageSize,
                    total: pagination.total,
                    onChange: (page, size) => void fetchUsers(page - 1, size, query),
                }}
            />

            <Modal
                open={creating}
                title={t("users.new")}
                okText={t("users.form.create")}
                cancelText={t("users.form.cancel")}
                onOk={() => createForm.submit()}
                onCancel={() => setCreating(false)}
                destroyOnHidden
            >
                <Form<NewUser> form={createForm} layout="vertical" onFinish={onCreate} initialValues={{ roles: ["OPERATOR"] }}>
                    <Form.Item name="username" label={t("users.form.username")} rules={[{ required: true, message: t("users.form.required") }]}>
                        <Input autoComplete="off" />
                    </Form.Item>
                    <Form.Item
                        name="email"
                        label={t("users.form.email")}
                        rules={[
                            { required: true, message: t("users.form.required") },
                            { type: "email", message: t("users.form.invalidEmail") },
                        ]}
                    >
                        <Input autoComplete="off" />
                    </Form.Item>
                    <Form.Item
                        name="password"
                        label={t("users.form.password.label")}
                        rules={[
                            { required: true, message: t("users.form.required") },
                            { min: 8, message: t("users.form.minPassword") },
                        ]}
                    >
                        <Input.Password autoComplete="new-password" />
                    </Form.Item>
                    <Form.Item name="roles" label={t("users.form.roles")} rules={[{ required: true, message: t("users.form.required") }]}>
                        <Select mode="multiple" options={roleOptions} />
                    </Form.Item>
                </Form>
            </Modal>

            <Modal
                open={editing !== null}
                title={`${t("users.editRoles")}: ${editing?.username ?? ""}`}
                okText={t("users.form.save")}
                cancelText={t("users.form.cancel")}
                onOk={() => rolesForm.submit()}
                onCancel={() => setEditing(null)}
                destroyOnHidden
            >
                <Form<{ roles: Role[] }> form={rolesForm} layout="vertical" onFinish={onSaveRoles}>
                    <Form.Item name="roles" label={t("users.form.roles")} rules={[{ required: true, message: t("users.form.required") }]}>
                        <Select mode="multiple" options={roleOptions} />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}
