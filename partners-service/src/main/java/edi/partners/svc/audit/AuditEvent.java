package edi.partners.svc.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Hecho que debe quedar en la bitacora.
 *
 * @param eventType      tipo en MAYUSCULAS, p. ej. {@code PARTNER_UPDATED} o {@code DOCUMENT_SENT}
 * @param partnerId      socio afectado (puede ser nulo)
 * @param partnerAs2Name identificador AS2 del socio en ese momento (puede ser nulo)
 * @param messageId      Message-ID AS2 del intercambio, si lo hay
 * @param actor          usuario que lo hizo, o "system"
 * @param success        si la operacion termino bien
 * @param detail         descripcion breve, sin datos sensibles
 * @param changes        campos cambiados con su valor anterior y nuevo (vacio si no aplica)
 * @param source         servicio que lo registra
 */
public record AuditEvent(String eventType, UUID partnerId, String partnerAs2Name, String messageId, String actor,
                         boolean success, String detail, Map<String, Change> changes, String source) {

    public static final String THIS_SERVICE = "partners-service";

    public AuditEvent {
        changes = changes == null ? Map.of() : Map.copyOf(changes);
    }

    /** Cambio administrativo de este servicio sobre un socio. */
    public static AuditEvent of(String eventType, UUID partnerId, String partnerAs2Name, String actor, String detail,
                                Map<String, Change> changes) {
        return new AuditEvent(eventType, partnerId, partnerAs2Name, null, actor, true, detail, changes, THIS_SERVICE);
    }

    /** Valor anterior y nuevo de un campo. */
    public record Change(Object from, Object to) {
    }
}
