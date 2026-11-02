package edi.documents.audit;

/**
 * Hecho del repositorio de documentos para la bitacora (HU-10), p. ej. {@code DOCUMENT_RECEIVED}. Sin
 * el contenido de los documentos.
 *
 * @param eventType      tipo en MAYUSCULAS
 * @param success        resultado
 * @param actor          usuario que lo provoco, o {@code system}
 * @param partnerAs2Name identificador AS2 del socio
 * @param messageId      Message-ID del intercambio
 * @param detail         descripcion breve
 */
public record AuditEvent(String eventType, boolean success, String actor, String partnerAs2Name, String messageId,
        String detail) {

    /** Lo que recibe {@code POST /internal/audit-logs} de partners-service. */
    record Body(String eventType, String status, String source, String actor, String partnerAs2Name,
            String messageId, String detail) {
    }

    Body body() {
        return new Body(eventType, success ? "SUCCESS" : "FAILURE", AuditClient.SOURCE, actor, partnerAs2Name,
                messageId, detail);
    }
}
