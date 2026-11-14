package edi.alerts.audit;

import java.util.Map;

/**
 * Hecho de alerts-service para la bitacora (HU-10), p. ej. {@code SMTP_CONFIG_UPDATED}. Nunca lleva la
 * contrasena SMTP: un cambio de contrasena se registra como {@code (cambiada)}.
 *
 * @param eventType tipo en MAYUSCULAS
 * @param success   resultado
 * @param actor     usuario que lo provoco, o {@code system}
 * @param detail    descripcion breve
 * @param changes   campo -> valor anterior y nuevo; vacio si no aplica
 */
public record AuditEvent(String eventType, boolean success, String actor, String detail,
        Map<String, Change> changes) {

    static final int MAX_DETAIL = 2000;

    /** Valor anterior y nuevo de un campo. */
    public record Change(Object from, Object to) {
    }

    /** Lo que recibe {@code POST /internal/audit-logs} de partners-service. */
    record Body(String eventType, String status, String source, String actor, String detail,
            Map<String, Change> changes) {
    }

    Body body() {
        String d = detail != null && detail.length() > MAX_DETAIL ? detail.substring(0, MAX_DETAIL) : detail;
        return new Body(eventType, success ? "SUCCESS" : "FAILURE", AuditClient.SOURCE, actor, d,
                changes == null ? Map.of() : changes);
    }
}
