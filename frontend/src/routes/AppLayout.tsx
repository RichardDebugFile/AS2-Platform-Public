import { LogoutOutlined, UserOutlined } from "@ant-design/icons";
import type { MenuProps } from "antd";
import { Avatar, Breadcrumb, Dropdown, Layout, Menu, Typography } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import logo from "../assets/logo.svg";
import LanguageSelector from "../components/LanguageSelector";
import SessionGuard from "../session/SessionGuard";
import { useAuthStore } from "../store/authStore";
import { locate, menuFor } from "./menu";

const { Header, Content, Footer, Sider } = Layout;

/** Marco de las pantallas con sesion: menu por rol, cabecera, migas de pan y avisos de sesion. */
export default function AppLayout() {
    const { t } = useTranslation("common");
    const navigate = useNavigate();
    const location = useLocation();
    const [collapsed, setCollapsed] = useState(false);
    const user = useAuthStore((s) => s.user);
    const logout = useAuthStore((s) => s.logout);

    const items: MenuProps["items"] = menuFor(user?.roles).map((section) => ({
        type: "group",
        key: section.key,
        label: t(section.label),
        children: section.entries.map((e) => ({ key: e.path, label: t(e.label) })),
    }));

    const here = locate(location.pathname);
    const crumbs = here ? [{ title: t(here.section.label) }, { title: t(here.entry.label) }] : [{ title: t("app.name") }];
    const roles = (user?.roles ?? []).map((r) => t(`roles.${r}`)).join(", ");

    const userMenu: MenuProps["items"] = [
        { key: "logout", icon: <LogoutOutlined />, label: t("userMenu.logout"), danger: true, onClick: () => void logout() },
    ];

    return (
        <Layout style={{ minHeight: "100vh" }}>
            <SessionGuard />
            <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={240} theme="dark">
                <div className="app-brand">
                    <img src={logo} alt="" width={32} height={32} />
                    {!collapsed && <span>{t("app.name")}</span>}
                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    items={items}
                    selectedKeys={[location.pathname]}
                    onClick={({ key }) => void navigate(String(key))}
                />
            </Sider>
            <Layout>
                <Header className="app-header" style={{ background: "#fff", padding: "0 24px" }}>
                    <LanguageSelector />
                    <Dropdown menu={{ items: userMenu }} trigger={["click"]} placement="bottomRight">
                        <button type="button" className="app-user" aria-label={user?.username}>
                            <Avatar icon={<UserOutlined />} style={{ backgroundColor: "#0f766e" }} />
                            <span className="app-user-text">
                                <Typography.Text strong>{user?.username}</Typography.Text>
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                    {roles}
                                </Typography.Text>
                            </span>
                        </button>
                    </Dropdown>
                </Header>
                <Content style={{ margin: "16px 24px 0" }}>
                    <Breadcrumb items={crumbs} style={{ marginBottom: 16 }} />
                    <div className="app-content">
                        <Outlet />
                    </div>
                </Content>
                <Footer style={{ textAlign: "center" }}>{t("app.footer", { year: new Date().getFullYear() })}</Footer>
            </Layout>
        </Layout>
    );
}
