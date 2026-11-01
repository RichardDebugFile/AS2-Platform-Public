import { LogoutOutlined } from "@ant-design/icons";
import type { MenuProps } from "antd";
import { Avatar, Dropdown, Layout, Menu, Typography } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import logo from "../assets/logo.svg";
import LanguageSelector from "../components/LanguageSelector";
import SessionGuard from "../session/SessionGuard";
import { useAuthStore } from "../store/authStore";
import { initials } from "./initials";
import { locate, menuFor } from "./menu";

const { Header, Content, Footer, Sider } = Layout;

/**
 * Marco de las pantallas con sesion (HU-24): barra lateral oscura con el menu por rol, cabecera con
 * la seccion y el titulo de la pantalla, idioma y usuario, y el contenido sobre el fondo de la app.
 */
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
    const roles = (user?.roles ?? []).map((r) => t(`roles.${r}`)).join(", ");

    const userMenu: MenuProps["items"] = [
        { key: "logout", icon: <LogoutOutlined />, label: t("userMenu.logout"), danger: true, onClick: () => void logout() },
    ];

    return (
        <Layout className="app-shell">
            <SessionGuard />
            <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={256} className="app-sider">
                <div className="app-brand">
                    <img src={logo} alt="" width={36} height={36} />
                    {!collapsed && (
                        <span className="app-brand-text">
                            <span className="app-brand-name">{t("app.name")}</span>
                            <span className="app-brand-sub">{t("app.console")}</span>
                        </span>
                    )}
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
                <Header className="app-header">
                    <div className="app-header-title">
                        {here && <span className="app-header-section">{t(here.section.label)}</span>}
                        <Typography.Title level={1}>{here ? t(here.entry.label) : t("app.name")}</Typography.Title>
                    </div>
                    <LanguageSelector />
                    <Dropdown menu={{ items: userMenu }} trigger={["click"]} placement="bottomRight">
                        <button type="button" className="app-user" aria-label={user?.username}>
                            <Avatar className="app-avatar">{initials(user?.username)}</Avatar>
                            <span className="app-user-text">
                                <Typography.Text strong>{user?.username}</Typography.Text>
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                    {roles}
                                </Typography.Text>
                            </span>
                        </button>
                    </Dropdown>
                </Header>
                <Content className="app-main">
                    <div className="app-content">
                        <Outlet />
                    </div>
                </Content>
                <Footer className="app-footer">{t("app.footer", { year: new Date().getFullYear() })}</Footer>
            </Layout>
        </Layout>
    );
}
