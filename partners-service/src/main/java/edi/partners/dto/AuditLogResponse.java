package edi.partners.dto;

import edi.partners.domain.AuditLogEntry;
import edi.partners.domain.AuditStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Evento de la bitacora tal como lo ve la consola. */
public record AuditLogResponse(
        UUID id,
        Instant occurredAt,
        String eventType,
        AuditStatus status,
        String actor,
        String source,
        UUID partnerId,
        String partnerAs2Name,
        String messageId,
        String detail,
        Map<String, Object> changes) {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    public static AuditLogResponse of(AuditLogEntry e) {
        return new AuditLogResponse(e.getId(), e.getOccurredAt(), e.getEventType(), e.getStatus(), e.getActor(),
                e.getSource(), e.getPartnerId(), e.getPartnerAs2Name(), e.getMessageId(), e.getDetail(),
                e.getChanges() == null ? Map.of() : JSON.readValue(e.getChanges(), MAP));
    }
}
