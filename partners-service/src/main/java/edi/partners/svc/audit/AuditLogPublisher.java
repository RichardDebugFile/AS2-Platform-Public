package edi.partners.svc.audit;

import edi.partners.domain.AuditLogEntry;

/** Punto unico por el que se registra un evento en la bitacora (HU-10). */
public interface AuditLogPublisher {

    AuditLogEntry publish(AuditEvent event);
}
