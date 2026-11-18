import { dataService } from "./dataService";

export type SmtpSecurity = "STARTTLS" | "SSL" | "NONE";

/** Configuracion del servidor de correo (AS2-106). Nunca trae la contrasena: solo si hay una guardada. */
export interface SmtpConfig {
    configured: boolean;
    host: string | null;
    port: number | null;
    security: SmtpSecurity | null;
    username: string | null;
    passwordSet: boolean;
    fromAddress: string | null;
    fromName: string | null;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** Lo que se guarda. Contrasena vacia conserva la guardada. */
export interface SmtpConfigRequest {
    host: string;
    port: number;
    security: SmtpSecurity;
    username?: string;
    password?: string;
    clearPassword?: boolean;
    fromAddress: string;
    fromName?: string;
}

export interface TestEmailResult {
    sent: boolean;
    to: string;
    server: string;
    elapsedMs: number;
}

/** Puerto habitual de cada modo de seguridad. */
export const DEFAULT_PORTS: Record<SmtpSecurity, number> = { STARTTLS: 587, SSL: 465, NONE: 25 };

export const smtpApi = {
    get: () => dataService.get<SmtpConfig>("/smtp-config"),
    save: (config: SmtpConfigRequest) => dataService.put<SmtpConfig>("/smtp-config", config),
    /** 502 si el servidor de correo no acepto el envio (el mensaje dice por que). */
    sendTest: (to: string) => dataService.post<TestEmailResult>("/smtp-config/test", { to }),
};
