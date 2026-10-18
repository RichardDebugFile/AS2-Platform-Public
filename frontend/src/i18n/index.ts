import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";

import adminEn from "./locales/en/admin.json";
import alertsEn from "./locales/en/alerts.json";
import authEn from "./locales/en/auth.json";
import commonEn from "./locales/en/common.json";
import documentsEn from "./locales/en/documents.json";
import partnersEn from "./locales/en/partners.json";
import sessionEn from "./locales/en/session.json";
import adminEs from "./locales/es/admin.json";
import alertsEs from "./locales/es/alerts.json";
import authEs from "./locales/es/auth.json";
import commonEs from "./locales/es/common.json";
import documentsEs from "./locales/es/documents.json";
import partnersEs from "./locales/es/partners.json";
import sessionEs from "./locales/es/session.json";

/** Un espacio de nombres por area de la consola; es y en deben tener las mismas claves. */
export const resources = {
    es: { common: commonEs, auth: authEs, session: sessionEs, admin: adminEs, partners: partnersEs, documents: documentsEs, alerts: alertsEs },
    en: { common: commonEn, auth: authEn, session: sessionEn, admin: adminEn, partners: partnersEn, documents: documentsEn, alerts: alertsEn },
} as const;

/** Clave de localStorage donde el navegador recuerda el idioma elegido. */
export const LANGUAGE_KEY = "as2.lang";

void i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
        resources,
        fallbackLng: "es",
        supportedLngs: ["es", "en"],
        nonExplicitSupportedLngs: true,
        defaultNS: "common",
        ns: Object.keys(resources.es),
        detection: { order: ["localStorage", "navigator"], caches: ["localStorage"], lookupLocalStorage: LANGUAGE_KEY },
        interpolation: { escapeValue: false },
    });

export default i18n;
