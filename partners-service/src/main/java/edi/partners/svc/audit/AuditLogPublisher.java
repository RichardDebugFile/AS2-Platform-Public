package edi.partners.svc.audit;

/**
 * Punto unico por el que el servicio registra sus cambios administrativos. Hasta HU-10 la
 * implementacion escribe en el log; con HU-10 pasa a la tabla de bitacora sin tocar a quien publica.
 */
public interface AuditLogPublisher {

    void publish(AuditEvent event);
}
