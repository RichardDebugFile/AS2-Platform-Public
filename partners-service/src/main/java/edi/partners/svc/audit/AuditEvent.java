package edi.partners.svc.audit;

import java.util.UUID;

/**
 * Cambio administrativo que debe quedar en la bitacora.
 *
 * @param action  accion en forma {@code recurso:VERBO}, p. ej. {@code partners:CREATE}
 * @param partnerId socio afectado (puede ser nulo)
 * @param actor   usuario que lo hizo, o "system"
 * @param success si la operacion termino bien
 * @param detail  descripcion breve, sin datos sensibles
 */
public record AuditEvent(String action, UUID partnerId, String actor, boolean success, String detail) {
}
