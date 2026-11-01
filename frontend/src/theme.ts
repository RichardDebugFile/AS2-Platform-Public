import type { ThemeConfig } from "antd";

/**
 * Sistema de diseño de la consola (HU-24), el mismo del archivo de Figma "AS2-Platform – Consola":
 * primitivas, colores semánticos y espaciado. Los nombres siguen a las variables de Figma y a las
 * variables CSS `--as2-*` de index.css; si cambia un valor, cambia en los tres lugares.
 */
export const palette = {
    navy950: "#0B1726",
    navy800: "#1C2F4A",
    blue600: "#1D5FD1",
    blue700: "#174CA8",
    blue50: "#EAF1FC",
    gray900: "#101828",
    gray600: "#5B6577",
    gray400: "#98A2B3",
    gray200: "#E4E7EC",
    gray100: "#EEF0F3",
    gray50: "#F5F7FA",
    white: "#FFFFFF",
    green600: "#12805C",
    green50: "#E7F5EF",
    amber600: "#B54708",
    amber50: "#FEF3E2",
    red600: "#D92D20",
    red50: "#FDECEB",
    cyan600: "#0E7490",
    cyan50: "#E6F4F7",
} as const;

/** Colores con significado, como las variables "Color" de Figma. */
export const color = {
    bgApp: palette.gray50,
    bgSurface: palette.white,
    bgSubtle: palette.gray100,
    bgSidebar: palette.navy950,
    bgSidebarActive: palette.navy800,
    bgSelected: palette.blue50,
    textPrimary: palette.gray900,
    textSecondary: palette.gray600,
    textOnDark: palette.white,
    textOnDarkMuted: palette.gray400,
    border: palette.gray200,
    primary: palette.blue600,
    primaryHover: palette.blue700,
    success: palette.green600,
    warning: palette.amber600,
    error: palette.red600,
    info: palette.cyan600,
} as const;

export const FONT_FAMILY = "Inter, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif";
export const FONT_MONO = "'Roboto Mono', ui-monospace, 'Cascadia Mono', Consolas, monospace";

/** Tema de Ant Design construido con los tokens anteriores. */
export const antdTheme: ThemeConfig = {
    token: {
        colorPrimary: color.primary,
        colorInfo: color.info,
        colorSuccess: color.success,
        colorWarning: color.warning,
        colorError: color.error,
        colorLink: color.primary,
        colorText: color.textPrimary,
        colorTextSecondary: color.textSecondary,
        colorBorder: color.border,
        colorBorderSecondary: color.border,
        colorBgLayout: color.bgApp,
        colorBgContainer: color.bgSurface,
        fontFamily: FONT_FAMILY,
        fontFamilyCode: FONT_MONO,
        fontSize: 14,
        borderRadius: 6,
        borderRadiusLG: 8,
        controlHeight: 36,
        boxShadowTertiary: "0 1px 2px rgba(16, 24, 40, 0.06)",
    },
    components: {
        Layout: {
            siderBg: color.bgSidebar,
            triggerBg: color.bgSidebarActive,
            headerBg: color.bgSurface,
            headerHeight: 64,
            headerPadding: "0 32px",
            bodyBg: color.bgApp,
        },
        Menu: {
            darkItemBg: color.bgSidebar,
            darkSubMenuItemBg: color.bgSidebar,
            darkItemColor: color.textOnDarkMuted,
            darkItemHoverColor: color.textOnDark,
            darkItemHoverBg: color.bgSidebarActive,
            darkItemSelectedBg: color.bgSidebarActive,
            darkItemSelectedColor: color.textOnDark,
            darkGroupTitleColor: color.textOnDarkMuted,
            itemHeight: 40,
            itemBorderRadius: 6,
            itemMarginInline: 8,
        },
        Table: {
            headerBg: color.bgSubtle,
            headerColor: color.textSecondary,
            headerSplitColor: "transparent",
            rowHoverBg: palette.gray50,
            cellPaddingBlock: 14,
        },
        Card: { headerFontSize: 16 },
        Tabs: { itemColor: color.textSecondary, inkBarColor: color.primary },
        Tag: { defaultBg: color.bgSubtle },
    },
};
