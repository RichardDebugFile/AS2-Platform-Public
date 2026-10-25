package edi.as2adapter.audit;

/**
 * Hecho del intercambio AS2 para la bitacora (HU-10), p. ej. {@code DOCUMENT_SENT} o
 * {@code MDN_RECEIVED}. Sin contenido de los mensajes ni datos sensibles.
 *
 * @param eventType      tipo en MAYUSCULAS
 * @param status         {@code SUCCESS} o {@code FAILURE}
 * @param partnerAs2Name identificador AS2 del socio
 * @param messageId      Message-ID AS2 del intercambio
 * @param detail         descripcion breve
 */
public record AuditEvent(String eventType, String status, String partnerAs2Name, String messageId, String detail) {

    /** Lo que recibe {@code POST /internal/audit-logs} de partners-service. */
    record Body(String eventType, String status, String source, String partnerAs2Name, String messageId,
                String detail) {
    }

    Body body() {
        return new Body(eventType, status, AuditClient.SOURCE, partnerAs2Name, messageId, detail);
    }
}
