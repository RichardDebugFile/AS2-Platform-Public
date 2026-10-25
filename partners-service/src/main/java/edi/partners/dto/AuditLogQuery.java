package edi.partners.dto;

import edi.partners.domain.AuditStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtros de la bitacora; todos opcionales y combinables. {@code from} es inclusivo y {@code to}
 * exclusivo. El usuario y el identificador AS2 no distinguen mayusculas.
 */
public record AuditLogQuery(UUID partnerId, String partnerAs2Name, String eventType, AuditStatus status,
                            String actor, String messageId, Instant from, Instant to) {
}
